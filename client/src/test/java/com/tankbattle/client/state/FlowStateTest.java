package com.tankbattle.client.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 转移、投票去重、暂停边沿、过关阶段。
 */
class FlowStateTest {

    @Test
    void followsWaitingToRunningToVoteToRestart() {
        FlowState flow = FlowState.EMPTY;
        assertEquals(Phase.WAITING, flow.phase());

        flow = flow.levelLoaded();
        assertEquals(Phase.RUNNING, flow.phase());

        flow = flow.enterGameOver();
        assertEquals(Phase.GAME_OVER_VOTE, flow.phase());
        assertTrue(flow.gameOver());

        flow = flow.withClientVote(true).withServerVote(true);
        assertTrue(flow.bothVoted());

        flow = flow.levelLoaded();
        assertEquals(Phase.RUNNING, flow.phase());
        assertFalse(flow.gameOver());
        assertFalse(flow.bothVoted());
        assertEquals(0, flow.winningCount());
    }

    @Test
    void pauseEdgeKeepsOtherPhases() {
        FlowState flow = FlowState.EMPTY.levelLoaded();
        flow = flow.withPaused(true);
        assertEquals(Phase.PAUSED, flow.phase());
        flow = flow.withPaused(false);
        assertEquals(Phase.RUNNING, flow.phase());

        FlowState voting = FlowState.EMPTY.enterGameOver();
        assertEquals(Phase.GAME_OVER_VOTE, voting.withPaused(false).phase());
    }

    @Test
    void winAnimationPhaseOnlyWithinThresholds() {
        FlowState flow = FlowState.EMPTY.levelLoaded();
        assertEquals(Phase.RUNNING, flow.withWinningCount(150).phase());
        assertEquals(Phase.WIN_ANIM, flow.withWinningCount(200).phase());
        assertEquals(Phase.RUNNING, flow.withWinningCount(50).phase());
        assertEquals(Phase.RUNNING, flow.withWinningCount(470).phase());
    }

    /**
     * 新开局（L）必须清空 clientVote，第二局才能再投。
     */
    @Test
    void levelLoadClearsClientVoteForNextRound() {
        FlowState flow = FlowState.EMPTY.levelLoaded().enterGameOver();
        flow = flow.withClientVote(true);
        assertTrue(flow.clientVote());

        flow = flow.levelLoaded();
        assertFalse(flow.clientVote(), "重开后 clientVote 必须复位");
        assertFalse(flow.serverVote());
    }

    /**
     * 投票置位幂等：重复 withClientVote(true) 不产生副作用。
     */
    @Test
    void clientVoteIsIdempotent() {
        FlowState voted = FlowState.EMPTY.enterGameOver().withClientVote(true);
        assertEquals(voted, voted.withClientVote(true));
    }

    /**
     * 过关动画期间暂停→恢复应回到 WIN_ANIM 而非 RUNNING。
     */
    @Test
    void resumeFromPauseDuringWinAnimationRestoresPhase() {
        FlowState flow = FlowState.EMPTY.levelLoaded().withWinningCount(200);
        assertEquals(Phase.WIN_ANIM, flow.phase());

        FlowState paused = flow.withPaused(true);
        assertEquals(Phase.PAUSED, paused.phase());

        FlowState resumed = paused.withPaused(false);
        assertEquals(Phase.WIN_ANIM, resumed.phase(), "winningCount 仍在动画阈值内，应恢复 WIN_ANIM");
    }

    @Test
    void gameFlowAdvancesPerFrame() {
        FlowState flow = FlowState.EMPTY;
        assertEquals(1, flow.nextFrame().gameFlow());
        assertEquals(2, flow.nextFrame().nextFrame().gameFlow());
    }
}
