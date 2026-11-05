package com.tankbattle.client.net;

import com.tankbattle.client.util.FrameStats;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帧入队、超长帧丢弃计数、背压丢最旧、close 后读循环退出且不触发断开回调。
 */
class FrameReceiverTest {

    @Test
    void enqueuesLinesAndDropsOversize() throws Exception {
        FrameStats stats = new FrameStats();
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            Socket peer = server.accept();
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(8);
            CopyOnWriteArrayList<DisconnectReason> callbacks = new CopyOnWriteArrayList<>();

            FrameReceiver receiver = new FrameReceiver(client, StandardCharsets.UTF_8,
                    100, queue, stats, callbacks::add);

            OutputStream out = peer.getOutputStream();
            out.write("n1,2,3;\n".getBytes(StandardCharsets.UTF_8));
            out.write("s1,2,1010;\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals("n1,2,3;", poll(queue));
            assertEquals("s1,2,1010;", poll(queue));

            String oversize = "m" + "x".repeat(120) + ";";
            out.write((oversize + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.write("d1;\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            assertEquals("d1;", poll(queue), "超长帧整帧丢弃");
            assertEquals(1, stats.droppedOversizeCount());

            receiver.close();
            client.close();
            peer.close();
        }
    }

    @Test
    void backpressureDropsOldestAndCounts() throws Exception {
        FrameStats stats = new FrameStats();
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            Socket peer = server.accept();
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);
            FrameReceiver receiver = new FrameReceiver(client, StandardCharsets.UTF_8,
                    4096, queue, stats, r -> {
            });

            OutputStream out = peer.getOutputStream();
            out.write("d1;\nd2;\nd3;\nd4;\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 等待两次背压丢弃完成，避免主线程过早 poll 干扰读线程
            awaitCondition(() -> stats.droppedByBackpressureCount() >= 2, 2000);

            assertEquals(2, stats.droppedByBackpressureCount(), "容量 2 收 4 帧，应丢最旧两帧");
            assertEquals("d3;", poll(queue), "容量 2 应保留最新两帧");
            assertEquals("d4;", poll(queue));

            receiver.close();
            client.close();
            peer.close();
        }
    }

    /**
     * close 后 1s 内读循环退出、不触发任何断开回调。
     */
    @Test
    void closeStopsLoopAndSuppressesCallback() throws Exception {
        FrameStats stats = new FrameStats();
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            Socket peer = server.accept();
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(8);
            CopyOnWriteArrayList<DisconnectReason> callbacks = new CopyOnWriteArrayList<>();

            FrameReceiver receiver = new FrameReceiver(client, StandardCharsets.UTF_8,
                    4096, queue, stats, callbacks::add);
            assertTrue(callbacks.isEmpty());

            receiver.close();
            assertTrue(receiver.isClosed());
            peer.close();

            // 关闭后即使对端 EOF/IO 异常也不得回调（finally 中以 closed 抑制）
            Thread.sleep(300);
            assertTrue(callbacks.isEmpty(), "close 后不得产生断开回调");
            assertEquals(0, queue.size(), "close 后不得再入队");
            assertNull(queue.poll(200, TimeUnit.MILLISECONDS));
            client.close();
        }
    }

    private static String poll(BlockingQueue<String> queue) throws InterruptedException {
        String line = queue.poll(2, TimeUnit.SECONDS);
        if (line == null) {
            throw new AssertionError("等待帧超时");
        }
        return line;
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
