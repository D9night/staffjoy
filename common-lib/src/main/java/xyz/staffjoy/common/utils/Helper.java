package xyz.staffjoy.common.utils;

public class Helper {

    /**
     * 根据邮件地址生成url
     * @param email
     * @return
     */
    public static String generateGravatarUrl(String email) {
        String hash = MD5Util.md5Hex(email);
        return String.format("https://www.gravatar.com/avatar/%s.jpg?s=400&d=identicon", hash);
    }
}
