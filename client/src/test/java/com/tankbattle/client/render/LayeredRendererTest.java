package com.tankbattle.client.render;

import com.tankbattle.client.state.views.EffectView;
import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.S2c;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 子弹尺寸（竖 3×9 / 横 9×3）与爆炸半径（big 14/9/6、small 7/4/2）。
 */
class LayeredRendererTest {

    @Test
    void bulletSizeFollowsDirection() {
        assertArrayEquals(new int[]{GameConfig.BULLET_THIN, GameConfig.BULLET_LONG},
                LayeredRenderer.bulletSize(Direction.UP));
        assertArrayEquals(new int[]{GameConfig.BULLET_THIN, GameConfig.BULLET_LONG},
                LayeredRenderer.bulletSize(Direction.DOWN));
        assertArrayEquals(new int[]{GameConfig.BULLET_LONG, GameConfig.BULLET_THIN},
                LayeredRenderer.bulletSize(Direction.LEFT));
        assertArrayEquals(new int[]{GameConfig.BULLET_LONG, GameConfig.BULLET_THIN},
                LayeredRenderer.bulletSize(Direction.RIGHT));
    }

    @Test
    void bombSizeRadiiMatchLegacyValues() {
        S2c.BombSize big = S2c.BombSize.BIG;
        assertEquals(GameConfig.BOMB_BIG_OUTER, big.outerRadius());
        assertEquals(GameConfig.BOMB_BIG_MIDDLE, big.middleRadius());
        assertEquals(GameConfig.BOMB_BIG_INNER, big.innerRadius());

        S2c.BombSize small = S2c.BombSize.SMALL;
        assertEquals(GameConfig.BOMB_SMALL_OUTER, small.outerRadius());
        assertEquals(GameConfig.BOMB_SMALL_MIDDLE, small.middleRadius());
        assertEquals(GameConfig.BOMB_SMALL_INNER, small.innerRadius());
    }

    @Test
    void effectViewCarriesBombSize() {
        EffectView.Bomb bomb = new EffectView.Bomb(100, 200, S2c.BombSize.SMALL);
        assertEquals(7, bomb.size().outerRadius());
        assertEquals(2, bomb.size().innerRadius());
    }

    @Test
    void protocolTextRoundTrip() {
        assertEquals("big", S2c.BombSize.BIG.text());
        assertEquals("small", S2c.BombSize.SMALL.text());
    }
}
