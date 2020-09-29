package xyz.staffjoy.sms.props;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

/**
 * 阿里云短信通知服务相关密匙
 *
 * 这个类怎么初始化
 * 见application-prod.yml文件中
 */
@Component
@ConfigurationProperties(prefix="staffjoy")//配置文件前缀
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppProps {

    // aliyun directmail props
    @NotNull private String aliyunAccessKey;
    @NotNull private String aliyunAccessSecret;
    @NotNull private String aliyunSmsSignName;

    //白名单 短信只允许发送到这些phoneNumber
    private boolean whiteListOnly;
    private String whiteListPhoneNumbers;
    private int concurrency;

}
