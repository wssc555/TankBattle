package com.tankbattle.client.render;

import com.tankbattle.client.state.SceneState;
import com.tankbattle.client.state.views.WallView;
import com.tankbattle.common.config.GameConfig;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 静态层缓存：墙 / 钢墙 / 河 / 基地一次烘焙为 680×545 图像。
 * <p>
 * 仅当 {@link SceneState} 实例变化（L / M / w / s / b 事件帧产生新实例）时重烘焙；
 * 普通帧结构共享同一实例，只做一次 {@code drawImage}。草不在此层，保持在图层 ⑥ 覆盖动态实体。
 */
public final class StaticLayerCache {

    private BufferedImage canvas;
    /**
     * 结构共享：普通帧复用同一 SceneState 实例，身份变化即需重烘焙。
     */
    private SceneState cachedScene;

    public BufferedImage imageFor(SceneState scene, TextureAtlas atlas) {
        if (canvas == null || cachedScene != scene) {
            canvas = bake(scene, atlas);
            cachedScene = scene;
        }
        return canvas;
    }

    private BufferedImage bake(SceneState scene, TextureAtlas atlas) {
        BufferedImage image = new BufferedImage(
                GameConfig.PANEL_W, GameConfig.PANEL_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            for (int key : scene.rivers()) {
                LayeredRenderer.drawRiver(g, key, atlas);
            }
            for (WallView.Brick brick : scene.bricks().values()) {
                LayeredRenderer.drawBrick(g, brick, atlas);
            }
            for (WallView.Steel steel : scene.steels().values()) {
                LayeredRenderer.drawSteel(g, steel, atlas);
            }
            if (scene.base() != null && scene.base().x() > 0) {
                LayeredRenderer.drawBase(g, scene.base(), atlas);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * 释放离屏图（窗口关闭时调用）。
     */
    public void flush() {
        if (canvas != null) {
            canvas.flush();
            canvas = null;
        }
        cachedScene = null;
    }
}
