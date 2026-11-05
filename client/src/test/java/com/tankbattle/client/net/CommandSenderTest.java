package com.tankbattle.client.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CommandSender 显式字符集、checkError 写失败检测、确定化 {@code '\n'} 行尾。
 */
class CommandSenderTest {

    /**
     * 发送一帧并从对端读回原始字节，校验内容与行尾为 LF（无 CR）。
     */
    @Test
    void sendsFrameWithDeterministicLineEnding() throws IOException, InterruptedException {
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            Socket peer = server.accept();

            CommandSender sender = new CommandSender(client, StandardCharsets.UTF_8);
            sender.send("V2;m00000;");
            sender.send("m10000;");

            String received = readUntilNewlines(peer.getInputStream(), 2, 2000);
            assertTrue(received.contains("V2;m00000;\n"), "第一帧应为 V2 握手帧，实际: " + received);
            assertTrue(received.contains("m10000;\n"), "第二帧应完整到达");
            assertTrue(!received.contains("\r"), "行尾必须为 LF，不得使用平台分隔符 CRLF");

            sender.close();
            client.close();
            peer.close();
        }
    }

    /**
     * 对端关闭后写必须失败并抛出明确异常（checkError 兜底）。
     */
    @Test
    void sendFailsExplicitlyAfterPeerClose() throws IOException, InterruptedException {
        try (ServerSocket server = new ServerSocket(0)) {
            Socket client = new Socket("127.0.0.1", server.getLocalPort());
            Socket peer = server.accept();

            CommandSender sender = new CommandSender(client, StandardCharsets.UTF_8);
            sender.send("m10000;");
            readUntilNewlines(peer.getInputStream(), 1, 500);

            peer.close();
            client.close();

            boolean failed = false;
            IllegalStateException thrown = null;
            for (int i = 0; i < 5 && !failed; i++) {
                try {
                    sender.send("m10000;");
                    Thread.sleep(50);
                } catch (IllegalStateException e) {
                    failed = true;
                    thrown = e;
                }
            }
            assertTrue(failed, "对端关闭后发送应抛 IllegalStateException，实际从未失败");
            assertEquals("socket 写入失败", thrown.getMessage());
        }
    }

    /**
     * 读取流中字节直到收满 expectedNewlines 个 LF 或超时。
     */
    private static String readUntilNewlines(InputStream in, int expectedNewlines, long timeoutMs)
            throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && countNewlines(sb) < expectedNewlines) {
            int available = in.available();
            if (available > 0) {
                byte[] buffer = new byte[available];
                int read = in.read(buffer);
                if (read > 0) {
                    sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } else {
                Thread.sleep(10);
            }
        }
        return sb.toString();
    }

    private static int countNewlines(CharSequence text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
