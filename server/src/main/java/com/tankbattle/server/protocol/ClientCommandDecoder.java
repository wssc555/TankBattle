package com.tankbattle.server.protocol;

import com.tankbattle.common.protocol.C2s;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * C2s 解码器。
 * <p>
 * 白名单 + 容错：任何畸形 token 只记 warn 并丢弃，绝不抛出异常。
 * 聊天负载按不透明文本处理，不做二次解析（防指令注入）。
 */
public final class ClientCommandDecoder {

    private static final Logger log = LoggerFactory.getLogger(ClientCommandDecoder.class);

    /**
     * {@code m} + 5 位开关。
     */
    private static final int INPUT_TOKEN_LENGTH = 6;
    private static final char ON = '1';
    private static final char OFF = '0';
    /**
     * 上行帧不带序号，序号语义仅客户端本地统计用。
     */
    private static final long NO_SEQ = 0L;

    private ClientCommandDecoder() {
    }

    public static List<C2s> decode(String frame) {
        List<C2s> out = new ArrayList<>(4);
        if (frame == null || frame.isEmpty()) {
            return out;
        }
        for (String token : frame.split(";", -1)) {
            if (token.isEmpty()) {
                continue;
            }
            switch (token.charAt(0)) {
                case 'm' -> out.add(decodeInput(token));
                case 'e' -> out.add(new C2s.Chat(token.substring(1)));
                case 'j' -> out.add(new C2s.VoteYes());
                case 'x' -> out.add(new C2s.TogglePause());
                case 'V' -> {
                    if ("V2".equals(token)) {
                        out.add(new C2s.Capability());
                    } else {
                        log.warn("未知能力声明，已丢弃: {}", abbreviate(token));
                    }
                }
                default -> log.warn("未知指令，已丢弃: {}", abbreviate(token));
            }
        }
        return out;
    }

    /**
     * 解析 {@code m{上}{下}{左}{右}{火}}。长度或字符不合法时退化为全松开。
     */
    private static C2s.Input decodeInput(String token) {
        if (token.length() != INPUT_TOKEN_LENGTH) {
            log.warn("m 指令长度非法({})，按全松开处理: {}", token.length(), abbreviate(token));
            return new C2s.Input(false, false, false, false, false, NO_SEQ);
        }
        for (int i = 1; i < INPUT_TOKEN_LENGTH; i++) {
            char c = token.charAt(i);
            if (c != ON && c != OFF) {
                log.warn("m 指令含非法字符 '{}'，按全松开处理", c);
                return new C2s.Input(false, false, false, false, false, NO_SEQ);
            }
        }
        return new C2s.Input(isOn(token, 1), isOn(token, 2), isOn(token, 3),
                isOn(token, 4), isOn(token, 5), NO_SEQ);
    }

    private static boolean isOn(String token, int index) {
        return token.charAt(index) == ON;
    }

    private static String abbreviate(String token) {
        return token.length() <= 32 ? token : token.substring(0, 32) + "...";
    }
}
