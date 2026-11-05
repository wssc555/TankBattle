package com.tankbattle.server.core;

import com.tankbattle.common.protocol.C2s;
import com.tankbattle.common.protocol.ChatSanitizer;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.TankIntent;
import com.tankbattle.server.protocol.ServerFrameEncoder;
import com.tankbattle.server.system.FlowSystem;
import com.tankbattle.server.system.InputSystem;
import com.tankbattle.server.system.SpawnSystem;
import com.tankbattle.server.system.SyncSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 单帧任务：drain 输入 → 状态机 → 生产 → 实体推进 → 消息过期 → 快照 → 同步。
 * <p>
 * 这是<b>唯一</b>写 {@link GameContext} 的代码路径（游戏线程）。
 */
public final class TickTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TickTask.class);

    private final GameContext ctx;
    private final InputSystem input;
    private final SyncSystem sync;
    private final Consumer<Snapshot> snapshotSink;
    private final Runnable exitHook;

    private volatile GameSession session;

    public TickTask(GameContext ctx,
                    InputSystem input,
                    SyncSystem sync,
                    Consumer<Snapshot> snapshotSink,
                    Runnable exitHook) {
        this.ctx = ctx;
        this.input = input;
        this.sync = sync;
        this.snapshotSink = snapshotSink;
        this.exitHook = exitHook;
    }

    public void bindSession(GameSession session) {
        this.session = session;
    }

    public GameSession session() {
        return session;
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        try {
            ctx.runPendingCommands();
            drainSessionEvents();
            drainInputs();

            FlowSystem.tick(ctx);

            if (ctx.phase().logicRuns()) {
                SpawnSystem.tick(ctx);
                tickActors();
            }

            if (ctx.shouldExpireMessage()) {
                ctx.messages().poll();
            }

            Snapshot snapshot = SnapshotBuilder.build(ctx);
            sync.send(ctx, snapshot, ctx.drainEvents(), session);
            ctx.purge();

            if (snapshotSink != null) {
                snapshotSink.accept(snapshot);
            }
            if (ctx.consumeExitRequest() && exitHook != null) {
                exitHook.run();
            }
        } catch (RuntimeException e) {
            // 单帧异常不能终止调度任务，否则整局静默停摆
            log.error("帧处理异常，已跳过本帧", e);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (elapsedMs > ctx.settings().slowFrameWarnMs()) {
                log.warn("帧耗时 {}ms 超过阈值 {}ms", elapsedMs, ctx.settings().slowFrameWarnMs());
            }
        }
    }

    private void drainSessionEvents() {
        SessionEvent event;
        while ((event = ctx.sessionEvents().poll()) != null) {
            if (event.bound()) {
                log.info("会话 {} 已接入", event.sessionId());
                FlowSystem.onSessionBound(ctx);
            } else {
                log.info("会话 {} 已断开", event.sessionId());
                FlowSystem.onSessionUnbound(ctx);
            }
        }
    }

    private void drainInputs() {
        PlayerTank p1 = ctx.p1();
        if (p1 != null) {
            p1.setIntent(input.intent());
        }

        GameSession current = session;
        if (current != null) {
            List<C2s> commands = current.drainInbox();
            for (C2s command : commands) {
                handle(command);
            }
        }

        PlayerTank p2 = ctx.p2();
        if (p2 != null) {
            p2.setIntent(ctx.p2Intent());
        }
    }

    private void handle(C2s command) {
        switch (command) {
            case C2s.Input i -> ctx.setP2Intent(new TankIntent(i.up(), i.down(), i.left(), i.right(), i.fire()));
            // A4：入站聊天先净化（去 ;/CR/LF、限长），防止撑爆 MessageLog 与打碎出站帧
            case C2s.Chat chat -> ctx.messages().add("用户端玩家说：" + ChatSanitizer.sanitize(chat.text()));
            case C2s.VoteYes ignored -> FlowSystem.clientVoteYes(ctx);
            case C2s.TogglePause ignored -> FlowSystem.requestPause(ctx, false);
            case C2s.Capability ignored -> negotiateV2();
        }
    }

    /**
     * v2 能力握手：仅在 {@code net.incremental=true} 时应答 {@code v2;} 并对该会话
     * 开启增量同步；未启用增量时保持纯 v1 全量。
     */
    private void negotiateV2() {
        GameSession current = session;
        if (current == null) {
            return;
        }
        if (!ctx.settings().incrementalSync()) {
            log.info("客户端声明 v2 能力；服务端未启用增量同步，以 v1 全量模式运行");
            return;
        }
        if (current.v2Negotiated()) {
            return;
        }
        current.setV2Negotiated(true);
        ServerFrameEncoder ack = new ServerFrameEncoder();
        ack.begin();
        ack.append(new S2c.CapabilityAck());
        String frame = ack.end();
        if (!frame.isEmpty()) {
            current.send(frame);
        }
        log.info("客户端声明 v2 能力，已应答 v2;，该会话切换增量同步");
    }

    private void tickActors() {
        ctx.rebuildDynamicIndex();
        ctx.beginFrame();
        for (Actor actor : ctx.frameActors()) {
            if (!actor.destroyed()) {
                actor.tick(ctx);
            }
        }
    }
}
