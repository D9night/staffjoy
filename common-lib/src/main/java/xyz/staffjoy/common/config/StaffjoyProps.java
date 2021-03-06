package xyz.staffjoy.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotBlank;

/**
 * 公共属性
 * 见配置文件application.yml中设置
 */
@ConfigurationProperties(prefix="staffjoy.common")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffjoyProps {
    //异常日志云服务提供的dsn
    @NotBlank
    private String sentryDsn;
    @NotBlank
    // DeployEnvVar is set by Kubernetes during a new deployment so we can identify the code version
    private String deployEnv;
}

