package xyz.staffjoy.common.auth;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 *
 * A context holder class for holding the current userId and authz info
 * 认证上线文助手类
 * 在本项目中主要是用来向后传递userid
 *
 * @author bobo
 */
public class AuthContext {

    /**
     * 获取请求头中属性为
     * headerName的属性值
     * @param headerName 请求头中属性名
     * @return
     */
    private static String getRequetHeader(String headerName) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes)requestAttributes).getRequest();
            String value = request.getHeader(headerName);
            return value;
        }
        return null;
    }

    public static String getUserId() {
        return getRequetHeader(AuthConstant.CURRENT_USER_HEADER);//faraday-current-user-id
    }

    public static String getAuthz() {
        //获取请求头中属性名为"Authorization"的属性值
        return getRequetHeader(AuthConstant.AUTHORIZATION_HEADER);//Authorization
    }

}
