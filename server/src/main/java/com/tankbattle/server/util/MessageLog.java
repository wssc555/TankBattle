package com.tankbattle.server.util;

import com.tankbattle.common.config.GameConfig;

import java.util.ArrayDeque;
import java.util.List;
import java.util.SequencedCollection;

/**
 * 8 条滚动消息队列。用 {@link ArrayDeque} + {@link SequencedCollection}（JEP 431）实现。
 * <p>
 * 采用组合而非继承，避免把可变队列操作暴露给调用方；方法加锁，允许 EDT（聊天提示）
 * 与游戏线程同时访问。
 */
public final class MessageLog {

    private final ArrayDeque<String> queue = new ArrayDeque<>(GameConfig.MESSAGE_CAPACITY);

    /**
     * 入队；超过容量时挤掉最旧的一条。
     */
    public synchronized void add(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        while (queue.size() >= GameConfig.MESSAGE_CAPACITY) {
            queue.pollFirst();
        }
        queue.addLast(message);
    }

    /**
     * 弹出最旧一条；空队列返回 {@code null}。
     */
    public synchronized String poll() {
        return queue.pollFirst();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized int size() {
        return queue.size();
    }

    /**
     * 不可变视图，顺序为最旧 → 最新。
     */
    public synchronized List<String> view() {
        return List.copyOf(queue);
    }

    /**
     * 反向视图（最新在前），JEP 431。
     */
    public synchronized SequencedCollection<String> reversed() {
        return List.copyOf(queue).reversed();
    }

    public synchronized void clear() {
        queue.clear();
    }
}
