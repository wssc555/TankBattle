package com.tankbattle.client.input;

import com.tankbattle.common.model.Direction;
import org.junit.jupiter.api.Test;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 方向互斥开关、失焦清零、序号单调、KeyStroke 绑定存在。
 */
class InputBindingsTest {

    @Test
    void exclusiveModeKeepsSingleDirection() {
        InputState state = new InputState(true);
        state.press(Direction.UP);
        state.press(Direction.RIGHT);
        InputState.InputFrame frame = state.snapshot();
        assertFalse(frame.up());
        assertTrue(frame.right());
        assertFalse(frame.left());
        assertFalse(frame.down());
    }

    @Test
    void independentModeAllowsDiagonalInput() {
        InputState state = new InputState(false);
        state.press(Direction.UP);
        state.press(Direction.RIGHT);
        InputState.InputFrame frame = state.snapshot();
        assertTrue(frame.up());
        assertTrue(frame.right());
    }

    @Test
    void releaseAndClearResetIntents() {
        InputState state = new InputState(false);
        state.press(Direction.LEFT);
        state.pressFire();
        state.release(Direction.LEFT);
        assertFalse(state.snapshot().left());
        assertTrue(state.snapshot().fire());

        state.clear();
        InputState.InputFrame frame = state.snapshot();
        assertFalse(frame.up() || frame.down() || frame.left() || frame.right() || frame.fire());
    }

    @Test
    void sequenceIsMonotonicAcrossMutations() {
        InputState state = new InputState(true);
        long previous = state.seq();
        for (int i = 0; i < 20; i++) {
            state.press(Direction.UP);
            long afterPress = state.seq();
            assertTrue(afterPress > previous);
            state.release(Direction.UP);
            assertTrue(state.seq() > afterPress);
            previous = state.seq();
        }
    }

    @Test
    void installsBindingsForAllGameKeys() {
        JPanel panel = new JPanel();
        InputState state = new InputState(true);
        UiActions actions = new UiActions(null, null, null, null, null);
        InputBindings bindings = new InputBindings(panel, state, actions);
        bindings.install();
        bindings.installGameKeys(panel);

        InputMap windowMap = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        int[] directionKeys = {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT};
        for (int key : directionKeys) {
            assertNotNull(windowMap.get(KeyStroke.getKeyStroke(key, 0, false)), "缺少方向键绑定: " + key);
        }
        assertNotNull(windowMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true)));

        InputMap focusedMap = panel.getInputMap(JComponent.WHEN_FOCUSED);
        int[] gameKeys = {KeyEvent.VK_S, KeyEvent.VK_Y, KeyEvent.VK_N, KeyEvent.VK_ENTER};
        for (int key : gameKeys) {
            assertNotNull(focusedMap.get(KeyStroke.getKeyStroke(key, 0, false)), "缺少游戏键绑定: " + key);
        }
    }

    @Test
    void installsDirectionsOnChatFieldWithoutSwallowingLetters() {
        JTextField field = new JTextField();
        InputState state = new InputState(true);
        UiActions actions = new UiActions(null, null, null, null, null);
        InputBindings bindings = new InputBindings(field, state, actions);
        bindings.installDirectionsOnly(field);

        InputMap map = field.getInputMap(JComponent.WHEN_FOCUSED);
        assertNotNull(map.get(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false)));
    }

    /**
     * 字母键的 Shift 变体不得被任何游戏键绑定吞掉。
     */
    @Test
    void chatFieldDoesNotSwallowShiftedLetters() {
        JTextField field = new JTextField();
        InputState state = new InputState(true);
        UiActions actions = new UiActions(null, null, null, null, null);
        InputBindings bindings = new InputBindings(field, state, actions);
        bindings.installDirectionsOnly(field);

        InputMap map = field.getInputMap(JComponent.WHEN_FOCUSED);
        assertNull(map.get(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.SHIFT_DOWN_MASK, false)),
                "聊天框内大写 S 必须正常输入");
        assertNull(map.get(KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.SHIFT_DOWN_MASK, false)));
        assertNull(map.get(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.SHIFT_DOWN_MASK, false)));
    }
}
