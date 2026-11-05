package com.tankbattle.client.protocol;

import com.tankbattle.common.protocol.C2s;
import com.tankbattle.common.protocol.ChatSanitizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 5 种 C2s 编码、32 种输入位组合、聊天净化、StringBuilder 复用。
 */
class ClientFrameEncoderTest {

    private final ClientFrameEncoder encoder = new ClientFrameEncoder();

    @Test
    void encodesAllThirtyTwoInputCombinations() {
        for (int mask = 0; mask < 32; mask++) {
            boolean up = (mask & 0b10000) != 0;
            boolean down = (mask & 0b01000) != 0;
            boolean left = (mask & 0b00100) != 0;
            boolean right = (mask & 0b00010) != 0;
            boolean fire = (mask & 0b00001) != 0;
            String encoded = encoder.encode(List.of(
                    new C2s.Input(up, down, left, right, fire, mask)));
            String expected = "m"
                    + (up ? '1' : '0') + (down ? '1' : '0') + (left ? '1' : '0')
                    + (right ? '1' : '0') + (fire ? '1' : '0') + ";";
            assertEquals(expected, encoded);
        }
    }

    @Test
    void sanitizesChatBeforeEncoding() {
        assertEquals("hi x", ChatSanitizer.sanitize("hi;x"));
        assertEquals("", ChatSanitizer.sanitize(null));
        assertEquals("ehi x;", encoder.encode(List.of(new C2s.Chat("hi;x"))));
        assertEquals("e;", encoder.encode(List.of(new C2s.Chat(""))));
        assertEquals("e;", encoder.encode(List.of(new C2s.Chat("   \r\n"))));
    }

    @Test
    void encodesEventsAndCapability() {
        assertEquals("j;", encoder.encode(List.of(new C2s.VoteYes())));
        assertEquals("x;", encoder.encode(List.of(new C2s.TogglePause())));
        assertEquals("V2;", encoder.encode(List.of(new C2s.Capability())));
    }

    @Test
    void preservesOrderAndReturnsEmptyForEmptyList() {
        String encoded = encoder.encode(List.of(
                new C2s.Capability(),
                new C2s.Input(true, false, false, false, false, 1),
                new C2s.Chat("hi"),
                new C2s.VoteYes()));
        assertEquals("V2;m10000;ehi;j;", encoded);
        assertEquals("", encoder.encode(List.of()));
    }

    @Test
    void reusesBufferWithoutStateResidue() {
        String first = encoder.encode(List.of(new C2s.Chat("first")));
        String second = encoder.encode(List.of(new C2s.Chat("second")));
        assertEquals("efirst;", first);
        assertEquals("esecond;", second);
        assertFalse(second.contains("first"));
    }
}
