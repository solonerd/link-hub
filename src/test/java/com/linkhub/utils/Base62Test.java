package com.linkhub.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62UtilTest {

    @Test
    @DisplayName("编码后解码，能还原原值")
    void roundTrip() {
        long[] values = {0L, 1L, 61L, 62L, 63L, 123456789L, Long.MAX_VALUE};
        for (long v : values) {
            String encoded = Base62Util.encodeBase62(v);
            assertEquals(v, Base62Util.decodeBase62(encoded), "v = " + v);
        }
    }

    @Test
    @DisplayName("0 编码为字符 '0'")
    void zero() {
        assertEquals("0", Base62Util.encodeBase62(0));
        assertEquals(0L, Base62Util.decodeBase62("0"));
    }

    @Test
    @DisplayName("负数编码抛 IllegalArgumentException")
    void negativeEncode() {
        assertThrows(IllegalArgumentException.class, () -> Base62Util.encodeBase62(-1));
    }

    @Test
    @DisplayName("非法字符 / 空串解码抛异常")
    void invalidDecode() {
        assertThrows(IllegalArgumentException.class, () -> Base62Util.decodeBase62("abc!def"));
        assertThrows(IllegalArgumentException.class, () -> Base62Util.decodeBase62(""));
        assertThrows(IllegalArgumentException.class, () -> Base62Util.decodeBase62(null));
    }

    @Test
    @DisplayName("isValidBase62 判定")
    void validCheck() {
        assertTrue(Base62Util.isValidBase62("ABC123abc"));
        assertFalse(Base62Util.isValidBase62("ABC-123"));
        assertFalse(Base62Util.isValidBase62(""));
        assertFalse(Base62Util.isValidBase62(null));
    }
}