package com.tankbattle.client.render;

import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.Phase;
import com.tankbattle.client.state.RenderState;
import com.tankbattle.client.state.views.BaseView;
import com.tankbattle.client.state.views.BulletView;
import com.tankbattle.client.state.views.EffectView;
import com.tankbattle.client.state.views.SpriteView;
import com.tankbattle.client.state.views.WallView;
import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.level.Cell;
import com.tankbattle.common.model.Direction;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * 固定 8 层渲染编排：
 * <ol>
 *   <li>背景</li>
 *   <li>HUD（x ≥ 515）</li>
 *   <li>蓝框</li>
 *   <li>静态层缓存（墙/钢/河/基地）</li>
 *   <li>动态实体（sprites / bullets / effects）</li>
 *   <li>草覆盖</li>
 *   <li>过关动画</li>
 *   <li>消息 8 行</li>
 * </ol>
 * 绘制全程只读快照，不修改状态、不删除对象。所有颜色为静态常量。
 */
public final class LayeredRenderer {

    private static final Color BACKGROUND = new Color(128, 64, 0);
    private static final Color FRAME_BLUE = Color.blue;
    private static final Color BRICK_BG = new Color(
            GameConfig.BRICK_BG_R, GameConfig.BRICK_BG_G, GameConfig.BRICK_BG_B);
    private static final Color GRASS_LIGHT = new Color(0, 225, 0);
    private static final Color GRASS_DARK = new Color(0, 128, 0);
    private static final Color SHIELD_RED = Color.red;
    private static final Color BULLET_GRAY = Color.lightGray;

    private final HudRenderer hudRenderer = new HudRenderer();
    private final MessageRenderer messageRenderer = new MessageRenderer();

    public void render(Graphics2D g, RenderState state, TextureAtlas atlas,
                       StaticLayerCache cache, MessageLog messages,
                       WinAnimationRenderer winAnimation) {
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, GameConfig.PANEL_W, GameConfig.PANEL_H);

        boolean started = state.flow().phase() != Phase.WAITING;
        if (started) {
            hudRenderer.render(g, state.hud(), atlas);
            g.setColor(FRAME_BLUE);
            g.drawRect(GameConfig.FRAME_INSET, GameConfig.FRAME_INSET,
                    GameConfig.FRAME_SIZE, GameConfig.FRAME_SIZE);
        }

        // 图层 ④：静态层一次烘焙，事件帧才重绘
        g.drawImage(cache.imageFor(state.scene(), atlas), 0, 0, null);

        // 图层 ⑤：动态实体
        for (SpriteView sprite : state.sprites()) {
            drawSprite(g, sprite, atlas);
        }
        for (BulletView bullet : state.bullets()) {
            drawBullet(g, bullet);
        }
        for (EffectView effect : state.effects()) {
            switch (effect) {
                case EffectView.Bomb bomb -> drawBomb(g, bomb);
                case EffectView.Shield shield -> drawShield(g, shield);
            }
        }

        // 图层 ⑥：草覆盖
        for (int key : state.scene().grass()) {
            drawGrassCell(g, key);
        }

        // 图层 ⑦：过关动画
        if (started) {
            winAnimation.render(g, state.flow().winningCount());
        }

        // 图层 ⑧：消息
        messageRenderer.render(g, messages);
    }

    // ------------------------------------------------------------------
    // 图层 ④ 复用：静态元素绘制（供 StaticLayerCache 烘焙）
    // ------------------------------------------------------------------

    static void drawBrick(Graphics2D g, WallView.Brick wall, TextureAtlas atlas) {
        int bits = wall.destroyedBits();
        if (bits == (1 << GameConfig.BRICK_BITS) - 1) {
            return;
        }
        int x = Cell.xOfKey(wall.key());
        int y = Cell.yOfKey(wall.key());
        int half = GameConfig.TANK_HALF;
        g.drawImage(atlas.at(GameConfig.TEX_BRICK), x - half, y - half, null);
        g.setColor(BRICK_BG);
        for (int i = 0; i < GameConfig.BRICK_BITS; i++) {
            if ((bits >>> i & 1) == 0) {
                continue;
            }
            int col = i % GameConfig.BRICK_SUB;
            int row = i / GameConfig.BRICK_SUB;
            int step = GameConfig.BRICK_SUB_SIZE - 1;
            g.fillRect(x - half + col * step,
                    y - half + row * step,
                    GameConfig.BRICK_SUB_SIZE, GameConfig.BRICK_SUB_SIZE);
        }
    }

    static void drawSteel(Graphics2D g, WallView.Steel wall, TextureAtlas atlas) {
        int bits = wall.destroyedBits();
        if (bits == (1 << GameConfig.STEEL_BITS) - 1) {
            return;
        }
        int x = Cell.xOfKey(wall.key());
        int y = Cell.yOfKey(wall.key());
        int half = GameConfig.TANK_HALF;
        g.drawImage(atlas.at(GameConfig.TEX_STEEL), x - half, y - half, null);
        g.setColor(BRICK_BG);
        for (int i = 0; i < GameConfig.STEEL_BITS; i++) {
            if ((bits >>> i & 1) == 0) {
                continue;
            }
            int col = i % GameConfig.STEEL_SUB;
            int row = i / GameConfig.STEEL_SUB;
            int step = GameConfig.STEEL_SUB_SIZE - 1;
            g.fillRect(x - half + col * step,
                    y - half + row * step,
                    GameConfig.STEEL_SUB_SIZE, GameConfig.STEEL_SUB_SIZE);
        }
    }

    static void drawRiver(Graphics2D g, int key, TextureAtlas atlas) {
        g.drawImage(atlas.at(GameConfig.TEX_RIVER),
                Cell.xOfKey(key) - GameConfig.TANK_HALF,
                Cell.yOfKey(key) - GameConfig.TANK_HALF, null);
    }

    static void drawBase(Graphics2D g, BaseView base, TextureAtlas atlas) {
        int texture = base.destroyed() ? GameConfig.TEX_BASE_DEAD : GameConfig.TEX_BASE_OK;
        g.drawImage(atlas.at(texture),
                base.x() - GameConfig.TANK_HALF,
                base.y() - GameConfig.TANK_HALF, null);
    }

    // ------------------------------------------------------------------
    // 图层 ⑤
    // ------------------------------------------------------------------

    static void drawSprite(Graphics2D g, SpriteView sprite, TextureAtlas atlas) {
        g.drawImage(atlas.at(sprite.textureIndex()),
                sprite.x() - GameConfig.TANK_HALF,
                sprite.y() - GameConfig.TANK_HALF, null);
    }

    static void drawBullet(Graphics2D g, BulletView bullet) {
        g.setColor(BULLET_GRAY);
        if (bullet.direction().vertical()) {
            g.fillRect(bullet.x() - 1, bullet.y() - 4,
                    GameConfig.BULLET_THIN, GameConfig.BULLET_LONG);
        } else {
            g.fillRect(bullet.x() - 4, bullet.y() - 1,
                    GameConfig.BULLET_LONG, GameConfig.BULLET_THIN);
        }
    }

    static void drawBomb(Graphics2D g, EffectView.Bomb bomb) {
        g.setColor(Color.red);
        g.fillOval(bomb.x() - bomb.size().outerRadius(), bomb.y() - bomb.size().outerRadius(),
                bomb.size().outerRadius() * 2, bomb.size().outerRadius() * 2);
        g.setColor(Color.orange);
        g.fillOval(bomb.x() - bomb.size().middleRadius(), bomb.y() - bomb.size().middleRadius(),
                bomb.size().middleRadius() * 2, bomb.size().middleRadius() * 2);
        g.setColor(Color.yellow);
        g.fillOval(bomb.x() - bomb.size().innerRadius(), bomb.y() - bomb.size().innerRadius(),
                bomb.size().innerRadius() * 2, bomb.size().innerRadius() * 2);
    }

    static void drawShield(Graphics2D g, EffectView.Shield shield) {
        g.setColor(SHIELD_RED);
        g.drawRect(shield.x() - GameConfig.TANK_HALF, shield.y() - GameConfig.TANK_HALF,
                GameConfig.SHIELD_OUTER, GameConfig.SHIELD_OUTER);
        g.drawRect(shield.x() - GameConfig.TANK_HALF + 1, shield.y() - GameConfig.TANK_HALF + 1,
                GameConfig.SHIELD_INNER, GameConfig.SHIELD_INNER);
    }

    // ------------------------------------------------------------------
    // 图层 ⑥
    // ------------------------------------------------------------------

    static void drawGrassCell(Graphics2D g, int key) {
        int x = Cell.xOfKey(key);
        int y = Cell.yOfKey(key);
        int half = GameConfig.TANK_HALF;
        g.setColor(GRASS_LIGHT);
        for (int i = y - 11; i <= y + 12; i += 5) {
            g.drawLine(x - half, i, x + half, i);
        }
        for (int i = x - 11; i <= x + 12; i += 5) {
            g.drawLine(i, y - half, i, y + half);
        }
        g.setColor(GRASS_DARK);
        for (int i = y - 10; i <= y + 12; i += 5) {
            g.drawLine(x - half, i, x + half, i);
        }
        for (int i = x - 10; i <= x + 12; i += 5) {
            g.drawLine(i, y - half, i, y + half);
        }
    }

    /**
     * 供测试与缓存使用：按方向取子弹尺寸。
     */
    static int[] bulletSize(Direction direction) {
        return direction.vertical()
                ? new int[]{GameConfig.BULLET_THIN, GameConfig.BULLET_LONG}
                : new int[]{GameConfig.BULLET_LONG, GameConfig.BULLET_THIN};
    }
}
