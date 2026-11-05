package com.tankbattle.client;

import com.tankbattle.client.config.ClientConfig;
import com.tankbattle.client.render.ClientFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 程序入口。
 * <p>
 * 配置在 main 线程加载一次；所有 Swing 组件在 EDT 创建，完整装配后再 {@code setVisible(true)}。
 * 关闭走窗口监听 → 连接层与渲染 Timer 优雅停机，不调用 {@code System.exit}。
 */
public final class GameClient {

    private static final Logger log = LoggerFactory.getLogger(GameClient.class);

    private GameClient() {
    }

    public static void main(String[] args) {
        ClientConfig config = ClientConfig.load(args);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                log.error("未捕获异常，线程={}", thread.getName(), error));

        SwingUtilities.invokeLater(() -> {
            assert SwingUtilities.isEventDispatchThread() : "GUI 装配必须在 EDT";
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                log.warn("系统外观不可用，使用默认外观：{}", e.toString());
            }
            ClientFrame frame = new ClientFrame(config);
            frame.setVisible(true);
            log.info("客户端启动完成，目标 {}:{}，字符集 {}", config.host(), config.port(), config.charset());
        });
    }
}
