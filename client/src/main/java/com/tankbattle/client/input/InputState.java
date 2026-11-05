package com.tankbattle.client.input;

import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.C2s;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 输入意图状态。
 * <p>
 * 单写者（EDT）写字段，发送线程读 {@link AtomicReference} 快照；不共享可变数组。
 * 方向互斥由配置控制：开启时置一清三，关闭时按服务端 5 bit 独立叠加。
 */
public final class InputState {

    /**
     * 一次采样值；{@code seq} 供丢输入检测与协议调试。
     */
    public record InputFrame(boolean up, boolean down, boolean left, boolean right,
                             boolean fire, long seq) {

        public static final InputFrame IDLE = new InputFrame(false, false, false, false, false, 0L);

        public C2s.Input toCommand() {
            return new C2s.Input(up, down, left, right, fire, seq);
        }
    }

    private final boolean exclusive;
    private final AtomicReference<InputFrame> published = new AtomicReference<>(InputFrame.IDLE);

    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private boolean fire;
    private long seq;

    public InputState(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public void press(Direction direction) {
        if (exclusive) {
            up = direction == Direction.UP;
            down = direction == Direction.DOWN;
            left = direction == Direction.LEFT;
            right = direction == Direction.RIGHT;
        } else {
            set(direction, true);
        }
        publish();
    }

    public void release(Direction direction) {
        set(direction, false);
        publish();
    }

    public void pressFire() {
        fire = true;
        publish();
    }

    public void releaseFire() {
        fire = false;
        publish();
    }

    /**
     * 失焦 / 断线时清零全部意图。
     */
    public void clear() {
        up = false;
        down = false;
        left = false;
        right = false;
        fire = false;
        publish();
    }

    private void set(Direction direction, boolean value) {
        switch (direction) {
            case UP -> up = value;
            case DOWN -> down = value;
            case LEFT -> left = value;
            case RIGHT -> right = value;
        }
    }

    private void publish() {
        published.set(new InputFrame(up, down, left, right, fire, ++seq));
    }

    public InputFrame snapshot() {
        return published.get();
    }

    public long seq() {
        return published.get().seq();
    }
}
