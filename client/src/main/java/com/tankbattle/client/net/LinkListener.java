package com.tankbattle.client.net;

/**
 * 连接层 → UI 的单一回调出口，禁止 UI 轮询。
 * <p>
 * 实现方负责切回 EDT；连接层内部线程绝直接触碰 Swing。
 */
public interface LinkListener {

    void onStateChanged(LinkState state);

    /**
     * 能力协商成功（收到 {@code v2;}）。
     */
    void onCapabilityAck();

    /**
     * 需要展示给玩家的提示（连接中/断开/超时等）。
     */
    void onNotice(String text);
}
