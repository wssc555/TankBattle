package com.tankbattle.client.state;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 单写多读的状态交换点。
 * <p>
 * 组装线程 {@link #publish(RenderState)}，EDT {@link #current()}；无锁、无撕裂帧。
 * {@link #previous()} 供插值取上一帧。
 */
public final class StateStore {

    private final AtomicReference<RenderState> current = new AtomicReference<>(RenderState.EMPTY);
    private volatile RenderState previous = RenderState.EMPTY;

    public void publish(RenderState state) {
        previous = current.getAndSet(state);
    }

    public RenderState current() {
        return current.get();
    }

    public RenderState previous() {
        return previous;
    }

    /**
     * 重连时清空画面。
     */
    public void clear() {
        publish(RenderState.EMPTY);
    }
}
