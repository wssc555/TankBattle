package com.tankbattle.common.protocol;

/**
 * 客户端 → 服务端消息模型（附录 A）。
 * <p>
 * v1 兼容 4 种 + v2 扩展 1 种（{@code V2}），共 5 种。
 */
public sealed interface C2s {

    /**
     * {@code m{u}{d}{l}{r}{f};} 5 位 0/1，位序：上、下、左、右、开火。seq 仅本地统计。
     */
    record Input(boolean up, boolean down, boolean left, boolean right, boolean fire, long seq)
            implements C2s {
    }

    /**
     * {@code e{text};} 客户端聊天（发送前必须净化）。
     */
    record Chat(String text) implements C2s {
    }

    /**
     * {@code j;} 投同意，事件语义：一次。
     */
    record VoteYes() implements C2s {
    }

    /**
     * {@code x;} 切换暂停，边沿语义。
     */
    record TogglePause() implements C2s {
    }

    /**
     * {@code V2;} 能力声明，连接首帧仅发一次。
     */
    record Capability() implements C2s {
    }
}
