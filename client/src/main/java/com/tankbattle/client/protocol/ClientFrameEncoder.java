package com.tankbattle.client.protocol;

import com.tankbattle.common.protocol.C2s;
import com.tankbattle.common.protocol.ChatSanitizer;

import java.util.List;

/**
 * 客户端帧编码器。
 * <p>
 * 复用 {@link StringBuilder}，30Hz 下零中间拼接垃圾（每帧仅一次 {@code toString} 分配）。
 * 实例非线程安全，只允许发送线程调用（由 {@code CommandSender} 的写锁保证单写者）；聊天发送前净化，杜绝指令注入。
 */
public final class ClientFrameEncoder {

    private final StringBuilder sb = new StringBuilder(256);

    public String encode(List<C2s> commands) {
        sb.setLength(0);
        for (C2s command : commands) {
            switch (command) {
                case C2s.Input input -> sb.append('m')
                        .append(bit(input.up()))
                        .append(bit(input.down()))
                        .append(bit(input.left()))
                        .append(bit(input.right()))
                        .append(bit(input.fire()))
                        .append(';');
                case C2s.Chat chat -> sb.append('e')
                        .append(ChatSanitizer.sanitize(chat.text()))
                        .append(';');
                case C2s.VoteYes ignored -> sb.append("j;");
                case C2s.TogglePause ignored -> sb.append("x;");
                case C2s.Capability ignored -> sb.append("V2;");
            }
        }
        return sb.toString();
    }

    private static char bit(boolean value) {
        return value ? '1' : '0';
    }
}
