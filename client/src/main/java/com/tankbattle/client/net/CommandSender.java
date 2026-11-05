package com.tankbattle.client.net;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单写者发送器。
 * <p>
 * 显式字符集 + 每次写后 {@code checkError()}；用 {@link ReentrantLock} 而非
 * {@code synchronized}，避免虚拟线程在阻塞 IO 上被 pin 住。
 */
public final class CommandSender implements AutoCloseable {

    private final PrintWriter out;
    private final ReentrantLock lock = new ReentrantLock();

    public CommandSender(Socket socket, Charset charset) throws IOException {
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset), true);
    }

    /**
     * 写一帧并以 {@code '\n'} 结尾；不用平台行分隔符，保证跨平台线缆格式确定。
     *
     * @throws IllegalStateException 写失败（对端已断开）
     */
    public void send(String frame) {
        lock.lock();
        try {
            out.write(frame);
            out.write('\n');
            out.flush();
            if (out.checkError()) {
                throw new IllegalStateException("socket 写入失败");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            out.close();
        } finally {
            lock.unlock();
        }
    }
}
