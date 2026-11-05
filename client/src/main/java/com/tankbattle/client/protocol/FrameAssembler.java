package com.tankbattle.client.protocol;

import com.tankbattle.client.state.FlowState;
import com.tankbattle.client.state.HudState;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.Phase;
import com.tankbattle.client.state.RenderState;
import com.tankbattle.client.state.SceneState;
import com.tankbattle.client.state.views.BulletView;
import com.tankbattle.client.state.views.EffectView;
import com.tankbattle.client.state.views.SpriteView;
import com.tankbattle.client.util.MessageKind;
import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.level.Cell;
import com.tankbattle.common.level.LevelRepository;
import com.tankbattle.common.protocol.S2c;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 帧组装器：一批 {@link S2c} → 一个不可变 {@link RenderState}。
 * <p>
 * 纯逻辑、零 Swing 依赖；sealed switch 穷尽分发，无 default —— 新增协议消息时编译器强制补全。
 * 实例只由组装线程调用（单写者），{@link #noteClientVote()} 例外，用原子标志跨线程投放意图。
 */
public final class FrameAssembler {

    private final MessageLog messages;
    private final Runnable onCapabilityAck;
    private final AtomicBoolean clientVoteRequested = new AtomicBoolean();

    private SceneState scene = SceneState.EMPTY;
    private HudState hud = HudState.EMPTY;
    private FlowState flow = FlowState.EMPTY;
    private long localSeq;

    public FrameAssembler(MessageLog messages, Runnable onCapabilityAck) {
        this.messages = messages;
        this.onCapabilityAck = onCapabilityAck;
    }

    /**
     * 处理一帧（一条 TCP 行）。batch 不可为 null（解码器恒返回列表）。
     *
     * @return 新快照；动态列表每帧重建，场景仅事件帧替换
     */
    public RenderState apply(List<S2c> batch) {
        long now = System.nanoTime();
        flow = flow.nextFrame();

        if (clientVoteRequested.getAndSet(false)) {
            flow = flow.withClientVote(true);
        }

        long seq = 0L;
        // 本批次内收到过权威地图（M）时，LevelLoad 的本地回退地图不得覆盖之
        boolean authoritativeMap = false;
        List<SpriteView> sprites = new ArrayList<>();
        List<BulletView> bullets = new ArrayList<>();
        List<EffectView> effects = new ArrayList<>();

        for (S2c message : batch) {
            switch (message) {
                case S2c.LevelLoad levelMsg -> {
                    // 本地回退地图；同行后续指令继续处理。
                    // 若本批次已含权威地图（M 在前），跳过回退仅做流程重置
                    boolean firstLoad = flow.phase() == Phase.WAITING;
                    if (!authoritativeMap) {
                        int level = LevelRepository.normalize(levelMsg.level());
                        scene = SceneState.fromLevel(LevelRepository.get(level));
                    }
                    flow = flow.levelLoaded();
                    if (firstLoad) {
                        messages.addMessage(MessageKind.LOADED.text());
                    }
                }
                case S2c.MapData mapMsg -> {
                    scene = SceneState.fromMapData(mapMsg.level(), mapMsg.cells400());
                    authoritativeMap = true;
                }
                case S2c.FrameSeq seqMsg -> seq = seqMsg.seq();
                case S2c.Sprite sprite -> sprites.add(new SpriteView(sprite.x(), sprite.y(), sprite.textureIndex()));
                case S2c.Bullet bullet -> bullets.add(new BulletView(bullet.x(), bullet.y(), bullet.direction()));
                case S2c.Bomb bomb -> effects.add(new EffectView.Bomb(bomb.x(), bomb.y(), bomb.size()));
                case S2c.Shield shield -> effects.add(new EffectView.Shield(shield.x(), shield.y()));
                case S2c.BrickWallState brick ->
                        scene = scene.withBrick(Cell.key(brick.x(), brick.y()), brick.destroyedBits());
                case S2c.SteelWallState steel ->
                        scene = scene.withSteel(Cell.key(steel.x(), steel.y()), steel.destroyedBits());
                case S2c.BaseState base -> scene = scene.withBase(base.x(), base.y(), base.destroyed());
                case S2c.PanelInfo panel -> hud = new HudState(
                        panel.enemyLeft(), panel.level(), panel.p1Life(), panel.p1Score(),
                        panel.p2Life(), panel.p2Score());
                case S2c.WinAnim win -> flow = flow.withWinningCount(win.winningCount());
                case S2c.Pause pause -> {
                    boolean changed = flow.paused() != pause.paused();
                    flow = flow.withPaused(pause.paused());
                    if (changed) {
                        messages.addMessage(pause.paused()
                                ? MessageKind.PAUSE_BY_HOST.text()
                                : MessageKind.RESUME_BY_HOST.text());
                    }
                }
                case S2c.VoteServerYes vote -> {
                    boolean first = !flow.serverVote();
                    flow = flow.withServerVote(true);
                    if (first && flow.clientVote()) {
                        messages.addMessage(MessageKind.VOTE_RESTART.text());
                    }
                }
                case S2c.GameOver over -> {
                    if (!flow.gameOver()) {
                        messages.addMessage(MessageKind.GAME_OVER.text());
                        flow = flow.enterGameOver();
                    }
                }
                case S2c.Chat chat -> messages.addMessage(MessageKind.HOST_SAYS.text() + chat.text());
                case S2c.CapabilityAck ack -> onCapabilityAck.run();
            }
        }

        if (flow.gameFlow() % GameConfig.MESSAGE_EXPIRE_FRAMES == 0) {
            messages.pollOldest();
        }

        return new RenderState(
                seq > 0 ? seq : ++localSeq,
                now,
                scene,
                hud,
                flow,
                sprites,
                bullets,
                effects,
                messages.snapshot());
    }

    /**
     * UI 投票意图（EDT）→ 下一帧组装时置位 clientVote。
     */
    public void noteClientVote() {
        clientVoteRequested.set(true);
    }

    /**
     * 重连时清空场景/HUD/流程，保留消息（提示"主机端退出了"）。
     */
    public void reset() {
        scene = SceneState.EMPTY;
        hud = HudState.EMPTY;
        flow = FlowState.EMPTY;
        localSeq = 0L;
        clientVoteRequested.set(false);
    }
}
