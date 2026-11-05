package com.tankbattle.client.state;

/**
 * HUD 数据快照（{@code p} 指令，6 字段顺序固定）。
 */
public record HudState(int enemyLeft, int level, int p1Life, int p1Score, int p2Life, int p2Score) {

    public static final HudState EMPTY = new HudState(0, 0, 0, 0, 0, 0);

    /**
     * 7 位补零分数。
     */
    public static String formatScore(int score) {
        return String.format("%07d", Math.max(0, score));
    }
}
