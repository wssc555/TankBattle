package com.tankbattle.client.state;

import com.tankbattle.client.state.views.BulletView;
import com.tankbattle.client.state.views.SpriteView;
import com.tankbattle.common.model.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 插值边界——缺帧序退回、尺寸不一致退回、alpha 饱和退回、
 * previous 比 current 新（乱序/竞态）退回、线性插值中点值。
 */
class InterpolatorTest {

    private static RenderState state(long seq, List<SpriteView> sprites, List<BulletView> bullets) {
        return new RenderState(seq, System.nanoTime(), SceneState.EMPTY, HudState.EMPTY,
                FlowState.EMPTY, sprites, bullets, List.of(), List.of());
    }

    private static RenderState pairOf(long seq, int x, long receivedNanos) {
        SpriteView s = new SpriteView(x, 100, 70);
        BulletView b = new BulletView(x, 200, Direction.UP);
        return new RenderState(seq, receivedNanos, SceneState.EMPTY, HudState.EMPTY,
                FlowState.EMPTY, List.of(s), List.of(b), List.of(), List.of());
    }

    @Test
    void returnsCurrentWhenSeqMissing() {
        RenderState noSeq = pairOf(0, 10, System.nanoTime());
        RenderState current = pairOf(5, 50, System.nanoTime());
        assertSame(current, Interpolator.interpolate(noSeq, current, System.nanoTime()));
        // previous 有序号、current 缺序号 → 缺 d{seq} 的一帧不插值
        assertSame(noSeq, Interpolator.interpolate(current, noSeq, System.nanoTime()));
    }

    @Test
    void returnsCurrentWhenSizesDiffer() {
        RenderState one = pairOf(1, 10, System.nanoTime());
        RenderState two = state(2, List.of(new SpriteView(10, 100, 70), new SpriteView(20, 100, 70)),
                List.of(new BulletView(10, 200, Direction.UP)));
        assertSame(two, Interpolator.interpolate(one, two, System.nanoTime()));
    }

    @Test
    void returnsCurrentWhenAlphaSaturated() {
        long old = System.nanoTime() - 1_000_000_000L; // 1s 前，远超 30ms 预算
        RenderState previous = pairOf(1, 10, old);
        RenderState current = pairOf(2, 50, old);
        assertSame(current, Interpolator.interpolate(previous, current, System.nanoTime()));
    }

    /**
     * previous 比 current 新（EDT 两读之间连发两帧）不得倒放。
     */
    @Test
    void returnsCurrentWhenPreviousIsNewer() {
        RenderState current = pairOf(2, 50, System.nanoTime());
        RenderState newer = pairOf(7, 90, System.nanoTime());
        assertSame(current, Interpolator.interpolate(newer, current, System.nanoTime()));
        assertSame(current, Interpolator.interpolate(current, current, System.nanoTime()));
    }

    @Test
    void lerpsHalfwayAtHalfBudget() {
        long now = System.nanoTime();
        long received = now - 15_000_000L; // 30ms 预算的一半
        RenderState previous = pairOf(1, 100, received);
        RenderState current = pairOf(2, 200, received);

        RenderState blended = Interpolator.interpolate(previous, current, now);
        assertEquals(150, blended.sprites().get(0).x());
        assertEquals(150, blended.bullets().get(0).x());
        assertEquals(2, blended.seq(), "插值结果沿用 current 的 seq");
    }
}
