package com.tankbattle.server.protocol;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.common.protocol.TextCodec;

/**
 * 服务端帧编码器：S2c → 分号文本指令。
 * <p>
 * 一帧 = 若干 {@code 指令;} 拼接，由 {@code PrintWriter.println} 一次性发送。
 * 内部复用同一个 {@link StringBuilder}，30 Hz 下零临时字符串。
 * 实例非线程安全，只允许游戏线程调用。
 */
public final class ServerFrameEncoder {

    private static final int BUFFER_SIZE = 4096;

    private final StringBuilder sb = new StringBuilder(BUFFER_SIZE);

    public void begin() {
        sb.setLength(0);
    }

    public boolean isEmpty() {
        return sb.isEmpty();
    }

    public String end() {
        return sb.toString();
    }

    /**
     * 追加一条消息指令。
     */
    public void append(S2c message) {
        switch (message) {
            case S2c.LevelLoad m -> sb.append('L').append(m.level()).append(';');
            case S2c.MapData m -> sb.append('M').append(m.level()).append(':')
                    .append(m.cells400()).append(';');
            case S2c.FrameSeq m -> sb.append('d').append(m.seq()).append(';');
            case S2c.CapabilityAck ignored -> sb.append("v2;");
            case S2c.Sprite m -> sb.append('n').append(m.x()).append(',')
                    .append(m.y()).append(',').append(m.textureIndex()).append(';');
            case S2c.Bullet m -> sb.append('t').append(m.x()).append(',')
                    .append(m.y()).append(',').append(m.direction().code()).append(';');
            case S2c.Bomb m -> sb.append('o').append(m.x()).append(',')
                    .append(m.y()).append(',').append(m.size().text()).append(';');
            case S2c.Shield m -> sb.append('i').append(m.x()).append(',')
                    .append(m.y()).append(';');
            case S2c.BaseState m -> sb.append('b').append(m.x()).append(',')
                    .append(m.y()).append(',').append(m.destroyed() ? 1 : 0).append(';');
            case S2c.BrickWallState m -> sb.append('w').append(m.x()).append(',')
                    .append(m.y()).append(',')
                    .append(TextCodec.bitString(m.destroyedBits(), GameConfig.BRICK_BITS)).append(';');
            case S2c.SteelWallState m -> sb.append('s').append(m.x()).append(',')
                    .append(m.y()).append(',')
                    .append(TextCodec.bitString(m.destroyedBits(), GameConfig.STEEL_BITS)).append(';');
            case S2c.PanelInfo m -> sb.append('p').append(m.enemyLeft()).append(',')
                    .append(m.level()).append(',').append(m.p1Life()).append(',')
                    .append(m.p1Score()).append(',').append(m.p2Life()).append(',')
                    .append(m.p2Score()).append(';');
            case S2c.WinAnim m -> sb.append('g').append(m.winningCount()).append(';');
            case S2c.Pause m -> sb.append(m.paused() ? "x1;" : "x0;");
            case S2c.VoteServerYes ignored -> sb.append("j;");
            case S2c.GameOver ignored -> sb.append("a;");
            case S2c.Chat m -> sb.append('m').append(m.text()).append(';');
        }
    }
}
