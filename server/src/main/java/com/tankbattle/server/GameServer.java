package com.tankbattle.server;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.GameEvent;
import com.tankbattle.server.core.GameLoop;
import com.tankbattle.server.core.Phase;
import com.tankbattle.server.core.SessionEvent;
import com.tankbattle.server.core.Snapshot;
import com.tankbattle.server.core.TickTask;
import com.tankbattle.server.net.ClientSession;
import com.tankbattle.server.net.NetworkService;
import com.tankbattle.server.render.ServerMonitorFrame;
import com.tankbattle.server.render.TextureBank;
import com.tankbattle.server.system.FlowSystem;
import com.tankbattle.server.system.InputSystem;
import com.tankbattle.server.system.SyncSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 服务端入口与装配根。
 * <p>
 * GUI 模式：窗口"建立主机"按钮触发 {@link NetworkService} + {@link GameLoop}；
 * headless 模式：无窗口直接起网络与循环，供自动化测试使用。
 */
public final class GameServer {

    private static final Logger log = LoggerFactory.getLogger(GameServer.class);

    private static final String MSG_WELCOME = "欢迎来到坦克大战主机端!  请点击\"建立主机\"按钮开始游戏";
    private static final String MSG_CREATING = "正在建立主机(端口%d)";
    private static final String MSG_CREATED = "建立完成，等待玩家连接";
    private static final String MSG_PORT_BUSY = "无法建立主机，请确认端口%d没有被别的程序使用";
    private static final String MSG_NO_CLIENT = "还没有和别的玩家联上, 无法发送对话";
    private static final String MSG_EMPTY_CHAT = "对话内容不能为空";

    private final ServerConfig.Runtime config;
    private final GameContext ctx;
    private final InputSystem input = new InputSystem();
    private final SyncSystem sync;
    private final TickTask tickTask;
    private final GameLoop loop;

    private volatile NetworkService network;
    private volatile ClientSession session;
    private volatile ServerMonitorFrame frame;
    private volatile boolean shuttingDown;

    public GameServer(ServerConfig.Runtime config) {
        this.config = config;
        this.ctx = new GameContext(config, System.nanoTime());
        this.sync = new SyncSystem(config.incrementalSync());
        this.tickTask = new TickTask(ctx, input, sync, this::publish, this::onExitRequested);
        this.loop = new GameLoop(tickTask, config.frameMs());
        this.ctx.messages().add(MSG_WELCOME);
    }

    // ------------------------------------------------------------------
    // 启动
    // ------------------------------------------------------------------

    public static void main(String[] args) {
        int port = GameConfig.PORT;
        boolean headless = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--headless".equals(arg)) {
                headless = true;
            } else if ("--port".equals(arg) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    log.warn("端口参数非法，使用默认端口 {}", GameConfig.PORT);
                }
            } else {
                log.warn("忽略未知参数: {}", arg);
            }
        }

        ServerConfig.Runtime base = ServerConfig.loadRuntime();
        ServerConfig.Runtime runtime = new ServerConfig.Runtime(port, base.frameMs(), base.incrementalSync(),
                base.classicPowerUpRoll(), base.voteTimeoutSeconds(), base.slowFrameWarnMs(), base.netCharset());
        GameServer server = new GameServer(runtime);

        // 无显示环境（容器/CI/远程 SSH）自动降级 headless，避免 JFrame 抛 HeadlessException
        // 后 EDT 静默退出（Important #19）
        if (headless || GraphicsEnvironment.isHeadless()) {
            if (!headless) {
                log.info("检测到无显示环境，自动切换 headless 模式");
            }
            log.info("headless 模式启动");
            server.startHosting();
            return;
        }
        SwingUtilities.invokeLater(server::startGui);
    }

    private void startGui() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 使用默认外观
        }
        TextureBank.load();
        this.frame = new ServerMonitorFrame(this);
        this.frame.setVisible(true);
        log.info("窗口已就绪");
    }

    /**
     * 建立主机并启动主循环。
     */
    public synchronized boolean startHosting() {
        if (network != null) {
            return true;
        }
        ctx.messages().add(MSG_CREATING.formatted(config.port()));
        try {
            network = new NetworkService(config.port(), this::onConnected, this::onDisconnected,
                    resolveCharset(config.netCharset()));
        } catch (IOException e) {
            log.error("端口 {} 绑定失败", config.port(), e);
            network = null;
            ctx.messages().add(MSG_PORT_BUSY.formatted(config.port()));
            return false;
        }
        ctx.messages().add(MSG_CREATED);
        loop.start();
        return true;
    }

    /**
     * 配置的字符集名非法时改用 UTF-8 并告警，不让配置错误阻塞启动。
     */
    private static Charset resolveCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            log.warn("net.charset 配置非法: {}，回退 UTF-8", name);
            return StandardCharsets.UTF_8;
        }
    }

    public boolean hostStarted() {
        return network != null;
    }

    // ------------------------------------------------------------------
    // 会话回调（网络线程）
    // ------------------------------------------------------------------

    private void onConnected(ClientSession incoming) {
        ClientSession previous = this.session;
        this.session = incoming;
        tickTask.bindSession(incoming);
        if (previous != null && previous != incoming) {
            previous.close();
        }
        ctx.postSessionEvent(new SessionEvent(incoming.id(), true));
    }

    private void onDisconnected(ClientSession closed) {
        if (session != closed) {
            return;
        }
        ctx.postSessionEvent(new SessionEvent(closed.id(), false));
    }

    // ------------------------------------------------------------------
    // GUI 动作（EDT）——一律投递到游戏线程执行
    // ------------------------------------------------------------------

    public void togglePause() {
        ctx.post(() -> {
            if (ctx.phase() == Phase.WAITING) {
                ctx.messages().add("游戏还没有开始");
                return;
            }
            FlowSystem.requestPause(ctx, true);
        });
    }

    public void hostVoteYes() {
        ctx.post(() -> FlowSystem.hostVoteYes(ctx));
    }

    public void hostVoteNo() {
        ctx.post(() -> FlowSystem.hostVoteNo(ctx));
    }

    public void sendChat(String text) {
        if (text == null || text.isEmpty()) {
            ctx.messages().add(MSG_EMPTY_CHAT);
            return;
        }
        if (session == null || !session.isOpen()) {
            ctx.messages().add(MSG_NO_CLIENT);
            return;
        }
        ctx.messages().add("主机端玩家说：" + text);
        ctx.post(() -> ctx.emit(new GameEvent.Chat(text)));
    }

    public void showHelp() {
        ctx.messages().add("-------------------------------坦克大战 1.0-----------------------------------");
        ctx.messages().add("帮助: 按 s 键开火,  按键盘的方向键来控制坦克的移动");
        ctx.messages().add("如果按键没有反应请 1. 关闭大写功能; 2. 用 tab键切换");
        ctx.messages().add("到控制界面如果您在使用对话界面.");
        ctx.messages().add("--------------------------------------------------------------------------------------");
    }

    public InputSystem input() {
        return input;
    }

    public GameContext context() {
        return ctx;
    }

    public ServerConfig.Runtime config() {
        return config;
    }

    // ------------------------------------------------------------------
    // 快照分发与停机
    // ------------------------------------------------------------------

    private void publish(Snapshot snapshot) {
        ServerMonitorFrame current = frame;
        if (current != null) {
            current.acceptSnapshot(snapshot);
        }
    }

    /**
     * 游戏线程请求退出：转交 EDT 弹确认框。
     */
    private void onExitRequested() {
        SwingUtilities.invokeLater(this::confirmExit);
    }

    public void confirmExit() {
        ServerMonitorFrame current = frame;
        if (current != null) {
            current.confirmAndExit();
        } else {
            shutdown();
        }
    }

    public synchronized void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        log.info("服务端正在停机");
        loop.close();
        NetworkService current = network;
        if (current != null) {
            current.close();
        }
        ClientSession active = session;
        if (active != null) {
            active.close();
        }
        ServerMonitorFrame currentFrame = frame;
        if (currentFrame != null) {
            currentFrame.dispose();
        }
    }
}
