package com.tankbattle.client.net;

import com.tankbattle.client.config.ClientConfig;
import com.tankbattle.client.input.InputState;
import com.tankbattle.client.protocol.ClientFrameEncoder;
import com.tankbattle.client.protocol.FrameAssembler;
import com.tankbattle.client.protocol.ServerFrameDecoder;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.RenderState;
import com.tankbattle.client.state.StateStore;
import com.tankbattle.client.util.FrameStats;
import com.tankbattle.client.util.MessageKind;
import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.protocol.C2s;
import com.tankbattle.common.protocol.S2c;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 连接管理器：六态机 + 自动重连 + 能力协商 + 空闲看门狗。
 * <p>
 * 线程分工：
 * <ul>
 *   <li>{@code net-connect} / {@code net-reconnect}（虚拟线程）：建立 socket，带 3s 超时；</li>
 *   <li>{@code net-reader}（虚拟线程，由 FrameReceiver 持有）：阻塞读 → 有界队列；</li>
 *   <li>{@code frame-assembler}（虚拟线程）：解码 → 组装 → 发布快照，LOCKSTEP 下就地回帧；</li>
 *   <li>{@code input-sender}（单平台线程，30Hz）：FREE_RUNNING 下独立发送；</li>
 *   <li>{@code net-control}（单平台线程）：握手超时与退避重连调度；</li>
 *   <li>{@code net-watchdog}（虚拟线程）：空闲检测。</li>
 * </ul>
 */
public final class ConnectionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

    private final ClientConfig cfg;
    private final StateStore store;
    private final InputState input;
    private final MessageLog messages;
    private final LinkListener listener;
    private final FrameAssembler assembler;
    private final FrameStats stats;
    private final ClientFrameEncoder encoder = new ClientFrameEncoder();
    private final ConcurrentLinkedQueue<C2s> pendingEvents = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService control;
    private final ScheduledExecutorService inputSender;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final Object lifecycleLock = new Object();
    /**
     * 连接代次：旧连接的迟到回调不得影响新连接。
     */
    private final AtomicLong generation = new AtomicLong();

    private volatile LinkState state = LinkState.DISCONNECTED;
    /**
     * 同步模式。HANDSHAKE → FREE_RUNNING / LOCKSTEP 的切换用 CAS 完成，
     * 保证握手超时与 {@code v2;} 应答不会互相覆盖。
     */
    private final AtomicReference<SyncMode> mode = new AtomicReference<>(SyncMode.HANDSHAKE);
    private final AtomicBoolean closed = new AtomicBoolean();
    /**
     * 本次连接是否已进入断开处理（CAS 去重，防止接收线程/看门狗/发送失败重复触发）。
     */
    private final AtomicBoolean disconnectHandled = new AtomicBoolean();
    private volatile long lastFrameNanos;
    /**
     * 最近一次上行发送时间，用于 V2 往返延迟估算；每次连接归零。
     */
    private volatile long lastSendNanos;

    private volatile Socket socket;
    private volatile CommandSender sender;
    private volatile FrameReceiver receiver;
    private volatile BlockingQueue<String> frameQueue;
    private volatile Thread assemblerThread;
    private volatile ScheduledFuture<?> handshakeTimeout;
    /**
     * 已调度的自动重连任务；手动 connect() 与 close() 时取消。
     */
    private volatile ScheduledFuture<?> reconnectFuture;

    private volatile String host;
    private volatile int port;
    private final AtomicInteger reconnectAttempt = new AtomicInteger();

    public ConnectionManager(ClientConfig cfg, StateStore store, InputState input,
                             MessageLog messages, LinkListener listener, FrameStats stats) {
        this.cfg = cfg;
        this.store = store;
        this.input = input;
        this.messages = messages;
        this.listener = listener;
        this.stats = stats;
        this.assembler = new FrameAssembler(messages, this::onCapabilityAck);
        this.control = Executors.newSingleThreadScheduledExecutor(daemonFactory("net-control"));
        this.inputSender = Executors.newSingleThreadScheduledExecutor(daemonFactory("input-sender"));
        startInputSender();
        startWatchdog();
    }

    // ------------------------------------------------------------------
    // 对外动作
    // ------------------------------------------------------------------

    /**
     * 建立连接；重复调用会先断开旧连接（幂等），并取消挂起的自动重连任务。
     */
    public void connect(String host, int port) {
        if (closed.get()) {
            return;
        }
        ScheduledFuture<?> pendingReconnect = reconnectFuture;
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
        }
        synchronized (lifecycleLock) {
            this.host = host;
            this.port = port;
            this.reconnectAttempt.set(0);
            // 抑制旧连接在 teardown 期间的迟到回调
            this.disconnectHandled.set(true);
        }
        setState(LinkState.CONNECTING);
        notice(MessageKind.CONNECTING.text());
        Thread.ofVirtual().name("net-connect").start(() -> {
            teardown();
            doConnect(false);
        });
    }

    public void sendChat(String text) {
        pendingEvents.add(new C2s.Chat(text));
    }

    public void voteYes() {
        pendingEvents.add(new C2s.VoteYes());
    }

    public void togglePause() {
        pendingEvents.add(new C2s.TogglePause());
    }

    /**
     * 记录"已投票"意图，供组装器写入 {@code FlowState.clientVote}。
     */
    public void noteClientVote() {
        assembler.noteClientVote();
    }

    public LinkState state() {
        return state;
    }

    public SyncMode mode() {
        return mode.get();
    }

    /**
     * FREE_RUNNING 且已连接时才启用插值。
     */
    public boolean isFreeRunning() {
        return mode.get() == SyncMode.FREE_RUNNING && state == LinkState.CONNECTED;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ScheduledFuture<?> pendingReconnect = reconnectFuture;
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
        }
        mode.set(SyncMode.LOCKSTEP);
        teardown();
        control.shutdownNow();
        inputSender.shutdownNow();
        pendingEvents.clear();
        setState(LinkState.CLOSED);
    }

    // ------------------------------------------------------------------
    // 连接建立
    // ------------------------------------------------------------------

    private void doConnect(boolean reconnect) {
        Socket newSocket = new Socket();
        long token = generation.incrementAndGet();
        try {
            newSocket.connect(new InetSocketAddress(host, port), cfg.connectTimeoutMs());
            newSocket.setTcpNoDelay(true);
            newSocket.setKeepAlive(true);

            CommandSender newSender = new CommandSender(newSocket, cfg.charset());
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(cfg.frameQueueCapacity());

            synchronized (lifecycleLock) {
                // 并发 doConnect 时，只有持有最新代次的连接允许发布资源，
                // 后到者放弃发布并只清理自己的 socket，避免覆盖对方造成泄漏。
                if (closed.get() || token != generation.get()) {
                    closeQuietly(newSender, newSocket);
                    return;
                }
                socket = newSocket;
                sender = newSender;
                frameQueue = queue;
                mode.set(SyncMode.HANDSHAKE);
                disconnectHandled.set(false);
                lastFrameNanos = System.nanoTime();
                lastSendNanos = 0L;
            }

            FrameReceiver newReceiver = new FrameReceiver(newSocket, cfg.charset(), cfg.maxFrameChars(),
                    queue, stats, reason -> onReceiverDown(token, reason));
            synchronized (lifecycleLock) {
                if (closed.get() || token != generation.get()) {
                    newReceiver.close();
                    closeQuietly(newSender, newSocket);
                    return;
                }
                receiver = newReceiver;
            }

            if (token != generation.get()) {
                // 本连接已被更新的连接取代：只清理自己的资源，不得 teardown 对方资源
                newReceiver.close();
                closeQuietly(newSender, newSocket);
                return;
            }
            if (closed.get() || disconnectHandled.get()) {
                teardown();
                return;
            }

            if (reconnect) {
                assembler.reset();
                store.clear();
                // 断线前排队的事件不得在重连后的新连接上重放
                pendingEvents.clear();
            }

            // 首帧：V2 能力声明 + 空输入；未启用 V2 的服务端按 m 帧处理；走编码器保持单一编码出处
            newSender.send(encoder.encode(
                    List.of(new C2s.Capability(), new C2s.Input(false, false, false, false, false, 0L))));
            setState(LinkState.HANDSHAKE);
            startAssembler(queue, token);
            scheduleHandshakeTimeout(token);
            log.info("已建立 TCP 连接 {}:{}（{}），等待能力协商", host, port, reconnect ? "重连" : "首次");
        } catch (ConnectException e) {
            closeSocketQuietly(newSocket);
            handleConnectFailure("连接失败：主机拒绝或端口不可达", e, reconnect, token);
        } catch (SocketTimeoutException e) {
            closeSocketQuietly(newSocket);
            handleConnectFailure("连接超时：" + host + ":" + port, e, reconnect, token);
        } catch (IOException e) {
            closeSocketQuietly(newSocket);
            handleConnectFailure("连接失败：" + e.getMessage(), e, reconnect, token);
        } catch (RuntimeException e) {
            closeSocketQuietly(newSocket);
            log.error("连接初始化异常", e);
            handleConnectFailure("连接初始化失败：" + e.getMessage(), e, reconnect, token);
        }
    }

    private void handleConnectFailure(String text, Exception cause, boolean reconnect, long token) {
        log.warn("{}（{}）", text, cause.toString());
        if (closed.get() || token != generation.get()) {
            return;
        }
        teardown();
        if (closed.get()) {
            return;
        }
        if (reconnect) {
            notice(text);
            setState(LinkState.RECONNECTING);
            scheduleReconnect();
        } else {
            setState(LinkState.DISCONNECTED);
            notice(text);
        }
    }

    private void startAssembler(BlockingQueue<String> queue, long token) {
        Thread thread = Thread.ofVirtual().name("frame-assembler").unstarted(() -> assembleLoop(queue, token));
        synchronized (lifecycleLock) {
            assemblerThread = thread;
        }
        thread.start();
    }

    private void assembleLoop(BlockingQueue<String> queue, long token) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String line = queue.take();
                if (closed.get() || token != generation.get()) {
                    return;
                }
                lastFrameNanos = System.nanoTime();
                stats.frameReceived();

                long started = System.nanoTime();
                List<S2c> batch = ServerFrameDecoder.decode(line);
                stats.frameDecoded(System.nanoTime() - started);
                stats.queueDepth(queue.size());
                if (containsFrameSeq(batch) && lastSendNanos > 0) {
                    stats.roundTrip(System.nanoTime() - lastSendNanos);
                }

                RenderState snapshot = assembler.apply(batch);
                if (closed.get() || token != generation.get()) {
                    // 陈旧组装线程不得在 reconnect reset 之后向新连接写入旧状态
                    return;
                }
                store.publish(snapshot);

                if (mode.get() != SyncMode.FREE_RUNNING) {
                    sendInputFrame();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("组装线程异常，触发重连", e);
            // 以入口捕获的代次回调，防止旧组装线程拆掉新连接
            onReceiverDown(token, DisconnectReason.IO_ERROR);
        }
    }

    // ------------------------------------------------------------------
    // 发送
    // ------------------------------------------------------------------

    private void startInputSender() {
        long period = Math.max(1L, 1000L / cfg.inputHz());
        inputSender.scheduleAtFixedRate(() -> {
            if (isFreeRunning()) {
                sendInputFrame();
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    private void sendInputFrame() {
        CommandSender current = sender;
        if (current == null) {
            return;
        }
        writeLock.lock();
        try {
            List<C2s> commands = new ArrayList<>(8);
            commands.add(input.snapshot().toCommand());
            C2s event;
            while ((event = pendingEvents.poll()) != null) {
                commands.add(event);
            }
            current.send(encoder.encode(commands));
            lastSendNanos = System.nanoTime();
        } catch (RuntimeException e) {
            log.warn("发送失败：{}", e.getMessage());
            onReceiverDown(DisconnectReason.IO_ERROR);
        } finally {
            writeLock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // 能力协商
    // ------------------------------------------------------------------

    private void scheduleHandshakeTimeout(long token) {
        try {
            ScheduledFuture<?> future = control.schedule(() -> {
                // 陈旧代次的超时任务不得降级新连接；
                // CAS 切换保证与 onCapabilityAck 的升级互斥，不会静默覆盖已收到的 v2;
                if (closed.get() || token != generation.get() || state != LinkState.HANDSHAKE) {
                    return;
                }
                SyncMode previous = mode.getAndUpdate(m -> m == SyncMode.HANDSHAKE ? SyncMode.LOCKSTEP : m);
                if (previous == SyncMode.HANDSHAKE) {
                    log.info("1s 内未收到 v2;，降级为 LOCKSTEP");
                    notice(MessageKind.CAPABILITY_LOCKSTEP.text());
                    markConnected();
                }
            }, GameConfig.HANDSHAKE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            synchronized (lifecycleLock) {
                handshakeTimeout = future;
            }
        } catch (RuntimeException e) {
            log.debug("握手定时器注册失败（可能已关闭）：{}", e.toString());
        }
    }

    private void onCapabilityAck() {
        SyncMode previous = mode.getAndUpdate(m ->
                (m == SyncMode.HANDSHAKE || m == SyncMode.LOCKSTEP) ? SyncMode.FREE_RUNNING : m);
        if (previous != SyncMode.FREE_RUNNING) {
            log.info("收到 v2;，切换 FREE_RUNNING（30Hz 输入 / 60FPS 渲染 / 插值）");
            notice(MessageKind.CAPABILITY_V2.text());
            listener.onCapabilityAck();
        }
        if (state == LinkState.HANDSHAKE) {
            markConnected();
        }
    }

    private void markConnected() {
        reconnectAttempt.set(0);
        setState(LinkState.CONNECTED);
        notice(MessageKind.CONNECTED.text());
    }

    // ------------------------------------------------------------------
    // 断开与重连
    // ------------------------------------------------------------------

    private void onReceiverDown(DisconnectReason reason) {
        onReceiverDown(generation.get(), reason);
    }

    private void onReceiverDown(long token, DisconnectReason reason) {
        if (closed.get() || reason == DisconnectReason.LOCAL_CLOSE || token != generation.get()) {
            return;
        }
        if (!disconnectHandled.compareAndSet(false, true)) {
            return;
        }
        log.info("连接断开：{}", reason);
        mode.set(SyncMode.LOCKSTEP);
        teardown();
        if (closed.get()) {
            return;
        }
        setState(LinkState.RECONNECTING);
        notice(reason == DisconnectReason.TIMEOUT
                ? MessageKind.CONNECT_TIMEOUT.text()
                : MessageKind.HOST_EXITED.text());
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempt.get();
        if (attempt >= cfg.maxReconnectAttempts()) {
            log.warn("自动重连 {} 次仍失败，转为手动重试", attempt);
            setState(LinkState.DISCONNECTED);
            notice(MessageKind.RECONNECT_FAILED.text());
            return;
        }
        reconnectAttempt.incrementAndGet();
        long backoff = Math.min(cfg.reconnectBaseMs() * (1L << Math.min(attempt, 5)), cfg.reconnectMaxMs());
        long delay = backoff + ThreadLocalRandom.current().nextLong(GameConfig.RECONNECT_JITTER_MS);
        log.info("第 {} 次重连将在 {} ms 后发起", attempt + 1, delay);
        notice(MessageKind.RECONNECTING.text());
        long tokenAtSchedule = generation.get();
        try {
            ScheduledFuture<?> future = control.schedule(() -> {
                // 手动重连/新连接会使代次前进，过期任务直接作废
                if (closed.get() || tokenAtSchedule != generation.get()) {
                    return;
                }
                Thread.ofVirtual().name("net-reconnect").start(() -> {
                    teardown();
                    doConnect(true);
                });
            }, delay, TimeUnit.MILLISECONDS);
            reconnectFuture = future;
        } catch (RuntimeException e) {
            log.debug("重连调度失败（可能已关闭）：{}", e.toString());
        }
    }

    /**
     * 关闭当前连接资源。顺序：停接收 → 停组装 → 关 socket；不改变状态、不触发回调。
     */
    private void teardown() {
        FrameReceiver oldReceiver;
        CommandSender oldSender;
        Socket oldSocket;
        Thread oldAssembler;
        ScheduledFuture<?> oldTimeout;
        synchronized (lifecycleLock) {
            oldReceiver = receiver;
            oldSender = sender;
            oldSocket = socket;
            oldAssembler = assemblerThread;
            oldTimeout = handshakeTimeout;
            receiver = null;
            sender = null;
            socket = null;
            frameQueue = null;
            assemblerThread = null;
            handshakeTimeout = null;
        }
        if (oldTimeout != null) {
            oldTimeout.cancel(false);
        }
        if (oldReceiver != null) {
            oldReceiver.close();
        }
        if (oldAssembler != null) {
            oldAssembler.interrupt();
            try {
                // 限时等待旧组装线程退出，压缩其 reset 后写入旧状态的时间窗
                oldAssembler.join(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (oldSender != null) {
            try {
                oldSender.close();
            } catch (RuntimeException ignored) {
                // 关闭阶段忽略
            }
        }
        if (oldSocket != null) {
            try {
                oldSocket.close();
            } catch (IOException ignored) {
                // 关闭阶段忽略
            }
        }
    }

    private void closeQuietly(CommandSender s, Socket sock) {
        try {
            s.close();
        } catch (RuntimeException ignored) {
            // ignore
        }
        closeSocketQuietly(sock);
    }

    private void closeSocketQuietly(Socket sock) {
        try {
            sock.close();
        } catch (IOException ignored) {
            // ignore
        }
    }

    private void startWatchdog() {
        Thread.ofVirtual().name("net-watchdog").start(() -> {
            while (!closed.get()) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    return;
                }
                LinkState current = state;
                if (current != LinkState.CONNECTED && current != LinkState.HANDSHAKE) {
                    continue;
                }
                long idle = System.nanoTime() - lastFrameNanos;
                if (idle > cfg.idleTimeoutMs() * 1_000_000L) {
                    log.warn("空闲 {} ms，判定半开连接", idle / 1_000_000L);
                    onReceiverDown(DisconnectReason.TIMEOUT);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    private void setState(LinkState next) {
        if (state == next) {
            return;
        }
        state = next;
        log.info("连接状态 → {}", next);
        try {
            listener.onStateChanged(next);
        } catch (RuntimeException e) {
            log.warn("状态回调异常：{}", e.toString());
        }
    }

    private void notice(String text) {
        messages.addMessage(text);
        try {
            listener.onNotice(text);
        } catch (RuntimeException e) {
            log.warn("提示回调异常：{}", e.toString());
        }
    }

    private static boolean containsFrameSeq(List<S2c> batch) {
        for (S2c message : batch) {
            if (message instanceof S2c.FrameSeq) {
                return true;
            }
        }
        return false;
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
