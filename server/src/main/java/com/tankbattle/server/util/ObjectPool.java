package com.tankbattle.server.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * 极简有界对象池。用于爆炸特效等高频短生命周期对象。
 * <p>
 * 只在游戏线程内使用，无需同步。
 */
public final class ObjectPool<T> {

    private final Deque<T> free;
    private final Supplier<T> factory;
    private final int capacity;
    private int created;

    public ObjectPool(int capacity, Supplier<T> factory) {
        this.capacity = capacity;
        this.factory = factory;
        this.free = new ArrayDeque<>(capacity);
    }

    public T acquire() {
        T pooled = free.pollLast();
        if (pooled != null) {
            return pooled;
        }
        created++;
        return factory.get();
    }

    public void release(T obj) {
        if (obj != null && free.size() < capacity) {
            free.addLast(obj);
        }
    }

    /**
     * 累计创建次数，用于基准对比池化收益。
     */
    public int createdCount() {
        return created;
    }

    public int idleCount() {
        return free.size();
    }
}
