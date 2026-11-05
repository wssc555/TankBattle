package com.tankbattle.client.net;

import com.tankbattle.client.util.FrameStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * 虚拟线程帧接收器：阻塞 {@code readLine()} → 有界队列。
 * <p>
 * 读循环在本类，{@code close()} 会中断线程并关流；
 * 队列满时丢最旧并计数，超长帧整帧丢弃。
 * <p>
 * 帧长上限与指标计数器经构造注入，不依赖静态状态。
 */
public final class FrameReceiver implements AutoCloseable {

    private final BlockingQueue<String> frames;
    private final BufferedReader in;
    private final Thread reader;
    private final Consumer<DisconnectReason> onDisconnect;
    private final FrameStats stats;
    private final int maxFrameChars;
    private volatile boolean closed;

    public FrameReceiver(Socket socket, Charset charset, int maxFrameChars,
                         BlockingQueue<String> frames, FrameStats stats,
                         Consumer<DisconnectReason> onDisconnect) throws IOException {
        this.frames = frames;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
        this.stats = stats;
        this.maxFrameChars = maxFrameChars;
        this.onDisconnect = onDisconnect;
        this.reader = Thread.ofVirtual().name("net-reader").unstarted(this::readLoop);
        this.reader.start();
    }

    private void readLoop() {
        DisconnectReason reason = DisconnectReason.EOF;
        try {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (line.length() > maxFrameChars) {
                    stats.droppedOversize();
                    continue;
                }
                if (!frames.offer(line)) {
                    frames.poll();
                    frames.offer(line);
                    stats.droppedByBackpressure();
                }
            }
        } catch (IOException e) {
            if (!closed) {
                reason = DisconnectReason.IO_ERROR;
            }
        } catch (RuntimeException e) {
            // 意外运行时异常不得静默杀死读线程（等待看门狗 5s 兜底），显式上报
            if (!closed) {
                reason = DisconnectReason.IO_ERROR;
            }
        } finally {
            if (!closed) {
                onDisconnect.accept(reason);
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        reader.interrupt();
        try {
            in.close();
        } catch (IOException ignored) {
            // 关闭阶段的异常无需处理
        }
    }
}
