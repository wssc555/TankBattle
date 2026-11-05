package com.tankbattle.server.net;

import com.tankbattle.common.protocol.C2s;
import com.tankbattle.server.core.GameSession;
import com.tankbattle.server.protocol.ClientCommandDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 一客户端一会话。
 * <p>
 * 读：独立虚拟线程逐行 {@code readLine()} → 解码 → 入队；游戏线程从队列取走，
 * 因此网络读不阻塞帧循环。写：游戏线程内同步写 + flush，单写者加锁。
 * <p>
 * 字符集可配置：默认 UTF-8；对接 GBK 客户端时由 {@code net.charset} 配置覆盖。
 * v2 能力握手状态由游戏线程写、游戏线程读。
 */
public final class ClientSession implements GameSession, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();

    private final int id = ID_GENERATOR.incrementAndGet();
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    /**
     * 单写者锁：ReentrantLock 避免 synchronized 包裹 socket 写导致虚拟线程 pin。
     */
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Queue<C2s> inbox = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Consumer<ClientSession> onClosed;
    private volatile boolean v2Negotiated;

    public ClientSession(Socket socket, Consumer<ClientSession> onClosed) throws IOException {
        this(socket, onClosed, StandardCharsets.UTF_8);
    }

    public ClientSession(Socket socket, Consumer<ClientSession> onClosed, Charset charset) throws IOException {
        this.socket = socket;
        this.onClosed = onClosed;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
        this.writer = new PrintWriter(new java.io.OutputStreamWriter(
                socket.getOutputStream(), charset), true);
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public boolean v2Negotiated() {
        return v2Negotiated;
    }

    @Override
    public void setV2Negotiated(boolean negotiated) {
        this.v2Negotiated = negotiated;
    }

    @Override
    public boolean isOpen() {
        return open.get() && !socket.isClosed();
    }

    /**
     * 读循环：只做解码入队，不做任何游戏逻辑。
     */
    public void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                inbox.addAll(ClientCommandDecoder.decode(line));
            }
        } catch (IOException e) {
            if (open.get()) {
                log.warn("会话 {} 读取异常: {}", id, e.getMessage());
            }
        } finally {
            closeAndNotify();
        }
    }

    @Override
    public List<C2s> drainInbox() {
        if (inbox.isEmpty()) {
            return List.of();
        }
        List<C2s> drained = new ArrayList<>(8);
        C2s command;
        while ((command = inbox.poll()) != null) {
            drained.add(command);
        }
        return drained;
    }

    @Override
    public void send(String frame) {
        if (!isOpen()) {
            return;
        }
        writeLock.lock();
        try {
            writer.write(frame);
            writer.write('\n');
            writer.flush();
            if (writer.checkError()) {
                closeAndNotify();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void close() {
        closeAndNotify();
    }

    private void closeAndNotify() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
        log.info("会话 {} 已关闭", id);
        if (onClosed != null) {
            onClosed.accept(this);
        }
    }
}
