package com.tankbattle.server.core;

import com.tankbattle.common.protocol.S2c;

import java.util.List;

/**
 * 每帧不可变快照：渲染层与协议层共用同一份数据，逻辑层不含渲染代码。
 * <p>
 * 所有列表在构造时做不可变拷贝，暂停 / 结束时复用上一帧即天然冻结画面。
 */
public record Snapshot(int gameFlow,
                       Phase phase,
                       int level,
                       int enemyLeft,
                       int winningCount,
                       List<TankView> tanks,
                       List<BulletView> bullets,
                       List<EffectView> effects,
                       List<WallView> walls,
                       List<RiverView> rivers,
                       List<PickupView> pickups,
                       List<GrassView> decor,
                       BaseView base,
                       List<String> messages,
                       int p1Life,
                       int p1Score,
                       int p2Life,
                       int p2Score,
                       boolean gameOver) {

    public Snapshot {
        tanks = List.copyOf(tanks);
        bullets = List.copyOf(bullets);
        effects = List.copyOf(effects);
        walls = List.copyOf(walls);
        rivers = List.copyOf(rivers);
        pickups = List.copyOf(pickups);
        decor = List.copyOf(decor);
        messages = List.copyOf(messages);
    }

    /**
     * 坦克视图：携带纹理索引与无敌标记（i 指令由此派生）。
     */
    public record TankView(int x, int y, int textureIndex, boolean invulnerable, boolean player) {
    }

    /**
     * 子弹视图。
     */
    public record BulletView(int x, int y, int direction) {
    }

    /**
     * 爆炸视图。
     */
    public record EffectView(int x, int y, S2c.BombSize size) {
    }

    /**
     * 墙体视图：{@code brick=true} 表示 16 位砖墙，否则 4 位钢墙。
     */
    public record WallView(int x, int y, boolean brick, int shapeBits) {
    }

    /**
     * 河流视图。
     */
    public record RiverView(int x, int y) {
    }

    /**
     * 道具视图（协议 n 指令，纹理索引 46+function）。
     */
    public record PickupView(int x, int y, int textureIndex) {
    }

    /**
     * 草地视图（覆盖层，最后绘制）。
     */
    public record GrassView(int x, int y) {
    }

    /**
     * 基地视图。
     */
    public record BaseView(int x, int y, boolean destroyed) {
    }
}
