package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;
import com.tankbattle.server.core.LevelState;
import com.tankbattle.server.core.Phase;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.level.LevelLoader;

/**
 * 流程状态机：RUNNING / PAUSED / GAME_OVER_VOTE / WIN_ANIM 的显式转移。
 * <p>
 * 退出采用优雅停机，由 GUI 侧确认；一次性提示由状态边沿驱动。
 */
public final class FlowSystem {

    private static final String MSG_GAME_OVER = "GAME OVER ! 　想再玩一次吗 ( y / n ) ?";
    private static final String MSG_RESTART_CLIENT = "用户端玩家决定再玩一次，游戏重新开始了...";
    private static final String MSG_WAIT_CLIENT = "等待用户端玩家的回应...";

    private FlowSystem() {
    }

    // ------------------------------------------------------------------
    // 每帧推进
    // ------------------------------------------------------------------

    public static void tick(GameContext ctx) {
        switch (ctx.phase()) {
            case WAITING -> {
                // 等待客户端接入，世界不推进
            }
            case RUNNING -> tickRunning(ctx);
            case PAUSED -> {
                // 逻辑整体短路；恢复由 requestPause 驱动
            }
            case GAME_OVER_VOTE -> tickGameOver(ctx);
            case WIN_ANIM -> tickWinAnim(ctx);
        }
    }

    private static void tickRunning(GameContext ctx) {
        ctx.advanceGameFlow();

        if (ctx.consumeGameOverRequest() || allPlayersDead(ctx)) {
            enterGameOver(ctx);
            return;
        }
        if (ctx.level().deathCount() >= ServerConfig.ENEMY_PER_LEVEL) {
            ctx.setPhase(Phase.WIN_ANIM);
            ctx.emit(new GameEvent.LevelCleared(ctx.level().currentLevel()));
            advanceWin(ctx);
        }
    }

    private static void tickGameOver(GameContext ctx) {
        ctx.advanceGameFlow();

        if (ctx.hostVoteYes()) {
            ctx.emit(new GameEvent.VoteServerYes());
        }
        if (ctx.hostVoteYes() && ctx.clientVoteYes()) {
            restart(ctx);
            return;
        }
        if (ctx.voteStartFlow() >= 0 && voteTimedOut(ctx)) {
            ctx.clearVotes();
            ctx.messages().add("用户端玩家未响应，重开投票已取消");
        }
    }

    private static void tickWinAnim(GameContext ctx) {
        ctx.advanceGameFlow();
        advanceWin(ctx);
    }

    /**
     * 胜利计数推进（同一帧内只调用一次，避免 gameFlow 双增）。
     */
    private static void advanceWin(GameContext ctx) {
        LevelState level = ctx.level();
        int count = level.winningCount() + 1;
        level.setWinningCount(count);

        if (count == ServerConfig.WIN_FREEZE_FRAME) {
            freezePlayers(ctx, true);
        }
        if (count == GameConfig.WIN_NEXT_LEVEL_FRAME) {
            PlayerTank p1 = ctx.p1();
            PlayerTank p2 = ctx.p2();
            if (p1 != null && p1.life() > 0) {
                p1.resetForNextLevel();
            }
            if (p2 != null && p2.life() > 0) {
                p2.resetForNextLevel();
            }
            LevelLoader.load(ctx);
            ctx.emit(new GameEvent.LevelLoaded(ctx.level().displayLevel()));
        }
        if (count >= GameConfig.WIN_RESET_FRAME) {
            freezePlayers(ctx, false);
            level.setDeathCount(0);
            level.setWinningCount(0);
            ctx.setPhase(Phase.RUNNING);
        }
    }

    // ------------------------------------------------------------------
    // 外部触发
    // ------------------------------------------------------------------

    /**
     * 客户端接入：首局开局；重连则解冻并补发 L 与全量快照。
     */
    public static void onSessionBound(GameContext ctx) {
        if (ctx.phase() == Phase.WAITING) {
            startGame(ctx);
            return;
        }
        if (ctx.reconnectPending()) {
            ctx.setReconnectPending(false);
            freezePlayers(ctx, false);
            if (ctx.phase() == Phase.PAUSED) {
                ctx.setPhase(Phase.RUNNING);
            }
            ctx.messages().add("玩家重新连接，继续本局");
        }
        ctx.emit(new GameEvent.LevelLoaded(ctx.level().displayLevel()));
    }

    /**
     * 客户端断开：进入等待重连（暂停推进，保留分数与关卡）。
     */
    public static void onSessionUnbound(GameContext ctx) {
        if (ctx.phase() == Phase.RUNNING || ctx.phase() == Phase.PAUSED) {
            ctx.setReconnectPending(true);
            ctx.setPhase(Phase.PAUSED);
            freezePlayers(ctx, true);
            ctx.messages().add("玩家退出了，等待重新连接");
        }
    }

    public static void startGame(GameContext ctx) {
        ctx.clearWorld();
        ctx.messages().add("玩家已连接上，开始载入游戏");

        PlayerTank p1 = new PlayerTank(1);
        PlayerTank p2 = new PlayerTank(2);
        ctx.installPlayers(p1, p2);

        ctx.level().reset();
        ctx.resetGameFlow();
        ctx.clearVotes();
        ctx.clearFreeze();
        ctx.setReconnectPending(false);

        LevelLoader.load(ctx);
        ctx.emit(new GameEvent.LevelLoaded(ctx.level().displayLevel()));
        ctx.setPhase(Phase.RUNNING);
        ctx.messages().add("载入完毕，游戏开始了！");
    }

    public static void restart(GameContext ctx) {
        PlayerTank p1 = ctx.p1();
        PlayerTank p2 = ctx.p2();
        if (p1 != null) {
            p1.resetAll();
        }
        if (p2 != null) {
            p2.resetAll();
        }
        ctx.level().reset();
        ctx.resetGameFlow();
        ctx.clearVotes();
        ctx.clearFreeze();

        LevelLoader.load(ctx);
        ctx.emit(new GameEvent.LevelLoaded(1));
        ctx.setPhase(Phase.RUNNING);
        ctx.messages().add(MSG_RESTART_CLIENT);
    }

    /**
     * 暂停 / 恢复。
     *
     * @param fromHost {@code true} 表示主机按钮，{@code false} 表示客户端 {@code x} 指令
     * @return 是否实际发生了切换
     */
    public static boolean requestPause(GameContext ctx, boolean fromHost) {
        if (ctx.phase() == Phase.PAUSED) {
            ctx.setPhase(Phase.RUNNING);
            ctx.emit(new GameEvent.PauseChanged(false));
            ctx.messages().add(fromHost ? "主机端玩家取消了暂停" : "用户端玩家取消了暂停");
            return true;
        }
        if (ctx.phase() == Phase.RUNNING) {
            ctx.setPhase(Phase.PAUSED);
            ctx.emit(new GameEvent.PauseChanged(true));
            ctx.messages().add(fromHost ? "主机端玩家暂停了游戏" : "用户端玩家暂停了游戏");
            return true;
        }
        // GAME_OVER_VOTE / WIN_ANIM 期间暂停无意义（保留原"gameOver 时按钮无效"约束）
        return false;
    }

    public static void hostVoteYes(GameContext ctx) {
        if (ctx.phase() != Phase.GAME_OVER_VOTE || ctx.hostVoteYes()) {
            return;
        }
        ctx.setHostVoteYes(true);
        if (ctx.voteStartFlow() < 0) {
            ctx.setVoteStartFlow(ctx.gameFlow());
        }
        ctx.messages().add(MSG_WAIT_CLIENT);
        ctx.emit(new GameEvent.VoteServerYes());
    }

    public static void clientVoteYes(GameContext ctx) {
        if (ctx.phase() == Phase.GAME_OVER_VOTE) {
            ctx.setClientVoteYes(true);
        }
    }

    /**
     * 主机拒绝：只置退出请求，由 GUI 弹确认框后优雅停机。
     */
    public static void hostVoteNo(GameContext ctx) {
        if (ctx.phase() == Phase.GAME_OVER_VOTE) {
            ctx.requestExit();
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static void enterGameOver(GameContext ctx) {
        ctx.setPhase(Phase.GAME_OVER_VOTE);
        freezePlayers(ctx, true);
        ctx.clearVotes();
        ctx.setVoteStartFlow(-1);
        ctx.emit(new GameEvent.GameOver());
        ctx.messages().add(MSG_GAME_OVER);
    }

    private static boolean allPlayersDead(GameContext ctx) {
        PlayerTank p1 = ctx.p1();
        PlayerTank p2 = ctx.p2();
        boolean p1Dead = p1 == null || p1.life() == 0;
        boolean p2Dead = p2 == null || p2.life() == 0;
        return p1Dead && p2Dead;
    }

    private static boolean voteTimedOut(GameContext ctx) {
        int frames = ctx.settings().voteTimeoutSeconds() * 1000 / Math.max(1, ctx.settings().frameMs());
        return ctx.gameFlow() - ctx.voteStartFlow() > frames;
    }

    private static void freezePlayers(GameContext ctx, boolean frozen) {
        if (ctx.p1() != null) {
            ctx.p1().setFrozen(frozen);
        }
        if (ctx.p2() != null) {
            ctx.p2().setFrozen(frozen);
        }
    }
}
