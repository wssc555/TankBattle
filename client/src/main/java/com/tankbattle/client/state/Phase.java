package com.tankbattle.client.state;

/**
 * 流程阶段。
 */
public enum Phase {
    /**
     * 尚未收到第一帧 {@code L}。
     */
    WAITING,
    /**
     * 正常游戏。
     */
    RUNNING,
    /**
     * 服务端确认暂停。
     */
    PAUSED,
    /**
     * 已 GAME OVER，等待双方投票。
     */
    GAME_OVER_VOTE,
    /**
     * 过关灰幕动画（WIN_ANIM_START=150 < g < 470；文字从 190 起）。
     */
    WIN_ANIM
}
