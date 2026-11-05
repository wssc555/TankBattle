package com.tankbattle.client.net;

/**
 * 断开原因（异常分层的一部分）。
 */
public enum DisconnectReason {
    /**
     * 对端正常关闭 / EOF。
     */
    EOF,
    /**
     * 读写 IO 异常。
     */
    IO_ERROR,
    /**
     * 空闲检测超时（半开连接）。
     */
    TIMEOUT,
    /**
     * 本地主动关闭，不触发重连。
     */
    LOCAL_CLOSE
}
