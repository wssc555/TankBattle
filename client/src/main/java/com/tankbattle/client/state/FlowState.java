package com.tankbattle.client.state;

import com.tankbattle.common.config.GameConfig;

/**
 * 流程状态快照。
 * <p>
 * 所有变更返回新实例；{@code gameFlow} 每帧自增，既作为消息过期节拍，也作为本地帧序号回退。
 */
public record FlowState(
        Phase phase,
        int winningCount,
        boolean gameOver,
        boolean paused,
        boolean serverVote,
        boolean clientVote,
        int gameFlow) {

    public static final FlowState EMPTY = new FlowState(
            Phase.WAITING, 0, false, false, false, false, 0);

    /**
     * 新的一帧：仅自增 gameFlow。
     */
    public FlowState nextFrame() {
        return new FlowState(phase, winningCount, gameOver, paused, serverVote, clientVote, gameFlow + 1);
    }

    /**
     * 收到 {@code L}：进入 RUNNING，清空过关计数与投票。
     */
    public FlowState levelLoaded() {
        return new FlowState(Phase.RUNNING, 0, false, false, false, false, gameFlow);
    }

    /**
     * 收到 {@code a}：进入投票阶段。
     */
    public FlowState enterGameOver() {
        return new FlowState(Phase.GAME_OVER_VOTE, winningCount, true, false, serverVote, clientVote, gameFlow);
    }

    public FlowState withWinningCount(int value) {
        Phase nextPhase = phase;
        if (value > GameConfig.WIN_ANIM_START && value < GameConfig.WIN_NEXT_LEVEL_FRAME
                && (phase == Phase.RUNNING || phase == Phase.WIN_ANIM)) {
            nextPhase = Phase.WIN_ANIM;
        } else if (value <= GameConfig.WIN_ANIM_START && phase == Phase.WIN_ANIM) {
            nextPhase = Phase.RUNNING;
        }
        return new FlowState(nextPhase, value, gameOver, paused, serverVote, clientVote, gameFlow);
    }

    public FlowState withPaused(boolean value) {
        Phase nextPhase;
        if (value) {
            nextPhase = Phase.PAUSED;
        } else if (phase == Phase.PAUSED) {
            // 从过关动画期间恢复时回到 WIN_ANIM，避免丢失动画阶段
            nextPhase = (winningCount > GameConfig.WIN_ANIM_START
                    && winningCount < GameConfig.WIN_NEXT_LEVEL_FRAME)
                    ? Phase.WIN_ANIM
                    : Phase.RUNNING;
        } else {
            nextPhase = phase;
        }
        return new FlowState(nextPhase, winningCount, gameOver, value, serverVote, clientVote, gameFlow);
    }

    public FlowState withServerVote(boolean value) {
        return new FlowState(phase, winningCount, gameOver, paused, value, clientVote, gameFlow);
    }

    public FlowState withClientVote(boolean value) {
        return new FlowState(phase, winningCount, gameOver, paused, serverVote, value, gameFlow);
    }

    public FlowState withPhase(Phase value) {
        return new FlowState(value, winningCount, gameOver, paused, serverVote, clientVote, gameFlow);
    }

    /**
     * 双方均已投票。
     */
    public boolean bothVoted() {
        return serverVote && clientVote;
    }

    /**
     * 是否处于结束投票阶段。
     */
    public boolean voting() {
        return phase == Phase.GAME_OVER_VOTE;
    }
}
