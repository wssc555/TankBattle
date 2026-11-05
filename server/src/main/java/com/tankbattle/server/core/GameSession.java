package com.tankbattle.server.core;

import com.tankbattle.common.protocol.C2s;

import java.util.List;

/**
 * 游戏线程看到的网络端点（{@code net.ClientSession} 的实现，避免 core → net 的硬依赖）。
 */
public interface GameSession {

    int id();

    boolean isOpen();

    /**
     * 取走当前可用的客户端指令（读线程入队）。
     */
    List<C2s> drainInbox();

    /**
     * 游戏线程内直接写出一帧。
     */
    void send(String frame);

    /**
     * 该会话是否已完成 v2 能力握手（服务端已应答 {@code v2;}）。
     * 仅握手成功的会话才允许增量同步（{@code d{seq};}），默认 false（v1 全量）。
     */
    default boolean v2Negotiated() {
        return false;
    }

    /**
     * 标记会话完成 v2 能力握手。默认空实现（v1 会话无需协商）。
     */
    default void setV2Negotiated(boolean negotiated) {
    }
}
