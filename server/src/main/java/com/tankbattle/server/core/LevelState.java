package com.tankbattle.server.core;

import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.entity.EnemyType;

import java.util.List;

/**
 * 关卡运行期计数。
 */
public final class LevelState {

    private int currentLevel;
    private int enemySpawnTime = ServerConfig.ENEMY_SPAWN_START;
    private int enemyLeft = ServerConfig.ENEMY_PER_LEVEL;
    private int deathCount;
    private int maxNoEnemy = ServerConfig.ENEMY_MAX_ALIVE_START;
    private int aliveEnemies;
    private int winningCount;
    private List<EnemyType> sequence = List.of();

    public int currentLevel() {
        return currentLevel;
    }

    /**
     * 关卡编号按 8 关取模循环：{@code 1+(currentLevel-1)%8}。
     */
    public int displayLevel() {
        return levelIndex(currentLevel);
    }

    public static int levelIndex(int sequenceNumber) {
        return 1 + Math.floorMod(sequenceNumber - 1, 8);
    }

    /**
     * 推进关卡序号并按曲线提升难度。
     */
    public void advanceLevel() {
        currentLevel++;
        if (enemySpawnTime > ServerConfig.ENEMY_SPAWN_MIN) {
            enemySpawnTime -= ServerConfig.ENEMY_SPAWN_STEP;
        }
        if (maxNoEnemy < ServerConfig.ENEMY_MAX_ALIVE && currentLevel % 2 == 0) {
            maxNoEnemy++;
        }
        enemyLeft = ServerConfig.ENEMY_PER_LEVEL;
    }

    public int enemySpawnTime() {
        return enemySpawnTime;
    }

    public int enemyLeft() {
        return enemyLeft;
    }

    public void setEnemyLeft(int value) {
        this.enemyLeft = value;
    }

    public void decrementEnemyLeft() {
        this.enemyLeft--;
    }

    public int deathCount() {
        return deathCount;
    }

    public void setDeathCount(int value) {
        this.deathCount = value;
    }

    public void incrementDeathCount() {
        this.deathCount++;
    }

    public int maxNoEnemy() {
        return maxNoEnemy;
    }

    public int aliveEnemies() {
        return aliveEnemies;
    }

    public void setAliveEnemies(int value) {
        this.aliveEnemies = value;
    }

    public void incrementAliveEnemies() {
        this.aliveEnemies++;
    }

    public void decrementAliveEnemies() {
        this.aliveEnemies--;
    }

    public int winningCount() {
        return winningCount;
    }

    public void setWinningCount(int value) {
        this.winningCount = value;
    }

    public List<EnemyType> sequence() {
        return sequence;
    }

    public void setSequence(List<EnemyType> sequence) {
        this.sequence = List.copyOf(sequence);
    }

    /**
     * 出场序列中第 n 个敌人。
     */
    public EnemyType nextEnemyType() {
        int index = ServerConfig.ENEMY_PER_LEVEL - enemyLeft;
        if (index < 0 || index >= sequence.size()) {
            return EnemyType.BASIC;
        }
        return sequence.get(index);
    }

    /**
     * 回到初始状态（重开局）。
     */
    public void reset() {
        currentLevel = 0;
        enemySpawnTime = ServerConfig.ENEMY_SPAWN_START;
        enemyLeft = ServerConfig.ENEMY_PER_LEVEL;
        deathCount = 0;
        maxNoEnemy = ServerConfig.ENEMY_MAX_ALIVE_START;
        aliveEnemies = 0;
        winningCount = 0;
        sequence = List.of();
    }
}
