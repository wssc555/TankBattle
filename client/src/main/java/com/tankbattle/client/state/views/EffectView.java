package com.tankbattle.client.state.views;

import com.tankbattle.common.protocol.S2c;

/**
 * 特效一帧快照（{@code o} / {@code i} 指令）。
 * <p>
 * 两者都是"只活一帧"的标记：服务端逐帧重发，状态快照不含即消失，渲染无副作用。
 */
public sealed interface EffectView {

    int x();

    int y();

    /**
     * 爆炸，半径由 {@link S2c.BombSize} 持有。
     */
    record Bomb(int x, int y, S2c.BombSize size) implements EffectView {
    }

    /**
     * 玩家无敌护盾标记。
     */
    record Shield(int x, int y) implements EffectView {
    }
}
