package com.tankbattle.common.protocol;

import com.tankbattle.common.config.GameConfig;

/**
 * 聊天净化（A4）：双端在发送聊天的最后一步执行。
 * <p>
 * 规则：去分号 / CR / LF（防止伪造 {@code x;} {@code j;} {@code L1;} 等指令）→ strip → 限长 120。
 * 解码侧把 {@code m}/{@code e} 负载当作不透明文本，绝不二次解析。
 */
public final class ChatSanitizer {

    private ChatSanitizer() {
    }

    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String value = text.replace(';', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .strip();
        int limit = GameConfig.CHAT_MAX_CHARS;
        if (value.length() <= limit) {
            return value;
        }
        // 按代码点截断，避免切在代理对中间产生非法 UTF-16
        int cut = value.offsetByCodePoints(0, Math.min(limit, value.codePointCount(0, value.length())));
        return value.substring(0, cut);
    }
}
