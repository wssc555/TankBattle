package com.tankbattle.server.protocol;

import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.C2s;
import com.tankbattle.common.protocol.S2c;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2c 全量编码、C2s 解码与畸形输入容错。
 */
class FrameCodecTest {

    private static String enc(S2c message) {
        ServerFrameEncoder encoder = new ServerFrameEncoder();
        encoder.begin();
        encoder.append(message);
        return encoder.end();
    }

    @Test
    void encodesEveryS2cMessage() {
        assertEquals("L3;", enc(new S2c.LevelLoad(3)));
        assertEquals("n260,498,54;", enc(new S2c.Sprite(260, 498, 54)));
        assertEquals("t198,498,1;", enc(new S2c.Bullet(198, 498, Direction.DOWN)));
        assertEquals("o300,300,big;", enc(new S2c.Bomb(300, 300, S2c.BombSize.BIG)));
        assertEquals("o300,300,small;", enc(new S2c.Bomb(300, 300, S2c.BombSize.SMALL)));
        assertEquals("i198,498;", enc(new S2c.Shield(198, 498)));
        assertEquals("b260,498,1;", enc(new S2c.BaseState(260, 498, true)));
        assertEquals("b260,498,0;", enc(new S2c.BaseState(260, 498, false)));
        assertEquals("w100,100,1111111111111111;", enc(new S2c.BrickWallState(100, 100, 0xFFFF)));
        assertEquals("s100,100,0000;", enc(new S2c.SteelWallState(100, 100, 0)));
        assertEquals("p20,1,3,0,3,0;", enc(new S2c.PanelInfo(20, 1, 3, 0, 3, 0)));
        assertEquals("g470;", enc(new S2c.WinAnim(470)));
        assertEquals("x1;", enc(new S2c.Pause(true)));
        assertEquals("x0;", enc(new S2c.Pause(false)));
        assertEquals("j;", enc(new S2c.VoteServerYes()));
        assertEquals("a;", enc(new S2c.GameOver()));
        assertEquals("m你好;", enc(new S2c.Chat("你好")));
        assertEquals("d42;", enc(new S2c.FrameSeq(42)));
        assertEquals("v2;", enc(new S2c.CapabilityAck()));
    }

    @Test
    void encodesFrameInOrder() {
        ServerFrameEncoder encoder = new ServerFrameEncoder();
        encoder.begin();
        encoder.append(new S2c.LevelLoad(1));
        encoder.append(new S2c.PanelInfo(20, 1, 3, 0, 3, 0));
        encoder.append(new S2c.WinAnim(0));
        assertEquals("L1;p20,1,3,0,3,0;g0;", encoder.end());
    }

    @Test
    void brickBitsEncodeLittleEndianBySubBlock() {
        // 位 0 对应左上子块，位 15 对应右下子块
        assertEquals("w0,0,1100000000000000;", enc(new S2c.BrickWallState(0, 0, 0b11)));
        assertEquals("s0,0,1100;", enc(new S2c.SteelWallState(0, 0, 0b11)));
    }

    @Test
    void decodesInput() {
        List<C2s> decoded = ClientCommandDecoder.decode("m10001;");
        assertEquals(1, decoded.size());
        C2s.Input input = (C2s.Input) decoded.get(0);
        assertTrue(input.up());
        assertFalse(input.down());
        assertFalse(input.left());
        assertFalse(input.right());
        assertTrue(input.fire());
    }

    @Test
    void decodesChatVotePauseAndCapability() {
        List<C2s> decoded = ClientCommandDecoder.decode("e你好;j;x;V2;");
        assertEquals(4, decoded.size());
        assertEquals("你好", ((C2s.Chat) decoded.get(0)).text());
        assertTrue(decoded.get(1) instanceof C2s.VoteYes);
        assertTrue(decoded.get(2) instanceof C2s.TogglePause);
        assertTrue(decoded.get(3) instanceof C2s.Capability);
    }

    @Test
    void malformedFramesNeverThrow() {
        assertTrue(ClientCommandDecoder.decode("").isEmpty());
        assertTrue(ClientCommandDecoder.decode(null).isEmpty());
        assertTrue(ClientCommandDecoder.decode(";;;").isEmpty());

        C2s.Input shortInput = (C2s.Input) ClientCommandDecoder.decode("m12;").get(0);
        assertFalse(shortInput.up() || shortInput.down() || shortInput.left()
                || shortInput.right() || shortInput.fire());

        C2s.Input dirtyInput = (C2s.Input) ClientCommandDecoder.decode("m1x001;").get(0);
        assertFalse(dirtyInput.up() || dirtyInput.down() || dirtyInput.left()
                || dirtyInput.right() || dirtyInput.fire());

        assertTrue(ClientCommandDecoder.decode("zzz;").isEmpty());
        assertTrue(ClientCommandDecoder.decode("V9;").isEmpty());
    }
}
