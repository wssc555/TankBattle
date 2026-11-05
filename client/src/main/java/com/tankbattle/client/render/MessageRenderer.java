package com.tankbattle.client.render;

import com.tankbattle.client.state.MessageLog;
import com.tankbattle.common.config.GameConfig;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * 消息渲染（图层 ⑧）：8 行，行距 16px，y 从 12 起。只读 {@link MessageLog} 快照。
 */
public final class MessageRenderer {

    private static final Color TEXT = Color.WHITE;

    public void render(Graphics2D g, MessageLog messages) {
        List<String> lines = messages.snapshot();
        g.setColor(TEXT);
        int count = Math.min(GameConfig.MESSAGE_CAPACITY, lines.size());
        for (int i = 0; i < count; i++) {
            g.drawString(lines.get(i), 5, GameConfig.MESSAGE_FIRST_Y + i * GameConfig.MESSAGE_LINE_HEIGHT);
        }
    }
}
