package com.tankbattle.client.input;

import com.tankbattle.common.model.Direction;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Key Bindings 输入绑定。
 * <p>
 * {@code WHEN_IN_FOCUSED_WINDOW} 消除焦点依赖：Tab 切到聊天框后方向键仍控制坦克。
 * S/Y/N 按 {@link KeyStroke} 匹配（CapsLock 不敏感），但在文本框内不触发，避免聊天内容误触投票/开火。
 */
public final class InputBindings {

    private static final String PREFIX_PRESS = "press-";
    private static final String PREFIX_RELEASE = "release-";

    private final JComponent target;
    private final InputState input;
    private final UiActions actions;

    public InputBindings(JComponent target, InputState input, UiActions actions) {
        this.target = target;
        this.input = input;
        this.actions = actions;
    }

    /**
     * 方向键：绑定在窗口级 InputMap，Tab 切到聊天框后仍可控制坦克。
     */
    public void install() {
        InputMap inputMap = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = target.getActionMap();
        bindDirection(inputMap, actionMap, "up", KeyEvent.VK_UP, Direction.UP);
        bindDirection(inputMap, actionMap, "down", KeyEvent.VK_DOWN, Direction.DOWN);
        bindDirection(inputMap, actionMap, "left", KeyEvent.VK_LEFT, Direction.LEFT);
        bindDirection(inputMap, actionMap, "right", KeyEvent.VK_RIGHT, Direction.RIGHT);
    }

    /**
     * 游戏键（S/Y/N/Enter）：只绑定在画布自身，聊天框获得焦点时正常输入文字。
     */
    public void installGameKeys(JComponent component) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = component.getActionMap();

        bind(inputMap, actionMap, "fire", KeyEvent.VK_S,
                input::pressFire, input::releaseFire);

        bind(inputMap, actionMap, "vote-yes", KeyEvent.VK_Y, actions::voteYes);
        bind(inputMap, actionMap, "vote-no", KeyEvent.VK_N, actions::voteNo);
        // 聊天框自身的 ActionListener 负责回车发送；此处处理画布焦点下的回车
        bind(inputMap, actionMap, "send-chat", KeyEvent.VK_ENTER, actions::sendChat);
    }

    /**
     * 只安装方向键。用于聊天输入框：保证 Tab 过去后方向键仍控制坦克，
     * 同时保留文本框对 S/Y/N 的正常输入。
     */
    public void installDirectionsOnly(JComponent component) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = component.getActionMap();
        bindDirection(inputMap, actionMap, "up", KeyEvent.VK_UP, Direction.UP);
        bindDirection(inputMap, actionMap, "down", KeyEvent.VK_DOWN, Direction.DOWN);
        bindDirection(inputMap, actionMap, "left", KeyEvent.VK_LEFT, Direction.LEFT);
        bindDirection(inputMap, actionMap, "right", KeyEvent.VK_RIGHT, Direction.RIGHT);
    }

    private void bindDirection(InputMap inputMap, ActionMap actionMap, String name,
                               int keyCode, Direction direction) {
        bind(inputMap, actionMap, name, keyCode,
                () -> input.press(direction),
                () -> input.release(direction));
    }

    private void bind(InputMap inputMap, ActionMap actionMap, String name, int keyCode,
                      Runnable onPress, Runnable onRelease) {
        String pressKey = PREFIX_PRESS + name;
        String releaseKey = PREFIX_RELEASE + name;
        inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, false), pressKey);
        inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, true), releaseKey);
        actionMap.put(pressKey, action(pressKey, onPress));
        actionMap.put(releaseKey, action(releaseKey, onRelease));
    }

    private void bind(InputMap inputMap, ActionMap actionMap, String name, int keyCode,
                      Runnable onPress) {
        String pressKey = PREFIX_PRESS + name;
        inputMap.put(KeyStroke.getKeyStroke(keyCode, 0, false), pressKey);
        actionMap.put(pressKey, action(pressKey, onPress));
    }

    private static AbstractAction action(String name, Runnable body) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.run();
            }
        };
    }
}
