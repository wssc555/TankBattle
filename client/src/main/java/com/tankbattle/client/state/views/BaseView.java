package com.tankbattle.client.state.views;

/**
 * 基地一帧快照（{@code b} 指令）。
 */
public record BaseView(int x, int y, boolean destroyed) {

    public static final BaseView EMPTY = new BaseView(0, 0, false);

    public BaseView destroyed(boolean value) {
        return new BaseView(x, y, value);
    }
}
