package xyz.staffjoy.common.config;

import com.github.structlog4j.StructLog4J;
import com.github.structlog4j.json.JsonFormatter;
import feign.RequestInterceptor;
import io.sentry.Sentry;
import io.sentry.SentryClient;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.staffjoy.common.auth.AuthorizeInterceptor;
import xyz.staffjoy.common.auth.FeignRequestHeaderInterceptor;
import xyz.staffjoy.common.env.EnvConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * StaffjoyConfig 公共配置
 */
@Configuration
@EnableConfigurationProperties(StaffjoyProps.class)
public class StaffjoyConfig implements WebMvcConfigurer {

    //激活的配置文件
    @Value("${spring.profiles.active:NA}")
    private String activeProfile;

    @Value("${spring.application.name:NA}")
    private String appName;

    @Autowired
    StaffjoyProps staffjoyProps;

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    //环境配置
    @Bean
    public EnvConfig envConfig() {
        return EnvConfig.getEnvConfg(activeProfile);
    }

    //异常日志 Sentry Client 依赖第三方库
    @Bean
    public SentryClient sentryClient() {

        SentryClient sentryClient = Sentry.init(staffjoyProps.getSentryDsn());
        sentryClient.setEnvironment(activeProfile);
        sentryClient.setRelease(staffjoyProps.getDeployEnv());
        sentryClient.addTag("service", appName);

        return sentryClient;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //注册服务间调用授权截获器
        registry.addInterceptor(new AuthorizeInterceptor());
    }

    //初始化请求截获器
    @Bean
    public RequestInterceptor feignRequestInterceptor() {
        //Feign客户端传递用户认证信息
        return new FeignRequestHeaderInterceptor();
    }

    @PostConstruct
    public void init() {
        // init structured logging
        //结构化日志 依赖第三方库
        StructLog4J.setFormatter(JsonFormatter.getInstance());

        // global log fields setting
        StructLog4J.setMandatoryContextSupplier(() -> new Object[]{
                "env", activeProfile,
                "service", appName});
    }

    @PreDestroy
    public void destroy() {
        sentryClient().closeConnection();
    }
}
