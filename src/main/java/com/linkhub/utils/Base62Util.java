package com.linkhub.utils;

import java.util.Arrays;

/**
 * Base62 编码/解码工具类
 * 用于短链接短码生成和解析
 *
 * <p>优化点：
 * <ul>
 *   <li>霍纳法则（Horner's Method）替代 Math.pow()，纯整数运算</li>
 *   <li>预编译查找表 CHAR_MAP 实现 O(1) 字符查找</li>
 *   <li>完善的异常处理，防止非法输入</li>
 * </ul>
 */
public final class Base62Util {

    /**
     * 62 进制字符集（0-9A-Za-z）
     */
    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SCALE = 62;

    /**
     * ASCII 预编译查找表（0-127）
     */
    private static final int[] CHAR_MAP = new int[128];

    static {
        Arrays.fill(CHAR_MAP, -1);
        for (int i = 0; i < CHARS.length(); i++) {
            char c = CHARS.charAt(i);
            if (c < CHAR_MAP.length) {
                CHAR_MAP[c] = i;
            }
        }
    }

    private Base62Util() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 将长整型数字编码为 Base62 字符串
     *
     * @param num 非负数
     * @return Base62 字符串
     * @throws IllegalArgumentException 如果 num < 0
     */
    public static String encodeBase62(long num) {
        if (num < 0) {
            throw new IllegalArgumentException("编码数字不能为负数: " + num);
        }
        if (num == 0) {
            return String.valueOf(CHARS.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        long temp = num;
        while (temp > 0) {
            sb.append(CHARS.charAt((int) (temp % SCALE)));
            temp /= SCALE;
        }
        return sb.reverse().toString();
    }

    /**
     * 将 Base62 字符串解码为长整型数字
     *
     * @param str Base62 字符串
     * @return 解码后的长整型数字
     * @throws IllegalArgumentException 如果 str 为 null、空或包含非法字符
     */
    public static long decodeBase62(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("待解码字符串不能为空");
        }

        long num = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = c < CHAR_MAP.length ? CHAR_MAP[c] : -1;
            if (index < 0) {
                throw new IllegalArgumentException("非法字符: '" + c + "'，位置: " + i);
            }
            num = num * SCALE + index;
        }
        return num;
    }

    /**
     * 判断字符串是否为合法的 Base62 字符串
     */
    public static boolean isValidBase62(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c >= CHAR_MAP.length || CHAR_MAP[c] < 0) {
                return false;
            }
        }

        return true;

    }
}