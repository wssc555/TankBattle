package com.tankbattle.client.input;

import com.tankbattle.client.config.ClientConfig;
import com.tankbattle.client.net.ConnectionManager;
import com.tankbattle.client.net.LinkState;
import com.tankbattle.client.state.FlowState;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.Phase;
import com.tankbattle.client.state.StateStore;
import com.tankbattle.client.util.MessageKind;
import com.tankbattle.common.protocol.ChatSanitizer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按钮 / 快捷键动作。
 * <p>
 * 所有方法在 EDT 调用；聊天与投票只入队（{@code ConnectionManager.pendingEvents}），绝不直接写 socket。
 * 暂停由服务端 {@code x0/x1} 确认后生效，本地不抢先翻转。
 */
public final class UiActions {

    private final UiHost host;
    private final ClientConfig cfg;
    private final ConnectionManager link;
    private final StateStore store;
    private final MessageLog messages;

    private final AtomicInteger chatHintCount = new AtomicInteger(1);

    public UiActions(UiHost host, ClientConfig cfg, ConnectionManager link,
                     StateStore store, MessageLog messages) {
        this.host = host;
        this.cfg = cfg;
        this.link = link;
        this.store = store;
        this.messages = messages;
    }

    /**
     * 连接按钮：读 IP 框 → 连接状态机。
     */
    public void connect() {
        String hostText = host.ipText();
        if (hostText == null || hostText.isBlank()) {
            messages.addMessage(MessageKind.EMPTY_HOST.text());
            return;
        }
        link.connect(hostText.strip(), cfg.port());
    }

    /**
     * 暂停/继续：边沿请求，不发本地状态翻转；仅 RUNNING/PAUSED 阶段有效。
     */
    public void togglePause() {
        if (link.state() != LinkState.CONNECTED) {
            return;
        }
        FlowState flow = store.current().flow();
        Phase phase = flow.phase();
        if (flow.gameOver() || (phase != Phase.RUNNING && phase != Phase.PAUSED)) {
            return;
        }
        messages.addMessage(flow.paused()
                ? MessageKind.RESUME_BY_CLIENT.text()
                : MessageKind.PAUSE_BY_CLIENT.text());
        link.togglePause();
    }

    /**
     * 帮助：显示操作说明。
     */
    public void help() {
        messages.addMessage(MessageKind.HELP_TITLE.text());
        messages.addMessage(MessageKind.HELP_FIRE.text());
        messages.addMessage(MessageKind.HELP_FOCUS.text());
        messages.addMessage(MessageKind.HELP_CHAT.text());
        messages.addMessage(MessageKind.HELP_FOOTER.text());
    }

    /**
     * 退出按钮：确认后优雅停机。
     */
    public void exit() {
        host.shutdown(true);
    }

    /**
     * 发送聊天：净化后入队。
     */
    public void sendChat() {
        if (link.state() != LinkState.CONNECTED) {
            messages.addMessage(MessageKind.NOT_CONNECTED_CHAT.text());
            return;
        }
        String text = ChatSanitizer.sanitize(host.chatText());
        if (text.isEmpty()) {
            messages.addMessage(MessageKind.EMPTY_CHAT.text());
            return;
        }
        messages.addMessage(MessageKind.CLIENT_SAYS.text() + text);
        link.sendChat(text);
        host.clearChat();
    }

    /**
     * Y：每局只投一次；以 {@code FlowState.clientVote} 为准，随 {@code L} 开局复位，
     * 保证后续对局可再次投票。
     * <p>
     * {@code clientVote} 在 {@code noteClientVote} 后下一帧组装才置位，同帧内连按理论上可能重复发
     * {@code j;}；服务端对重复投票幂等（布尔置位），无副作用。
     */
    public void voteYes() {
        FlowState flow = store.current().flow();
        if (flow.phase() != Phase.GAME_OVER_VOTE) {
            return;
        }
        if (flow.clientVote()) {
            return;
        }
        messages.addMessage(MessageKind.WAIT_HOST.text());
        link.noteClientVote();
        link.voteYes();
    }

    /**
     * N：结束投票阶段拒绝再玩 → 确认后优雅停机。
     */
    public void voteNo() {
        if (store.current().flow().phase() != Phase.GAME_OVER_VOTE) {
            return;
        }
        host.shutdown(true);
    }

    /**
     * 聊天框首次按键的两条 Tab 提示。
     */
    public void onChatTyping() {
        if (chatHintCount.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            messages.addMessage(MessageKind.TAB_HINT_1.text());
            messages.addMessage(MessageKind.TAB_HINT_2.text());
        }
    }
}
