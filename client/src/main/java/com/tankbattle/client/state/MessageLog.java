package com.tankbattle.client.state;

import com.tankbattle.common.config.GameConfig;

import java.util.ArrayDeque;
import java.util.List;

/**
 * 8 条滚动消息队列（JEP 431）。
 * <p>
 * 新消息入队首，最旧在队尾；{@link #snapshot()} 用 {@code reversed()} 还原"旧 → 新"的渲染顺序。
 * 容量 8，超出即丢最旧。
 * <p>
 * 该对象被网络线程（写入服务端消息）与 EDT（写入本地提示、绘制）共享，因此方法同步；
 * 临界区内只做内存操作，不包含 IO。
 * <p>
 * 注意：本类继承自 {@link ArrayDeque}，未同步的变形方法（{@code addFirst}/{@code iterator} 等）
 * 不受容量约束且非线程安全——外部只允许调用 {@link #addMessage}/{@link #snapshot}/{@link #pollOldest}。
 */
public final class MessageLog extends ArrayDeque<String> {

    private final int capacity;

    public MessageLog() {
        this(GameConfig.MESSAGE_CAPACITY);
    }

    public MessageLog(int capacity) {
        super(capacity);
        this.capacity = capacity;
    }

    /**
     * 追加一条消息；空白文本忽略。
     */
    public synchronized void addMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        addFirst(text);
        while (size() > capacity) {
            removeLast();
        }
    }

    /**
     * 弹出最旧一条（{@code gameFlow % 300 == 0} 时调用）。
     */
    public synchronized String pollOldest() {
        return pollLast();
    }

    /**
     * 渲染序快照：第 0 条为最旧，最后一条为最新。
     */
    public synchronized List<String> snapshot() {
        return List.copyOf(reversed());
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}
