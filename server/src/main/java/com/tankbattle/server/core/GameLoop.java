package com.tankbattle.server.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 固定步长主循环。
 * <p>
 * 单线程调度，超时不会叠加执行；{@link #close()} 后线程真正退出。
 */
public final class GameLoop implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GameLoop.class);

    private final ScheduledExecutorService executor;
    private final Runnable tick;
    private final int frameMs;
    private boolean started;

    public GameLoop(Runnable tick, int frameMs) {
        this.tick = tick;
        this.frameMs = frameMs;
        this.executor = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("game-loop").factory());
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        executor.scheduleAtFixedRate(tick, 0, frameMs, TimeUnit.MILLISECONDS);
        log.info("game loop started, frame={}ms", frameMs);
    }

    public boolean isRunning() {
        return started && !executor.isShutdown();
    }

    @Override
    public synchronized void close() {
        started = false;
        executor.shutdownNow();
        log.info("game loop stopped");
    }
}
