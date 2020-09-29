package xyz.staffjoy.bot.service;

import com.github.structlog4j.ILogger;
import com.github.structlog4j.SLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.staffjoy.account.dto.AccountDto;

@Service
public class GreetingService {

    static final ILogger logger = SLoggerFactory.getLogger(GreetingService.class);

    @Autowired
    HelperService helperService;

    public void greeting(String userId) {
        AccountDto account = helperService.getAccountById(userId);

        DispatchPreference dispatchPreference = helperService.getPreferredDispatch(account);
        switch (dispatchPreference) {
            case DISPATCH_SMS://短信
                helperService.smsGreetingAsync(account.getPhoneNumber());
                break;
            case DISPATCH_EMAIL://邮件
                helperService.mailGreetingAsync(account);
                break;
            default:
                logger.info("Unable to send greeting to user %s - no comm method found", userId);
        }
    }
}
