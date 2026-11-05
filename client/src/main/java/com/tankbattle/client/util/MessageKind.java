package com.tankbattle.client.util;

/**
 * 界面文案唯一来源。
 * <p>
 * 全部界面文案集中定义、逐字固定，保证显示一致。
 */
public enum MessageKind {

    WELCOME("欢迎来到坦克大战用户端！请输入主机IP地址然后点击\"连接主机\"按钮开始游戏"),
    HOST_SAYS("主机端玩家说："),
    CLIENT_SAYS("用户端玩家说："),

    HELP_TITLE("******************************坦克大战 ******************************"),
    HELP_FIRE("帮助: 按s键发射子弹,按键盘的方向键来控制坦克的移动"),
    HELP_FOCUS("如果按键没有反应请 1. 关闭大写功能; 2. 用 tab键切换 "),
    HELP_CHAT("如果您在使用对话界面请移动到控制界面."),
    HELP_FOOTER("********************************************************************************"),

    CONNECTING("正在连接主机"),
    CONNECTED("已成功连接到主机，开始载入游戏"),
    LOADED("载入完毕，游戏开始了！"),
    HOST_EXITED("主机端退出了"),
    RECONNECTING("正在重连主机..."),
    RECONNECT_FAILED("重连失败，请手动重试"),
    CONNECT_TIMEOUT("连接超时"),
    CAPABILITY_V2("已启用 V2 模式（60FPS 插值）"),
    CAPABILITY_LOCKSTEP("未检测到 V2 能力，使用逐帧同步模式"),

    GAME_OVER("GAME OVER ! 　想再玩一次吗 ( y / n ) ?"),
    WAIT_HOST("等待主机端玩家回应..."),
    VOTE_RESTART("主机端玩家决定再玩一次，游戏重新开始了..."),

    PAUSE_BY_HOST("主机端玩家暂停了游戏"),
    RESUME_BY_HOST("主机端玩家取消了暂停"),
    PAUSE_BY_CLIENT("用户端玩家暂停了游戏"),
    RESUME_BY_CLIENT("用户端玩家取消了暂停"),

    NOT_CONNECTED_CHAT("还没有和主机端玩家联上, 无法发送对话"),
    EMPTY_CHAT("对话内容不能为空"),
    EMPTY_HOST("请输入主机IP地址"),

    TAB_HINT_1("提示：用\"tab\"键可以自由切换于控制界面和对话界面"),
    TAB_HINT_2("提示：按回车键可以直接发送您的对话");

    private final String text;

    MessageKind(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
