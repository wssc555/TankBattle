package com.tankbattle.client.net;

/**
 * 连接状态机。
 * <pre>
 * DISCONNECTED --connect()--> CONNECTING --socket ok--> HANDSHAKE --v2 或超时--> CONNECTED
 * CONNECTED --断开--> RECONNECTING --成功--> HANDSHAKE
 *                              └─ 超过重试上限 --> DISCONNECTED
 * 任意状态 --close()--> CLOSED
 * </pre>
 */
public enum LinkState {
    DISCONNECTED,
    CONNECTING,
    /**
     * 已建链，等待 {@code v2;} 或 1s 超时。
     */
    HANDSHAKE,
    CONNECTED,
    RECONNECTING,
    CLOSED
}
