package com.tankbattle.client.net;

import com.tankbattle.client.config.ClientConfig;
import com.tankbattle.client.input.InputState;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.StateStore;
import com.tankbattle.client.util.FrameStats;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力协商升级、1s 超时降级、
 * 已升级后超时任务不得覆盖、拒连映射 DISCONNECTED。
 */
class ConnectionManagerTest {

    /**
     * 1s 握手超时 + 观察余量。
     */
    private static final long HANDSHAKE_PLUS_MARGIN_MS = 1400L;

    private static ClientConfig config(int port, int idleTimeoutMs) {
        return new ClientConfig("127.0.0.1", port, StandardCharsets.UTF_8, 60, 30, true,
                1000, idleTimeoutMs, 500, 8000, 16384, 64, true, 6);
    }

    /**
     * 后台接受连接：读首行后立即回 {@code v2;}，随后静默吞掉上行帧。
     */
    private static void acceptAndAckV2(ServerSocket server, CountDownLatch firstLineSeen) {
        Thread.ofVirtual().name("test-acceptor").start(() -> {
            try (Socket peer = server.accept()) {
                StringBuilder line = new StringBuilder();
                int c;
                while ((c = peer.getInputStream().read()) != -1) {
                    if (c == '\n') {
                        if (line.toString().startsWith("V2;")) {
                            firstLineSeen.countDown();
                            OutputStream out = peer.getOutputStream();
                            out.write("v2;\n".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        }
                        line.setLength(0);
                    } else {
                        line.append((char) c);
                    }
                }
            } catch (Exception ignored) {
                // 测试结束时连接被关闭，忽略
            }
        });
    }

    private static ConnectionManager newManager(ClientConfig cfg, StateStore store,
                                                MessageLog messages) {
        return new ConnectionManager(cfg, store, new InputState(true), messages,
                new LinkListener() {
                    @Override
                    public void onStateChanged(LinkState state) {
                        // 测试不关心 UI 回调
                    }

                    @Override
                    public void onCapabilityAck() {
                        // 忽略
                    }

                    @Override
                    public void onNotice(String text) {
                        // 忽略
                    }
                }, new FrameStats());
    }

    /**
     * 收到 v2; → FREE_RUNNING + CONNECTED；且 1s 后超时任务不得覆盖。
     */
    @Test
    void capabilityAckUpgradesAndSurvivesHandshakeTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch firstLineSeen = new CountDownLatch(1);
            acceptAndAckV2(server, firstLineSeen);

            MessageLog messages = new MessageLog();
            ConnectionManager cm = newManager(config(server.getLocalPort(), 8000),
                    new StateStore(), messages);
            try {
                cm.connect("127.0.0.1", server.getLocalPort());
                assertTrue(awaitLinkState(cm, LinkState.CONNECTED, 5000), "应在协商成功后进入 CONNECTED");
                assertTrue(awaitMode(cm, SyncMode.FREE_RUNNING, 3000), "应升级为 FREE_RUNNING");
                assertTrue(cm.isFreeRunning());
                assertTrue(firstLineSeen.await(2, TimeUnit.SECONDS), "对端应收到 V2 握手帧");

                // 跨过 1s 握手超时后，模式必须仍是 FREE_RUNNING
                Thread.sleep(HANDSHAKE_PLUS_MARGIN_MS);
                assertEquals(SyncMode.FREE_RUNNING, cm.mode(), "超时任务不得覆盖已收到的 v2;");
                assertEquals(LinkState.CONNECTED, cm.state());
                assertTrue(cm.isFreeRunning());
            } finally {
                cm.close();
            }
        }
    }

    /**
     * 1s 内无 v2; → 降级 LOCKSTEP 并进入 CONNECTED。
     */
    @Test
    void handshakeTimeoutDegradesToLockstep() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            AtomicReference<Socket> peerRef = new AtomicReference<>();
            CountDownLatch accepted = new CountDownLatch(1);
            Thread.ofVirtual().name("test-acceptor").start(() -> {
                try (Socket peer = server.accept()) {
                    peerRef.set(peer);
                    accepted.countDown();
                    // 不回 v2；保持连接读取，模拟不支持 v2 的服务端
                    while (peer.getInputStream().read() != -1) {
                        // 丢弃
                    }
                } catch (Exception ignored) {
                    // 忽略
                }
            });

            MessageLog messages = new MessageLog();
            ConnectionManager cm = newManager(config(server.getLocalPort(), 8000),
                    new StateStore(), messages);
            try {
                cm.connect("127.0.0.1", server.getLocalPort());
                assertTrue(accepted.await(3, TimeUnit.SECONDS));
                assertTrue(awaitLinkState(cm, LinkState.CONNECTED, 5000), "超时降级后应进入 CONNECTED");
                assertEquals(SyncMode.LOCKSTEP, cm.mode(), "未收到 v2; 应降级 LOCKSTEP");
                assertFalse(cm.isFreeRunning());
            } finally {
                cm.close();
                Socket peer = peerRef.get();
                if (peer != null) {
                    peer.close();
                }
            }
        }
    }

    /**
     * 拒连（端口不可达）应落入 DISCONNECTED 并给出提示，而非卡在 CONNECTING。
     */
    @Test
    void refusedConnectLandsInDisconnected() throws Exception {
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            deadPort = probe.getLocalPort();
        }
        MessageLog messages = new MessageLog();
        ConnectionManager cm = newManager(config(deadPort, 8000), new StateStore(), messages);
        try {
            cm.connect("127.0.0.1", deadPort);
            assertTrue(awaitLinkState(cm, LinkState.DISCONNECTED, 5000),
                    "拒连应在连接超时内报告 DISCONNECTED");
            assertTrue(messages.snapshot().stream().anyMatch(m -> m.contains("连接失败") || m.contains("连接超时")),
                    "应给出明确失败提示");
        } finally {
            cm.close();
        }
    }

    private static boolean awaitLinkState(ConnectionManager cm, LinkState target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cm.state() == target) {
                return true;
            }
            Thread.sleep(20);
        }
        return cm.state() == target;
    }

    private static boolean awaitMode(ConnectionManager cm, SyncMode target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cm.mode() == target) {
                return true;
            }
            Thread.sleep(20);
        }
        return cm.mode() == target;
    }
}
