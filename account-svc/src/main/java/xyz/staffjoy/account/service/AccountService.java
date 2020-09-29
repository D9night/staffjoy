package xyz.staffjoy.account.service;

import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.staffjoy.account.AccountConstant;
import xyz.staffjoy.account.dto.AccountDto;
import xyz.staffjoy.account.model.Account;
import xyz.staffjoy.account.model.AccountSecret;
import xyz.staffjoy.account.props.AppProps;
import xyz.staffjoy.account.dto.AccountList;
import xyz.staffjoy.account.repo.AccountRepo;
import xyz.staffjoy.account.repo.AccountSecretRepo;
import xyz.staffjoy.account.service.helper.ServiceHelper;
import xyz.staffjoy.common.api.BaseResponse;
import xyz.staffjoy.common.api.ResultCode;
import xyz.staffjoy.common.auditlog.LogEntry;
import xyz.staffjoy.common.auth.AuthConstant;
import xyz.staffjoy.common.auth.AuthContext;
import xyz.staffjoy.common.crypto.Sign;
import xyz.staffjoy.common.env.EnvConfig;
import xyz.staffjoy.common.error.ServiceException;
import xyz.staffjoy.common.utils.Helper;
import xyz.staffjoy.mail.client.MailClient;
import xyz.staffjoy.mail.dto.EmailRequest;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class AccountService {

    static ILogger logger = SLoggerFactory.getLogger(AccountService.class);

    private final AccountRepo accountRepo;

    private final AccountSecretRepo accountSecretRepo;

    private final AppProps appProps;

    private final EnvConfig envConfig;

    private final MailClient mailClient;

    private final ServiceHelper serviceHelper;

    //org.springframework.security.crypto
    private final PasswordEncoder passwordEncoder;

    private final ModelMapper modelMapper;

    @PersistenceContext
    private EntityManager entityManager;

    //获得或创建账户
    // GetOrCreate is for internal use by other APIs to match a user based on their phonenumber or email.
    public AccountDto getOrCreate(String name, String email, String phoneNumber) {
        // rely on downstream permissions

        // check for existing user
        Account existingAccount = null;
        if (StringUtils.hasText(email)) {
            existingAccount = accountRepo.findAccountByEmail(email);
        }
        if (existingAccount == null && StringUtils.hasText(phoneNumber)) {
            existingAccount = accountRepo.findAccountByPhoneNumber(phoneNumber);
        }

        if (existingAccount != null) {
            return this.convertToDto(existingAccount);
        }
        //直接创建账户
        return this.create(name, email, phoneNumber);
    }

    /**
     * 通过用户手机号码 获取账户信息
     * @param phoneNumber
     * @return
     */
    public AccountDto getAccountByPhoneNumber(String phoneNumber) {
        Account account = accountRepo.findAccountByPhoneNumber(phoneNumber);
        if (account == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "User with specified phonenumber not found");
        }
        //DTM转化到DTO
        return this.convertToDto(account);
    }

    /**
     * 创建账户
     * @param name 姓名
     * @param email 邮箱
     * @param phoneNumber 手机号码
     * @return
     */
    public AccountDto create(String name, String email, String phoneNumber) {
        if (StringUtils.hasText(email)) {
            // Check to see if account exists
            Account foundAccount = accountRepo.findAccountByEmail(email);
            if (foundAccount != null) {
                throw new ServiceException("A user with that email already exists. Try a password reset");
            }
        }
        if (StringUtils.hasText(phoneNumber)) {
            Account foundAccount = accountRepo.findAccountByPhoneNumber(phoneNumber);
            if (foundAccount != null) {
                throw new ServiceException("A user with that phonenumber already exists. Try a password reset");
            }
        }

        // Column name/email/phone_number cannot be null
        if (name == null) {
            name = "";
        }
        if (email == null) {
            email = "";
        }
        if (phoneNumber == null) {
            phoneNumber = "";
        }

        Account account = Account.builder()
                .email(email).name(name).phoneNumber(phoneNumber)
                .build();
        //设置图像url
        account.setPhotoUrl(Helper.generateGravatarUrl(account.getEmail()));
        account.setMemberSince(Instant.now());

        try {
            accountRepo.save(account);
        } catch (Exception ex) {
            String errMsg = "Could not create user account";
            //生成异常日志，同步到sentry 异常日志云服务上
            serviceHelper.handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }

        //同步用户信息到 Intercom 客服云服务上(采取异步形式)
        serviceHelper.syncUserAsync(account.getId());

        if (StringUtils.hasText(email)) {
            // Email confirmation

            String emailName = name;
            if (StringUtils.isEmpty(emailName)) {
                emailName = "there";
            }

            String subject = "Activate your Staffjoy account";
            //给用户邮箱发送通知邮件,通知用户激活账户
            this.sendEmail(account.getId(), email, emailName, subject, AccountConstant.ACTIVATE_ACCOUNT_TMPL, true);
        }

        // todo - sms onboarding (if worker??)

        LogEntry auditLog = LogEntry.builder()
                .authorization(AuthContext.getAuthz())
                .currentUserId(AuthContext.getUserId())
                .targetType("account")
                .targetId(account.getId())
                .updatedContents(account.toString())
                .build();

        logger.info("created account", auditLog);

        AccountDto accountDto = this.convertToDto(account);
        return accountDto;
    }

    /**
     * 获取账户信息列表
     * @param offset
     * @param limit
     * @return
     */
    public AccountList list(int offset, int limit) {
        if (limit <= 0) {
            limit = 10;
        }

        //分页请求
        Pageable pageRequest = PageRequest.of(offset, limit);
        Page<Account> accountPage = accountRepo.findAll(pageRequest);
        List<AccountDto> accountDtoList = accountPage.getContent().stream().map(account -> convertToDto(account)).collect(toList());

        return AccountList.builder()
                .limit(limit)
                .offset(offset)
                .accounts(accountDtoList)
                .build();
    }

    /**
     * 通过userid获取用户信息
     * @param userId
     * @return
     */
    public AccountDto get(String userId) {
        Account account = accountRepo.findAccountById(userId);
        if (account == null) {
            throw new ServiceException(String.format("User with id %s not found", userId));
        }
        return this.convertToDto(account);
    }

    /**
     * 更新账户信息
     * @param newAccountDto
     * @return
     */
    public AccountDto update(AccountDto newAccountDto) {
        //DTO转化到DMO
        Account newAccount = this.convertToModel(newAccountDto);

        Account existingAccount = accountRepo.findAccountById(newAccount.getId());
        if (existingAccount == null) {
            //该处异常会被统一捕获
            throw new ServiceException(ResultCode.NOT_FOUND, String.format("User with id %s not found", newAccount.getId()));
        }
        //不知道这步干嘛用的？
        entityManager.detach(existingAccount);

        //同一账户 间隔时间太短，则不给进行更新信息
        if (!serviceHelper.isAlmostSameInstant(newAccount.getMemberSince(), existingAccount.getMemberSince())) {
            throw new ServiceException(ResultCode.REQ_REJECT, "You cannot modify the member_since date");
        }

        //注册邮箱不同，则予与更新
        if (StringUtils.hasText(newAccount.getEmail()) && !newAccount.getEmail().equals(existingAccount.getEmail())) {
            Account foundAccount = accountRepo.findAccountByEmail(newAccount.getEmail());
            if (foundAccount != null) {
                throw new ServiceException(ResultCode.REQ_REJECT, "A user with that email already exists. Try a password reset");
            }
        }

        //注册手机号码不同，则予更新
        if (StringUtils.hasText(newAccount.getPhoneNumber()) && !newAccount.getPhoneNumber().equals(existingAccount.getPhoneNumber())) {
            Account foundAccount = accountRepo.findAccountByPhoneNumber(newAccount.getPhoneNumber());
            if (foundAccount != null) {
                throw new ServiceException(ResultCode.REQ_REJECT, "A user with that phonenumber already exists. Try a password reset");
            }
        }

        if (AuthConstant.AUTHORIZATION_AUTHENTICATED_USER.equals(AuthContext.getAuthz())) {//鉴权信息通过
            //为进行激活账户，则不予更新
            if (!existingAccount.isConfirmedAndActive() && newAccount.isConfirmedAndActive()) {
                throw new ServiceException(ResultCode.REQ_REJECT, "You cannot activate this account");
            }
            //修改support角色，则不予更新
            if (existingAccount.isSupport() != newAccount.isSupport()) {
                throw new ServiceException(ResultCode.REQ_REJECT, "You cannot change the support parameter");
            }
            //更改用户头像地址，则不予更新
            if (!existingAccount.getPhotoUrl().equals(newAccount.getPhotoUrl())) {
                throw new ServiceException(ResultCode.REQ_REJECT, "You cannot change the photo through this endpoint (see docs)");
            }
            //当更新的账户email信息不相同是，则进行修改注册的邮箱信息
            // User can request email change - not do it :-)
            if (!existingAccount.getEmail().equals(newAccount.getEmail())) {
                //修改邮箱地址请求
                this.requestEmailChange(newAccount.getId(), newAccount.getEmail());
                // revert
                newAccount.setEmail(existingAccount.getEmail());
            }
        }

        //设置头像地址
        newAccount.setPhotoUrl(Helper.generateGravatarUrl(newAccount.getEmail()));

        try {
            accountRepo.save(newAccount);
        } catch (Exception ex) {
            String errMsg = "Could not update the user account";
            //将异常日志发送到sentry 异常日志云服务上
            serviceHelper.handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }

        //同步用户信息到客服云服务上
        serviceHelper.syncUserAsync(newAccount.getId());

        //审计日志 依赖第三方包
        LogEntry auditLog = LogEntry.builder()
                .authorization(AuthContext.getAuthz())
                .currentUserId(AuthContext.getUserId())
                .targetType("account")
                .targetId(newAccount.getId())
                .originalContents(existingAccount.toString())
                .updatedContents(newAccount.toString())
                .build();

        //结构化日志
        logger.info("updated account", auditLog);

        // If account is being activated, or if phone number is changed by current user - send text
        if (newAccount.isConfirmedAndActive() &&
                StringUtils.hasText(newAccount.getPhoneNumber()) &&
                !newAccount.getPhoneNumber().equals(existingAccount.getPhoneNumber())) {
            //给用户发送问候短信
            serviceHelper.sendSmsGreeting(newAccount.getId());
        }

        //将 跟踪事件 信息 发送到 云客服系统
        this.trackEventWithAuthCheck("account_updated");

        //DTM转化到DTO
        AccountDto accountDto = this.convertToDto(newAccount);
        return accountDto;
    }

    /**
     * 更新用户密码
     * @param userId
     * @param password
     */
    public void updatePassword(String userId, String password) {
        //spring.security.crypto
        String pwHash = passwordEncoder.encode(password);

        //更新用户密码
        int affected = accountSecretRepo.updatePasswordHashById(pwHash, userId);
        if (affected != 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "user with specified id not found");
        }

        //审计日志
        LogEntry auditLog = LogEntry.builder()
                .authorization(AuthContext.getAuthz())
                .currentUserId(AuthContext.getUserId())
                .targetType("account")
                .targetId(userId)
                .build();

        //结构化日志
        logger.info("updated password", auditLog);

        //将 跟踪事件 信息 发送到 云客服系统
        this.trackEventWithAuthCheck("password_updated");
    }

    /**
     * 确认密码
     * @param email
     * @param password
     * @return
     */
    public AccountDto verifyPassword(String email, String password) {
        AccountSecret accountSecret = accountSecretRepo.findAccountSecretByEmail(email);
        if (accountSecret == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "account with specified email not found");
        }

        if (!accountSecret.isConfirmedAndActive()) {
            throw new ServiceException(ResultCode.REQ_REJECT, "This user has not confirmed their account");
        }

        if (StringUtils.isEmpty(accountSecret.getPasswordHash())) {
            throw new ServiceException(ResultCode.REQ_REJECT, "This user has not set up their password");
        }

        if (!passwordEncoder.matches(password, accountSecret.getPasswordHash())) {
            throw new ServiceException(ResultCode.UN_AUTHORIZED, "Incorrect password");
        }

        Account account = accountRepo.findAccountById(accountSecret.getId());
        if (account == null) {
            throw new ServiceException(String.format("User with id %s not found", accountSecret.getId()));
        }

        // You shall pass
        //DTM转化到DTO
        AccountDto accountDto = this.convertToDto(account);
        return accountDto;
    }

    // RequestPasswordReset sends an email to a user with a password reset link

    /**
     * 请求重置密码
     * 给用户邮箱发送一封邮件，内含重置密码请求的链接地址
     * @param email
     */
    public void requestPasswordReset(String email) {
        String newEmail = email.toLowerCase().trim();

        Account account = accountRepo.findAccountByEmail(email);
        if(account == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "No user with that email exists");
        }

        String subject = "Reset your Staffjoy password";
        boolean activate = false; // reset
        String tmpl = AccountConstant.RESET_PASSWORD_TMPL;
        if (!account.isConfirmedAndActive()) {
            // Not actually active - make some tweaks for activate instead of password reset
            activate = true; // activate
            subject = "Activate your Staffjoy account";
            tmpl = AccountConstant.ACTIVATE_ACCOUNT_TMPL;
        }

        // Send verification email
        //发送确认邮件
        this.sendEmail(account.getId(), email, account.getName(), subject, tmpl, activate);
    }

    // requestEmailChange sends an email to a user with a confirm email link

    /**
     * 修改邮箱地址请求
     * @param userId
     * @param email
     */
    public void requestEmailChange(String userId, String email) {
        Account account = accountRepo.findAccountById(userId);
        if (account == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, String.format("User with id %s not found", userId));
        }

        String subject = "Confirm Your New Email Address";
        //发送“确认邮件”的邮件
        this.sendEmail(account.getId(), email, account.getName(), subject, AccountConstant.CONFIRM_EMAIL_TMPL, true);
    }

    // ChangeEmail sets an account to active and updates its email. It is
    // used after a user clicks a confirmation link in their email.
    //修改邮件并激活账户
    public void changeEmailAndActivateAccount(String userId, String email) {

        int affected = accountRepo.updateEmailAndActivateById(email, userId);
        if (affected != 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "user with specified id not found");
        }

        //同步用户信息到客服云服务上
        serviceHelper.syncUserAsync(userId);

        //审计日志
        LogEntry auditLog = LogEntry.builder()
                .authorization(AuthContext.getAuthz())
                .currentUserId(AuthContext.getUserId())
                .targetType("account")
                .targetId(userId)
                .updatedContents(email)
                .build();

        //结构化日志
        logger.info("changed email", auditLog);

        //将 跟踪事件 信息 发送到 云客服系统
        this.trackEventWithAuthCheck("email_updated");
    }

    /**
     * 给用户邮箱发送通知邮件
     * 通知用户激活或者确认身份
     * @param userId
     * @param email
     * @param name
     * @param subject 邮件主题
     * @param template  通知邮件模板
     * @param activateOrConfirm
     */
    void sendEmail(String userId, String email, String name, String subject, String template, boolean activateOrConfirm) {
        String token = null;
        try {
            //生成邮件确认token
            token = Sign.generateEmailConfirmationToken(userId, email, appProps.getSigningSecret());
        } catch(Exception ex) {
            String errMsg = "Could not create token";
            //将异常日志发送到sentry 异常日志云服务上
            serviceHelper.handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }

        //激活
        String pathFormat = "/activate/%s";
        if (!activateOrConfirm) {
            //重置
            pathFormat = "/reset/%s";
        }
        //路径
        String path = String.format(pathFormat, token);
        URI link = null;
        try {
            //生成激活的链接
            link = new URI("http", "www." + envConfig.getExternalApex(), path, null);
        } catch (URISyntaxException ex) {
            String errMsg = "Could not create activation url";
            if (!activateOrConfirm) {
                errMsg = "Could not create reset url";
            }
            //将异常日志发送到sentry 异常日志云服务上
            serviceHelper.handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }

        //邮件内容
        String htmlBody = null;
        if (activateOrConfirm) { // active or confirm
            htmlBody = String.format(template, name, link.toString(), link.toString(), link.toString());
        } else { // reset
            htmlBody = String.format(template, link.toString(), link.toString());
        }

        //邮件请求
        EmailRequest emailRequest = EmailRequest.builder()
                .to(email)
                .name(name)
                .subject(subject)
                .htmlBody(htmlBody)
                .build();

        BaseResponse baseResponse = null;
        try {
            //调用邮件服务发送邮件
            baseResponse = mailClient.send(emailRequest);
        } catch (Exception ex) {
            String errMsg = "Unable to send email";
            //将异常日志发送到sentry 异常日志云服务上
            serviceHelper.handleException(logger, ex, errMsg);
            throw new ServiceException(errMsg, ex);
        }
        if (!baseResponse.isSuccess()) {
            //将错误日志发送到sentry 异常日志云服务上
            serviceHelper.handleError(logger, baseResponse.getMessage());
            throw new ServiceException(baseResponse.getMessage());
        }
    }

    /**
     * 将 跟踪事件 信息 同步到云服务系统上
     * @param userId
     * @param eventName
     */
    public void trackEvent(String userId, String eventName) {
        serviceHelper.trackEventAsync(userId, eventName);
    }

    /**
     * 同步用户信息到客服云服务上
     * @param userId
     */
    public void syncUser(String userId) {
        //同步用户信息到客服云服务上
        serviceHelper.syncUserAsync(userId);
    }

    //DTM转化到DTO
    private AccountDto convertToDto(Account account) {
        return modelMapper.map(account, AccountDto.class);
    }

    //DTO转化到DMO
    private Account convertToModel(AccountDto accountDto) {
        return modelMapper.map(accountDto, Account.class);
    }

    /**
     * 将 跟踪事件 信息 发送到 云客服系统
     * @param eventName
     */
    private void trackEventWithAuthCheck(String eventName) {
        String userId = AuthContext.getUserId();
        if (StringUtils.isEmpty(userId)) {
            // Not an action performed by a normal user
            // (noop - not an view)
            return;
        }

        //将 跟踪事件 信息 同步到云服务系统上
        this.trackEvent(userId, eventName);
    }

}
