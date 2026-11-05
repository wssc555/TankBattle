package com.tankbattle.server.entity;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;

import java.awt.Rectangle;

/**
 * 砖墙。25×25 视觉 16 块 7×7（4×4 位图，1 = 已毁），碰撞 4 象限 11×11。
 * <p>
 * 破坏逻辑按「方向（4）× 象限组合（3）× 该象限是否已有损（2）」展开，
 * 实现为「跨象限整层破坏（8 条）」+「象限内细粒度破坏（16 条）」两张表 + 位运算。
 */
public final class BrickWall implements Wall {

    private static final int BITS = 16;
    private static final int COLS_PER_SIDE = 4;

    /**
     * 完整砖墙（无预毁子块）。
     */
    public static final int FULL_BITS = 0;
    /**
     * 朝向 0（上半）的初始位图：下半已毁。
     */
    public static final int BITS_TOP_HALF = 0xFF00;
    /**
     * 朝向 1（下半）的初始位图：上半已毁。
     */
    public static final int BITS_BOTTOM_HALF = 0x00FF;
    /**
     * 朝向 2（左半）的初始位图：右两列已毁。
     */
    public static final int BITS_LEFT_HALF = 0xCCCC;
    /**
     * 朝向 3（右半）的初始位图：左两列已毁。
     */
    public static final int BITS_RIGHT_HALF = 0x3333;

    /**
     * 每个象限在某方向上的"守卫子块"：0=a/top-left、1=b/top-right、2=c/bottom-left、3=d/bottom-right。
     * 行 = 象限，列 = 方向编码（0 上 / 1 下 / 2 左 / 3 右）。
     */
    private static final int[][] GUARD_SUB = {
            {3, 1, 3, 2},   // 象限 0：上→d 下→b 左→d 右→c
            {2, 0, 3, 2},   // 象限 1：上→c 下→a 左→d 右→c
            {3, 1, 1, 0},   // 象限 2：上→d 下→b 左→b 右→a
            {2, 0, 1, 0},   // 象限 3：上→c 下→a 左→b 右→a
    };

    /**
     * 守卫判定的"已整列/整行连通"四元组：纵向取列、横向取行。
     */
    private static final int[][] GUARD_BITS = {
            {1, 2, 5, 6},       // 象限 0 纵向
            {4, 5, 8, 9},       // 象限 0 横向
            {1, 2, 5, 6},       // 象限 1 纵向
            {6, 7, 10, 11},     // 象限 1 横向
            {9, 10, 13, 14},    // 象限 2 纵向
            {4, 5, 8, 9},       // 象限 2 横向
            {9, 10, 13, 14},    // 象限 3 纵向
            {6, 7, 10, 11},     // 象限 3 横向
    };

    /**
     * 守卫判定里"相邻象限那一列/行"的两个位：{@code (!a || !b)} 中的 a、b。
     * 与 {@link #GUARD_BITS} 的排列顺序不同，必须单独列出。
     */
    private static final int[][] NEIGHBOUR_BITS = {
            {2, 6},       // 象限 0 纵向（右侧邻居 q1 的左列）
            {8, 9},       // 象限 0 横向（下侧邻居 q2 的上行）
            {1, 5},       // 象限 1 纵向（左侧邻居 q0 的右列）
            {10, 11},     // 象限 1 横向（下侧邻居 q3 的上行）
            {10, 14},     // 象限 2 纵向（右侧邻居 q3 的左列）
            {4, 5},       // 象限 2 横向（上侧邻居 q0 的下行）
            {9, 13},      // 象限 3 纵向（左侧邻居 q2 的右列）
            {6, 7},       // 象限 3 横向（上侧邻居 q1 的下行）
    };

    /**
     * 象限内 a/b/c/d 四个子块在该方向上的"受击面"（子块下标，按 {@link Direction#code()} 0上/1下/2左/3右 索引）。
     */
    private static final int[][] NEAR_SUB = {
            {2, 3},   // 上：c, d（子弹向上打的是象限底面）
            {0, 1},   // 下：a, b
            {1, 3},   // 左：b, d
            {0, 2},   // 右：a, c
    };

    /**
     * 与受击面一一对应的"对侧面"相对位偏移：同一象限内穿透一层后要毁掉的那一对。
     */
    private static final int[][] FAR_IDX = {
            {0, 1},   // 上：c→a, d→b
            {2, 3},   // 下：a→c, b→d
            {0, 2},   // 左：b→a, d→c
            {1, 3},   // 右：a→b, c→d
    };

    private final Vec pos;
    private final Rectangle generalBorder;
    private final Rectangle[] quadrants = new Rectangle[4];

    private int shapeBits;
    private boolean bulletStopped;
    private boolean removed;

    public BrickWall(int x, int y, int initialBits) {
        this.pos = new Vec(x, y);
        this.generalBorder = new Rectangle(x - GameConfig.TANK_HALF, y - GameConfig.TANK_HALF,
                ServerConfig.TANK_BOX, ServerConfig.TANK_BOX);
        this.shapeBits = initialBits & 0xFFFF;
        buildQuadrants();
    }

    /**
     * 朝向 0/1/2/3 → 对应的初始位图（0 上半、1 下半、2 左半、3 右半）。
     */
    public static int bitsForOrientation(int orientation) {
        return switch (orientation) {
            case 0 -> BITS_TOP_HALF;
            case 1 -> BITS_BOTTOM_HALF;
            case 2 -> BITS_LEFT_HALF;
            case 3 -> BITS_RIGHT_HALF;
            default -> FULL_BITS;
        };
    }

    /**
     * 4 个子块全毁的象限不参与碰撞。
     */
    private void buildQuadrants() {
        for (int q = 0; q < 4; q++) {
            int base = quadrantBase(q);
            boolean gone = bit(base) && bit(base + 1) && bit(base + 4) && bit(base + 5);
            quadrants[q] = gone ? null : quadrant(q);
        }
    }

    private Rectangle quadrant(int index) {
        int qx = pos.x() + ((index & 1) == 0 ? -11 : 1);
        int qy = pos.y() + ((index & 2) == 0 ? -11 : 1);
        return new Rectangle(qx, qy, ServerConfig.QUADRANT_SIZE, ServerConfig.QUADRANT_SIZE);
    }

    public Vec pos() {
        return pos;
    }

    @Override
    public int textureIndex() {
        return GameConfig.TEX_BRICK;
    }

    @Override
    public int bitCount() {
        return BITS;
    }

    @Override
    public int shapeBits() {
        return shapeBits;
    }

    @Override
    public Rectangle[] quadrants() {
        return quadrants;
    }

    @Override
    public Rectangle border() {
        return generalBorder;
    }

    @Override
    public boolean isDestroyed() {
        return shapeBits == 0xFFFF;
    }

    @Override
    public boolean destroyed() {
        return removed;
    }

    @Override
    public void markDestroyed() {
        removed = true;
    }

    @Override
    public void tick(GameContext ctx) {
        // 静态实体，无逐帧行为
    }

    /**
     * 结算一次子弹命中。
     *
     * @param bullet 子弹碰撞盒
     * @param dir    子弹方向
     * @param power  1 = 普通，2 = 超级
     * @return 子弹是否应消失
     */
    public boolean damage(Rectangle bullet, Direction dir, int power) {
        bulletStopped = false;
        if (power == 1) {
            damageSpanningPower1(bullet, dir);
        } else if (power == 2) {
            damageSpanningPower2(bullet, dir);
        }
        damageQuadrants(bullet, dir, power);
        return bulletStopped;
    }

    // ------------------------------------------------------------------
    // 跨象限整层破坏
    // ------------------------------------------------------------------

    private void damageSpanningPower1(Rectangle bullet, Direction dir) {
        switch (dir) {
            case DOWN -> {
                if (spanHit(bullet, 0, 1)) {
                    if (bit(1) && bit(2)) {
                        fillBits(4, 8);
                        quadrants[0] = null;
                        quadrants[1] = null;
                    }
                    if (!bit(1) || !bit(2)) {
                        fillBits(0, 4);
                    }
                    bulletStopped = true;
                }
                if (spanHit(bullet, 2, 3)) {
                    if (bit(9) && bit(10)) {
                        fillBits(12, 16);
                        quadrants[2] = null;
                        quadrants[3] = null;
                    }
                    if (!bit(9) || !bit(10)) {
                        fillBits(8, 12);
                    }
                    bulletStopped = true;
                }
            }
            case UP -> {
                if (spanHit(bullet, 0, 1)) {
                    if (bit(5) && bit(6)) {
                        fillBits(0, 4);
                        quadrants[0] = null;
                        quadrants[1] = null;
                    }
                    if (!bit(5) || !bit(6)) {
                        fillBits(4, 8);
                    }
                    bulletStopped = true;
                }
                if (spanHit(bullet, 2, 3)) {
                    if (bit(13) && bit(14)) {
                        fillBits(8, 12);
                        quadrants[2] = null;
                        quadrants[3] = null;
                    }
                    if (!bit(13) || !bit(14)) {
                        fillBits(12, 16);
                    }
                    bulletStopped = true;
                }
            }
            case RIGHT -> {
                if (spanHit(bullet, 0, 2)) {
                    if (bit(4) && bit(8)) {
                        setBits(1, 5, 9, 13);
                        quadrants[0] = null;
                        quadrants[2] = null;
                    }
                    if (!bit(4) || !bit(8)) {
                        setBits(0, 4, 8, 12);
                    }
                    bulletStopped = true;
                }
                if (spanHit(bullet, 1, 3)) {
                    if (bit(6) && bit(10)) {
                        setBits(3, 7, 11, 15);
                        quadrants[1] = null;
                        quadrants[3] = null;
                    }
                    if (!bit(6) || !bit(10)) {
                        setBits(2, 6, 10, 14);
                    }
                    bulletStopped = true;
                }
            }
            case LEFT -> {
                if (spanHit(bullet, 0, 2)) {
                    if (bit(5) && bit(9)) {
                        setBits(0, 4, 8, 12);
                        quadrants[0] = null;
                        quadrants[2] = null;
                    }
                    if (!bit(5) || !bit(9)) {
                        setBits(1, 5, 9, 13);
                    }
                    bulletStopped = true;
                }
                if (spanHit(bullet, 1, 3)) {
                    if (bit(7) && bit(11)) {
                        setBits(2, 6, 10, 14);
                        quadrants[1] = null;
                        quadrants[3] = null;
                    }
                    if (!bit(7) || !bit(11)) {
                        setBits(3, 7, 11, 15);
                    }
                    bulletStopped = true;
                }
            }
        }
    }

    private void damageSpanningPower2(Rectangle bullet, Direction dir) {
        boolean vertical = dir.vertical();
        if (vertical) {
            if (spanHit(bullet, 0, 1)) {
                fillBits(0, 8);
                quadrants[0] = null;
                quadrants[1] = null;
                bulletStopped = true;
            }
            if (spanHit(bullet, 2, 3)) {
                fillBits(8, 16);
                quadrants[2] = null;
                quadrants[3] = null;
                bulletStopped = true;
            }
        } else {
            if (spanHit(bullet, 0, 2)) {
                setBits(0, 4, 8, 12);
                setBits(1, 5, 9, 13);
                quadrants[0] = null;
                quadrants[2] = null;
                bulletStopped = true;
            }
            if (spanHit(bullet, 1, 3)) {
                setBits(2, 6, 10, 14);
                setBits(3, 7, 11, 15);
                quadrants[1] = null;
                quadrants[3] = null;
                bulletStopped = true;
            }
        }
    }

    // ------------------------------------------------------------------
    // 象限内细粒度破坏
    // ------------------------------------------------------------------

    private void damageQuadrants(Rectangle bullet, Direction dir, int power) {
        int dirCode = dir.code();
        int guardRow = dir.vertical() ? 0 : 1;
        for (int q = 0; q < 4; q++) {
            Rectangle quad = quadrants[q];
            if (quad == null || !bullet.intersects(quad)) {
                continue;
            }
            int[] four = GUARD_BITS[q * 2 + guardRow];
            int[] neighbour = NEIGHBOUR_BITS[q * 2 + guardRow];
            boolean neighbourIntact = bit(neighbour[0]) && bit(neighbour[1]);
            boolean allFourIntact = bit(four[0]) && bit(four[1]) && bit(four[2]) && bit(four[3]);
            boolean guardHit = bullet.intersects(subRect(quad, GUARD_SUB[q][dirCode]));
            // 命中守卫子块 且（相邻列/行未连通 或 四方全毁）时跳过本象限
            if (guardHit && (!neighbourIntact || allFourIntact)) {
                continue;
            }
            applyQuadrant(bullet, quad, q, dir, power);
        }
    }

    private void applyQuadrant(Rectangle bullet, Rectangle quad, int q, Direction dir, int power) {
        int base = quadrantBase(q);
        int dirCode = dir.code();
        int[] nearSubs = NEAR_SUB[dirCode];
        int[] farOffsets = FAR_IDX[dirCode];

        Rectangle[] subs = {
                subRect(quad, 0), subRect(quad, 1), subRect(quad, 2), subRect(quad, 3)
        };
        int[] nearBits = new int[2];
        int[] farBits = new int[2];
        for (int k = 0; k < 2; k++) {
            nearBits[k] = base + subBitOffset(nearSubs[k]);
            farBits[k] = base + subBitOffset(farOffsets[k]);
        }

        // 受击面已毁 → 穿透一层，毁掉对侧面
        for (int k = 0; k < 2; k++) {
            if (bullet.intersects(subs[nearSubs[k]]) && bit(nearBits[k]) && !bit(farBits[k])) {
                setBits(farBits[0], farBits[1]);
                bulletStopped = true;
                quadrants[q] = null;
            }
        }
        // 受击面未毁 → 毁掉受击面（威力 2 同时穿透对侧面）
        for (int k = 0; k < 2; k++) {
            if (bullet.intersects(subs[nearSubs[k]]) && !bit(nearBits[k])) {
                setBits(nearBits[0], nearBits[1]);
                if (power == 2) {
                    setBits(farBits[0], farBits[1]);
                }
                bulletStopped = true;
                if (bit(farBits[0]) && bit(farBits[1])) {
                    quadrants[q] = null;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 位运算与几何辅助
    // ------------------------------------------------------------------

    /**
     * 行优先 4×4 位图中象限 q（0左上/1右上/2左下/3右下）的锚点位：
     * 象限占位图的 2×2 子块，即 bits {base, base+1, base+4, base+5}。
     * 与 {@link #BITS_TOP_HALF}/{@link #BITS_LEFT_HALF} 等朝向常量、
     * {@link #GUARD_BITS}/{@link #NEIGHBOUR_BITS} 的行优先下标一致。
     */
    private static int quadrantBase(int q) {
        return (q & 2) * 4 + (q & 1) * 2;
    }

    /**
     * 子块下标（0..3 = a,b,c,d）→ 象限内位偏移（行优先：a=+0, b=+1, c=+4, d=+5）。
     */
    private static int subBitOffset(int sub) {
        return (sub & 1) + (sub & 2) * 2;
    }

    private boolean spanHit(Rectangle bullet, int qa, int qb) {
        return quadrants[qa] != null && quadrants[qb] != null
                && bullet.intersects(quadrants[qa]) && bullet.intersects(quadrants[qb]);
    }

    private static Rectangle subRect(Rectangle quad, int index) {
        int sx = quad.x + ((index & 1) == 0 ? 0 : ServerConfig.QUADRANT_SUB_OFFSET);
        int sy = quad.y + ((index & 2) == 0 ? 0 : ServerConfig.QUADRANT_SUB_OFFSET);
        return new Rectangle(sx, sy, ServerConfig.QUADRANT_SUB_SIZE, ServerConfig.QUADRANT_SUB_SIZE);
    }

    private boolean bit(int index) {
        return (shapeBits >>> index & 1) != 0;
    }

    private void setBits(int... indices) {
        for (int index : indices) {
            shapeBits |= 1 << index;
        }
    }

    private void fillBits(int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            shapeBits |= 1 << i;
        }
    }
}
