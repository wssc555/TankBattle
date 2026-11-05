package com.tankbattle.server.core;

import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.Bullet;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.Wall;

/**
 * 一局内的领域事件流（sealed）。由逻辑层产生，由 SyncSystem 与本帧快照一起消费。
 */
public sealed interface GameEvent {

    /**
     * 关卡已加载 → 协议 {@code L{n};}。
     */
    record LevelLoaded(int level) implements GameEvent {
    }

    /**
     * 游戏结束边沿 → 协议 {@code a;}。
     */
    record GameOver() implements GameEvent {
    }

    /**
     * 暂停/恢复边沿 → 协议 {@code x1;/x0;}。
     */
    record PauseChanged(boolean paused) implements GameEvent {
    }

    /**
     * 主机已投同意 → 协议 {@code j;}（每帧重发由 FlowSystem 负责）。
     */
    record VoteServerYes() implements GameEvent {
    }

    /**
     * 墙体形状变化 → 协议 {@code w}/{@code s}。
     */
    record WallChanged(Wall wall) implements GameEvent {
    }

    /**
     * 基地状态变化 → 协议 {@code b}。
     */
    record BaseChanged(Base base) implements GameEvent {
    }

    /**
     * 聊天 → 协议 {@code m}。
     */
    record Chat(String text) implements GameEvent {
    }

    /**
     * 新爆炸点，供渲染与同步使用。
     */
    record ExplosionSpawned(int x, int y, S2c.BombSize size) implements GameEvent {
    }

    /**
     * 子弹消亡（含被抵消、出界、命中）。
     */
    record BulletDestroyed(Bullet bullet) implements GameEvent {
    }

    /**
     * 敌方坦克被击杀。
     */
    record EnemyKilled(EnemyTank enemy) implements GameEvent {
    }

    /**
     * 一关通过（deathCount 达到 20）。
     */
    record LevelCleared(int level) implements GameEvent {
    }
}
