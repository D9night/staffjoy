package xyz.staffjoy.common.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.util.StringUtils;

/**
 * Feign interceptor，for passing auth info to backend
 * Feign客户端传递用户认证信息
 *
 * @author bobo
 */
public class FeignRequestHeaderInterceptor implements RequestInterceptor {//继承自feign包中的RequestInterceptor接口

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String userId = AuthContext.getUserId();
        if (!StringUtils.isEmpty(userId)) {
            requestTemplate.header(AuthConstant.CURRENT_USER_HEADER, userId);
        }
    }
}
