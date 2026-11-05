package com.tankbattle.common.protocol;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;

/**
 * 服务端 → 客户端消息模型（附录 A）。
 * <p>
 * v1 兼容 14 种 + v2 扩展 3 种（{@code M} / {@code d} / {@code v2}），共 17 种。
 * 全部 record 深不可变：位图用 {@code int} 而非 {@code boolean[]}；文本按不可变 String 持有。
 * sealed 保证消费方 switch 的穷尽性由编译器检查。
 */
public sealed interface S2c {

    /**
     * {@code L{n};} 加载第 n 关。
     */
    record LevelLoad(int level) implements S2c {
    }

    /**
     * {@code n{x},{y},{idx};} 普通实体（坦克 / 道具）位置与纹理索引 0..87。
     */
    record Sprite(int x, int y, int textureIndex) implements S2c {
    }

    /**
     * {@code t{x},{y},{d};} 子弹位置与方向。
     */
    record Bullet(int x, int y, Direction direction) implements S2c {
    }

    /**
     * {@code o{x},{y},{big|small};} 爆炸特效。
     */
    record Bomb(int x, int y, BombSize size) implements S2c {
    }

    /**
     * {@code i{x},{y};} 玩家无敌标记。
     */
    record Shield(int x, int y) implements S2c {
    }

    /**
     * {@code b{x},{y},{0|1};} 基地状态，1 = 已毁。
     */
    record BaseState(int x, int y, boolean destroyed) implements S2c {
    }

    /**
     * {@code w{x},{y},{16bit};} 砖墙 16 子块，1 = 已毁。
     */
    record BrickWallState(int x, int y, int destroyedBits) implements S2c {
    }

    /**
     * {@code s{x},{y},{4bit};} 钢墙 4 象限，1 = 已毁。
     */
    record SteelWallState(int x, int y, int destroyedBits) implements S2c {
    }

    /**
     * {@code p{敌余},{关},{P1命},{P1分},{P2命},{P2分};} 面板信息，字段顺序不可变。
     */
    record PanelInfo(int enemyLeft, int level, int p1Life, int p1Score, int p2Life, int p2Score)
            implements S2c {
    }

    /**
     * {@code g{n};} 过关动画计数。
     */
    record WinAnim(int winningCount) implements S2c {
    }

    /**
     * {@code x1;} 已暂停 / {@code x0;} 已恢复。
     */
    record Pause(boolean paused) implements S2c {
    }

    /**
     * {@code j;} 主机已投同意。
     */
    record VoteServerYes() implements S2c {
    }

    /**
     * {@code a;} 游戏结束。
     */
    record GameOver() implements S2c {
    }

    /**
     * {@code m{text};} 主机聊天。
     */
    record Chat(String text) implements S2c {
    }

    /**
     * {@code M{n}:{400符号};} v2 权威地图，随 L 下发。
     */
    record MapData(int level, String cells400) implements S2c {
        public MapData {
            if (cells400 == null || cells400.length() != GameConfig.CELL_COUNT) {
                throw new IllegalArgumentException("地图负载必须为 400 字符");
            }
        }
    }

    /**
     * {@code d{seq};} v2 帧序号。
     */
    record FrameSeq(long seq) implements S2c {
    }

    /**
     * {@code v2;} v2 能力确认。
     */
    record CapabilityAck() implements S2c {
    }

    /**
     * 爆炸尺寸；渲染半径由枚举集中持有。
     */
    enum BombSize {
        BIG(GameConfig.BOMB_BIG_OUTER, GameConfig.BOMB_BIG_MIDDLE, GameConfig.BOMB_BIG_INNER),
        SMALL(GameConfig.BOMB_SMALL_OUTER, GameConfig.BOMB_SMALL_MIDDLE, GameConfig.BOMB_SMALL_INNER);

        private final int outer;
        private final int middle;
        private final int inner;

        BombSize(int outer, int middle, int inner) {
            this.outer = outer;
            this.middle = middle;
            this.inner = inner;
        }

        public int outerRadius() {
            return outer;
        }

        public int middleRadius() {
            return middle;
        }

        public int innerRadius() {
            return inner;
        }

        /**
         * 协议字面量：{@code big} / {@code small}。
         */
        public String text() {
            return this == SMALL ? "small" : "big";
        }
    }
}
