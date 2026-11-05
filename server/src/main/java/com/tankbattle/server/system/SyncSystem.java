package com.tankbattle.server.system;

import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.ChatSanitizer;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;
import com.tankbattle.server.core.GameSession;
import com.tankbattle.server.core.Snapshot;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.BrickWall;
import com.tankbattle.server.entity.SteelWall;
import com.tankbattle.server.entity.Wall;
import com.tankbattle.server.protocol.ServerFrameEncoder;

import java.util.List;

/**
 * 同步系统：全量 S→C 指令的唯一出口。
 * <p>
 * 组帧顺序：事件指令（L/a/j/x/w/s/b/m）→ 常驻指令（n/i/t/o）→ 面板（p/g）。
 * 复用同一个 {@link ServerFrameEncoder}，每帧一次 {@code println}。
 */
public final class SyncSystem {

    private final ServerFrameEncoder encoder = new ServerFrameEncoder();
    private final boolean incremental;
    private Snapshot previous;
    private long frameSeq;
    private GameSession lastSession;

    public SyncSystem(boolean incremental) {
        this.incremental = incremental;
    }

    public void send(GameContext ctx, Snapshot snapshot, List<GameEvent> events, GameSession session) {
        if (session == null || !session.isOpen()) {
            previous = snapshot;
            lastSession = null;
            return;
        }
        // 会话更替（重连 / 踢旧接新）→ 强制全量并重置帧序号：
        // 断线期间 previous 仍被刷新，若不清空，重连端首帧的"未变化"指令会被增量比对全部跳过，
        // 客户端拿到残缺画面
        if (session != lastSession) {
            previous = null;
            lastSession = session;
            frameSeq = 0;
        }
        // v1 全量是默认行为；仅"启用增量 且 该会话已完成 v2 握手"才允许增量——
        // d{seq}; 绝不发给未完成 v2 握手的会话
        boolean inc = incremental && session.v2Negotiated();
        encoder.begin();
        for (GameEvent event : events) {
            appendEvent(event);
        }
        appendPersistent(snapshot, inc);
        if (inc) {
            encoder.append(new S2c.FrameSeq(++frameSeq));
        }
        String frame = encoder.end();
        if (!frame.isEmpty()) {
            session.send(frame);
        }
        previous = snapshot;
    }

    // ------------------------------------------------------------------
    // 事件指令
    // ------------------------------------------------------------------

    private void appendEvent(GameEvent event) {
        switch (event) {
            case GameEvent.LevelLoaded e -> encoder.append(new S2c.LevelLoad(e.level()));
            case GameEvent.GameOver e -> encoder.append(new S2c.GameOver());
            case GameEvent.PauseChanged e -> encoder.append(new S2c.Pause(e.paused()));
            case GameEvent.VoteServerYes e -> encoder.append(new S2c.VoteServerYes());
            case GameEvent.WallChanged e -> appendWall(e.wall());
            case GameEvent.BaseChanged e -> {
                Base base = e.base();
                // b 指令仅在基地被毁时发送一次、第三段固定为 "1"，不做每帧常驻 b。
                // 因此断线重连的客户端不会收到基地已毁状态。
                encoder.append(new S2c.BaseState(base.pos().x(), base.pos().y(), base.killed()));
            }
            case GameEvent.Chat e ->
                // A4：出帧前再净化一次（主机聊天文本含 ';' 会打碎帧结构）
                    encoder.append(new S2c.Chat(ChatSanitizer.sanitize(e.text())));
            case GameEvent.ExplosionSpawned e -> {
                // 由 effects 常驻指令发送
            }
            case GameEvent.BulletDestroyed e -> {
                // 子弹消亡即从快照消失
            }
            case GameEvent.EnemyKilled e -> {
                // 计分已在逻辑层结算
            }
            case GameEvent.LevelCleared e -> {
                // 通过 g 指令动画表达
            }
        }
    }

    private void appendWall(Wall wall) {
        if (wall instanceof BrickWall brick) {
            encoder.append(new S2c.BrickWallState(brick.pos().x(), brick.pos().y(), brick.shapeBits()));
        } else if (wall instanceof SteelWall steel) {
            encoder.append(new S2c.SteelWallState(steel.pos().x(), steel.pos().y(), steel.shapeBits()));
        }
    }

    // ------------------------------------------------------------------
    // 常驻指令
    // ------------------------------------------------------------------

    private void appendPersistent(Snapshot snapshot, boolean inc) {
        List<Snapshot.TankView> tanks = snapshot.tanks();
        for (int i = 0; i < tanks.size(); i++) {
            Snapshot.TankView tank = tanks.get(i);
            if (unchanged(previousTank(i), tank, inc)) {
                continue;
            }
            encoder.append(new S2c.Sprite(tank.x(), tank.y(), tank.textureIndex()));
            if (tank.invulnerable()) {
                encoder.append(new S2c.Shield(tank.x(), tank.y()));
            }
        }

        for (Snapshot.PickupView pickup : snapshot.pickups()) {
            encoder.append(new S2c.Sprite(pickup.x(), pickup.y(), pickup.textureIndex()));
        }

        List<Snapshot.BulletView> bullets = snapshot.bullets();
        for (int i = 0; i < bullets.size(); i++) {
            Snapshot.BulletView bullet = bullets.get(i);
            if (unchanged(previousBullet(i), bullet, inc)) {
                continue;
            }
            encoder.append(new S2c.Bullet(bullet.x(), bullet.y(),
                    Direction.fromCode(bullet.direction())));
        }

        for (Snapshot.EffectView effect : snapshot.effects()) {
            encoder.append(new S2c.Bomb(effect.x(), effect.y(), effect.size()));
        }

        encoder.append(new S2c.PanelInfo(snapshot.enemyLeft(), snapshot.level(),
                snapshot.p1Life(), snapshot.p1Score(), snapshot.p2Life(), snapshot.p2Score()));
        encoder.append(new S2c.WinAnim(snapshot.winningCount()));
    }

    private Snapshot.TankView previousTank(int index) {
        if (previous == null || index >= previous.tanks().size()) {
            return null;
        }
        return previous.tanks().get(index);
    }

    private Snapshot.BulletView previousBullet(int index) {
        if (previous == null || index >= previous.bullets().size()) {
            return null;
        }
        return previous.bullets().get(index);
    }

    private boolean unchanged(Object before, Object current, boolean inc) {
        return inc && before != null && before.equals(current);
    }
}
