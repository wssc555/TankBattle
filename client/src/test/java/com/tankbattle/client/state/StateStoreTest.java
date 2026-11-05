package com.tankbattle.client.state;

import com.tankbattle.client.state.views.SpriteView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * publish 语义、previous 交接、防御性拷贝、clear。
 */
class StateStoreTest {

    private static RenderState state(long seq, int x) {
        return new RenderState(seq, 0L, SceneState.EMPTY, HudState.EMPTY, FlowState.EMPTY,
                List.of(new SpriteView(x, 100, 70)), List.of(), List.of(), List.of());
    }

    @Test
    void publishKeepsPreviousAndCurrent() {
        StateStore store = new StateStore();
        assertEquals(0, store.current().seq());
        assertEquals(0, store.previous().seq());

        store.publish(state(1, 10));
        assertEquals(1, store.current().seq());
        assertEquals(0, store.previous().seq());

        store.publish(state(2, 20));
        assertEquals(2, store.current().seq());
        assertEquals(1, store.previous().seq());
    }

    /**
     * publish 后修改源 List 不影响已发布状态。
     */
    @Test
    void publishCopiesSourceLists() {
        List<SpriteView> sprites = new ArrayList<>();
        sprites.add(new SpriteView(1, 1, 0));
        RenderState published = new RenderState(1, 0L, SceneState.EMPTY, HudState.EMPTY,
                FlowState.EMPTY, sprites, List.of(), List.of(), List.of());

        StateStore store = new StateStore();
        store.publish(published);

        sprites.add(new SpriteView(2, 2, 1));
        assertEquals(1, store.current().sprites().size(), "修改源列表不得影响已发布状态");
        assertThrows(UnsupportedOperationException.class, () -> store.current().sprites().add(null));
    }

    /**
     * 快速连发时 previous 可能比 EDT 已读到的 current 新，由插值层防御，store 只如实交接。
     */
    @Test
    void rapidPublishSequenceIsConsistent() {
        StateStore store = new StateStore();
        for (long seq = 1; seq <= 5; seq++) {
            store.publish(state(seq, (int) seq * 10));
        }
        assertEquals(5, store.current().seq());
        assertEquals(4, store.previous().seq());
        assertTrue(store.previous().seq() < store.current().seq());
    }

    @Test
    void clearPublishesEmptyState() {
        StateStore store = new StateStore();
        store.publish(state(3, 30));
        store.clear();
        assertEquals(0, store.current().seq());
        assertSame(RenderState.EMPTY, store.current());
    }
}
