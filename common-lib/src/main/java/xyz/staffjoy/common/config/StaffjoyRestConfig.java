package xyz.staffjoy.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import xyz.staffjoy.common.error.GlobalExceptionTranslator;
import xyz.staffjoy.common.aop.*;

/**
 * Use this common config for Rest API
 * 公共配置
 * 同时import SentryClientAspect Sentry异常日志 配置
 * GlobalExceptionTranslator 统一的异常处理、统一的异常捕获
 *
 */
@Configuration
@Import(value = {StaffjoyConfig.class, SentryClientAspect.class, GlobalExceptionTranslator.class})
public class StaffjoyRestConfig  {
}
