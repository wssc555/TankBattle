package com.tankbattle.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聊天净化：去分号/CR/LF、strip、120 截断（含代码点边界）。
 */
class ChatSanitizerTest {

    @Test
    void stripsCommandInjectionChars() {
        // ;/CR/LF 一律替换为空格（不做连续空格压缩）
        assertEquals("a b c d", ChatSanitizer.sanitize("a;b\rc\nd"));
        assertEquals("x1  y", ChatSanitizer.sanitize("x1;\ny"));
        assertEquals("", ChatSanitizer.sanitize(null));
        assertEquals("", ChatSanitizer.sanitize("   \r\n "));
    }

    @Test
    void truncatesTo120Chars() {
        String longText = "字".repeat(300);
        String result = ChatSanitizer.sanitize(longText);
        assertEquals(120, result.length());
    }

    @Test
    void truncationNeverSplitsSurrogatePair() {
        // 300 个代理对 emoji：按代码点截断后仍是合法 UTF-16
        String emoji = "😀".repeat(200);
        String result = ChatSanitizer.sanitize(emoji);
        assertTrue(result.length() % 2 == 0, "截断不得落在代理对中间");
        assertTrue(result.codePoints().allMatch(cp -> Character.isValidCodePoint(cp)));
    }

    @Test
    void preservesShortTextWithMixedWhitespace() {
        assertEquals("你好 坦克", ChatSanitizer.sanitize(" 你好;坦克 "));
    }
}
