package com.tankbattle.client.render;

import com.tankbattle.client.config.ClientConfig;
import com.tankbattle.client.input.InputBindings;
import com.tankbattle.client.input.InputState;
import com.tankbattle.client.input.UiActions;
import com.tankbattle.client.input.UiHost;
import com.tankbattle.client.net.ConnectionManager;
import com.tankbattle.client.net.LinkListener;
import com.tankbattle.client.net.LinkState;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.StateStore;
import com.tankbattle.client.util.FrameStats;
import com.tankbattle.client.util.MessageKind;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主窗口：BorderLayout 重排，工具栏 / 画布 / 聊天行三段式。
 * <p>
 * 组件只在 EDT 创建与访问；本类不持有网络或模型的可变引用，只订阅 {@link LinkListener} 回调。
 */
public final class ClientFrame extends JFrame implements UiHost {

    private static final Color STATUS_OFF = new Color(120, 120, 120);
    private static final Color STATUS_BUSY = new Color(200, 120, 0);
    private static final Color STATUS_ON = new Color(0, 150, 0);

    private final ClientConfig cfg;
    private final MessageLog messages = new MessageLog();
    private final StateStore store = new StateStore();
    private final TextureAtlas atlas = new TextureAtlas();
    private final InputState inputState;
    private final GamePanel gamePanel;
    /**
     * 构造期注入，之后只读；非 final 以便 GamePanel 的插值开关在构造阶段引用。
     */
    private ConnectionManager connection;
    private final UiActions actions;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    private JTextField ipField;
    private JTextField messageField;
    private JButton connectButton;
    private JButton pauseButton;
    private JButton helpButton;
    private JButton exitButton;
    private JButton sendButton;
    private JLabel statusLabel;

    public ClientFrame(ClientConfig cfg) {
        super("坦克大战");
        assert SwingUtilities.isEventDispatchThread() : "ClientFrame 必须在 EDT 构造";
        this.cfg = cfg;
        this.inputState = new InputState(cfg.exclusiveDirection());
        this.messages.addMessage(MessageKind.WELCOME.text());

        atlas.load();
        FrameStats stats = new FrameStats();
        this.gamePanel = new GamePanel(cfg, stats, store, atlas, messages,
                () -> cfg.interpolation() && connection != null && connection.isFreeRunning());

        this.connection = new ConnectionManager(cfg, store, inputState, messages, new UiBridge(), stats);
        this.actions = new UiActions(this, cfg, connection, store, messages);

        buildUi();
        InputBindings bindings = new InputBindings(getRootPane(), inputState, actions);
        bindings.install();
        bindings.installGameKeys(gamePanel);
        // 聊天框内方向键仍控制坦克，字母键保留正常输入
        bindings.installDirectionsOnly(messageField);
        gamePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                gamePanel.requestFocusInWindow();
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                gamePanel.requestFocusInWindow();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                shutdown(false);
            }
        });
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                // 无需处理
            }

            @Override
            public void windowLostFocus(WindowEvent e) {
                inputState.clear();
            }
        });

        pack();
        setResizable(true);
        setLocationRelativeTo(null);
        applyState(LinkState.DISCONNECTED);
        gamePanel.start();
    }

    // ------------------------------------------------------------------
    // 布局
    // ------------------------------------------------------------------

    private void buildUi() {
        setLayout(new BorderLayout());
        add(buildToolBar(), BorderLayout.NORTH);
        add(gamePanel, BorderLayout.CENTER);
        add(buildChatRow(), BorderLayout.SOUTH);
    }

    private JPanel buildToolBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));

        bar.add(new JLabel("输入主机IP"));
        ipField = new JTextField(cfg.host(), 12);
        bar.add(ipField);

        connectButton = new JButton("连接主机");
        connectButton.setFocusable(false);
        connectButton.addActionListener(e -> actions.connect());
        bar.add(connectButton);

        pauseButton = new JButton("暂停/继续");
        pauseButton.setFocusable(false);
        pauseButton.addActionListener(e -> actions.togglePause());
        bar.add(pauseButton);

        helpButton = new JButton("帮助");
        helpButton.setFocusable(false);
        helpButton.addActionListener(e -> actions.help());
        bar.add(helpButton);

        exitButton = new JButton("退出");
        exitButton.setFocusable(false);
        exitButton.addActionListener(e -> actions.exit());
        bar.add(exitButton);

        statusLabel = new JLabel("● 未连接");
        statusLabel.setForeground(STATUS_OFF);
        bar.add(statusLabel);
        return bar;
    }

    private JPanel buildChatRow() {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        messageField = new JTextField();
        messageField.setEnabled(false);
        messageField.addActionListener(e -> actions.sendChat());
        messageField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                actions.onChatTyping();
            }
        });
        row.add(messageField, BorderLayout.CENTER);

        sendButton = new JButton("发送");
        sendButton.setFocusable(false);
        sendButton.addActionListener(e -> actions.sendChat());
        row.add(sendButton, BorderLayout.EAST);
        return row;
    }

    // ------------------------------------------------------------------
    // 状态灯
    // ------------------------------------------------------------------

    private void applyState(LinkState state) {
        if (state != LinkState.CONNECTED && state != LinkState.HANDSHAKE) {
            inputState.clear();
        }
        switch (state) {
            case DISCONNECTED, CLOSED -> {
                statusLabel.setText("● 未连接");
                statusLabel.setForeground(STATUS_OFF);
                ipField.setEnabled(true);
                connectButton.setEnabled(true);
                pauseButton.setEnabled(false);
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
            }
            case CONNECTING -> {
                statusLabel.setText("● 连接中");
                statusLabel.setForeground(STATUS_BUSY);
                ipField.setEnabled(false);
                connectButton.setEnabled(false);
                pauseButton.setEnabled(false);
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
            }
            case HANDSHAKE -> {
                statusLabel.setText("● 协商中");
                statusLabel.setForeground(STATUS_BUSY);
                ipField.setEnabled(false);
                connectButton.setEnabled(false);
                // 协商期尚未开局，暂停按钮与 togglePause 的 RUNNING/PAUSED 门槛保持一致
                pauseButton.setEnabled(false);
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
            }
            case CONNECTED -> {
                statusLabel.setText("● 已连接");
                statusLabel.setForeground(STATUS_ON);
                ipField.setEnabled(false);
                connectButton.setEnabled(false);
                pauseButton.setEnabled(true);
                messageField.setEnabled(true);
                sendButton.setEnabled(true);
            }
            case RECONNECTING -> {
                statusLabel.setText("● 重连中");
                statusLabel.setForeground(STATUS_BUSY);
                ipField.setEnabled(false);
                connectButton.setEnabled(false);
                pauseButton.setEnabled(false);
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
            }
        }
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void shutdown(boolean confirm) {
        if (confirm && !shuttingDown.get()) {
            int choice = JOptionPane.showConfirmDialog(this, "确定退出?", "退出",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        gamePanel.shutdown();
        connection.close();
        dispose();
    }

    // ------------------------------------------------------------------
    // UiHost
    // ------------------------------------------------------------------

    @Override
    public String ipText() {
        return ipField == null ? "" : ipField.getText();
    }

    @Override
    public String chatText() {
        return messageField == null ? "" : messageField.getText();
    }

    @Override
    public void clearChat() {
        if (messageField != null) {
            messageField.setText("");
        }
    }

    // ------------------------------------------------------------------
    // 连接层回调（任意线程 → 切回 EDT）
    // ------------------------------------------------------------------

    private final class UiBridge implements LinkListener {

        @Override
        public void onStateChanged(LinkState state) {
            SwingUtilities.invokeLater(() -> applyState(state));
        }

        @Override
        public void onCapabilityAck() {
            // 连接层已写入消息队列，这里只负责日志/后续 UI 扩展
            SwingUtilities.invokeLater(() -> statusLabel.setForeground(STATUS_ON));
        }

        @Override
        public void onNotice(String text) {
            // 文案已由 ConnectionManager 写入 MessageLog，避免重复
        }
    }
}
