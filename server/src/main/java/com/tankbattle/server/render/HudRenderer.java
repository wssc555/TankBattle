package com.tankbattle.server.render;

import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.Snapshot;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;

/**
 * HUD 绘制：关卡、敌数、双方分数与生命。全部数据来自 {@link Snapshot}。
 */
public final class HudRenderer {

    private static final Color HUD_INFO = new Color(81, 111, 230);
    private static final Color HUD_P1 = Color.YELLOW;
    private static final Color HUD_P2 = Color.GREEN;

    private static final int LEVEL_X = 527;
    private static final int LEVEL_Y = 39;
    private static final int ENEMY_X = 527;
    private static final int ENEMY_Y = 79;

    private static final int P1_ICON_X = 520;
    private static final int P1_ICON_Y = 380;
    private static final int P2_ICON_X = 520;
    private static final int P2_ICON_Y = 460;
    private static final int TEXT_X = 515;
    private static final int LIFE_X = 565;
    private static final int SCORE_X = 566;
    private static final int P1_LABEL_Y = 370;
    private static final int P2_LABEL_Y = 450;
    private static final int ICON_LIFE_DY = 16;
    private static final int SCORE_WIDTH = 7;

    private HudRenderer() {
    }

    public static void draw(Graphics g, Snapshot snapshot) {
        g.setColor(HUD_INFO);
        g.drawString("第  " + snapshot.level() + "  关", LEVEL_X, LEVEL_Y);
        g.drawString("敌人数 =  " + snapshot.enemyLeft(), ENEMY_X, ENEMY_Y);

        drawPlayer(g, 1, snapshot.p1Life(), snapshot.p1Score());
        drawPlayer(g, 2, snapshot.p2Life(), snapshot.p2Score());
    }

    private static void drawPlayer(Graphics g, int player, int life, int score) {
        boolean first = player == 1;
        g.setColor(first ? HUD_P1 : HUD_P2);
        Image icon = TextureBank.get(first ? ServerConfig.TEX_PLAYER1 : ServerConfig.TEX_PLAYER2);
        int iconX = first ? P1_ICON_X : P2_ICON_X;
        int iconY = first ? P1_ICON_Y : P2_ICON_Y;
        if (icon != null) {
            g.drawImage(icon, iconX, iconY, null);
        }
        int labelY = first ? P1_LABEL_Y : P2_LABEL_Y;
        g.drawString("x", iconX + 35, iconY + ICON_LIFE_DY - 1);
        g.drawString(String.valueOf(life), LIFE_X, iconY + ICON_LIFE_DY);
        g.drawString("P" + player + " 得分:", TEXT_X, labelY);
        g.drawString(padScore(score), SCORE_X, labelY);
    }

    /**
     * 左补零到 7 位。
     */
    static String padScore(int score) {
        String raw = "000000000" + score;
        return raw.substring(raw.length() - SCORE_WIDTH);
    }
}
