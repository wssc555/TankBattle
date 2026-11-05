package com.tankbattle.client.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 运行期指标。实例持有，经构造注入，不依赖静态可变状态。
 * <p>
 * 六项指标：接收帧率、渲染帧率、解码耗时、队列深度、丢弃帧数（oversize / 背压分开）、
 * 往返延迟（V2 下由 {@code d{seq}} 到达时间估算）。
 * <p>
 * 计数器只做原子递增，方法可在任意线程调用。
 */
public final class FrameStats {

    private static final Logger log = LoggerFactory.getLogger(FrameStats.class);

    private static final long REPORT_INTERVAL_NANOS = 10_000_000_000L;
    private static final long SLOW_DECODE_NANOS = 5_000_000L;

    private final AtomicLong droppedOversizeCounter = new AtomicLong();
    private final AtomicLong droppedByBackpressureCounter = new AtomicLong();
    private final AtomicLong receivedFrames = new AtomicLong();
    private final AtomicLong renderedFrames = new AtomicLong();
    private final AtomicLong decodeNanosTotal = new AtomicLong();
    private final AtomicLong decodeSamples = new AtomicLong();
    private final AtomicLong queueDepth = new AtomicLong();
    private final AtomicLong rttNanos = new AtomicLong();
    private final AtomicLong windowReceived = new AtomicLong();
    private final AtomicLong windowRendered = new AtomicLong();
    private final AtomicLong lastReportNanos = new AtomicLong(System.nanoTime());

    public void frameReceived() {
        receivedFrames.incrementAndGet();
        windowReceived.incrementAndGet();
    }

    /**
     * 单帧解码耗时；超过 5ms 记 WARN。
     */
    public void frameDecoded(long nanos) {
        decodeNanosTotal.addAndGet(nanos);
        decodeSamples.incrementAndGet();
        if (nanos > SLOW_DECODE_NANOS) {
            log.warn("单帧解码耗时 {} ms，超过阈值 5ms", String.format("%.2f", nanos / 1_000_000.0));
        }
    }

    public void frameRendered() {
        renderedFrames.incrementAndGet();
        windowRendered.incrementAndGet();
    }

    public void queueDepth(int depth) {
        queueDepth.set(depth);
    }

    public void roundTrip(long nanos) {
        rttNanos.set(nanos);
    }

    public void droppedOversize() {
        droppedOversizeCounter.incrementAndGet();
    }

    public void droppedByBackpressure() {
        droppedByBackpressureCounter.incrementAndGet();
    }

    public long receivedFrames() {
        return receivedFrames.get();
    }

    public long renderedFrames() {
        return renderedFrames.get();
    }

    public long droppedOversizeCount() {
        return droppedOversizeCounter.get();
    }

    public long droppedByBackpressureCount() {
        return droppedByBackpressureCounter.get();
    }

    public long droppedTotal() {
        return droppedOversizeCounter.get() + droppedByBackpressureCounter.get();
    }

    /**
     * 平均解码耗时（纳秒）。
     */
    public long averageDecodeNanos() {
        long samples = decodeSamples.get();
        return samples == 0 ? 0 : decodeNanosTotal.get() / samples;
    }

    /**
     * 每 10s 输出一次 INFO 汇总；由 EDT 渲染节拍调用，避免额外线程。
     */
    public void reportIfDue() {
        long now = System.nanoTime();
        long last = lastReportNanos.get();
        if (now - last < REPORT_INTERVAL_NANOS || !lastReportNanos.compareAndSet(last, now)) {
            return;
        }
        long seconds = Math.max(1, (now - last) / 1_000_000_000L);
        long recv = windowReceived.getAndSet(0);
        long render = windowRendered.getAndSet(0);
        log.info("指标 | 接收帧率={}fps 渲染帧率={}fps 平均解码={}ms 队列深度={} "
                        + "丢弃帧数=oversize:{} backpressure:{} 往返延迟={}ms",
                recv / seconds,
                render / seconds,
                String.format("%.3f", averageDecodeNanos() / 1_000_000.0),
                queueDepth.get(),
                droppedOversizeCounter.get(),
                droppedByBackpressureCounter.get(),
                String.format("%.1f", rttNanos.get() / 1_000_000.0));
    }

    /**
     * 测试用：清零全部计数器。
     */
    public void reset() {
        droppedOversizeCounter.set(0);
        droppedByBackpressureCounter.set(0);
        receivedFrames.set(0);
        renderedFrames.set(0);
        decodeNanosTotal.set(0);
        decodeSamples.set(0);
        queueDepth.set(0);
        rttNanos.set(0);
        windowReceived.set(0);
        windowRendered.set(0);
        lastReportNanos.set(System.nanoTime());
    }
}
