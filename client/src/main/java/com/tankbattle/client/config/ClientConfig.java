package com.tankbattle.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 客户端进程配置。不可变 record，{@link #load(String[])} 只在 {@code main} 中执行一次，
 * 随后经构造注入分发给各组件；不提供任何 setter，也无静态可变单例。
 */
public record ClientConfig(
        String host,
        int port,
        Charset charset,
        int renderFps,
        int inputHz,
        boolean interpolation,
        int connectTimeoutMs,
        int idleTimeoutMs,
        int reconnectBaseMs,
        int reconnectMaxMs,
        int maxFrameChars,
        int frameQueueCapacity,
        boolean exclusiveDirection,
        int maxReconnectAttempts) {

    private static final String RESOURCE = "/client.properties";
    private static final String DEFAULT_CHARSET = "UTF-8";

    public ClientConfig {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port 越界: " + port);
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset 不能为空");
        }
        if (renderFps <= 0 || inputHz <= 0) {
            throw new IllegalArgumentException("帧率必须为正: render=" + renderFps + ", input=" + inputHz);
        }
        if (maxFrameChars <= 0 || frameQueueCapacity <= 0) {
            throw new IllegalArgumentException("帧长上限与队列容量必须为正");
        }
        if (connectTimeoutMs <= 0 || idleTimeoutMs <= 0) {
            throw new IllegalArgumentException("超时必须为正");
        }
        if (reconnectBaseMs <= 0 || reconnectMaxMs < reconnectBaseMs) {
            throw new IllegalArgumentException("重连退避参数非法");
        }
    }

    /**
     * 读取 classpath {@code client.properties}，再用命令行参数覆盖。
     * <p>
     * 支持 {@code --host 1.2.3.4} 与 {@code --host=1.2.3.4} 两种写法。
     */
    public static ClientConfig load(String[] args) {
        Properties properties = readProperties();
        String host = properties.getProperty("client.net.host", "127.0.0.1");
        int port = intOf(properties, "client.net.port", 9999);
        String charsetName = properties.getProperty("client.net.charset", DEFAULT_CHARSET);
        int renderFps = intOf(properties, "client.render.fps", 60);
        int inputHz = intOf(properties, "client.input.hz", 30);
        boolean interpolation = boolOf(properties, "client.render.interpolation", true);
        int connectTimeout = intOf(properties, "client.net.connectTimeoutMs", 3000);
        int idleTimeout = intOf(properties, "client.net.idleTimeoutMs", 5000);
        int reconnectBase = intOf(properties, "client.net.reconnectBaseMs", 500);
        int reconnectMax = intOf(properties, "client.net.reconnectMaxMs", 8000);
        int maxFrameChars = intOf(properties, "client.net.maxFrameChars", 16384);
        int queueCapacity = intOf(properties, "client.net.frameQueueCapacity", 64);
        boolean exclusive = boolOf(properties, "client.input.exclusiveDirection", true);
        int maxAttempts = intOf(properties, "client.net.reconnectMaxAttempts", 6);

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg == null || !arg.startsWith("--")) {
                    continue;
                }
                String key;
                String value;
                int eq = arg.indexOf('=');
                if (eq > 2) {
                    key = arg.substring(2, eq);
                    value = arg.substring(eq + 1);
                } else {
                    key = arg.substring(2);
                    value = (i + 1 < args.length && !args[i + 1].startsWith("--")) ? args[++i] : "";
                }
                switch (key) {
                    case "host" -> {
                        if (!value.isBlank()) {
                            host = value.strip();
                        }
                    }
                    case "port" -> {
                        if (!value.isBlank()) {
                            port = parseInt(value, port);
                        }
                    }
                    case "charset" -> {
                        if (!value.isBlank()) {
                            charsetName = value.strip();
                        }
                    }
                    case "fps" -> {
                        if (!value.isBlank()) {
                            renderFps = parseInt(value, renderFps);
                        }
                    }
                    default -> {
                        // 未识别的参数直接忽略
                    }
                }
            }
        }

        Charset charset;
        try {
            charset = Charset.forName(charsetName);
        } catch (RuntimeException e) {
            // 非法字符集给出明确错误，而非裸异常栈
            throw new IllegalArgumentException("字符集不可用: " + charsetName
                    + "（可用 --charset UTF-8 或 GBK 覆盖）", e);
        }
        return new ClientConfig(host, port, charset, renderFps, inputHz, interpolation,
                connectTimeout, idleTimeout, reconnectBase, reconnectMax, maxFrameChars, queueCapacity,
                exclusive, maxAttempts);
    }

    private static Properties readProperties() {
        Properties properties = new Properties();
        try (InputStream in = ClientConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException ignored) {
            // 配置不可读时全部使用内置默认值
        }
        return properties;
    }

    private static int intOf(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : parseInt(raw, fallback);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOf(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.strip());
    }

    /**
     * 测试/工具用：以默认值加载，避免依赖外部配置。
     */
    public static ClientConfig defaults() {
        return new ClientConfig("127.0.0.1", 9999, StandardCharsets.UTF_8, 60, 30, true,
                3000, 5000, 500, 8000, 16384, 64, true, 6);
    }
}
