package com.tankbattle.common.config;

/**
 * 双端共享常量（附录 B）。
 * <p>
 * 客户端与服务端只读取这里；禁止把魔法数字散落到渲染/协议代码。本类不持有运行期可变状态。
 */
public final class GameConfig {

    private GameConfig() {
    }

    // ------------------------------------------------------------------
    // 网络 / 帧
    // ------------------------------------------------------------------

    /**
     * 默认监听端口。
     */
    public static final int PORT = 9999;
    /**
     * 服务端逻辑帧长（毫秒）。
     */
    public static final int FRAME_MS = 30;
    /**
     * 客户端渲染帧率。
     */
    public static final int RENDER_FPS = 60;
    /**
     * 客户端输入采样率。
     */
    public static final int INPUT_HZ = 30;
    /**
     * 单帧文本长度上限（字符）。
     */
    public static final int MAX_FRAME_CHARS = 16384;
    /**
     * 接收队列容量。
     */
    public static final int FRAME_QUEUE_CAPACITY = 64;
    /**
     * 能力协商等待时间（毫秒）。
     */
    public static final int HANDSHAKE_TIMEOUT_MS = 1000;
    /**
     * 空闲检测阈值（毫秒）。
     */
    public static final int IDLE_TIMEOUT_MS = 5000;
    /**
     * 连接超时（毫秒）。
     */
    public static final int CONNECT_TIMEOUT_MS = 3000;
    /**
     * 重连退避基数 / 上限（毫秒）。
     */
    public static final int RECONNECT_BASE_MS = 500;
    public static final int RECONNECT_MAX_MS = 8000;
    /**
     * 自动重连最大次数。
     */
    public static final int RECONNECT_MAX_ATTEMPTS = 6;
    /**
     * 重连抖动上限（毫秒）。
     */
    public static final int RECONNECT_JITTER_MS = 200;

    // ------------------------------------------------------------------
    // 地图几何
    // ------------------------------------------------------------------

    /**
     * 网格边长（像素）。
     */
    public static final int GRID = 25;
    /**
     * 地图原点。
     */
    public static final int MAP_ORIGIN = 23;
    /**
     * 行列数。
     */
    public static final int MAP_COLS = 20;
    public static final int MAP_ROWS = 20;
    /**
     * 格子总数。
     */
    public static final int CELL_COUNT = MAP_COLS * MAP_ROWS;
    /**
     * 地图可用边长（20 × 25）。
     */
    public static final int MAP_PIXEL = MAP_COLS * GRID;

    /**
     * 固定画布逻辑尺寸（图层 680×545）。
     */
    public static final int PANEL_W = 680;
    public static final int PANEL_H = 545;
    /**
     * 地图蓝框。
     */
    public static final int FRAME_INSET = 10;
    public static final int FRAME_SIZE = 501;
    /**
     * 玩家坦克半径（贴图偏移）。
     */
    public static final int TANK_HALF = 12;

    // ------------------------------------------------------------------
    // 基地区域
    // ------------------------------------------------------------------

    public static final int BASE_X = 260;
    public static final int BASE_Y = 498;

    public static final int ARMOR_X0 = 248;
    public static final int ARMOR_Y0 = 498;
    public static final int ARMOR_X1 = 273;
    public static final int ARMOR_Y1 = 498;
    public static final int ARMOR_X2 = 248;
    public static final int ARMOR_Y2 = 473;
    public static final int ARMOR_X3 = 273;
    public static final int ARMOR_Y3 = 473;

    /**
     * 护甲朝向：0=上半、1=下半、2=左半、3=右半。
     */
    public static final int ARMOR_O0 = 2;
    public static final int ARMOR_O1 = 3;
    public static final int ARMOR_O2 = 1;
    public static final int ARMOR_O3 = 1;

    // ------------------------------------------------------------------
    // 纹理
    // ------------------------------------------------------------------

    public static final int TEXTURE_COUNT = 88;
    /**
     * classpath 资源模板，索引 k 对应文件 (k+1).jpg。
     */
    public static final String TEXTURE_PATH = "/image/%d.jpg";

    public static final int TEX_BASE_OK = 0;
    public static final int TEX_BASE_DEAD = 1;
    public static final int TEX_STEEL = 53;
    public static final int TEX_HUD_P1 = 54;
    public static final int TEX_BRICK = 70;
    public static final int TEX_RIVER = 71;
    public static final int TEX_HUD_P2 = 72;

    // ------------------------------------------------------------------
    // 墙体 / 子弹 / 爆炸 / 护盾
    // ------------------------------------------------------------------

    /**
     * 砖墙 4×4 子块，每块 7px。
     */
    public static final int BRICK_SUB = 4;
    public static final int BRICK_SUB_SIZE = 7;
    public static final int BRICK_BITS = BRICK_SUB * BRICK_SUB;
    /**
     * 钢墙 2×2 象限，每块 13px。
     */
    public static final int STEEL_SUB = 2;
    public static final int STEEL_SUB_SIZE = 13;
    public static final int STEEL_BITS = STEEL_SUB * STEEL_SUB;
    /**
     * 砖墙底色（被打掉子块露出）。
     */
    public static final int BRICK_BG_R = 128;
    public static final int BRICK_BG_G = 64;
    public static final int BRICK_BG_B = 0;

    /**
     * 子弹尺寸：竖 3×9 / 横 9×3。
     */
    public static final int BULLET_THIN = 3;
    public static final int BULLET_LONG = 9;

    /**
     * 爆炸半径：big 14/9/6、small 7/4/2。
     */
    public static final int BOMB_BIG_OUTER = 14;
    public static final int BOMB_BIG_MIDDLE = 9;
    public static final int BOMB_BIG_INNER = 6;
    public static final int BOMB_SMALL_OUTER = 7;
    public static final int BOMB_SMALL_MIDDLE = 4;
    public static final int BOMB_SMALL_INNER = 2;

    /**
     * 护盾双线矩形：外 25×25、内 23×23。
     */
    public static final int SHIELD_OUTER = 25;
    public static final int SHIELD_INNER = 23;

    // ------------------------------------------------------------------
    // HUD / 消息 / 过关动画
    // ------------------------------------------------------------------

    public static final int MESSAGE_CAPACITY = 8;
    public static final int MESSAGE_EXPIRE_FRAMES = 300;
    public static final int MESSAGE_LINE_HEIGHT = 16;
    public static final int MESSAGE_FIRST_Y = 12;

    /**
     * 过关阈值。
     */
    public static final int WIN_ANIM_START = 150;
    public static final int WIN_TEXT_START = 190;
    public static final int WIN_FADE_START = 400;
    public static final int WIN_NEXT_LEVEL_FRAME = 470;
    public static final int WIN_RESET_FRAME = 500;
    /**
     * 灰幕最大高度步长（temp 上限 30）。
     */
    public static final int WIN_MASK_MAX_STEP = 30;

    /**
     * 过关文字初始颜色。
     */
    public static final int WIN_TEXT_R = 34;
    public static final int WIN_TEXT_G = 23;
    public static final int WIN_TEXT_B = 128;
    /**
     * 缓动目标灰度。
     */
    public static final int WIN_FADE_TARGET = 128;
    /**
     * 缓动系数（整数百分比）。
     */
    public static final double WIN_FADE_RATE = 0.2;

    // ------------------------------------------------------------------
    // 聊天
    // ------------------------------------------------------------------

    /**
     * 聊天最大长度（字符）。
     */
    public static final int CHAT_MAX_CHARS = 120;

}
