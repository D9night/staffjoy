package xyz.staffjoy.common.auth;

import xyz.staffjoy.common.crypto.Sign;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * session
 */
public class Sessions {
    public static final long SHORT_SESSION = TimeUnit.HOURS.toMillis(12);
    public static final long LONG_SESSION = TimeUnit.HOURS.toMillis(30 * 24);

    /**
     * 用户登录
     * @param userId
     * @param support
     * @param rememberMe
     * @param signingSecret
     * @param externalApex
     * @param response
     */
    public static void loginUser(String userId,
                                 boolean support,
                                 boolean rememberMe,
                                 String signingSecret,
                                 String externalApex,
                                 HttpServletResponse response) {
        long duration;
        int maxAge;

        if (rememberMe) {
            // "Remember me"
            duration = LONG_SESSION;
        } else {
            duration = SHORT_SESSION;
        }
        maxAge = (int) (duration / 1000);

        //生成JWT
        String token = Sign.generateSessionToken(userId, signingSecret, support, duration);

        /**
         * 登录login中Cookie
         */
        Cookie cookie = new Cookie(AuthConstant.COOKIE_NAME, token);//staffjoy-faraday
        cookie.setPath("/");
        cookie.setDomain(externalApex);
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);//浏览器中js不能操作cookie 防止xss跨站点攻击
        response.addCookie(cookie);
    }

    /**
     * Cookie中取出JWT令牌
     * @param request
     * @return
     */
    public static String getToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) return null;
        Cookie tokenCookie = Arrays.stream(cookies)
                .filter(cookie -> AuthConstant.COOKIE_NAME.equals(cookie.getName()))
                .findAny().orElse(null);
        if (tokenCookie == null) return null;
        return tokenCookie.getValue();
    }

    /**
     * 用户登出
     * @param externalApex
     * @param response
     */
    public static void logout(String externalApex, HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthConstant.COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setDomain(externalApex);
        response.addCookie(cookie);
    }
}
