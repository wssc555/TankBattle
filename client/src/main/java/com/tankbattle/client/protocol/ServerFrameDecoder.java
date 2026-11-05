package com.tankbattle.client.protocol;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.level.MapSymbols;
import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.common.protocol.TextCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端帧解码器。
 * <p>
 * 线性切分 {@code ;}，单条畸形指令只丢弃自身；任何分支都不提前 return，因此 {@code L} 后同一行的
 * {@code p}/{@code g}/{@code n} 全部保留。未知前缀只 warn，不断线。
 * <p>
 * 无状态、零实例依赖（帧长上限取协议常量 {@link GameConfig#MAX_FRAME_CHARS}），
 * 回放工具与单测可在任意进程直接调用。
 */
public final class ServerFrameDecoder {

    private static final Logger log = LoggerFactory.getLogger(ServerFrameDecoder.class);

    private ServerFrameDecoder() {
    }

    /**
     * 解码一条 TCP 文本行；永不抛出运行时异常（畸形输入只 warn 并丢弃）。
     */
    public static List<S2c> decode(String frame) {
        if (frame == null || frame.isBlank()) {
            return List.of();
        }
        if (frame.length() > GameConfig.MAX_FRAME_CHARS) {
            log.warn("帧超长丢弃: {} 字符", frame.length());
            return List.of();
        }
        List<S2c> out = new ArrayList<>(32);
        int start = 0;
        int length = frame.length();
        while (start < length) {
            int end = frame.indexOf(';', start);
            if (end < 0) {
                log.warn("帧缺少结束分号，丢弃尾部: {}",
                        frame.substring(start, Math.min(length, start + 40)));
                break;
            }
            String token = frame.substring(start, end);
            start = end + 1;
            if (token.isEmpty()) {
                continue;
            }
            S2c message = parseToken(token);
            if (message != null) {
                out.add(message);
            }
        }
        return out;
    }

    private static S2c parseToken(String token) {
        try {
            return switch (token.charAt(0)) {
                case 'L' -> new S2c.LevelLoad(parseInt(token.substring(1), token));
                case 'M' -> parseMap(token);
                case 'd' -> new S2c.FrameSeq(Long.parseLong(token.substring(1)));
                case 'v' -> "v2".equals(token) ? new S2c.CapabilityAck() : unknown(token);
                case 'n' -> {
                    int[] p = TextCodec.ints(token.substring(1), 3);
                    yield new S2c.Sprite(p[0], p[1], checkedTexture(p[2]));
                }
                case 't' -> {
                    int[] p = TextCodec.ints(token.substring(1), 3);
                    yield new S2c.Bullet(p[0], p[1], Direction.fromCode(p[2]));
                }
                case 'o' -> {
                    String[] p = TextCodec.fields(token.substring(1), 3);
                    yield new S2c.Bomb(parseInt(p[0], token), parseInt(p[1], token), bombSize(p[2]));
                }
                case 'i' -> {
                    int[] p = TextCodec.ints(token.substring(1), 2);
                    yield new S2c.Shield(p[0], p[1]);
                }
                case 'b' -> {
                    int[] p = TextCodec.ints(token.substring(1), 3);
                    yield new S2c.BaseState(p[0], p[1], p[2] == 1);
                }
                case 'w' -> {
                    String[] p = TextCodec.fields(token.substring(1), 3);
                    yield new S2c.BrickWallState(parseInt(p[0], token), parseInt(p[1], token),
                            TextCodec.bits(p[2], GameConfig.BRICK_BITS));
                }
                case 's' -> {
                    String[] p = TextCodec.fields(token.substring(1), 3);
                    yield new S2c.SteelWallState(parseInt(p[0], token), parseInt(p[1], token),
                            TextCodec.bits(p[2], GameConfig.STEEL_BITS));
                }
                case 'p' -> {
                    int[] p = TextCodec.ints(token.substring(1), 6);
                    yield new S2c.PanelInfo(p[0], p[1], p[2], p[3], p[4], p[5]);
                }
                case 'g' -> new S2c.WinAnim(parseInt(token.substring(1), token));
                case 'x' -> new S2c.Pause(token.length() >= 2 && token.charAt(1) == '1');
                case 'j' -> new S2c.VoteServerYes();
                case 'a' -> new S2c.GameOver();
                case 'm' -> new S2c.Chat(token.substring(1));
                default -> unknown(token);
            };
        } catch (RuntimeException ex) {
            log.warn("丢弃畸形指令 [{}]: {}", abbreviate(token), ex.toString());
            return null;
        }
    }

    private static S2c.MapData parseMap(String token) {
        int colon = token.indexOf(':');
        if (colon < 2) {
            throw new IllegalArgumentException("M 指令缺少关卡号或冒号");
        }
        int level = parseInt(token.substring(1, colon), token);
        String cells = token.substring(colon + 1);
        if (cells.length() != GameConfig.CELL_COUNT) {
            throw new IllegalArgumentException("地图长度不符: " + cells.length());
        }
        for (int i = 0; i < cells.length(); i++) {
            if (MapSymbols.MAP_ALPHABET.indexOf(cells.charAt(i)) < 0) {
                throw new IllegalArgumentException("非法地图符号: " + cells.charAt(i));
            }
        }
        return new S2c.MapData(level, cells);
    }

    private static S2c.BombSize bombSize(String literal) {
        return switch (literal) {
            case "small" -> S2c.BombSize.SMALL;
            case "big" -> S2c.BombSize.BIG;
            default -> throw new IllegalArgumentException("非法爆炸尺寸: " + literal);
        };
    }

    private static int checkedTexture(int index) {
        if (index < 0 || index >= GameConfig.TEXTURE_COUNT) {
            throw new IllegalArgumentException("纹理越界: " + index);
        }
        return index;
    }

    private static int parseInt(String raw, String token) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法整数: [" + raw + "] in " + abbreviate(token), e);
        }
    }

    private static S2c unknown(String token) {
        log.warn("未知指令前缀: {}", token.charAt(0));
        return null;
    }

    private static String abbreviate(String token) {
        return token.length() <= 40 ? token : token.substring(0, 40) + "...";
    }
}
