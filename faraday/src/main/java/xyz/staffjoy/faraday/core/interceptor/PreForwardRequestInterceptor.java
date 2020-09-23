package xyz.staffjoy.faraday.core.interceptor;

import xyz.staffjoy.faraday.config.MappingProperties;
import xyz.staffjoy.faraday.core.http.RequestData;

/**
 * 请求转发前拦截器
 * 注意和重定向redirect的区别
 */
public interface PreForwardRequestInterceptor {
    void intercept(RequestData data, MappingProperties mapping);
}
