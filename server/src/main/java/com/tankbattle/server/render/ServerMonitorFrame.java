package com.tankbattle.server.render;

import com.tankbattle.common.model.Direction;
import com.tankbattle.server.GameServer;
import com.tankbattle.server.core.Phase;
import com.tankbattle.server.core.Snapshot;
import com.tankbattle.server.system.InputSystem;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 服务端监控窗口。使用 {@link BorderLayout}，尺寸由画布决定后 {@code pack()}。
 */
public final class ServerMonitorFrame extends JFrame {

    private static final int BUTTON_W = 120;
    private static final int BUTTON_H = 22;
    private static final int CHAT_FIELD_H = 24;
    private static final int CHAT_BUTTON_W = 62;
    private static final int TOOLBAR_GAP = 0;
    private static final int TOOLBAR_MARGIN = 6;

    private final GameServer server;
    private final MonitorPanel mainPanel = new MonitorPanel();
    private final JTextField messageField = new JTextField();
    private final JButton sendMessage = new JButton("发送");
    private final JButton createServer = new JButton("建立主机");
    private final JButton pauseAndResume = new JButton("暂停/继续");
    private final JButton help = new JButton("帮助");
    private final JButton exit = new JButton("退出");

    private int helpMessageCount = 1;
    private volatile boolean chatEnabled;

    public ServerMonitorFrame(GameServer server) {
        super("坦克大战");
        this.server = server;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        getContentPane().setLayout(new BorderLayout());

        getContentPane().add(buildToolbar(), BorderLayout.NORTH);
        getContentPane().add(mainPanel, BorderLayout.CENTER);
        getContentPane().add(buildChatRow(), BorderLayout.SOUTH);

        installKeyBindings();
        installListeners();
        installFocusReset();

        pack();
        setLocationRelativeTo(null);
        setResizable(true);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, TOOLBAR_GAP, TOOLBAR_MARGIN));
        for (JButton button : new JButton[]{createServer, pauseAndResume, help, exit}) {
            button.setPreferredSize(new Dimension(BUTTON_W, BUTTON_H));
            button.setFocusable(false);
            toolbar.add(button);
        }
        return toolbar;
    }

    private JPanel buildChatRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 2));
        messageField.setEnabled(false);
        messageField.setPreferredSize(new Dimension(0, CHAT_FIELD_H));
        sendMessage.setPreferredSize(new Dimension(CHAT_BUTTON_W, CHAT_FIELD_H));
        sendMessage.setFocusable(false);
        row.add(messageField, BorderLayout.CENTER);
        row.add(sendMessage, BorderLayout.EAST);
        return row;
    }

    private void installListeners() {
        createServer.addActionListener(e -> {
            if (server.startHosting()) {
                createServer.setEnabled(false);
            }
        });
        pauseAndResume.addActionListener(e -> server.togglePause());
        help.addActionListener(e -> server.showHelp());
        exit.addActionListener(e -> confirmAndExit());
        sendMessage.addActionListener(e -> sendCurrentChat());
        messageField.addActionListener(e -> sendCurrentChat());
        messageField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (helpMessageCount > 0 && messageField.isEnabled()) {
                    server.context().messages().add("提示：用\"tab\"键可以自由切换于控制界面和对话界面");
                    server.context().messages().add("提示：按回车键可以直接发送您的对话");
                    helpMessageCount--;
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmAndExit();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                mainPanel.requestFocusInWindow();
            }
        });
    }

    private void installFocusReset() {
        mainPanel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                server.input().clear();
            }
        });
    }

    // ------------------------------------------------------------------
    // 键盘绑定（基于 InputMap/ActionMap）
    // ------------------------------------------------------------------

    private void installKeyBindings() {
        InputMap inputMap = mainPanel.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = mainPanel.getActionMap();
        InputSystem input = server.input();

        bindDirection(inputMap, actionMap, input, "up", KeyEvent.VK_UP, Direction.UP);
        bindDirection(inputMap, actionMap, input, "down", KeyEvent.VK_DOWN, Direction.DOWN);
        bindDirection(inputMap, actionMap, input, "left", KeyEvent.VK_LEFT, Direction.LEFT);
        bindDirection(inputMap, actionMap, input, "right", KeyEvent.VK_RIGHT, Direction.RIGHT);

        bind(inputMap, actionMap, "fire-on", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false),
                () -> input.setFire(true));
        bind(inputMap, actionMap, "fire-off", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true),
                () -> input.setFire(false));

        bind(inputMap, actionMap, "vote-yes", KeyStroke.getKeyStroke(KeyEvent.VK_Y, 0, false),
                server::hostVoteYes);
        bind(inputMap, actionMap, "vote-no", KeyStroke.getKeyStroke(KeyEvent.VK_N, 0, false),
                server::hostVoteNo);

        bind(inputMap, actionMap, "chat-enter", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false),
                this::sendCurrentChat);

        mainPanel.setFocusable(true);
        mainPanel.requestFocusInWindow();
    }

    private void bindDirection(InputMap inputMap, ActionMap actionMap, InputSystem input,
                               String name, int keyCode, Direction direction) {
        bind(inputMap, actionMap, name + "-on", KeyStroke.getKeyStroke(keyCode, 0, false),
                () -> input.press(direction));
        bind(inputMap, actionMap, name + "-off", KeyStroke.getKeyStroke(keyCode, 0, true),
                () -> input.release(direction));
    }

    private void bind(InputMap inputMap, ActionMap actionMap, String name, KeyStroke stroke, Runnable action) {
        inputMap.put(stroke, name);
        actionMap.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ------------------------------------------------------------------
    // 外部交互
    // ------------------------------------------------------------------

    public void acceptSnapshot(Snapshot snapshot) {
        mainPanel.accept(snapshot);
        boolean connected = snapshot.phase() != Phase.WAITING;
        if (connected != chatEnabled) {
            chatEnabled = connected;
            SwingUtilities.invokeLater(() -> messageField.setEnabled(connected));
        }
    }

    private void sendCurrentChat() {
        if (!messageField.isEnabled()) {
            return;
        }
        String text = messageField.getText();
        if (text == null || text.isEmpty()) {
            server.context().messages().add("对话内容不能为空");
            return;
        }
        server.sendChat(text);
        messageField.setText("");
        mainPanel.requestFocusInWindow();
    }

    /**
     * 退出确认：弹框确认后优雅停机。
     */
    public void confirmAndExit() {
        int choice = JOptionPane.showConfirmDialog(this, "确定退出坦克大战主机端吗？", "退出确认",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            server.shutdown();
            dispose();
            System.exit(0);
        }
    }
}
