package com.tankbattle.server.core;

/**
 * 网络会话生命周期事件（读线程 → 游戏线程的唯一通道）。
 *
 * @param sessionId 会话编号
 * @param bound     {@code true}=接入，{@code false}=断开
 */
public record SessionEvent(int sessionId, boolean bound) {
}
