package xyz.staffjoy.common.crypto;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.util.StringUtils;
import xyz.staffjoy.common.error.ServiceException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 相关算法类
 */
public class Sign {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_SUPPORT = "support";//用户的角色

    private static Map<String, JWTVerifier> verifierMap = new HashMap<>();
    private static Map<String, Algorithm> algorithmMap = new HashMap<>();

    /**
     * 获取生成jwt的流程算法
     * @param signingToken
     * @return
     */
    private static Algorithm getAlgorithm(String signingToken) {
        Algorithm algorithm = algorithmMap.get(signingToken);
        if (algorithm == null) {
            synchronized (algorithmMap) {
                algorithm = algorithmMap.get(signingToken);
                if (algorithm == null) {
                    //默认采用HMAC流程 使用相同的Secret
                    algorithm = Algorithm.HMAC512(signingToken);
                    algorithmMap.put(signingToken, algorithm);
                }
            }
        }
        return algorithm;
    }

    public static String generateEmailConfirmationToken(String userId, String email, String signingToken) {
        Algorithm algorithm = getAlgorithm(signingToken);
        String token = JWT.create()
                .withClaim(CLAIM_EMAIL, email)
                .withClaim(CLAIM_USER_ID, userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)))
                .sign(algorithm);
        return token;
    }

    public static DecodedJWT verifyEmailConfirmationToken(String tokenString, String signingToken) {
        return verifyToken(tokenString, signingToken);
    }

    /**
     * JWT校验算法
     * @param tokenString
     * @param signingToken 即相关的secret
     * @return
     */
    public static DecodedJWT verifySessionToken(String tokenString, String signingToken) {
        return verifyToken(tokenString, signingToken);
    }

    /**
     * JWT校验算法
     * @param tokenString
     * @param signingToken
     * @return
     */
    static DecodedJWT verifyToken(String tokenString, String signingToken) {
        JWTVerifier verifier = verifierMap.get(signingToken);
        if (verifier == null) {
            synchronized (verifierMap) {
                verifier = verifierMap.get(signingToken);
                if (verifier == null) {
                    Algorithm algorithm = Algorithm.HMAC512(signingToken);
                    verifier = JWT.require(algorithm).build();
                    verifierMap.put(signingToken, verifier);
                }
            }
        }

        //解析jwt数据
        DecodedJWT jwt = verifier.verify(tokenString);
        return jwt;
    }

    /**
     *  JWT生成算法
     * @param userId
     * @param signingToken 即相关的Secret
     * @param support
     * @param duration
     * @return
     */
    public static String generateSessionToken(String userId, String signingToken, boolean support, long duration) {
        if (StringUtils.isEmpty(signingToken)) {
            throw new ServiceException("No signing token present");
        }
        //获取生成JWT的流程算法
        Algorithm algorithm = getAlgorithm(signingToken);
        //生成具体的jwt令牌
        String token = JWT.create()
                .withClaim(CLAIM_USER_ID, userId)
                .withClaim(CLAIM_SUPPORT, support)//support角色
                .withExpiresAt(new Date(System.currentTimeMillis() + duration))//过期时间
                .sign(algorithm);//签名算法
        return token;
    }

}
