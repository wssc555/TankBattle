package com.tankbattle.server.render;

import com.tankbattle.common.config.GameConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;

/**
 * 纹理库：88 张图统一小写文件名放在 classpath，{@link ImageIO} 一次同步预加载。
 * <p>
 * 图片路径不依赖工作目录，同步加载不产生异步竞态。
 * 用惰性 Holder 惯用法在类初始化时构建一次不可变数组，之后零写入。
 */
public final class TextureBank {

    private static final Logger log = LoggerFactory.getLogger(TextureBank.class);

    private TextureBank() {
    }

    /**
     * 惰性初始化 Holder：JVM 类初始化语义保证恰好加载一次且线程安全。
     */
    private static final class Holder {
        private static final Image[] TEXTURES = preload();

        private static Image[] preload() {
            Image[] images = new Image[GameConfig.TEXTURE_COUNT];
            int loaded = 0;
            for (int i = 0; i < images.length; i++) {
                String path = String.format(GameConfig.TEXTURE_PATH, i + 1);
                try (InputStream in = TextureBank.class.getResourceAsStream(path)) {
                    if (in == null) {
                        log.error("纹理缺失: {}", path);
                        continue;
                    }
                    images[i] = ImageIO.read(in);
                    if (images[i] != null) {
                        loaded++;
                    }
                } catch (IOException e) {
                    log.error("纹理读取失败: {}", path, e);
                }
            }
            log.info("纹理预加载完成 {}/{}", loaded, GameConfig.TEXTURE_COUNT);
            return images;
        }
    }

    /**
     * 同步预加载（显式调用以在启动期完成 IO）；重复调用无副作用。
     */
    public static void load() {
        Image[] ignored = Holder.TEXTURES;
    }

    public static Image get(int index) {
        Image[] images = Holder.TEXTURES;
        if (index < 0 || index >= images.length) {
            return null;
        }
        return images[index];
    }

    public static int loadedCount() {
        int count = 0;
        for (Image image : Holder.TEXTURES) {
            if (image != null) {
                count++;
            }
        }
        return count;
    }

    public static boolean isLoaded() {
        return true;
    }
}
