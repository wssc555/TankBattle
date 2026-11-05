package com.tankbattle.server.render;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.Snapshot;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 监视画布：只读 {@link Snapshot}。
 * <p>
 * 绘制层序：底色 → 边框 → 地面物（河/砖/钢/基地）→ 坦克/子弹 → 草地 → 特效 → HUD → 消息。
 */
public final class MonitorPanel extends JPanel {

    private static final Color GROUND = new Color(128, 64, 0);
    private static final Color FRAME = Color.blue;
    private static final Color MESSAGE = Color.WHITE;
    private static final Color WIN_SHADE = Color.gray;
    private static final Color GRASS_LIGHT = new Color(0, 225, 0);
    private static final Color GRASS_DARK = new Color(0, 128, 0);
    private static final Color BULLET = Color.lightGray;
    private static final Color SHIELD = Color.red;

    private static final int MESSAGE_X = 5;
    private static final int MESSAGE_Y = 12;
    private static final int MESSAGE_DY = 16;
    private static final int WIN_TEXT_X = 240;
    private static final int WIN_TEXT_Y = 250;
    /**
     * 砖墙 4×4 子块的绘制步长（7px 块、6px 间距）。
     */
    private static final int BRICK_STEP = 6;

    private volatile Snapshot snapshot;
    private BufferedImage offscreen;

    private int winRed = GameConfig.WIN_TEXT_R;
    private int winGreen = GameConfig.WIN_TEXT_G;
    private int winBlue = GameConfig.WIN_TEXT_B;

    public MonitorPanel() {
        setDoubleBuffered(true);
        setBackground(GROUND);
        setPreferredSize(new java.awt.Dimension(GameConfig.PANEL_W, GameConfig.PANEL_H));
        setFocusable(true);
    }

    /**
     * 游戏线程调用；内容变化时才触发重绘。
     */
    public void accept(Snapshot next) {
        Snapshot previous = this.snapshot;
        this.snapshot = next;
        if (previous == null || !previous.equals(next)) {
            repaint();
        }
    }

    public Snapshot currentSnapshot() {
        return snapshot;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        if (offscreen == null || offscreen.getWidth() != width || offscreen.getHeight() != height) {
            offscreen = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D buffer = offscreen.createGraphics();
        try {
            buffer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            render(buffer);
        } finally {
            buffer.dispose();
        }
        g.drawImage(offscreen, 0, 0, null);
    }

    private void render(Graphics2D g) {
        Snapshot current = snapshot;
        g.setColor(GROUND);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (current != null) {
            g.setColor(FRAME);
            g.drawRect(GameConfig.FRAME_INSET, GameConfig.FRAME_INSET,
                    GameConfig.FRAME_SIZE, GameConfig.FRAME_SIZE);

            drawGround(g, current);
            drawPickups(g, current);
            drawTanks(g, current);
            drawBullets(g, current);
            drawGrass(g, current);
            drawEffects(g, current);
            HudRenderer.draw(g, current);
            drawWinAnimation(g, current);
        }

        drawMessages(g, current);
    }

    private void drawGround(Graphics2D g, Snapshot snapshot) {
        Image river = TextureBank.get(GameConfig.TEX_RIVER);
        if (river != null) {
            for (Snapshot.RiverView view : snapshot.rivers()) {
                g.drawImage(river, view.x() - GameConfig.TANK_HALF,
                        view.y() - GameConfig.TANK_HALF, null);
            }
        }
        for (Snapshot.WallView wall : snapshot.walls()) {
            if (wall.brick()) {
                drawBrick(g, wall);
            } else {
                drawSteel(g, wall);
            }
        }
        if (snapshot.base() != null) {
            Snapshot.BaseView base = snapshot.base();
            Image texture = TextureBank.get(base.destroyed()
                    ? GameConfig.TEX_BASE_DEAD : GameConfig.TEX_BASE_OK);
            if (texture != null) {
                g.drawImage(texture, base.x() - GameConfig.TANK_HALF,
                        base.y() - GameConfig.TANK_HALF, null);
            }
        }
    }

    private void drawBrick(Graphics2D g, Snapshot.WallView wall) {
        Image texture = TextureBank.get(GameConfig.TEX_BRICK);
        if (texture != null) {
            g.drawImage(texture, wall.x() - GameConfig.TANK_HALF,
                    wall.y() - GameConfig.TANK_HALF, null);
        }
        g.setColor(GROUND);
        int bits = wall.shapeBits();
        for (int index = 0; index < 16; index++) {
            if ((bits >>> index & 1) == 0) {
                continue;
            }
            int col = index & 3;
            int row = index >> 2;
            g.fillRect(wall.x() - GameConfig.TANK_HALF + col * BRICK_STEP,
                    wall.y() - GameConfig.TANK_HALF + row * BRICK_STEP,
                    GameConfig.BRICK_SUB_SIZE, GameConfig.BRICK_SUB_SIZE);
        }
    }

    private void drawSteel(Graphics2D g, Snapshot.WallView wall) {
        Image texture = TextureBank.get(GameConfig.TEX_STEEL);
        if (texture != null) {
            g.drawImage(texture, wall.x() - GameConfig.TANK_HALF,
                    wall.y() - GameConfig.TANK_HALF, null);
        }
        g.setColor(GROUND);
        int bits = wall.shapeBits();
        for (int index = 0; index < 4; index++) {
            if ((bits >>> index & 1) == 0) {
                continue;
            }
            int col = index & 1;
            int row = index >> 1;
            g.fillRect(wall.x() - GameConfig.TANK_HALF + col * GameConfig.STEEL_SUB_SIZE,
                    wall.y() - GameConfig.TANK_HALF + row * GameConfig.STEEL_SUB_SIZE,
                    GameConfig.STEEL_SUB_SIZE, GameConfig.STEEL_SUB_SIZE);
        }
    }

    private void drawPickups(Graphics2D g, Snapshot snapshot) {
        for (Snapshot.PickupView pickup : snapshot.pickups()) {
            Image texture = TextureBank.get(pickup.textureIndex());
            if (texture != null) {
                g.drawImage(texture, pickup.x() - GameConfig.TANK_HALF,
                        pickup.y() - GameConfig.TANK_HALF, null);
            }
        }
    }

    private void drawTanks(Graphics2D g, Snapshot snapshot) {
        for (Snapshot.TankView tank : snapshot.tanks()) {
            Image texture = TextureBank.get(tank.textureIndex());
            if (texture != null) {
                g.drawImage(texture, tank.x() - GameConfig.TANK_HALF,
                        tank.y() - GameConfig.TANK_HALF, null);
            }
            if (tank.player() && tank.invulnerable()) {
                g.setColor(SHIELD);
                g.drawRect(tank.x() - GameConfig.TANK_HALF, tank.y() - GameConfig.TANK_HALF,
                        ServerConfig.TANK_BOX, ServerConfig.TANK_BOX);
                g.drawRect(tank.x() - GameConfig.TANK_HALF + 1, tank.y() - GameConfig.TANK_HALF + 1,
                        ServerConfig.TANK_BOX - 2, ServerConfig.TANK_BOX - 2);
            }
        }
    }

    private void drawBullets(Graphics2D g, Snapshot snapshot) {
        g.setColor(BULLET);
        for (Snapshot.BulletView bullet : snapshot.bullets()) {
            boolean vertical = bullet.direction() == 0 || bullet.direction() == 1;
            if (vertical) {
                g.fillRect(bullet.x() - 2, bullet.y() - 5, 3, 9);
            } else {
                g.fillRect(bullet.x() - 5, bullet.y() - 2, 9, 3);
            }
        }
    }

    private void drawGrass(Graphics2D g, Snapshot snapshot) {
        for (Snapshot.GrassView grass : snapshot.decor()) {
            g.setColor(GRASS_LIGHT);
            for (int y = grass.y() - 11; y <= grass.y() + 12; y += 5) {
                g.drawLine(grass.x() - 12, y, grass.x() + 12, y);
            }
            for (int x = grass.x() - 11; x <= grass.x() + 12; x += 5) {
                g.drawLine(x, grass.y() - 12, x, grass.y() + 12);
            }
            g.setColor(GRASS_DARK);
            for (int y = grass.y() - 10; y <= grass.y() + 12; y += 5) {
                g.drawLine(grass.x() - 12, y, grass.x() + 12, y);
            }
            for (int x = grass.x() - 10; x <= grass.x() + 12; x += 5) {
                g.drawLine(x, grass.y() - 12, x, grass.y() + 12);
            }
        }
    }

    private void drawEffects(Graphics2D g, Snapshot snapshot) {
        for (Snapshot.EffectView effect : snapshot.effects()) {
            int inner = effect.size().innerRadius();
            int middle = effect.size().middleRadius();
            int outer = effect.size().outerRadius();
            g.setColor(Color.red);
            g.fillOval(effect.x() - outer, effect.y() - outer, outer * 2, outer * 2);
            g.setColor(Color.orange);
            g.fillOval(effect.x() - middle, effect.y() - middle, middle * 2, middle * 2);
            g.setColor(Color.yellow);
            g.fillOval(effect.x() - inner, effect.y() - inner, inner * 2, inner * 2);
        }
    }

    private void drawWinAnimation(Graphics2D g, Snapshot snapshot) {
        int count = snapshot.winningCount();
        if (count <= GameConfig.WIN_ANIM_START) {
            winRed = GameConfig.WIN_TEXT_R;
            winGreen = GameConfig.WIN_TEXT_G;
            winBlue = GameConfig.WIN_TEXT_B;
            return;
        }
        int temp = count - GameConfig.WIN_ANIM_START;
        if (temp > GameConfig.WIN_MASK_MAX_STEP) {
            temp = GameConfig.WIN_MASK_MAX_STEP;
        }
        if (count > GameConfig.WIN_NEXT_LEVEL_FRAME) {
            temp = GameConfig.WIN_RESET_FRAME - count;
        }
        g.setColor(WIN_SHADE);
        g.fillRect(GameConfig.FRAME_INSET + 1, GameConfig.FRAME_INSET + 1, 500, temp * 10);
        g.fillRect(GameConfig.FRAME_INSET + 1, 500 - temp * 10, 500, (1 + temp) * 10 + 2);

        if (count > GameConfig.WIN_TEXT_START && count < GameConfig.WIN_NEXT_LEVEL_FRAME) {
            if (count > GameConfig.WIN_FADE_START) {
                winRed += (int) ((GameConfig.WIN_FADE_TARGET - winRed) * GameConfig.WIN_FADE_RATE);
                winGreen += (int) ((GameConfig.WIN_FADE_TARGET - winGreen) * GameConfig.WIN_FADE_RATE);
            }
            g.setColor(new Color(clamp(winRed), clamp(winGreen), clamp(winBlue)));
            g.drawString("过 关 了  ！", WIN_TEXT_X, WIN_TEXT_Y);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void drawMessages(Graphics2D g, Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        g.setColor(MESSAGE);
        var messages = snapshot.messages();
        for (int i = 0; i < messages.size() && i < GameConfig.MESSAGE_CAPACITY; i++) {
            String message = messages.get(i);
            if (message != null && !message.isEmpty()) {
                g.drawString(message, MESSAGE_X, MESSAGE_Y + i * MESSAGE_DY);
            }
        }
    }
}
