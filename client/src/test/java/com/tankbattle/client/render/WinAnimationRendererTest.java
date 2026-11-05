package com.tankbattle.client.render;

import com.tankbattle.common.config.GameConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 150/190/400/470/500 五个边界。
 */
class WinAnimationRendererTest {

    @Test
    void maskHeightFollowsThresholdTable() {
        assertEquals(0, WinAnimationRenderer.maskHeight(GameConfig.WIN_ANIM_START));
        assertEquals(1, WinAnimationRenderer.maskHeight(151));
        assertEquals(30, WinAnimationRenderer.maskHeight(180));
        assertEquals(30, WinAnimationRenderer.maskHeight(181));
        assertEquals(30, WinAnimationRenderer.maskHeight(470));
        assertEquals(29, WinAnimationRenderer.maskHeight(471));
        assertEquals(1, WinAnimationRenderer.maskHeight(499));
        assertEquals(0, WinAnimationRenderer.maskHeight(500));
        assertEquals(0, WinAnimationRenderer.maskHeight(501));
    }

    @Test
    void textVisibilityMatchesOpenInterval() {
        assertFalse(WinAnimationRenderer.textVisible(190));
        assertTrue(WinAnimationRenderer.textVisible(191));
        assertTrue(WinAnimationRenderer.textVisible(469));
        assertFalse(WinAnimationRenderer.textVisible(470));
        assertFalse(WinAnimationRenderer.textVisible(500));
    }

    @Test
    void easesTowardGrayAfterFourHundred() {
        WinAnimationRenderer renderer = new WinAnimationRenderer();
        renderer.updateColors(469);
        assertEquals(52, renderer.red());
        assertEquals(44, renderer.green());
        assertEquals(128, renderer.blue());
    }

    @Test
    void resetsColorWhenAnimationEnds() {
        WinAnimationRenderer renderer = new WinAnimationRenderer();
        renderer.updateColors(460);
        renderer.updateColors(150);
        assertEquals(GameConfig.WIN_TEXT_R, renderer.red());
        assertEquals(GameConfig.WIN_TEXT_G, renderer.green());
        assertEquals(GameConfig.WIN_TEXT_B, renderer.blue());
    }
}
