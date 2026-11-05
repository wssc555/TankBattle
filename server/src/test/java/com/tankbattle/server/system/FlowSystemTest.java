package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.TestContexts;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;
import com.tankbattle.server.core.Phase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程状态机全转移。
 */
class FlowSystemTest {

    @Test
    void startGameBeginsFirstLevel() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);

        assertEquals(Phase.RUNNING, ctx.phase());
        assertEquals(1, ctx.level().currentLevel());
        assertEquals(ServerConfig.ENEMY_PER_LEVEL, ctx.level().enemyLeft());
        assertNotNull(ctx.base());
        assertNotNull(ctx.p1());
        assertNotNull(ctx.p2());
    }

    @Test
    void waitingPhaseDoesNotAdvanceFlow() {
        GameContext ctx = TestContexts.create();
        FlowSystem.tick(ctx);
        assertEquals(Phase.WAITING, ctx.phase());
        assertEquals(0, ctx.gameFlow());
    }

    @Test
    void pauseTogglesBetweenRunningAndPaused() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);

        assertTrue(FlowSystem.requestPause(ctx, true));
        assertEquals(Phase.PAUSED, ctx.phase());
        assertTrue(FlowSystem.requestPause(ctx, false));
        assertEquals(Phase.RUNNING, ctx.phase());
    }

    @Test
    void pauseIgnoredDuringGameOver() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.base().doom(ctx);
        FlowSystem.tick(ctx);

        assertEquals(Phase.GAME_OVER_VOTE, ctx.phase());
        assertFalse(FlowSystem.requestPause(ctx, true), "gameOver 期间主机暂停按钮无效");
        assertEquals(Phase.GAME_OVER_VOTE, ctx.phase());
    }

    @Test
    void baseDestroyLeadsToVoteThenRestart() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.base().doom(ctx);
        assertEquals(Phase.RUNNING, ctx.phase(), "doom 只置请求，下一帧才转移");

        FlowSystem.tick(ctx);
        assertEquals(Phase.GAME_OVER_VOTE, ctx.phase());
        assertTrue(ctx.p1().frozen());
        List<GameEvent> events = ctx.drainEvents();
        assertTrue(events.stream().anyMatch(GameEvent.GameOver.class::isInstance), "进入结局时发一次 a;");

        FlowSystem.hostVoteYes(ctx);
        FlowSystem.clientVoteYes(ctx);
        FlowSystem.tick(ctx);

        assertEquals(Phase.RUNNING, ctx.phase());
        assertEquals(1, ctx.level().currentLevel());
        assertFalse(ctx.p1().frozen());
        assertEquals(0, ctx.gameFlow());
    }

    @Test
    void hostVoteEmitsVoteEvent() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.base().doom(ctx);
        FlowSystem.tick(ctx);
        ctx.drainEvents();

        FlowSystem.hostVoteYes(ctx);
        List<GameEvent> events = ctx.drainEvents();
        assertTrue(events.stream().anyMatch(GameEvent.VoteServerYes.class::isInstance));
        assertTrue(ctx.hostVoteYes());
    }

    /**
     * 投票等待期间每帧重发 {@code j;}，帧流指令集合须保持稳定。
     * 消息文本去重（messageIndex）只约束文本，不约束帧指令。
     * 帧流指令集合须保持一致，去掉每帧重发会改变输出。
     */
    @Test
    void hostVoteYesResentEveryFrameUntilClientResponds() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.base().doom(ctx);
        FlowSystem.tick(ctx);
        ctx.drainEvents();

        FlowSystem.hostVoteYes(ctx);
        ctx.drainEvents();

        int frames = 10;
        long resendCount = 0;
        for (int i = 0; i < frames; i++) {
            FlowSystem.tick(ctx);
            resendCount += ctx.drainEvents().stream()
                    .filter(GameEvent.VoteServerYes.class::isInstance).count();
        }
        assertEquals(frames, resendCount, "每帧恰一条 j;");
        assertEquals(Phase.GAME_OVER_VOTE, ctx.phase());
    }

    @Test
    void voteTimesOutWhenClientSilent() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.base().doom(ctx);
        FlowSystem.tick(ctx);

        FlowSystem.hostVoteYes(ctx);
        int frames = ctx.settings().voteTimeoutSeconds() * 1000 / ctx.settings().frameMs();
        for (int i = 0; i <= frames; i++) {
            FlowSystem.tick(ctx);
        }
        assertFalse(ctx.hostVoteYes(), "客户端久未响应时应取消投票");
        assertEquals(Phase.GAME_OVER_VOTE, ctx.phase());
    }

    @Test
    void winAnimationLoadsNextLevelAtFrame470() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.level().setDeathCount(ServerConfig.ENEMY_PER_LEVEL);

        FlowSystem.tick(ctx);
        assertEquals(Phase.WIN_ANIM, ctx.phase());
        assertEquals(1, ctx.level().winningCount());

        for (int i = 0; i < GameConfig.WIN_NEXT_LEVEL_FRAME - 1; i++) {
            FlowSystem.tick(ctx);
        }
        assertEquals(GameConfig.WIN_NEXT_LEVEL_FRAME, ctx.level().winningCount());
        assertEquals(2, ctx.level().currentLevel(), "下一关在 470 帧已加载");

        for (int i = 0; i < GameConfig.WIN_RESET_FRAME - GameConfig.WIN_NEXT_LEVEL_FRAME; i++) {
            FlowSystem.tick(ctx);
        }
        assertEquals(Phase.RUNNING, ctx.phase());
        assertEquals(0, ctx.level().winningCount());
        assertEquals(0, ctx.level().deathCount());
    }

    @Test
    void reconnectPausesAndResumes() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);

        FlowSystem.onSessionUnbound(ctx);
        assertEquals(Phase.PAUSED, ctx.phase());
        assertTrue(ctx.reconnectPending());
        assertTrue(ctx.p1().frozen());

        FlowSystem.onSessionBound(ctx);
        assertEquals(Phase.RUNNING, ctx.phase());
        assertFalse(ctx.reconnectPending());
        assertFalse(ctx.p1().frozen());
    }
}
