package com.tankbattle.server.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 网络服务：{@code ServerSocket} accept 循环跑在虚拟线程上（JEP 444），
 * 不占用平台线程，也不会阻塞游戏线程。
 */
public final class NetworkService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private final ServerSocket serverSocket;
    private final Consumer<ClientSession> onConnected;
    private final Consumer<ClientSession> onDisconnected;
    private final Charset charset;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * @throws IOException 端口被占用等绑定失败
     */
    public NetworkService(int port,
                          Consumer<ClientSession> onConnected,
                          Consumer<ClientSession> onDisconnected) throws IOException {
        this(port, onConnected, onDisconnected, StandardCharsets.UTF_8);
    }

    public NetworkService(int port,
                          Consumer<ClientSession> onConnected,
                          Consumer<ClientSession> onDisconnected,
                          Charset charset) throws IOException {
        this.onConnected = onConnected;
        this.onDisconnected = onDisconnected;
        this.charset = charset;
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(port));
        Thread.ofVirtual().name("acceptor").start(this::acceptLoop);
        log.info("server started on {}", port);
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed.get()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                // keepAlive 探测半开连接（拔网线/静默丢包）：无它则 readLine 永久阻塞，
                // 会话不关闭、不产生 SessionUnbound，"等待重连"状态无法进入
                socket.setKeepAlive(true);
            } catch (SocketException e) {
                if (closed.get()) {
                    break;
                }
                log.warn("accept 失败: {}", e.getMessage());
                continue;
            } catch (IOException e) {
                if (closed.get()) {
                    break;
                }
                log.warn("accept 失败: {}", e.getMessage());
                continue;
            }
            // 会话构造 / onConnected 回调的任何 Throwable 都不得杀死 acceptor，
            // 否则端口仍在监听却无法再接受连接，且无 error 日志
            try {
                ClientSession session = new ClientSession(socket, onDisconnected, charset);
                log.info("客户端接入，会话 {}", session.id());
                onConnected.accept(session);
                Thread.ofVirtual().name("reader-" + session.id()).start(session::readLoop);
            } catch (Throwable t) {
                log.error("处理新连接异常，已丢弃该连接", t);
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 已尽力关闭
                }
            }
        }
        log.info("acceptor 线程退出");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // 关闭失败无需处理
        }
    }
}
