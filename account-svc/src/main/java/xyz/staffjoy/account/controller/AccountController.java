package xyz.staffjoy.account.controller;

import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xyz.staffjoy.account.dto.*;
import xyz.staffjoy.account.service.AccountService;
import xyz.staffjoy.common.api.BaseResponse;
import xyz.staffjoy.common.auth.AuthConstant;
import xyz.staffjoy.common.auth.AuthContext;
import xyz.staffjoy.common.auth.Authorize;
import xyz.staffjoy.common.auth.PermissionDeniedException;
import xyz.staffjoy.common.env.EnvConfig;
import xyz.staffjoy.common.env.EnvConstant;
import xyz.staffjoy.common.error.ServiceException;
import xyz.staffjoy.common.validation.PhoneNumber;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;


@RestController
@RequestMapping("/v1/account")
@Validated
public class AccountController {

    //结构化日志
    static final ILogger logger = SLoggerFactory.getLogger(AccountController.class);

    @Autowired
    private AccountService accountService;

    @Autowired
    private EnvConfig envConfig;

    //获取或创建用户
    // GetOrCreate is for internal use by other APIs to match a user based on their phonenumber or email.
    @PostMapping(path = "/get_or_create")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_SUPPORT_USER,//支持的用户
            AuthConstant.AUTHORIZATION_WWW_SERVICE,//支持www服务调用该服务
            AuthConstant.AUTHORIZATION_COMPANY_SERVICE//支持company服务调用该服务
    })//控制器调用授权
    public GenericAccountResponse getOrCreate(@RequestBody @Valid GetOrCreateRequest request) {
        AccountDto accountDto = accountService.getOrCreate(request.getName(), request.getEmail(), request.getPhoneNumber());
        GenericAccountResponse genericAccountResponse = new GenericAccountResponse(accountDto);
        return genericAccountResponse;
    }

    //创建用户
    @PostMapping(path = "/create")
    @Authorize(value = {
                    AuthConstant.AUTHORIZATION_SUPPORT_USER,
                    AuthConstant.AUTHORIZATION_WWW_SERVICE,
                    AuthConstant.AUTHORIZATION_COMPANY_SERVICE
    })
    public GenericAccountResponse createAccount(@RequestBody @Valid CreateAccountRequest request) {
        AccountDto accountDto = accountService.create(request.getName(), request.getEmail(), request.getPhoneNumber());
        GenericAccountResponse genericAccountResponse = new GenericAccountResponse(accountDto);
        return genericAccountResponse;
    }

    //通过用户手机号码 获取账户信息
    @GetMapping(path = "/get_account_by_phonenumber")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_SUPPORT_USER,
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_COMPANY_SERVICE
    })
    public GenericAccountResponse getAccountByPhonenumber(@RequestParam @PhoneNumber String phoneNumber) {
        AccountDto accountDto = accountService.getAccountByPhoneNumber(phoneNumber);
        GenericAccountResponse genericAccountResponse = new GenericAccountResponse(accountDto);
        return genericAccountResponse;
    }

    //分页请求获取账户信息列表
    @GetMapping(path = "/list")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public ListAccountResponse listAccounts(@RequestParam int offset, @RequestParam @Min(0) int limit) {
        AccountList accountList = accountService.list(offset, limit);
        ListAccountResponse listAccountResponse = new ListAccountResponse(accountList);
        return listAccountResponse;
    }

    //通过userid获取账户信息
    @GetMapping(path = "/get")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_ACCOUNT_SERVICE,
            AuthConstant.AUTHORIZATION_COMPANY_SERVICE,
            AuthConstant.AUTHORIZATION_WHOAMI_SERVICE,
            AuthConstant.AUTHORIZATION_BOT_SERVICE,
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,//结果faraday认证过的用户
            AuthConstant.AUTHORIZATION_SUPPORT_USER,//用户角色为support
            AuthConstant.AUTHORIZATION_SUPERPOWERS_SERVICE//superpowers-service
    })
    public GenericAccountResponse getAccount(@RequestParam @NotBlank String userId) {
        //用户角色鉴权
        this.validateAuthenticatedUser(userId);
        //环境鉴权
        this.validateEnv();

        AccountDto accountDto = accountService.get(userId);

        GenericAccountResponse genericAccountResponse = new GenericAccountResponse(accountDto);
        return genericAccountResponse;
    }

    //更新用户信息
    @PutMapping(path = "/update")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_COMPANY_SERVICE,
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER,
            AuthConstant.AUTHORIZATION_SUPERPOWERS_SERVICE
    })
    public GenericAccountResponse updateAccount(@RequestBody @Valid AccountDto newAccountDto) {
        //用户角色鉴权
        this.validateAuthenticatedUser(newAccountDto.getId());
        //环境鉴权
        this.validateEnv();

        AccountDto accountDto =  accountService.update(newAccountDto);

        GenericAccountResponse genericAccountResponse = new GenericAccountResponse(accountDto);
        return genericAccountResponse;
    }

    //更新密码
    @PutMapping(path = "/update_password")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public BaseResponse updatePassword(@RequestBody @Valid UpdatePasswordRequest request) {
        //用户角色鉴权
        this.validateAuthenticatedUser(request.getUserId());

        //更新用户密码
        accountService.updatePassword(request.getUserId(), request.getPassword());

        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setMessage("password updated");

        return baseResponse;
    }

    //确认密码
    @PostMapping(path = "/verify_password")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public GenericAccountResponse verifyPassword(@RequestBody @Valid VerifyPasswordRequest request) {
        //确认密码
        AccountDto accountDto = accountService.verifyPassword(request.getEmail(), request.getPassword());

        GenericAccountResponse genericAccountResponse = new GenericAccountResponse(accountDto);
        return genericAccountResponse;
    }

    // RequestPasswordReset sends an email to a user with a password reset link
    //给用户邮箱发送一封邮件，内含重置密码请求的链接地址
    @PostMapping(path = "/request_password_reset")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public BaseResponse requestPasswordReset(@RequestBody @Valid PasswordResetRequest request) {
        //请求重置密码
        accountService.requestPasswordReset(request.getEmail());

        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setMessage("password reset requested");

        return baseResponse;
    }

    // RequestPasswordReset sends an email to a user with a password reset link
    //修改邮箱地址请求
    @PostMapping(path = "/request_email_change")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_AUTHENTICATED_USER,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public BaseResponse requestEmailChange(@RequestBody @Valid EmailChangeRequest request) {
        //用户角色鉴权
        this.validateAuthenticatedUser(request.getUserId());

        //修改邮箱地址请求
        accountService.requestEmailChange(request.getUserId(), request.getEmail());

        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setMessage("email change requested");

        return baseResponse;
    }

    // ChangeEmail sets an account to active and updates its email. It is
    // used after a user clicks a confirmation link in their email.
    //修改邮箱
    @PostMapping(path = "/change_email")
    @Authorize(value = {
            AuthConstant.AUTHORIZATION_WWW_SERVICE,
            AuthConstant.AUTHORIZATION_SUPPORT_USER
    })
    public BaseResponse changeEmail(@RequestBody @Valid EmailConfirmation request) {
        accountService.changeEmailAndActivateAccount(request.getUserId(), request.getEmail());

        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setMessage("email change requested");

        return baseResponse;
    }

    //同步用户事件到云客服系统Intercom上
    @PostMapping(path = "/track_event")
    public BaseResponse trackEvent(@RequestBody @Valid TrackEventRequest request) {
        //将 跟踪事件 信息 同步到云服务系统上
        accountService.trackEvent(request.getUserId(), request.getEvent());

        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setMessage("event tracked");

        return baseResponse;
    }

    //同步用户信息到云客服系统上 Intercom
    @PostMapping(path = "/sync_user")
    public BaseResponse syncUser(@RequestBody @Valid SyncUserRequest request) {
        accountService.syncUser(request.getUserId());

        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setMessage("user synced");

        return baseResponse;
    }

    /**
     * 用户角色鉴权
     * @param userId
     */
    private void validateAuthenticatedUser(String userId) {
        //获取请求头中属性名为"Authorization"的属性值
        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {//faraday-authenticated
            String currentUserId = AuthContext.getUserId();
            if (StringUtils.isEmpty(currentUserId)) {
                throw new ServiceException("failed to find current user id");
            }
            if (!userId.equals(currentUserId)) {
                //无权访问该服务
                throw new PermissionDeniedException("You do not have access to this service");
            }
        }
    }

    /**
     * 环境鉴权
     */
    private void validateEnv() {
        if (AuthConstant.AUTHORIZATION_SUPERPOWERS_SERVICE.equals(AuthContext.getAuthz())) {//superpowers-service
            if (!EnvConstant.ENV_DEV.equals(this.envConfig.getName())) {
                logger.warn("Development service trying to connect outside development environment");
                throw new PermissionDeniedException("This service is not available outside development environments");
            }
        }
    }
}
