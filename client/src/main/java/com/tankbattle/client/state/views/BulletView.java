package com.tankbattle.client.state.views;

import com.tankbattle.common.model.Direction;

/**
 * 子弹一帧快照（{@code t} 指令）。
 */
public record BulletView(int x, int y, Direction direction) {
}
