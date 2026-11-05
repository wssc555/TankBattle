package com.tankbattle.client.state.views;

/**
 * 普通实体一帧快照（{@code n} 指令）。
 * <p>
 * 生命周期由状态决定：下一帧快照不含它即视为消失，渲染只读、不删对象。
 */
public record SpriteView(int x, int y, int textureIndex) {
}
