package com.tankbattle.client.state;

import com.tankbattle.client.state.views.BulletView;
import com.tankbattle.client.state.views.SpriteView;
import com.tankbattle.common.config.GameConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 双状态线性插值。
 * <p>
 * 仅在 FREE_RUNNING + 收到 {@code d{seq}} 时启用：按列表下标匹配（服务端快照顺序稳定），
 * 以当前帧到达时间为基准在 30ms 内从上一帧位置过渡到当前帧。尺寸不一致或缺少帧序号时退回当前帧。
 * <p>
 * 纯函数，不持有状态，不修改入参。
 */
public final class Interpolator {

    private static final long FRAME_NANOS = GameConfig.FRAME_MS * 1_000_000L;

    private Interpolator() {
    }

    public static RenderState interpolate(RenderState previous, RenderState current, long nowNanos) {
        if (previous == null || current == null) {
            return current;
        }
        if (previous.seq() <= 0 || current.seq() <= 0) {
            return current;
        }
        // EDT 两次读取之间组装线程可能连发数帧，previous 比 current 新时禁止倒放
        if (previous.seq() >= current.seq()) {
            return current;
        }
        if (previous.sprites().size() != current.sprites().size()
                || previous.bullets().size() != current.bullets().size()) {
            return current;
        }

        long age = Math.max(0L, nowNanos - current.receivedNanos());
        double alpha = Math.min(1.0, age / (double) FRAME_NANOS);
        if (alpha >= 1.0) {
            return current;
        }

        List<SpriteView> sprites = new ArrayList<>(current.sprites().size());
        for (int i = 0; i < current.sprites().size(); i++) {
            SpriteView before = previous.sprites().get(i);
            SpriteView after = current.sprites().get(i);
            if (before.textureIndex() != after.textureIndex()) {
                sprites.add(after);
            } else {
                sprites.add(new SpriteView(
                        lerp(before.x(), after.x(), alpha),
                        lerp(before.y(), after.y(), alpha),
                        after.textureIndex()));
            }
        }

        List<BulletView> bullets = new ArrayList<>(current.bullets().size());
        for (int i = 0; i < current.bullets().size(); i++) {
            BulletView before = previous.bullets().get(i);
            BulletView after = current.bullets().get(i);
            if (before.direction() != after.direction()) {
                bullets.add(after);
            } else {
                bullets.add(new BulletView(
                        lerp(before.x(), after.x(), alpha),
                        lerp(before.y(), after.y(), alpha),
                        after.direction()));
            }
        }

        return new RenderState(current.seq(), current.receivedNanos(), current.scene(),
                current.hud(), current.flow(), sprites, bullets, current.effects(), current.messages());
    }

    private static int lerp(int from, int to, double alpha) {
        return (int) Math.round(from + (to - from) * alpha);
    }
}
