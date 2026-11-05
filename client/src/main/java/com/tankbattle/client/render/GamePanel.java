package com.tankbattle.client.render;

import com.tankbattle.client.config.ClientConfig;
import com.tankbattle.client.state.Interpolator;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.RenderState;
import com.tankbattle.client.state.StateStore;
import com.tankbattle.client.util.FrameStats;
import com.tankbattle.common.config.GameConfig;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;

/**
 * 渲染面板：Swing 原生双缓冲 + 60 FPS Timer + 只读快照。
 * <p>
 * {@code paintComponent} 不修改任何外部状态；网络挂起时继续绘制最后一帧，UI 不卡。
 * 渲染 Timer 天然运行在 EDT。
 */
public final class GamePanel extends JPanel {
    private final StateStore store;
    private final TextureAtlas atlas;
    private final MessageLog messages;
    private final FrameStats stats;
    private final LayeredRenderer renderer = new LayeredRenderer();
    private final StaticLayerCache cache = new StaticLayerCache();
    private final WinAnimationRenderer winAnimation = new WinAnimationRenderer();
    private final BooleanSupplier interpolationEnabled;
    private final Timer timer;

    public GamePanel(ClientConfig cfg, FrameStats stats, StateStore store, TextureAtlas atlas,
                     MessageLog messages, BooleanSupplier interpolationEnabled) {
        this.store = store;
        this.atlas = atlas;
        this.messages = messages;
        this.stats = stats;
        this.interpolationEnabled = interpolationEnabled;
        setPreferredSize(new Dimension(GameConfig.PANEL_W, GameConfig.PANEL_H));
        setBackground(new Color(128, 64, 0));
        setDoubleBuffered(true);
        setFocusable(true);

        int delay = Math.max(1, 1000 / cfg.renderFps());
        this.timer = new Timer(delay, e -> {
            stats.frameRendered();
            stats.reportIfDue();
            repaint();
        });
        this.timer.setCoalesce(true);
        this.timer.setInitialDelay(0);
    }

    public void start() {
        timer.start();
    }

    /**
     * 停止渲染并释放离屏缓存。
     */
    public void shutdown() {
        timer.stop();
        cache.flush();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        try {
            RenderState state = store.current();
            if (interpolationEnabled.getAsBoolean()) {
                state = Interpolator.interpolate(store.previous(), state, System.nanoTime());
            }
            renderer.render(g, state, atlas, cache, messages, winAnimation);
        } finally {
            g.dispose();
        }
    }

    public WinAnimationRenderer winAnimation() {
        return winAnimation;
    }
}
