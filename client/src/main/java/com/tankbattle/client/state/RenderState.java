package com.tankbattle.client.state;

import com.tankbattle.client.state.views.BulletView;
import com.tankbattle.client.state.views.EffectView;
import com.tankbattle.client.state.views.SpriteView;

import java.util.List;

/**
 * 一帧完整、深不可变、可回放的渲染快照。
 * <p>
 * 由组装线程独占创建，经 {@link StateStore} 发布；EDT 只读。所有集合在紧凑构造器中防御性拷贝。
 */
public record RenderState(
        long seq,
        long receivedNanos,
        SceneState scene,
        HudState hud,
        FlowState flow,
        List<SpriteView> sprites,
        List<BulletView> bullets,
        List<EffectView> effects,
        List<String> messages) {

    public static final RenderState EMPTY = new RenderState(
            0L, 0L, SceneState.EMPTY, HudState.EMPTY, FlowState.EMPTY,
            List.of(), List.of(), List.of(), List.of());

    public RenderState {
        sprites = List.copyOf(sprites);
        bullets = List.copyOf(bullets);
        effects = List.copyOf(effects);
        messages = List.copyOf(messages);
    }
}
