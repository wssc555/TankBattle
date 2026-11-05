package com.tankbattle.client.net;

/**
 * 同步模式。
 */
public enum SyncMode {
    /**
     * 逐帧应答：收到一帧立即回一帧；用于未启用 V2 能力的服务端。
     */
    LOCKSTEP,
    /**
     * 首帧已发 {@code V2;m00000;}，等待能力确认。
     */
    HANDSHAKE,
    /**
     * 独立 30Hz 输入 + 60Hz 渲染 + 插值。
     */
    FREE_RUNNING
}
