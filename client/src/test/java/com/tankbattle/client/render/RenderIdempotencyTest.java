package com.tankbattle.client.render;

import com.tankbattle.client.protocol.FrameAssembler;
import com.tankbattle.client.protocol.ServerFrameDecoder;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.RenderState;
import com.tankbattle.common.config.GameConfig;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 幂等渲染：同一 RenderState 连续 10 次 paint 逐像素一致。
 * <p>
 * 直接驱动 {@link LayeredRenderer} 到离屏 BufferedImage，无需真实显示器（headless 可跑）。
 */
class RenderIdempotencyTest {

    private static final int PASSES = 10;

    @Test
    void repeatedPaintProducesIdenticalPixels() {
        TextureAtlas atlas = new TextureAtlas();
        atlas.load();

        MessageLog messages = new MessageLog();
        messages.addMessage("幂等渲染测试");

        FrameAssembler assembler = new FrameAssembler(messages, () -> {
        });
        RenderState state = assembler.apply(ServerFrameDecoder.decode(
                "L1;p20,1,3,0,3,0;g0;"
                        + "n100,498,0;n400,498,0;n150,100,70;"
                        + "t100,300,0;o100,300,big;i100,498;"
                        + "w73,123,1111000011110000;s125,200,1010;"
                        + "b260,498,0;d1;"));

        LayeredRenderer renderer = new LayeredRenderer();
        StaticLayerCache cache = new StaticLayerCache();
        WinAnimationRenderer winAnimation = new WinAnimationRenderer();

        BufferedImage canvas = new BufferedImage(GameConfig.PANEL_W, GameConfig.PANEL_H,
                BufferedImage.TYPE_INT_RGB);
        int[] baseline = null;
        for (int pass = 0; pass < PASSES; pass++) {
            Graphics2D g = canvas.createGraphics();
            try {
                renderer.render(g, state, atlas, cache, messages, winAnimation);
            } finally {
                g.dispose();
            }
            int[] pixels = canvas.getRGB(0, 0, canvas.getWidth(), canvas.getHeight(), null, 0, canvas.getWidth());
            if (baseline == null) {
                baseline = pixels;
            } else {
                int mismatch = firstMismatch(baseline, pixels);
                assertTrue(mismatch < 0,
                        "第 " + (pass + 1) + " 次绘制与首次不一致，首个差异像素下标 " + mismatch);
            }
        }
    }

    private static int firstMismatch(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return -1;
    }
}
