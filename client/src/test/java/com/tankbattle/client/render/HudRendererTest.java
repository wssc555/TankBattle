package com.tankbattle.client.render;

import com.tankbattle.client.state.HudState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 7 位补零、6 字段顺序。
 */
class HudRendererTest {

    @Test
    void padsScoreToSevenDigits() {
        assertEquals("0000000", HudState.formatScore(0));
        assertEquals("0000123", HudState.formatScore(123));
        assertEquals("9999999", HudState.formatScore(9999999));
        assertEquals("0000000", HudState.formatScore(-5));
    }

    @Test
    void keepsProtocolFieldOrder() {
        HudState hud = new HudState(5, 3, 4, 1200, 2, 800);
        assertEquals(5, hud.enemyLeft());
        assertEquals(3, hud.level());
        assertEquals(4, hud.p1Life());
        assertEquals(1200, hud.p1Score());
        assertEquals(2, hud.p2Life());
        assertEquals(800, hud.p2Score());
    }
}
