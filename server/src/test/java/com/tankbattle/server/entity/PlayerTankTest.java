package com.tankbattle.server.entity;

import com.tankbattle.server.TestContexts;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 玩家生命/血量轨迹与状态升级参数。
 */
class PlayerTankTest {

    @Test
    void initialStats() {
        PlayerTank tank = new PlayerTank(1);
        assertEquals(ServerConfig.PLAYER_LIFE, tank.life());
        assertEquals(ServerConfig.PLAYER_HEALTH, tank.health());
        assertEquals(ServerConfig.PLAYER_BULLETS, tank.bullets());
        assertEquals(150, ServerConfig.PLAYER_INVULN_FRAMES, "无敌帧时长为 150 帧");
        assertTrue(tank.invulnerable());
        assertEquals(ServerConfig.SPAWN_P1_X, tank.pos().x());
    }

    @Test
    void lifeDropsThreeTwoOneZero() {
        GameContext ctx = TestContexts.create();
        PlayerTank tank = new PlayerTank(1);
        for (int expected : new int[]{2, 1, 0}) {
            tank.grantShield(0);
            tank.hurt(ctx);
            assertEquals(expected, tank.life());
        }
        assertEquals(ServerConfig.PLAYER_OUT_X, tank.pos().x(), "出局后被移到 100000,100000");
        assertEquals(ServerConfig.PLAYER_OUT_Y, tank.pos().y());
    }

    @Test
    void respawnRestoresAllFields() {
        GameContext ctx = TestContexts.create();
        PlayerTank tank = new PlayerTank(2);
        tank.grantShield(0);
        tank.hurt(ctx);
        assertEquals(2, tank.life());
        assertEquals(ServerConfig.SPAWN_P2_X, tank.pos().x());
        assertEquals(ServerConfig.SPAWN_P2_Y, tank.pos().y());
        assertEquals(PlayerStatus.ONE, tank.status());
        assertEquals(ServerConfig.PLAYER_HEALTH, tank.health());
        assertEquals(ServerConfig.PLAYER_BULLETS, tank.bullets());
        assertTrue(tank.invulnerable(), "复活后 150 帧无敌");
    }

    @Test
    void invulnerablePlayerIgnoresHits() {
        GameContext ctx = TestContexts.create();
        PlayerTank tank = new PlayerTank(1);
        tank.hurt(ctx);
        assertEquals(ServerConfig.PLAYER_LIFE, tank.life(), "无敌帧内不掉命");
    }

    @Test
    void megaStarGrantsSecondHealthWithoutExtraLife() {
        GameContext ctx = TestContexts.create();
        PlayerTank tank = new PlayerTank(1);
        tank.applyMegaStar();
        assertEquals(PlayerStatus.FOUR, tank.status());
        assertEquals(2, tank.health());
        assertEquals(ServerConfig.PLAYER_LIFE, tank.life(), "超级星星不增加生命");

        tank.grantShield(0);
        tank.hurt(ctx);
        assertEquals(1, tank.health());
        assertEquals(PlayerStatus.THREE, tank.status(), "双血被打一次降为 Status 3");
        assertEquals(ServerConfig.PLAYER_LIFE, tank.life());
    }

    @Test
    void starUpgradePath() {
        PlayerTank tank = new PlayerTank(1);
        assertEquals(PlayerStatus.ONE, tank.status());
        assertEquals(1, tank.bullets());

        tank.upgrade();
        assertEquals(PlayerStatus.TWO, tank.status());
        assertEquals(1, tank.bullets());

        tank.upgrade();
        assertEquals(PlayerStatus.THREE, tank.status());
        assertEquals(2, tank.bullets(), "升到 Status 3 时同屏弹量 +1");

        tank.upgrade();
        assertEquals(PlayerStatus.FOUR, tank.status());
        assertEquals(2, tank.bullets(), "Status 3→4 弹量不变");

        tank.upgrade();
        assertEquals(PlayerStatus.FOUR, tank.status(), "已满级不再变化");
    }

    @Test
    void statusParametersMatchTable() {
        assertEquals(7, PlayerStatus.ONE.bulletSpeed());
        assertEquals(8, PlayerStatus.ONE.cooldownTicks());
        assertEquals(1, PlayerStatus.ONE.maxBullets());
        assertEquals(1, PlayerStatus.ONE.bulletPower());

        assertEquals(12, PlayerStatus.TWO.bulletSpeed());
        assertEquals(8, PlayerStatus.TWO.cooldownTicks());
        assertEquals(1, PlayerStatus.TWO.maxBullets());

        assertEquals(12, PlayerStatus.THREE.bulletSpeed());
        assertEquals(5, PlayerStatus.THREE.cooldownTicks());
        assertEquals(2, PlayerStatus.THREE.maxBullets());

        assertEquals(12, PlayerStatus.FOUR.bulletSpeed());
        assertEquals(5, PlayerStatus.FOUR.cooldownTicks());
        assertEquals(2, PlayerStatus.FOUR.maxBullets());
        assertEquals(2, PlayerStatus.FOUR.bulletPower());
    }

    @Test
    void textureIndexFollowsStatusAndDirection() {
        PlayerTank tank = new PlayerTank(1);
        assertEquals(ServerConfig.TEX_PLAYER1, tank.textureIndex(0));
        tank.applyMegaStar();
        assertEquals(ServerConfig.TEX_PLAYER1 + 12, tank.textureIndex(0));
    }
}
