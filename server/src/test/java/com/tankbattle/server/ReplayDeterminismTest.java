package com.tankbattle.server;

import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.entity.Actor;
import com.tankbattle.server.system.FlowSystem;
import com.tankbattle.server.system.SpawnSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 固定种子确定性（同种子 → 逐帧状态轨迹完全一致）。
 * <p>
 * 校验模拟无隐藏随机源、无迭代序泄漏：
 * 同种子跑 900 帧，两次运行的实体状态签名逐帧一致；异种子则发散。
 * 帧循环复刻 {@code TickTask} 的推进结构（FlowSystem → Spawn → 实体推进 → purge），
 * 不含网络与 GUI。
 */
class ReplayDeterminismTest {

    private static final int FRAMES = 900;

    private long run(long seed) {
        GameContext ctx = new GameContext(TestContexts.runtime(), seed);
        FlowSystem.startGame(ctx);
        long signature = 17;
        for (int frame = 0; frame < FRAMES; frame++) {
            FlowSystem.tick(ctx);
            if (ctx.phase().logicRuns()) {
                SpawnSystem.tick(ctx);
                ctx.rebuildDynamicIndex();
                ctx.beginFrame();
                for (Actor actor : ctx.frameActors()) {
                    if (!actor.destroyed()) {
                        actor.tick(ctx);
                    }
                }
            }
            ctx.purge();
            signature = signature * 31 + ctx.actors().size();
            for (Actor actor : ctx.actors()) {
                signature = signature * 31 + actor.border().x;
                signature = signature * 31 + actor.border().y;
                signature = signature * 31 + actor.border().width;
            }
        }
        return signature;
    }

    @Test
    void sameSeedReplaysIdentically() {
        assertEquals(run(TestContexts.SEED), run(TestContexts.SEED),
                "同种子 900 帧状态轨迹必须逐帧一致（T31 回放前提）");
    }

    @Test
    void differentSeedDiverges() {
        assertNotEquals(run(TestContexts.SEED), run(42),
                "异种子应产生不同轨迹（证明随机源确实经种子注入）");
    }
}
