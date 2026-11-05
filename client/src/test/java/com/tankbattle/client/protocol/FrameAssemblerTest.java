package com.tankbattle.client.protocol;

import com.tankbattle.client.state.FlowState;
import com.tankbattle.client.state.MessageLog;
import com.tankbattle.client.state.Phase;
import com.tankbattle.client.state.RenderState;
import com.tankbattle.common.level.Cell;
import com.tankbattle.common.level.LevelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 同一行多指令生效、M 覆盖、w/s 定位、快照不可变。
 */
class FrameAssemblerTest {

    private MessageLog messages;
    private FrameAssembler assembler;

    @BeforeEach
    void setUp() {
        messages = new MessageLog();
        assembler = new FrameAssembler(messages, () -> {
        });
    }

    /**
     * 同一行的 p/g 必须生效。
     */
    @Test
    void appliesLevelPanelAndAnimationInOneFrame() {
        RenderState state = apply("L1;p2,1,3,0,3,0;g77;");

        assertEquals(Phase.RUNNING, state.flow().phase());
        assertEquals(77, state.flow().winningCount());
        assertEquals(2, state.hud().enemyLeft());
        assertEquals(1, state.hud().level());
        assertEquals(1, state.scene().level());
        assertEquals(0, state.scene().version());
        // 84 块地图砖 + 4 面基地护甲
        assertEquals(88, state.scene().bricks().size());
    }

    @Test
    void updatesOnlyTargetWallCell() {
        RenderState before = apply("L1;p1,1,3,0,3,0;g0;");
        int key = Cell.index(2, 2);
        int x = Cell.x(key);
        int y = Cell.y(key);
        assertTrue(before.scene().bricks().containsKey(key));

        RenderState after = apply("w" + x + "," + y + ",1111111111111111;");

        assertEquals(0xFFFF, after.scene().bricks().get(key).destroyedBits());
        assertEquals(before.scene().version() + 1, after.scene().version());
        // 其他格子与共享集合未被触碰（结构共享）
        assertEquals(before.scene().grass(), after.scene().grass());
        assertEquals(before.scene().bricks().size(), after.scene().bricks().size());
        assertEquals(0, before.scene().bricks().get(key).destroyedBits());
    }

    @Test
    void mapDataOverridesLocalFallback() {
        apply("L1;p1,1,3,0,3,0;g0;");
        RenderState state = apply("M1:" + ".".repeat(400) + ";");

        assertEquals(4, state.scene().bricks().size(), "仅保留 4 面基地护甲");
        assertTrue(state.scene().steels().isEmpty());
        assertTrue(state.scene().rivers().isEmpty());
        assertTrue(state.scene().grass().isEmpty());
    }

    /**
     * M 在前、L 在后时，权威地图不得被本地默认地图覆盖（仅做流程重置）。
     */
    @Test
    void levelLoadAfterMapDataKeepsAuthoritativeMap() {
        RenderState state = apply("M1:" + ".".repeat(400) + ";L1;p1,1,3,0,3,0;g0;");

        assertEquals(Phase.RUNNING, state.flow().phase(), "L 仍要驱动流程重置");
        assertEquals(4, state.scene().bricks().size(), "权威地图保留，未回退到本地 88 砖");
        assertTrue(state.scene().steels().isEmpty());
        assertTrue(state.scene().grass().isEmpty());
    }

    /**
     * s 钢墙指令只改目标格，其余格不受影响。
     */
    @Test
    void steelWallStateOnlyAffectsTargetCell() {
        RenderState before = apply("L1;p1,1,3,0,3,0;g0;");
        assertTrue(before.scene().steels().size() > 0, "L1 应含钢墙");
        int key = before.scene().steels().keySet().iterator().next();
        int x = Cell.x(key);
        int y = Cell.y(key);

        RenderState after = apply("s" + x + "," + y + ",1111;");

        assertEquals(0xF, after.scene().steels().get(key).destroyedBits());
        assertEquals(before.scene().steels().size(), after.scene().steels().size());
        assertEquals(before.scene().bricks(), after.scene().bricks(), "砖墙集合不受 s 指令影响");
        assertEquals(before.scene().version() + 1, after.scene().version());
    }

    @Test
    void frameSeqOverridesLocalCounter() {
        RenderState state = apply("d42;");
        assertEquals(42, state.seq());
    }

    @Test
    void publishedListsAreImmutable() {
        RenderState state = apply("n1,2,3;t4,5,0;o6,7,big;i8,9;");
        assertEquals(4, state.effects().size() + state.bullets().size() + state.sprites().size());
        assertThrows(UnsupportedOperationException.class, () -> state.sprites().add(null));
        assertThrows(UnsupportedOperationException.class, () -> state.messages().add("x"));
    }

    @Test
    void emptyFrameStillAdvancesGameFlow() {
        RenderState first = assembler.apply(List.of());
        RenderState second = assembler.apply(List.of());
        assertEquals(1, first.flow().gameFlow());
        assertEquals(2, second.flow().gameFlow());
        assertNotEquals(first.seq(), second.seq());
    }

    @Test
    void levelLoadResetsFlowAndKeepsSceneOfRequestedLevel() {
        apply("L1;g200;a;");
        RenderState state = apply("L2;");

        FlowState flow = state.flow();
        assertEquals(Phase.RUNNING, flow.phase());
        assertEquals(0, flow.winningCount());
        assertTrue(!flow.gameOver() && !flow.serverVote() && !flow.clientVote());
        assertEquals(2, state.scene().level());
        assertEquals(LevelRepository.get(2).grassCells().size(), state.scene().grass().size());
    }

    /**
     * 300 帧节拍弹出最旧一条消息。
     */
    @Test
    void messageExpiresAtThreeHundredFrameTick() {
        messages.addMessage("旧消息");
        messages.addMessage("新消息");
        RenderState state = null;
        for (int i = 0; i < 300; i++) {
            state = assembler.apply(List.of());
        }
        assertEquals(1, messages.size(), "gameFlow=300 时应弹出最旧一条");
        assertEquals(List.of("新消息"), messages.snapshot());
        assertEquals(300, state.flow().gameFlow());
    }

    private RenderState apply(String frame) {
        return assembler.apply(ServerFrameDecoder.decode(frame));
    }
}
