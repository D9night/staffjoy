package xyz.staffjoy.account.service.helper;

import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import com.google.common.collect.Maps;
import io.intercom.api.Avatar;
import io.intercom.api.CustomAttribute;
import io.intercom.api.Event;
import io.intercom.api.User;
import io.sentry.SentryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.staffjoy.account.config.AppConfig;
import xyz.staffjoy.account.model.Account;
import xyz.staffjoy.account.repo.AccountRepo;
import xyz.staffjoy.bot.client.BotClient;
import xyz.staffjoy.bot.dto.GreetingRequest;
import xyz.staffjoy.common.api.BaseResponse;
import xyz.staffjoy.common.api.ResultCode;
import xyz.staffjoy.common.auth.AuthConstant;
import xyz.staffjoy.common.env.EnvConfig;
import xyz.staffjoy.common.error.ServiceException;
import xyz.staffjoy.company.client.CompanyClient;
import xyz.staffjoy.company.dto.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 同步信息到异常日志云服务sentry
 * 以及同步用户信息到客服云服务上的
 * service辅助类
 */
@RequiredArgsConstructor
@Component
public class ServiceHelper {
    static final ILogger logger = SLoggerFactory.getLogger(ServiceHelper.class);

    private final CompanyClient companyClient;

    private final AccountRepo accountRepo;

    //异常日志云服务客户端
    private final SentryClient sentryClient;

    private final BotClient botClient;

    private final EnvConfig envConfig;

    /**
     * 同步用户信息到客服云服务上
     * 采取异步的形式
     * @param userId
     */
    @Async(AppConfig.ASYNC_EXECUTOR_NAME)
    public void syncUserAsync(String userId) {
        if (envConfig.isDebug()) {
            //开发和测试环境不同步信息到客服系统上
            logger.debug("intercom disabled in dev & test environment");
            return;
        }

        Account account = accountRepo.findAccountById(userId);
        if (account == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, String.format("User with id %s not found", userId));
        }
        if (StringUtils.isEmpty(account.getPhoneNumber()) && StringUtils.isEmpty(account.getEmail())) {
            logger.info(String.format("skipping sync for user %s because no email or phonenumber", account.getId()));
            return;
        }

        // use a map to de-dupe
        Map<String, CompanyDto> memberships = new HashMap<>();

        GetWorkerOfResponse workerOfResponse = null;
        try {
            //公司雇员信息
            workerOfResponse = companyClient.getWorkerOf(AuthConstant.AUTHORIZATION_ACCOUNT_SERVICE, userId);
        } catch(Exception ex) {
            String errMsg = "could not fetch workOfList";
            //将异常日志发送到sentry 异常日志云服务上
            handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }
        if (!workerOfResponse.isSuccess()) {
            //将错误信息发送到sentry 异常日志云服务上
            handleError(logger, workerOfResponse.getMessage());
            throw new ServiceException(workerOfResponse.getMessage());
        }
        WorkerOfList workerOfList = workerOfResponse.getWorkerOfList();

        boolean isWorker = workerOfList.getTeams().size() > 0;
        //遍历团队信息
        for(TeamDto teamDto : workerOfList.getTeams()) {
            GenericCompanyResponse genericCompanyResponse = null;
            try {
                //获取公司信息
                genericCompanyResponse = companyClient.getCompany(AuthConstant.AUTHORIZATION_ACCOUNT_SERVICE, teamDto.getCompanyId());
            } catch (Exception ex) {
                String errMsg = "could not fetch companyDto from teamDto";
                //将异常日志发送到sentry 异常日志云服务上
                handleException(logger, ex, errMsg);
                throw new ServiceException(errMsg, ex);
            }

            if (!genericCompanyResponse.isSuccess()) {
                //将错误信息发送到sentry 异常日志云服务上
                handleError(logger, genericCompanyResponse.getMessage());
                throw new ServiceException(genericCompanyResponse.getMessage());
            }
            //公司信息
            CompanyDto companyDto = genericCompanyResponse.getCompany();

            memberships.put(companyDto.getId(), companyDto);
        }

        GetAdminOfResponse getAdminOfResponse = null;
        try {
            //获取管理员信息
            getAdminOfResponse = companyClient.getAdminOf(AuthConstant.AUTHORIZATION_ACCOUNT_SERVICE, userId);
        } catch (Exception ex) {
            String errMsg = "could not fetch adminOfList";
            //将异常日志发送到sentry 异常日志云服务上
            handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }
        if (!getAdminOfResponse.isSuccess()) {
            //将错误信息发送到sentry 异常日志云服务上
            handleError(logger, getAdminOfResponse.getMessage());
            throw new ServiceException(getAdminOfResponse.getMessage());
        }
        AdminOfList adminOfList = getAdminOfResponse.getAdminOfList();

        boolean isAdmin = adminOfList.getCompanies().size() > 0;
        for(CompanyDto companyDto : adminOfList.getCompanies()) {
            memberships.put(companyDto.getId(), companyDto);
        }

        /**
         * 下面为构造云客服系统Intercom的用户信息
         * 依赖第三方包io.intercom
         */
        User user = new User();
        user.setUserId(account.getId());
        user.setEmail(account.getEmail());
        user.setName(account.getName());
        user.setSignedUpAt(account.getMemberSince().toEpochMilli());
        user.setAvatar(new Avatar().setImageURL(account.getPhotoUrl()));
        user.setLastRequestAt(Instant.now().toEpochMilli());

        user.addCustomAttribute(CustomAttribute.newBooleanAttribute("v2", true));
        user.addCustomAttribute(CustomAttribute.newStringAttribute("phonenumber", account.getPhoneNumber()));
        user.addCustomAttribute(CustomAttribute.newBooleanAttribute("confirmed_and_active", account.isConfirmedAndActive()));
        user.addCustomAttribute(CustomAttribute.newBooleanAttribute("is_worker", isWorker));
        user.addCustomAttribute(CustomAttribute.newBooleanAttribute("is_admin", isAdmin));
        user.addCustomAttribute(CustomAttribute.newBooleanAttribute("is_staffjoy_support", account.isSupport()));

        for(CompanyDto companyDto : memberships.values()) {
            user.addCompany(new io.intercom.api.Company().setCompanyID(companyDto.getId()).setName(companyDto.getName()));
        }

        //同步用户信息到云客服系统
        this.syncUserWithIntercom(user, account.getId());
    }

    /**
     * 同步用户信息到云客服系统
     * @param user
     * @param userId
     */
    void syncUserWithIntercom(User user, String userId) {
        try {
            Map<String, String> params = Maps.newHashMap();
            params.put("user_id", userId);

            //根据user_id，查询云客服系统是否已经有该用户信息
            User existing = User.find(params);

            if (existing != null) {
                //已经存在，即更新信息
                User.update(user);
            } else {
                //无，则创建新的用户
                User.create(user);
            }

            logger.debug("updated intercom");
        } catch (Exception ex) {
            String errMsg = "fail to create/update user on Intercom";
            //将异常日志发送到sentry 异常日志云服务上
            handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }
    }

    /**
     * 异步跟踪事件到
     * 云客服系统
     * 依赖第三方包 io.intercom.api
     * @param userId
     * @param eventName
     */
    @Async(AppConfig.ASYNC_EXECUTOR_NAME)
    public void trackEventAsync(String userId, String eventName) {
        if (envConfig.isDebug()) {
            //开发和测试环境不同步
            logger.debug("intercom disabled in dev & test environment");
            return;
        }

        //构建 实践  依赖第三方包 io.intercom.api
        Event event = new Event()
                .setUserID(userId)
                .setEventName("v2_" + eventName)
                .setCreatedAt(Instant.now().toEpochMilli());

        try {
            //创建事件 到云客服系统
            Event.create(event);
        } catch (Exception ex) {
            String errMsg = "fail to create event on Intercom";
            //将异常日志发送到sentry 异常日志云服务上
            handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }

        logger.debug("updated intercom");
    }

    /**
     * 给用户发送问候短信
     * @param userId
     */
    public void sendSmsGreeting(String userId) {
        BaseResponse baseResponse = null;
        try {
            GreetingRequest greetingRequest = GreetingRequest.builder().userId(userId).build();
            //发送问候短信
            baseResponse = botClient.sendSmsGreeting(greetingRequest);
        } catch (Exception ex) {
            String errMsg = "could not send welcome sms";
            //将异常日志发送到sentry 异常日志云服务上
            handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }
        if (!baseResponse.isSuccess()) {
            //将错误信息发送到sentry 异常日志云服务上
            handleError(logger, baseResponse.getMessage());
            throw new ServiceException(baseResponse.getMessage());
        }
    }

    // for time diff < 2s, treat them as almost same
    //同一账户 间隔时间太短，则不给进行更新信息
    public boolean isAlmostSameInstant(Instant dt1, Instant dt2) {
        long diff = dt1.toEpochMilli() - dt2.toEpochMilli();
        diff = Math.abs(diff);
        if (diff < TimeUnit.SECONDS.toMillis(1)) {
            return true;
        }
        return false;
    }

    /**
     * 将错误信息发送到sentry 异常日志云服务上
     * @param log
     * @param errMsg
     */
    public void handleError(ILogger log, String errMsg) {
        log.error(errMsg);
        if (!envConfig.isDebug()) {
            sentryClient.sendMessage(errMsg);
        }
    }

    /**
     * 处理异常日志
     * 主要是将异常日志发送到sentry 异常日志云服务上
     * @param log
     * @param ex
     * @param errMsg
     */
    public void handleException(ILogger log, Exception ex, String errMsg) {
        log.error(errMsg, ex);
        if (!envConfig.isDebug()) {
            sentryClient.sendException(ex);
        }
    }
}
