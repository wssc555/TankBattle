package com.tankbattle.client.protocol;

import com.tankbattle.common.model.Direction;
import com.tankbattle.common.protocol.C2s;
import com.tankbattle.common.protocol.S2c;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金样本回放：标注帧流 → 解码逐条断言 + 上行编码逐字节黄金断言。
 * <p>
 * 语料位于 {@code src/test/resources/replay/}；真机录制 3000 帧语料可后补替换。
 */
class ProtocolReplayTest {

    private static List<List<S2c>> replay(String resource) throws IOException {
        try (InputStream in = ProtocolReplayTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("缺失回放语料 " + resource);
            }
            List<List<S2c>> frames = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    frames.add(ServerFrameDecoder.decode(trimmed));
                }
            }
            return frames;
        }
    }

    @Test
    void level1IntroFramesDecodeExactly() throws IOException {
        List<List<S2c>> frames = replay("/replay/level1_intro.txt");
        assertEquals(8, frames.size(), "语料应有 8 行有效帧");

        // 行 1：L + p + g（同行全部生效）
        List<S2c> first = frames.get(0);
        assertEquals(3, first.size());
        assertEquals(1, assertInstanceOf(S2c.LevelLoad.class, first.get(0)).level());
        S2c.PanelInfo panel = assertInstanceOf(S2c.PanelInfo.class, first.get(1));
        assertEquals(20, panel.enemyLeft());
        assertEquals(1, panel.level());
        assertEquals(0, assertInstanceOf(S2c.WinAnim.class, first.get(2)).winningCount());

        // 行 2：两辆玩家坦克 + 帧序
        List<S2c> second = frames.get(1);
        assertEquals(3, second.size());
        assertEquals(100, assertInstanceOf(S2c.Sprite.class, second.get(0)).x());
        assertEquals(400, assertInstanceOf(S2c.Sprite.class, second.get(1)).x());
        assertEquals(1, assertInstanceOf(S2c.FrameSeq.class, second.get(2)).seq());

        assertEquals(70, assertInstanceOf(S2c.Sprite.class, frames.get(2).get(0)).textureIndex());

        S2c.Bullet bullet = assertInstanceOf(S2c.Bullet.class, frames.get(3).get(0));
        assertEquals(Direction.UP, bullet.direction());

        S2c.BrickWallState brick = assertInstanceOf(S2c.BrickWallState.class, frames.get(4).get(0));
        assertEquals(0x0F0F, brick.destroyedBits());
        assertEquals(4, assertInstanceOf(S2c.FrameSeq.class, frames.get(4).get(1)).seq());

        assertEquals(S2c.BombSize.BIG, assertInstanceOf(S2c.Bomb.class, frames.get(5).get(0)).size());
        assertInstanceOf(S2c.Shield.class, frames.get(6).get(0));
        assertEquals("GL HF", assertInstanceOf(S2c.Chat.class, frames.get(7).get(0)).text());

        // d{seq} 全程单调递增（60FPS 插值依赖）
        long last = 0;
        for (List<S2c> frame : frames) {
            for (S2c message : frame) {
                if (message instanceof S2c.FrameSeq seq) {
                    assertTrue(seq.seq() > last, "帧序必须单调递增: " + seq.seq() + " <= " + last);
                    last = seq.seq();
                }
            }
        }
        assertEquals(7, last);
    }

    @Test
    void pauseVoteChatFramesDecodeExactly() throws IOException {
        List<List<S2c>> frames = replay("/replay/pause_vote_chat.txt");
        assertEquals(7, frames.size());

        assertTrue(assertInstanceOf(S2c.Pause.class, frames.get(1).get(0)).paused());
        assertEquals(false, assertInstanceOf(S2c.Pause.class, frames.get(2).get(0)).paused());
        assertInstanceOf(S2c.GameOver.class, frames.get(3).get(0));
        assertInstanceOf(S2c.VoteServerYes.class, frames.get(4).get(0));
        assertEquals("主机投了同意票", assertInstanceOf(S2c.Chat.class, frames.get(5).get(0)).text());

        // 重开帧：L + p + g + d
        List<S2c> restart = frames.get(6);
        assertEquals(4, restart.size());
        assertEquals(1, assertInstanceOf(S2c.LevelLoad.class, restart.get(0)).level());
        assertEquals(6, assertInstanceOf(S2c.FrameSeq.class, restart.get(3)).seq());
    }

    /**
     * 上行编码逐字节黄金断言（除 {@code V2;} 握手帧外，各指令按拼帧规则输出）。
     */
    @Test
    void uplinkGoldenFrames() {
        ClientFrameEncoder encoder = new ClientFrameEncoder();

        // 握手帧：能力指令编码为 V2;，输入位图编码为 m00000;
        assertEquals("V2;m00000;", encoder.encode(List.of(
                new C2s.Capability(), new C2s.Input(false, false, false, false, false, 0L))));

        // 5 位输入位图：上下左右火
        assertEquals("m10000;", encoder.encode(List.of(new C2s.Input(true, false, false, false, false, 1L))));
        assertEquals("m01000;", encoder.encode(List.of(new C2s.Input(false, true, false, false, false, 2L))));
        assertEquals("m00100;", encoder.encode(List.of(new C2s.Input(false, false, true, false, false, 3L))));
        assertEquals("m00010;", encoder.encode(List.of(new C2s.Input(false, false, false, true, false, 4L))));
        assertEquals("m00001;", encoder.encode(List.of(new C2s.Input(false, false, false, false, true, 5L))));

        // 事件指令：j; / x; / e
        assertEquals("j;x;m10000;", encoder.encode(List.of(
                new C2s.VoteYes(), new C2s.TogglePause(),
                new C2s.Input(true, false, false, false, false, 6L))));

        // 聊天净化后拼接：e + 文本 + ;
        assertEquals("e你好 坦克;", encoder.encode(List.of(new C2s.Chat("你好;坦克\r"))));
    }
}
