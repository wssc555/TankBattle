package com.tankbattle.client.protocol;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.S2c;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 17 种 S2c 正例 + 畸形例 + L 后续指令仍解析。
 */
class ServerFrameDecoderTest {

    private static final String MAP_400 = ".".repeat(400);

    @Test
    void decodesAllSeventeenCommands() {
        String frame = "L3;"
                + "n100,200,70;"
                + "t120,130,2;"
                + "o150,160,small;"
                + "i170,180;"
                + "b260,498,1;"
                + "w100,200,1111000011110000;"
                + "s125,200,1010;"
                + "p5,3,4,1200,2,800;"
                + "g210;"
                + "x1;"
                + "j;"
                + "a;"
                + "m主机玩家;"
                + "M3:" + MAP_400 + ";"
                + "d42;"
                + "v2;";

        List<S2c> messages = ServerFrameDecoder.decode(frame);
        assertEquals(17, messages.size());

        S2c.LevelLoad level = assertInstanceOf(S2c.LevelLoad.class, messages.get(0));
        assertEquals(3, level.level());

        S2c.Sprite sprite = assertInstanceOf(S2c.Sprite.class, messages.get(1));
        assertEquals(100, sprite.x());
        assertEquals(200, sprite.y());
        assertEquals(70, sprite.textureIndex());

        S2c.Bullet bullet = assertInstanceOf(S2c.Bullet.class, messages.get(2));
        assertEquals(Direction.LEFT, bullet.direction());

        assertEquals(S2c.BombSize.SMALL, assertInstanceOf(S2c.Bomb.class, messages.get(3)).size());
        assertEquals(170, assertInstanceOf(S2c.Shield.class, messages.get(4)).x());

        S2c.BaseState base = assertInstanceOf(S2c.BaseState.class, messages.get(5));
        assertTrue(base.destroyed());

        S2c.BrickWallState brick = assertInstanceOf(S2c.BrickWallState.class, messages.get(6));
        assertEquals(0x0F0F, brick.destroyedBits());
        // 位图为 LSB-first：串 "1010" → bit0 + bit2 = 0x5
        assertEquals(0x5, assertInstanceOf(S2c.SteelWallState.class, messages.get(7)).destroyedBits());

        S2c.PanelInfo panel = assertInstanceOf(S2c.PanelInfo.class, messages.get(8));
        assertEquals(5, panel.enemyLeft());
        assertEquals(3, panel.level());
        assertEquals(4, panel.p1Life());
        assertEquals(1200, panel.p1Score());
        assertEquals(2, panel.p2Life());
        assertEquals(800, panel.p2Score());

        assertEquals(210, assertInstanceOf(S2c.WinAnim.class, messages.get(9)).winningCount());
        assertTrue(assertInstanceOf(S2c.Pause.class, messages.get(10)).paused());
        assertInstanceOf(S2c.VoteServerYes.class, messages.get(11));
        assertInstanceOf(S2c.GameOver.class, messages.get(12));
        assertEquals("主机玩家", assertInstanceOf(S2c.Chat.class, messages.get(13)).text());
        assertEquals(3, assertInstanceOf(S2c.MapData.class, messages.get(14)).level());
        assertEquals(42, assertInstanceOf(S2c.FrameSeq.class, messages.get(15)).seq());
        assertInstanceOf(S2c.CapabilityAck.class, messages.get(16));
    }

    /**
     * L 之后的分号指令仍会被继续解析。
     */
    @Test
    void keepsFollowingTokensAfterLevelLoad() {
        List<S2c> messages = ServerFrameDecoder.decode("L1;p1,1,3,0,3,0;g0;");
        assertEquals(3, messages.size());
        assertInstanceOf(S2c.LevelLoad.class, messages.get(0));
        assertEquals(1, assertInstanceOf(S2c.PanelInfo.class, messages.get(1)).enemyLeft());
        assertEquals(0, assertInstanceOf(S2c.WinAnim.class, messages.get(2)).winningCount());
    }

    @Test
    void toleratesMalformedInputWithoutThrowing() {
        assertTrue(ServerFrameDecoder.decode("").isEmpty());
        assertTrue(ServerFrameDecoder.decode(";;;").isEmpty());
        assertTrue(ServerFrameDecoder.decode("Z1;").isEmpty());
        assertTrue(ServerFrameDecoder.decode("n1,2,999;").isEmpty());
        assertTrue(ServerFrameDecoder.decode("w1,2,abc;").isEmpty());
        assertTrue(ServerFrameDecoder.decode("p1,2,3;").isEmpty());
        assertTrue(ServerFrameDecoder.decode("o1,2,huge;").isEmpty());
        assertTrue(ServerFrameDecoder.decode("L1").isEmpty());
    }

    @Test
    void keepsParsedPrefixWhenTailHasNoSemicolon() {
        List<S2c> messages = ServerFrameDecoder.decode("L1;n1,2,3");
        assertEquals(1, messages.size());
    }

    @Test
    void dropsOversizedFrame() {
        String oversized = "m" + "x".repeat(GameConfig.MAX_FRAME_CHARS) + ";";
        assertTrue(ServerFrameDecoder.decode(oversized).isEmpty());
    }

    @Test
    void parsesBothPauseStates() {
        List<S2c> messages = ServerFrameDecoder.decode("x1;x0;");
        assertEquals(2, messages.size());
        assertTrue(assertInstanceOf(S2c.Pause.class, messages.get(0)).paused());
        assertEquals(false, assertInstanceOf(S2c.Pause.class, messages.get(1)).paused());
    }
}
