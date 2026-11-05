package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.TestContexts;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.entity.EnemyTank;
import com.tankbattle.server.entity.EnemyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敌人出场序列、出生点轮换与难度曲线。
 */
class SpawnSystemTest {

    private static List<EnemyTank> enemies(GameContext ctx) {
        return ctx.actors().stream()
                .filter(EnemyTank.class::isInstance)
                .map(EnemyTank.class::cast)
                .toList();
    }

    @Test
    void firstLevelDifficultyBaseline() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        assertEquals(1, ctx.level().currentLevel());
        assertEquals(ServerConfig.ENEMY_SPAWN_START - ServerConfig.ENEMY_SPAWN_STEP,
                ctx.level().enemySpawnTime());
        assertEquals(ServerConfig.ENEMY_MAX_ALIVE_START, ctx.level().maxNoEnemy());
        assertEquals(ServerConfig.ENEMY_PER_LEVEL, ctx.level().enemyLeft());
    }

    @Test
    void threeEnemiesSpawnThenCapBlocks() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);

        SpawnSystem.tick(ctx);
        SpawnSystem.tick(ctx);
        SpawnSystem.tick(ctx);

        assertEquals(3, ctx.level().aliveEnemies());
        assertEquals(17, ctx.level().enemyLeft());
        assertEquals(3, enemies(ctx).size());

        SpawnSystem.tick(ctx);
        assertEquals(3, enemies(ctx).size(), "同屏上限 3 时不再生产");
    }

    @Test
    void spawnPointsRotateAndEveryThirdEnemyFlashes() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        SpawnSystem.tick(ctx);
        SpawnSystem.tick(ctx);
        SpawnSystem.tick(ctx);

        List<EnemyTank> spawned = enemies(ctx);
        assertEquals(GameConfig.MAP_ORIGIN, spawned.get(0).pos().x());
        assertEquals(GameConfig.MAP_ORIGIN, spawned.get(0).pos().y());
        assertEquals(GameConfig.MAP_ORIGIN + ServerConfig.ENEMY_SPAWN_X_STEP, spawned.get(1).pos().x());
        assertEquals(GameConfig.MAP_ORIGIN + 2 * ServerConfig.ENEMY_SPAWN_X_STEP, spawned.get(2).pos().x());

        assertFalse(spawned.get(0).flashing());
        assertFalse(spawned.get(1).flashing());
        assertTrue(spawned.get(2).flashing(), "enemyLeft%3==0（18/15/12/9/6/3）携带道具");
    }

    @Test
    void firstLevelSequenceStartsWithBasicTanks() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        for (int i = 0; i < 3; i++) {
            SpawnSystem.tick(ctx);
        }
        for (EnemyTank tank : enemies(ctx)) {
            assertEquals(EnemyType.BASIC, tank.type(), "第 1 关前 17 个敌人均为 Type1");
        }
    }

    @Test
    void spawnOnlyOnSpawnFrame() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        ctx.advanceGameFlow();
        SpawnSystem.tick(ctx);
        assertEquals(0, enemies(ctx).size(), "非 enemySpawnTime 帧不生产");

        for (int i = 1; i < ctx.level().enemySpawnTime(); i++) {
            ctx.advanceGameFlow();
        }
        SpawnSystem.tick(ctx);
        assertEquals(1, enemies(ctx).size(), "gameFlow 达到 enemySpawnTime 的整数倍时生产");
    }
}
