package com.linkhub.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CodeGeneratorTest {

    @Test
    @DisplayName("generateNumberCode 长度为 6，内容全为数字")
    void numberCodeFormat() {
        for (int i = 0; i < 100; i++) {
            String code = CodeGenerator.generateNumberCode(6);
            assertEquals(6, code.length());
            assertTrue(code.matches("\\d+"), "非数字: " + code);
        }
    }

    @Test
    @DisplayName("generateMixedCode 不含易混淆字符 0/O/1/I/l")
    void mixedCodeExcludesConfusingChars() {
        for (int i = 0; i < 200; i++) {
            String code = CodeGenerator.generateMixedCode(8);
            assertEquals(8, code.length());
            for (char c : new char[]{'0', 'O', '1', 'I', 'l'}) {
                assertFalse(code.indexOf(c) >= 0, "包含易混字符 " + c + ": " + code);
            }
        }
    }

    @Test
    @DisplayName("长度为 0 或负数抛异常")
    void invalidLength() {
        assertThrows(IllegalArgumentException.class, () -> CodeGenerator.generateNumberCode(0));
        assertThrows(IllegalArgumentException.class, () -> CodeGenerator.generateNumberCode(-1));
    }

    @Test
    @DisplayName("10000 次 6 位数字码几乎不重复")
    void uniqueness() {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            set.add(CodeGenerator.generateNumberCode(6));
        }
        assertTrue(set.size() > 9900, "唯一数仅 " + set.size() + " 个，随机性可能有问题");
    }
}