package com.tankbattle.server.config;

import com.tankbattle.common.config.GameConfig;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 服务端专属参数（权威模拟用）。
 * <p>
 * 双端共享的常量（端口/帧长/网格/地图原点/纹理/墙体/爆炸半径/消息/过关动画）在
 * {@link GameConfig}；这里只放客户端不需要的权威模拟参数：出生点、生命、敌人曲线、
 * 道具权重、象限几何、服务端纹理基址等。代码中不得出现裸魔法数字。
 */
public final class ServerConfig {

    private ServerConfig() {
    }

    // ------------------------------------------------------------------
    // 可配置项
    // ------------------------------------------------------------------

    /**
     * 运行期可配置项（{@code application.properties} 覆盖）。
     */
    public record Runtime(int port,
                          int frameMs,
                          boolean incrementalSync,
                          boolean classicPowerUpRoll,
                          int voteTimeoutSeconds,
                          int slowFrameWarnMs,
                          String netCharset) {
    }

    private static final String RESOURCE = "/application.properties";

    public static Runtime loadRuntime() {
        Properties p = new Properties();
        try (InputStream in = ServerConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
            // 配置不可读时退回内置默认值
        }
        return new Runtime(
                intOf(p, "server.port", GameConfig.PORT),
                intOf(p, "server.frameMs", GameConfig.FRAME_MS),
                boolOf(p, "net.incremental", false),
                boolOf(p, "powerup.classicRoll", true),
                intOf(p, "flow.voteTimeoutSeconds", DEFAULT_VOTE_TIMEOUT_SECONDS),
                intOf(p, "log.slowFrameWarnMs", SLOW_FRAME_WARN_MS),
                stringOf(p, "net.charset", DEFAULT_NET_CHARSET));
    }

    /**
     * 会话收发字符集：默认 UTF-8；对接 GBK 编码客户端时配置 {@code net.charset=GBK}。
     */
    public static final String DEFAULT_NET_CHARSET = "UTF-8";

    private static String stringOf(Properties p, String key, String fallback) {
        String raw = p.getProperty(key);
        return raw == null || raw.isBlank() ? fallback : raw.trim();
    }

    private static int intOf(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean boolOf(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    // ------------------------------------------------------------------
    // 主循环 / 可观测
    // ------------------------------------------------------------------

    public static final int SLOW_FRAME_WARN_MS = 5;
    public static final int DEFAULT_VOTE_TIMEOUT_SECONDS = 30;

    // ------------------------------------------------------------------
    // 坦克几何与活动区域
    // ------------------------------------------------------------------

    /**
     * 坦克碰撞盒边长（25×25）。
     */
    public static final int TANK_BOX = 25;

    public static final int PLAYER_AREA_X = 35;
    public static final int PLAYER_AREA_Y = 35;
    public static final int PLAYER_AREA_W = 452;
    public static final int PLAYER_AREA_H = 452;

    /**
     * 子弹活动区域，出界即消失。
     */
    public static final int BULLET_AREA_X = 18;
    public static final int BULLET_AREA_Y = 18;
    public static final int BULLET_AREA_W = 486;
    public static final int BULLET_AREA_H = 486;

    public static Rectangle playerArea() {
        return new Rectangle(PLAYER_AREA_X, PLAYER_AREA_Y, PLAYER_AREA_W, PLAYER_AREA_H);
    }

    public static Rectangle bulletArea() {
        return new Rectangle(BULLET_AREA_X, BULLET_AREA_Y, BULLET_AREA_W, BULLET_AREA_H);
    }

    // ------------------------------------------------------------------
    // 玩家
    // ------------------------------------------------------------------

    public static final int SPAWN_P1_X = 198;
    public static final int SPAWN_P1_Y = 498;
    public static final int SPAWN_P2_X = 323;
    public static final int SPAWN_P2_Y = 498;

    public static final int PLAYER_LIFE = 3;
    public static final int PLAYER_HEALTH = 1;
    public static final int PLAYER_MAX_HEALTH = 2;
    public static final int PLAYER_INVULN_FRAMES = 150;
    public static final int PLAYER_BULLETS = 1;
    public static final int PLAYER_MAX_SPEED = 3;
    public static final int PLAYER_OUT_X = 100000;
    public static final int PLAYER_OUT_Y = 100000;

    // ------------------------------------------------------------------
    // 敌人与生产曲线
    // ------------------------------------------------------------------

    public static final int ENEMY_SPAWN_START = 150;
    public static final int ENEMY_SPAWN_STEP = 10;
    public static final int ENEMY_SPAWN_MIN = 30;
    public static final int ENEMY_MAX_ALIVE_START = 3;
    public static final int ENEMY_MAX_ALIVE = 10;
    public static final int ENEMY_PER_LEVEL = 20;
    public static final int ENEMY_SPAWN_Y = GameConfig.MAP_ORIGIN;
    public static final int ENEMY_SPAWN_X_STEP = 238;
    public static final int ENEMY_SPAWN_X_PHASE = 3;
    public static final int ENEMY_AI_INTERVAL_MAX = 200;
    public static final int ENEMY_AI_INTERVAL_AFTER_BOUNCE = 250;
    public static final double ENEMY_TURN_CHANCE = 0.90;
    public static final int ENEMY_FLASH_PERIOD = 10;
    public static final int ENEMY_FLASH_THRESHOLD = 4;
    public static final int ENEMY_SCORE_UNIT = 100;

    // ------------------------------------------------------------------
    // 基地护甲
    // ------------------------------------------------------------------

    public static final int STEEL_ARMOR_FRAMES = 600;

    // ------------------------------------------------------------------
    // 子弹与爆炸
    // ------------------------------------------------------------------

    public static final int BULLET_BORDER_V_W = 5;
    public static final int BULLET_BORDER_V_H = 13;
    public static final int BULLET_BORDER_H_W = 13;
    public static final int BULLET_BORDER_H_H = 5;

    public static final int BOMB_BIG_FRAMES = 6;
    public static final int BOMB_SMALL_FRAMES = 4;
    public static final int BOMB_BIG_JITTER = 8;
    public static final int BOMB_SMALL_JITTER = 4;
    public static final int BOMB_POOL_SIZE = 64;
    public static final int BULLET_POOL_SIZE = 64;

    // ------------------------------------------------------------------
    // 道具
    // ------------------------------------------------------------------

    public static final int POWERUP_SCORE = 50;
    public static final int POWERUP_POS_MIN = 24;
    public static final int POWERUP_POS_RANGE = 475;
    public static final int POWERUP_WEIGHT_TOTAL = 17;
    public static final int POWERUP_LIFE_MIN = 100;
    public static final int POWERUP_LIFE_RANGE = 630;
    public static final int POWERUP_TIMED_MIN = 300;
    public static final int POWERUP_TIMED_RANGE = 400;

    // ------------------------------------------------------------------
    // 墙体象限
    // ------------------------------------------------------------------

    public static final int QUADRANT_SIZE = 11;
    public static final int QUADRANT_SUB_SIZE = 5;
    public static final int QUADRANT_SUB_OFFSET = 7;

    // ------------------------------------------------------------------
    // 服务端纹理基址（客户端只接收索引，不关心基址）
    // ------------------------------------------------------------------

    public static final int TEX_PLAYER1 = 54;
    public static final int TEX_PLAYER2 = 72;
    public static final int TEX_POWERUP = 46;
    public static final int TEX_ENEMY_TYPE1 = 38;
    public static final int TEX_ENEMY_TYPE2 = 2;
    public static final int TEX_ENEMY_TYPE3 = 10;
    public static final int TEX_ENEMY_HEAVY = 18;
    public static final int TEX_FLASH_OFFSET = 4;
    public static final int TEX_HEAVY_ARMOR_STEP = 4;
    public static final int TEX_PLAYER_STATUS_STEP = 4;

    // ------------------------------------------------------------------
    // 流程 / 空间索引
    // ------------------------------------------------------------------

    /**
     * 过关动画冻结帧（120 冻结、470 换关、500 解冻见 {@link GameConfig}）。
     */
    public static final int WIN_FREEZE_FRAME = 120;

    public static final int SPATIAL_COLS = GameConfig.MAP_COLS;
    public static final int SPATIAL_ROWS = GameConfig.MAP_ROWS;

    /**
     * 离屏画布尺寸。
     */
    public static final int CANVAS_W = 640;
    public static final int CANVAS_H = 550;
}
