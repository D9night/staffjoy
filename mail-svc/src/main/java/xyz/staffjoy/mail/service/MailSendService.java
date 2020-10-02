package xyz.staffjoy.mail.service;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest;
import com.aliyuncs.dm.model.v20151123.SingleSendMailResponse;
import com.aliyuncs.exceptions.ClientException;
import com.github.structlog4j.ILogger;
import com.github.structlog4j.IToLog;
import com.github.structlog4j.SLoggerFactory;
import io.sentry.SentryClient;
import io.sentry.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.staffjoy.common.env.EnvConfig;
import xyz.staffjoy.common.env.EnvConstant;
import xyz.staffjoy.mail.MailConstant;
import xyz.staffjoy.mail.config.AppConfig;
import xyz.staffjoy.mail.dto.EmailRequest;

@Service
public class MailSendService {

    private static ILogger logger = SLoggerFactory.getLogger(MailSendService.class);

    @Autowired
    EnvConfig envConfig;

    //阿里云邮件client
    @Autowired
    IAcsClient acsClient;

    @Autowired
    SentryClient sentryClient;

    //发送邮件 异步
    @Async(AppConfig.ASYNC_EXECUTOR_NAME)
    public void sendMailAsync(EmailRequest req) {
        //结构化日志
        IToLog logContext = () -> {
            return new Object[] {
                    "subject", req.getSubject(),
                    "to", req.getTo(),
                    "html_body", req.getHtmlBody()
            };
        };

        // In dev and uat - only send emails to @jskillcloud.com
        if (!EnvConstant.ENV_PROD.equals(envConfig.getName())) {
            // prepend env for sanity
            String subject = String.format("[%s] %s", envConfig.getName(), req.getSubject());
            req.setSubject(subject);

            /*if (!req.getTo().endsWith(MailConstant.STAFFJOY_EMAIL_SUFFIX)) {//@jskillcloud.com
                logger.warn("Intercepted sending due to non-production environment.");//非生产环境 邮件都拦截
                return;
            }*/
        }

        //构建阿里云邮件request
        SingleSendMailRequest mailRequest = new SingleSendMailRequest();
        mailRequest.setAccountName(MailConstant.FROM);
        mailRequest.setFromAlias(MailConstant.FROM_NAME);
        mailRequest.setAddressType(1);
        mailRequest.setToAddress(req.getTo());
        mailRequest.setReplyToAddress(false);
        mailRequest.setSubject(req.getSubject());
        mailRequest.setHtmlBody(req.getHtmlBody());

        try {
            //调用阿里云的client发送邮件
            SingleSendMailResponse mailResponse = acsClient.getAcsResponse(mailRequest);
            logger.info("Successfully sent email - request id : " + mailResponse.getRequestId(), logContext);
        } catch (ClientException ex) {
            //构建异常日志，并发送到异常日志sentry云服务
            Context sentryContext = sentryClient.getContext();
            sentryContext.addTag("subject", req.getSubject());
            sentryContext.addTag("to", req.getTo());
            sentryClient.sendException(ex);
            logger.error("Unable to send email ", ex, logContext);
        }
    }
}
