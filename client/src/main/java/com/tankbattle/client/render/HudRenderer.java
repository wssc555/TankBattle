package com.tankbattle.client.render;

import com.tankbattle.client.state.HudState;
import com.tankbattle.common.config.GameConfig;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * HUD 渲染（图层 ②，x ≥ 515 区域）。
 * <p>
 * 只读 {@link HudState}；颜色为静态常量。
 */
public final class HudRenderer {

    private static final Color HUD_BLUE = new Color(81, 111, 230);
    private static final Color HUD_P1 = Color.yellow;
    private static final Color HUD_P2 = Color.green;

    public void render(Graphics2D g, HudState hud, TextureAtlas atlas) {
        g.setColor(HUD_BLUE);
        g.drawString("第  " + hud.level() + "  关", 527, 39);
        g.drawString("敌人数 =  " + hud.enemyLeft(), 527, 79);

        g.setColor(HUD_P1);
        g.drawImage(atlas.at(GameConfig.TEX_HUD_P1), 520, 380, null);
        g.drawString("x", 555, 395);
        g.drawString(String.valueOf(hud.p1Life()), 565, 396);
        g.drawString("P1 得分:", 515, 370);
        g.drawString(HudState.formatScore(hud.p1Score()), 566, 370);

        g.setColor(HUD_P2);
        g.drawImage(atlas.at(GameConfig.TEX_HUD_P2), 520, 460, null);
        g.drawString("x", 555, 475);
        g.drawString(String.valueOf(hud.p2Life()), 565, 476);
        g.drawString("P2 得分:", 515, 450);
        g.drawString(HudState.formatScore(hud.p2Score()), 566, 450);
    }
}
