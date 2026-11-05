package com.tankbattle.client.render;

import com.tankbattle.common.config.GameConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 纹理图集：classpath 同步预载 88 张 {@link BufferedImage}。
 * <p>
 * 同步加载，缺图即启动失败并给出明确路径；
 * 统一 {@code /image/%d.jpg} 正斜杠路径，jar 内可用。
 * <p>
 * 用 {@link ReentrantLock} 而非 {@code synchronized}：load 含磁盘 IO，
 * 避免虚拟线程场景 pin 住载体线程。
 */
public final class TextureAtlas {

    private static final Logger log = LoggerFactory.getLogger(TextureAtlas.class);

    private final BufferedImage[] textures = new BufferedImage[GameConfig.TEXTURE_COUNT];
    private final ReentrantLock loadLock = new ReentrantLock();
    private long startedNanos;
    private volatile boolean loaded;

    public void load() {
        if (loaded) {
            return;
        }
        loadLock.lock();
        try {
            if (loaded) {
                return;
            }
            loadAll();
            loaded = true;
            log.info("纹理预载完成: {} 张，耗时 {} ms", GameConfig.TEXTURE_COUNT,
                    (System.nanoTime() - startedNanos) / 1_000_000L);
        } finally {
            loadLock.unlock();
        }
    }

    private void loadAll() {
        startedNanos = System.nanoTime();
        for (int k = 0; k < GameConfig.TEXTURE_COUNT; k++) {
            String path = GameConfig.TEXTURE_PATH.formatted(k + 1);
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("缺失纹理 " + path);
                }
                BufferedImage image = ImageIO.read(in);
                if (image == null) {
                    throw new IllegalStateException("无法解码纹理 " + path);
                }
                textures[k] = image;
            } catch (IOException e) {
                throw new UncheckedIOException("读取纹理失败 " + path, e);
            }
        }
    }

    /**
     * 取纹理；{@code index ∈ [0,87]}，对应文件 {@code (index+1).jpg}。
     *
     * @throws IllegalArgumentException 越界
     * @throws IllegalStateException    尚未 {@link #load()}
     */
    public BufferedImage at(int index) {
        if (!loaded) {
            throw new IllegalStateException("纹理图集尚未加载");
        }
        if (index < 0 || index >= GameConfig.TEXTURE_COUNT) {
            throw new IllegalArgumentException("纹理越界: " + index);
        }
        return textures[index];
    }

    public boolean loaded() {
        return loaded;
    }
}
