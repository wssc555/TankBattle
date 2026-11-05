package com.tankbattle.client.input;

/**
 * UiActions 对窗口的最小依赖（避免 input 包反向依赖 render 包）。
 */
public interface UiHost {

    String ipText();

    String chatText();

    void clearChat();

    /**
     * 确认后优雅停机；{@code confirm} 为 true 时先弹确认框。
     */
    void shutdown(boolean confirm);
}
