package com.university.awards.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码工具类 - 用于生成 BCrypt 密码哈希
 */
public class PasswordUtil {

    /**
     * 生成密码哈希
     * @param plainPassword 明文密码
     * @return BCrypt 哈希值
     */
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /**
     * 验证密码
     * @param plainPassword 明文密码
     * @param hashedPassword 哈希密码
     * @return 是否匹配
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }

    public static void main(String[] args) {
        // 生成测试用户的密码哈希
        String password = "123456";
        String hash = hashPassword(password);
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + hash);
        System.out.println("Hash Length: " + hash.length());
        System.out.println("Verify: " + checkPassword(password, hash));
    }
}
