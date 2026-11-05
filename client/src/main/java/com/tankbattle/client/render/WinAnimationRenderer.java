package com.tankbattle.client.render;

import com.tankbattle.common.config.GameConfig;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * 过关动画（图层 ⑦）。
 * <p>
 * 纯绘制 + 一个 EDT 独占的颜色缓动状态（无并发）。阈值 150/190/400/470/500 全部来自
 * {@link GameConfig}；{@link #maskHeight(int)} / {@link #textVisible(int)} 提为纯函数便于断言。
 */
public final class WinAnimationRenderer {

    private static final Color MASK = Color.gray;
    private static final String TEXT = "过 关 了  ！";

    private int red = GameConfig.WIN_TEXT_R;
    private int green = GameConfig.WIN_TEXT_G;
    private int blue = GameConfig.WIN_TEXT_B;

    public void render(Graphics2D g, int winningCount) {
        updateColors(winningCount);
        if (winningCount <= GameConfig.WIN_ANIM_START) {
            return;
        }
        int temp = maskHeight(winningCount);
        g.setColor(MASK);
        g.fillRect(11, 11, 500, temp * 10);
        g.fillRect(11, 500 - temp * 10, 500, (1 + temp) * 10 + 2);

        if (textVisible(winningCount)) {
            g.setColor(new Color(red, green, blue));
            g.drawString(TEXT, 240, 250);
        }
    }

    /**
     * 上下灰幕高度步长（每步 10px）。
     */
    public static int maskHeight(int winningCount) {
        int temp = winningCount - GameConfig.WIN_ANIM_START;
        if (temp * 10 > 300) {
            temp = GameConfig.WIN_MASK_MAX_STEP;
        }
        if (winningCount > GameConfig.WIN_NEXT_LEVEL_FRAME) {
            temp = GameConfig.WIN_RESET_FRAME - winningCount;
        }
        return Math.max(0, temp);
    }

    /**
     * "过 关 了 ！" 是否可见。
     */
    public static boolean textVisible(int winningCount) {
        return winningCount > GameConfig.WIN_TEXT_START
                && winningCount < GameConfig.WIN_NEXT_LEVEL_FRAME;
    }

    /**
     * 颜色缓动：n > 400 时红/绿向 128 逼近；n <= 150 复位。
     */
    public void updateColors(int winningCount) {
        if (winningCount <= GameConfig.WIN_ANIM_START) {
            red = GameConfig.WIN_TEXT_R;
            green = GameConfig.WIN_TEXT_G;
            blue = GameConfig.WIN_TEXT_B;
            return;
        }
        if (winningCount > GameConfig.WIN_FADE_START && textVisible(winningCount)) {
            red += (int) ((GameConfig.WIN_FADE_TARGET - red) * GameConfig.WIN_FADE_RATE);
            green += (int) ((GameConfig.WIN_FADE_TARGET - green) * GameConfig.WIN_FADE_RATE);
        }
    }

    public int red() {
        return red;
    }

    public int green() {
        return green;
    }

    public int blue() {
        return blue;
    }

    public void reset() {
        red = GameConfig.WIN_TEXT_R;
        green = GameConfig.WIN_TEXT_G;
        blue = GameConfig.WIN_TEXT_B;
    }
}
