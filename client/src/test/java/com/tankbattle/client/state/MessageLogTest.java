package com.tankbattle.client.state;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 8 条滚动、300 帧逐条过期、reversed 渲染序。
 */
class MessageLogTest {

    @Test
    void keepsOnlyLatestEightInChronologicalOrder() {
        MessageLog log = new MessageLog();
        for (int i = 1; i <= 11; i++) {
            log.addMessage("m" + i);
        }
        List<String> snapshot = log.snapshot();
        assertEquals(8, snapshot.size());
        assertEquals("m4", snapshot.get(0));
        assertEquals("m11", snapshot.get(7));
        assertEquals(8, log.size());
    }

    @Test
    void pollsOldestFirst() {
        MessageLog log = new MessageLog();
        log.addMessage("a");
        log.addMessage("b");
        assertEquals("a", log.pollOldest());
        assertEquals(List.of("b"), log.snapshot());
        assertEquals("b", log.pollOldest());
        assertNull(log.pollOldest());
        assertTrue(log.snapshot().isEmpty());
    }

    @Test
    void ignoresBlankAndNullMessages() {
        MessageLog log = new MessageLog();
        log.addMessage(null);
        log.addMessage("   ");
        log.addMessage("ok");
        assertEquals(List.of("ok"), log.snapshot());
    }
}
