package com.linkhub.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 验证码生成器
 * 支持：纯数字、纯字母、数字+字母混合
 */
public class CodeGenerator {

    // 使用 SecureRandom 更安全，防止被猜测
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== 字符池 ====================

    /** 纯数字池：0-9 */
    private static final String NUMBER_POOL = "0123456789";

    /** 纯大写字母池 */
    private static final String UPPER_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** 纯小写字母池 */
    private static final String LOWER_POOL = "abcdefghijklmnopqrstuvwxyz";

    /** 大写+小写字母池 */
    private static final String LETTER_POOL = UPPER_POOL + LOWER_POOL;

    /** 数字+字母混合池（排除容易混淆的字符） */
    private static final String MIXED_POOL = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    // 排除了：0, O, 1, I, l 等容易混淆的字符

    // ==================== 公开方法 ====================

    /**
     * 生成纯数字验证码（推荐用于邮箱/短信）
     * @param length 长度，通常是6
     */
    public static String generateNumberCode(int length) {
        return generateCode(NUMBER_POOL, length);
    }

    /**
     * 生成纯数字验证码，默认6位
     */
    public static String generateNumberCode() {
        return generateNumberCode(6);
    }

    /**
     * 生成纯大写字母验证码
     * @param length 长度
     */
    public static String generateUpperCode(int length) {
        return generateCode(UPPER_POOL, length);
    }

    /**
     * 生成纯小写字母验证码
     * @param length 长度
     */
    public static String generateLowerCode(int length) {
        return generateCode(LOWER_POOL, length);
    }

    /**
     * 生成大小写混合字母验证码
     * @param length 长度
     */
    public static String generateLetterCode(int length) {
        return generateCode(LETTER_POOL, length);
    }

    /**
     * 生成数字+字母混合验证码（推荐用于图片验证码）
     * @param length 长度，通常是4
     */
    public static String generateMixedCode(int length) {
        return generateCode(MIXED_POOL, length);
    }

    // ==================== 核心方法 ====================

    /**
     * 核心生成逻辑
     * @param pool 字符池
     * @param length 验证码长度
     * @return 生成的验证码
     */
    private static String generateCode(String pool, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("验证码长度必须大于0");
        }
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("字符池不能为空");
        }

        StringBuilder code = new StringBuilder(length);
        int poolSize = pool.length();

        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(poolSize);
            code.append(pool.charAt(index));
        }

        return code.toString();
    }

    // ==================== 便捷方法 ====================

    /**
     * 格式化数字验证码（统一补零）
     * 例如：生成 4 位验证码，但想要 6 位格式，就用这个方法
     * @param number 数字（0-999999）
     * @param length 最终长度
     */
    public static String formatNumberCode(int number, int length) {
        return String.format("%0" + length + "d", number);
    }


    public static void main(String[] args) {
        // 测试各种验证码
        System.out.println("数字(6位): " + CodeGenerator.generateNumberCode(6));
        System.out.println("数字(4位): " + CodeGenerator.generateNumberCode(4));
        System.out.println("大写字母: " + CodeGenerator.generateUpperCode(6));
        System.out.println("混合字母: " + CodeGenerator.generateMixedCode(4));
        System.out.println("格式化: " + CodeGenerator.formatNumberCode(42, 6));
    }
}