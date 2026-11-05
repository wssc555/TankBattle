package com.tankbattle.server.entity;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * 基地。聚合持有四周 4 面护甲墙，负责钢墙化 / 还原的定时切换。
 * <p>
 * 基地由 {@code GameContext} 直接持有引用。
 */
public final class Base implements Actor {

    /**
     * 4 面护甲的几何定义：x、y、朝向（0 上 / 1 下 / 2 左 / 3 右）。
     */
    private static final int[][] ARMOR = {
            {GameConfig.ARMOR_X0, GameConfig.ARMOR_Y0, GameConfig.ARMOR_O0},
            {GameConfig.ARMOR_X1, GameConfig.ARMOR_Y1, GameConfig.ARMOR_O1},
            {GameConfig.ARMOR_X2, GameConfig.ARMOR_Y2, GameConfig.ARMOR_O2},
            {GameConfig.ARMOR_X3, GameConfig.ARMOR_Y3, GameConfig.ARMOR_O3},
    };

    private final Vec pos;
    private final Rectangle border;
    private final List<Wall> armor = new ArrayList<>(ARMOR.length);

    private boolean killed;
    private boolean removed;
    private int steelWallTime;
    private boolean steelArmor;

    public Base() {
        this.pos = new Vec(GameConfig.BASE_X, GameConfig.BASE_Y);
        this.border = new Rectangle(pos.x() - 11, pos.y() - 11, 23, 23);
    }

    public Vec pos() {
        return pos;
    }

    /**
     * 纹理索引：0 = 完好、1 = 已毁。
     */
    public int textureIndex() {
        return killed ? GameConfig.TEX_BASE_DEAD : GameConfig.TEX_BASE_OK;
    }

    public boolean killed() {
        return killed;
    }

    public int steelWallTime() {
        return steelWallTime;
    }

    public boolean steelArmor() {
        return steelArmor;
    }

    /**
     * 道具"钢墙"：启动 600 帧护甲计时。
     */
    public void fortify() {
        steelWallTime = ServerConfig.STEEL_ARMOR_FRAMES;
    }

    /**
     * 由关卡加载器注入 4 面初始砖墙护甲。
     */
    public void attachArmor(List<Wall> walls) {
        armor.clear();
        armor.addAll(walls);
    }

    public List<Wall> armor() {
        return List.copyOf(armor);
    }

    @Override
    public Rectangle border() {
        return border;
    }

    @Override
    public boolean destroyed() {
        return removed;
    }

    @Override
    public void markDestroyed() {
        removed = true;
    }

    @Override
    public void tick(GameContext ctx) {
        if (steelWallTime == ServerConfig.STEEL_ARMOR_FRAMES) {
            replaceArmor(ctx, true);
        }
        if (steelWallTime > 0) {
            steelWallTime--;
        }
        if (steelWallTime == 1) {
            replaceArmor(ctx, false);
        }
    }

    private void replaceArmor(GameContext ctx, boolean steel) {
        if (armor.size() != ARMOR.length) {
            return;
        }
        steelArmor = steel;
        for (int i = 0; i < ARMOR.length; i++) {
            Wall old = armor.get(i);
            if (old != null) {
                ctx.removeActor(old);
            }
            Wall created = steel
                    ? new SteelWall(ARMOR[i][0], ARMOR[i][1], SteelWall.bitsForOrientation(ARMOR[i][2]))
                    : new BrickWall(ARMOR[i][0], ARMOR[i][1], BrickWall.bitsForOrientation(ARMOR[i][2]));
            armor.set(i, created);
            ctx.addActor(created);
            ctx.emit(new GameEvent.WallChanged(created));
        }
        ctx.rebuildStaticIndex();
    }

    /**
     * 被子弹摧毁：切纹理、大爆炸、发 b 指令、触发 GameOver。
     */
    public void doom(GameContext ctx) {
        if (killed) {
            return;
        }
        killed = true;
        ctx.spawnExplosion(S2c.BombSize.BIG, pos);
        ctx.emit(new GameEvent.BaseChanged(this));
        ctx.requestGameOver();
    }
}
