package com.tankbattle.server.system;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;
import com.tankbattle.server.core.LevelState;
import com.tankbattle.server.entity.EnemyTank;

/**
 * 敌人生产与难度曲线。仅在 RUNNING 阶段由主循环调用。
 */
public final class SpawnSystem {

    private SpawnSystem() {
    }

    public static void tick(GameContext ctx) {
        LevelState level = ctx.level();
        if (level.aliveEnemies() >= level.maxNoEnemy()) {
            return;
        }
        if (level.enemyLeft() <= 0) {
            return;
        }
        if (ctx.gameFlow() % level.enemySpawnTime() != 0) {
            return;
        }
        int x = GameConfig.MAP_ORIGIN
                + (ServerConfig.ENEMY_PER_LEVEL - level.enemyLeft()) % ServerConfig.ENEMY_SPAWN_X_PHASE
                * ServerConfig.ENEMY_SPAWN_X_STEP;
        boolean flashing = level.enemyLeft() % ServerConfig.ENEMY_SPAWN_X_PHASE == 0;
        EnemyTank enemy = new EnemyTank(level.nextEnemyType(), flashing, x,
                ServerConfig.ENEMY_SPAWN_Y, ctx.random());
        ctx.addActor(enemy);
        level.decrementEnemyLeft();
        level.incrementAliveEnemies();
    }
}
