package com.tankbattle.server.core;

/**
 * 一局流程状态。
 */
public enum Phase {

    /**
     * 尚未连接客户端，世界不推进。
     */
    WAITING,
    /**
     * 正常进行。
     */
    RUNNING,
    /**
     * 暂停：逻辑系统整体短路，网络与消息仍工作。
     */
    PAUSED,
    /**
     * 游戏结束，等待双方投票。
     */
    GAME_OVER_VOTE,
    /**
     * 胜利动画（winningCount 0..500）。
     */
    WIN_ANIM;

    /**
     * 该阶段是否推进实体逻辑。
     */
    public boolean logicRuns() {
        return this == RUNNING || this == GAME_OVER_VOTE || this == WIN_ANIM;
    }

    /**
     * 该阶段是否推进 gameFlow 计数。
     */
    public boolean flowAdvances() {
        return this == RUNNING || this == GAME_OVER_VOTE || this == WIN_ANIM;
    }
}
