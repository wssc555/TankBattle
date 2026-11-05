package com.tankbattle.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TextCodec 位图与整数字段解析的正反例。
 */
class TextCodecTest {

    @Test
    void bitsParsesPositionalBitmap() {
        // 字符 i 为 1 置位 1 << i（低位在前）
        assertEquals(1, TextCodec.bits("1000000000000000", 16));
        assertEquals(0x8000, TextCodec.bits("0000000000000001", 16));
        assertEquals(0x5, TextCodec.bits("101", 3));
        assertEquals(0, TextCodec.bits("0000", 4));
        assertEquals(0xFFFF, TextCodec.bits("1111111111111111", 16));
    }

    @Test
    void bitsRejectsIllegalInput() {
        assertThrows(IllegalArgumentException.class, () -> TextCodec.bits("abc", 4));
        assertThrows(IllegalArgumentException.class, () -> TextCodec.bits("10", 4));
        assertThrows(IllegalArgumentException.class, () -> TextCodec.bits("1021", 4));
        assertThrows(IllegalArgumentException.class, () -> TextCodec.bits("12,3", 4));
        assertThrows(IllegalArgumentException.class, () -> TextCodec.bits(null, 4));
    }

    @Test
    void bitStringIsInverseOfBits() {
        for (int bits = 0; bits <= 0xFFFF; bits += 4369) {
            assertEquals(bits, TextCodec.bits(TextCodec.bitString(bits, 16), 16));
        }
        assertEquals("0101", TextCodec.bitString(0b1010, 4));
    }

    @Test
    void intsParsesFields() {
        assertArrayEquals(new int[]{12, 3}, TextCodec.ints("12,3", 2));
        assertArrayEquals(new int[]{1, 2, 3}, TextCodec.ints(" 1 , 2 ,3 ", 3));
    }

    @Test
    void intsRejectsWrongCountOrFormat() {
        assertThrows(IllegalArgumentException.class, () -> TextCodec.ints("12,3", 3));
        assertThrows(IllegalArgumentException.class, () -> TextCodec.ints("12,3", 1));
        assertThrows(IllegalArgumentException.class, () -> TextCodec.ints("a,b", 2));
        assertThrows(NumberFormatException.class, () -> TextCodec.ints("1,x", 2));
    }

    @Test
    void fieldsPreservesTrailingEmptyAndValidatesCount() {
        // split(",", -1) 保留尾部空串
        String[] parts = TextCodec.fields("1,2,", 3);
        assertEquals(3, parts.length);
        assertEquals("", parts[2]);
        assertThrows(IllegalArgumentException.class, () -> TextCodec.fields("1,2", 3));
    }
}
