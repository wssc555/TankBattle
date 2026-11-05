package com.tankbattle.server.system;

import com.tankbattle.server.TestContexts;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.entity.PlayerStatus;
import com.tankbattle.server.entity.PlayerTank;
import com.tankbattle.server.entity.PowerUp;
import com.tankbattle.server.entity.PowerUpType;
import com.tankbattle.server.entity.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 道具权重、存活时长与 7 种效果。
 */
class PowerUpSystemTest {

    private static PowerUp item(PowerUpType type) {
        return new PowerUp(type, new Vec(100, 100), ServerConfig.POWERUP_LIFE_MIN);
    }

    @Test
    void weightDistributionMatchesThreeToThreeToThreeToThreeToThreeToOnes() {
        GameContext ctx = TestContexts.create();
        int rounds = 170_000;
        int[] counts = new int[PowerUpType.values().length];
        for (int i = 0; i < rounds; i++) {
            counts[PowerUpSystem.pickType(ctx).function()]++;
        }
        double unit = 1.0 / ServerConfig.POWERUP_WEIGHT_TOTAL;
        for (int function = 0; function <= 4; function++) {
            double ratio = counts[function] / (double) rounds;
            assertTrue(Math.abs(ratio - 3 * unit) < 0.01,
                    "function " + function + " 权重应为 3/17，实测 " + ratio);
        }
        for (int function = 5; function <= 6; function++) {
            double ratio = counts[function] / (double) rounds;
            assertTrue(Math.abs(ratio - unit) < 0.01,
                    "function " + function + " 权重应为 1/17，实测 " + ratio);
        }
    }

    @Test
    void timedFramesHonourClassicRoll() {
        GameContext classic = TestContexts.create(true);
        for (int i = 0; i < 50; i++) {
            assertEquals(ServerConfig.POWERUP_TIMED_MIN, PowerUpSystem.timedFrames(classic));
        }
        GameContext fixed = TestContexts.create(false);
        for (int i = 0; i < 200; i++) {
            int frames = PowerUpSystem.timedFrames(fixed);
            assertTrue(frames >= ServerConfig.POWERUP_TIMED_MIN
                    && frames < ServerConfig.POWERUP_TIMED_MIN + ServerConfig.POWERUP_TIMED_RANGE);
        }
    }

    @Test
    void starPickupUpgradesAndScores() {
        GameContext ctx = TestContexts.create();
        PlayerTank tank = new PlayerTank(1);
        PowerUp star = item(PowerUpType.STAR);
        ctx.addActor(star);

        PowerUpSystem.apply(ctx, tank, star);

        assertEquals(ServerConfig.POWERUP_SCORE, tank.score());
        assertEquals(PlayerStatus.TWO, tank.status());
        assertTrue(star.destroyed());
    }

    @Test
    void lifeAndShieldPickups() {
        GameContext ctx = TestContexts.create();
        PlayerTank tank = new PlayerTank(1);
        PowerUpSystem.apply(ctx, tank, item(PowerUpType.LIFE));
        assertEquals(ServerConfig.PLAYER_LIFE + 1, tank.life());

        tank.grantShield(0);
        PowerUpSystem.apply(ctx, tank, item(PowerUpType.SHIELD));
        assertTrue(tank.invulnerable());
    }

    @Test
    void fortifyTurnsBaseArmorIntoTimedSteel() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        PlayerTank tank = ctx.p1();
        PowerUpSystem.apply(ctx, tank, item(PowerUpType.FORTIFY));
        assertEquals(ServerConfig.STEEL_ARMOR_FRAMES, ctx.base().steelWallTime());
    }

    @Test
    void nukeClearsFieldAndKeepsUnspawnedCount() {
        GameContext ctx = TestContexts.create();
        FlowSystem.startGame(ctx);
        SpawnSystem.tick(ctx);
        SpawnSystem.tick(ctx);
        assertEquals(2, ctx.level().aliveEnemies());

        PowerUpSystem.apply(ctx, ctx.p1(), item(PowerUpType.NUKE));

        assertEquals(0, ctx.level().aliveEnemies());
        assertEquals(ServerConfig.ENEMY_PER_LEVEL - ctx.level().enemyLeft(), ctx.level().deathCount());
    }

    @Test
    void freezeStopsEnemiesForTimedFrames() {
        GameContext ctx = TestContexts.create(true);
        FlowSystem.startGame(ctx);
        PowerUpSystem.apply(ctx, ctx.p1(), item(PowerUpType.FREEZE));
        assertTrue(ctx.enemiesFrozen());
        assertEquals(ServerConfig.POWERUP_TIMED_MIN, ctx.freezeTicks());
    }
}
