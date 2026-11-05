package com.tankbattle.server.core;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.protocol.S2c;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.entity.Base;
import com.tankbattle.server.entity.Bomb;
import com.tankbattle.server.entity.Grass;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.PowerUp;
import com.tankbattle.server.entity.TankIntent;
import com.tankbattle.server.entity.Vec;
import com.tankbattle.server.entity.Wall;
import com.tankbattle.server.system.PowerUpSystem;
import com.tankbattle.server.system.SpatialGrid;
import com.tankbattle.server.util.MessageLog;
import com.tankbattle.server.util.ObjectPool;

import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 一局的全部可变状态。
 * <p>
 * 只允许游戏线程写入；网络线程仅通过 {@link #postSessionEvent(SessionEvent)} 投递事件。
 */
public final class GameContext {

    private final ServerConfig.Runtime settings;
    private final Random random;
    private final MessageLog messages = new MessageLog();
    private final LevelState level = new LevelState();
    private final SpatialGrid grid = new SpatialGrid();
    private final ObjectPool<Bomb> bombPool;

    private final List<Actor> actors = new ArrayList<>(512);
    private final List<Actor> frameActors = new ArrayList<>(512);
    private final List<Grass> decor = new ArrayList<>(64);
    private final List<Actor> scratch = new ArrayList<>(512);
    private final Deque<GameEvent> events = new ArrayDeque<>(64);
    private final Queue<SessionEvent> sessionEvents = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();

    private Phase phase = Phase.WAITING;
    private int gameFlow;
    private PlayerTank p1;
    private PlayerTank p2;
    private Base base;
    private TankIntent p2Intent = TankIntent.NONE;
    private boolean hostVoteYes;
    private boolean clientVoteYes;
    private int voteStartFlow = -1;
    private boolean gameOverRequested;
    private boolean exitRequested;
    private boolean reconnectPending;
    private int freezeStartFlow = -1;
    private int freezeTicks;
    private int lastMessageExpireFlow = -1;

    public GameContext(ServerConfig.Runtime settings, long seed) {
        this.settings = settings;
        this.random = new Random(seed);
        this.bombPool = new ObjectPool<>(ServerConfig.BOMB_POOL_SIZE,
                () -> new Bomb(new Vec(0, 0), S2c.BombSize.SMALL));
    }

    // ------------------------------------------------------------------
    // 基础访问器
    // ------------------------------------------------------------------

    public ServerConfig.Runtime settings() {
        return settings;
    }

    public Random random() {
        return random;
    }

    public MessageLog messages() {
        return messages;
    }

    public LevelState level() {
        return level;
    }

    public SpatialGrid grid() {
        return grid;
    }

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int gameFlow() {
        return gameFlow;
    }

    public void advanceGameFlow() {
        this.gameFlow++;
    }

    public void resetGameFlow() {
        this.gameFlow = 0;
        this.lastMessageExpireFlow = -1;
        // 不变量：gameFlow 归零必须同步失效所有以 freezeStartFlow 为锚的计时。
        // 否则 enemiesFrozen() 判据 freezeTicks > gameFlow - freezeStartFlow 因右端为负而恒真，
        // 重开一局后敌人可能被冻结数千帧。
        clearFreeze();
    }

    public PlayerTank p1() {
        return p1;
    }

    public PlayerTank p2() {
        return p2;
    }

    public Base base() {
        return base;
    }

    public List<Actor> actors() {
        return actors;
    }

    public List<Grass> decor() {
        return decor;
    }

    public TankIntent p2Intent() {
        return p2Intent;
    }

    public void setP2Intent(TankIntent intent) {
        this.p2Intent = intent;
    }

    // ------------------------------------------------------------------
    // 实体增删与索引
    // ------------------------------------------------------------------

    public void addActor(Actor actor) {
        actors.add(actor);
        grid.track(actor);
    }

    /**
     * 标记移除；真正的清理由 {@link #purge()} 在本帧末尾统一完成。
     */
    public void removeActor(Actor actor) {
        if (actor == null) {
            return;
        }
        actor.markDestroyed();
    }

    /**
     * 本帧迭代用的快照；本帧产生的实体从下一帧开始参与推进。
     */
    public void beginFrame() {
        frameActors.clear();
        frameActors.addAll(actors);
    }

    public List<Actor> frameActors() {
        return frameActors;
    }

    /**
     * 帧末清理：移除已毁实体，爆炸对象归还对象池。
     */
    public void purge() {
        for (int i = actors.size() - 1; i >= 0; i--) {
            Actor actor = actors.get(i);
            if (actor.destroyed()) {
                actors.remove(i);
                grid.untrack(actor);
                if (actor instanceof Bomb bomb) {
                    bombPool.release(bomb);
                }
            }
        }
    }

    public void rebuildStaticIndex() {
        scratch.clear();
        for (Actor actor : actors) {
            if (actor instanceof Wall) {
                scratch.add(actor);
            }
        }
        grid.rebuildStatic(scratch);
    }

    public void rebuildDynamicIndex() {
        scratch.clear();
        for (Actor actor : actors) {
            if (!(actor instanceof Wall)) {
                scratch.add(actor);
            }
        }
        grid.rebuildDynamic(scratch);
    }

    public List<Actor> query(Rectangle area, Actor exclude) {
        return grid.query(area, exclude);
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    public void emit(GameEvent event) {
        events.addLast(event);
    }

    public List<GameEvent> drainEvents() {
        List<GameEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    public void postSessionEvent(SessionEvent event) {
        sessionEvents.add(event);
    }

    public Queue<SessionEvent> sessionEvents() {
        return sessionEvents;
    }

    /**
     * EDT → 游戏线程的命令投递（暂停、投票、聊天等）。
     */
    public void post(Runnable command) {
        commands.add(command);
    }

    /**
     * 在游戏线程内执行所有待处理命令。
     */
    public void runPendingCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            command.run();
        }
    }

    // ------------------------------------------------------------------
    // 世界构建与调整
    // ------------------------------------------------------------------

    public void installPlayers(PlayerTank first, PlayerTank second) {
        this.p1 = first;
        this.p2 = second;
    }

    public void installBase(Base newBase) {
        this.base = newBase;
    }

    public void clearWorld() {
        actors.clear();
        decor.clear();
        grid.clear();
        p1 = null;
        p2 = null;
        base = null;
        freezeTicks = 0;
        freezeStartFlow = -1;
        gameOverRequested = false;
    }

    // ------------------------------------------------------------------
    // 爆炸与道具
    // ------------------------------------------------------------------

    public void spawnExplosion(S2c.BombSize size, Vec pos) {
        Bomb bomb = bombPool.acquire();
        bomb.reset(size, pos);
        // 统一走 addActor（track 空边界的爆炸无碰撞副作用），与其它生成路径保持一致，
        // 避免绕过索引维护形成隐性约定。
        addActor(bomb);
        emit(new GameEvent.ExplosionSpawned(pos.x(), pos.y(), size));
    }

    public void spawnPowerUp() {
        PowerUpSystem.spawn(this);
    }

    public void collect(PlayerTank tank, PowerUp powerUp) {
        PowerUpSystem.apply(this, tank, powerUp);
    }

    // ------------------------------------------------------------------
    // 冻结状态
    // ------------------------------------------------------------------

    public void freezeEnemies(int frames) {
        this.freezeTicks = frames;
        this.freezeStartFlow = gameFlow;
    }

    public void clearFreeze() {
        this.freezeTicks = 0;
        this.freezeStartFlow = -1;
    }

    public boolean enemiesFrozen() {
        return freezeTicks > gameFlow - freezeStartFlow;
    }

    public int freezeTicks() {
        return freezeTicks;
    }

    // ------------------------------------------------------------------
    // 流程辅助
    // ------------------------------------------------------------------

    public void requestGameOver() {
        this.gameOverRequested = true;
    }

    public boolean consumeGameOverRequest() {
        boolean requested = gameOverRequested;
        gameOverRequested = false;
        return requested;
    }

    /**
     * 请求优雅停机，由 GUI 弹确认框后处理。
     */
    public void requestExit() {
        this.exitRequested = true;
    }

    public boolean consumeExitRequest() {
        boolean requested = exitRequested;
        exitRequested = false;
        return requested;
    }

    /**
     * 是否处于"等待客户端重连"状态（保留分数与关卡）。
     */
    public boolean reconnectPending() {
        return reconnectPending;
    }

    public void setReconnectPending(boolean value) {
        this.reconnectPending = value;
    }

    public boolean hostVoteYes() {
        return hostVoteYes;
    }

    public void setHostVoteYes(boolean value) {
        this.hostVoteYes = value;
    }

    public boolean clientVoteYes() {
        return clientVoteYes;
    }

    public void setClientVoteYes(boolean value) {
        this.clientVoteYes = value;
    }

    public int voteStartFlow() {
        return voteStartFlow;
    }

    public void setVoteStartFlow(int value) {
        this.voteStartFlow = value;
    }

    public void clearVotes() {
        hostVoteYes = false;
        clientVoteYes = false;
        voteStartFlow = -1;
    }

    /**
     * 是否应在当前帧弹出一条消息。仅在 {@code gameFlow} 为 300 的整数倍时成立，
     * 且同一帧号只触发一次（暂停时 gameFlow 不增，避免一次暂停把队列排空）。
     */
    public boolean shouldExpireMessage() {
        if (gameFlow % GameConfig.MESSAGE_EXPIRE_FRAMES != 0) {
            return false;
        }
        if (gameFlow == lastMessageExpireFlow) {
            return false;
        }
        lastMessageExpireFlow = gameFlow;
        return true;
    }
}
