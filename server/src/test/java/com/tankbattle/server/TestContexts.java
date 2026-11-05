package com.tankbattle.server;

import com.tankbattle.common.config.GameConfig;
import com.tankbattle.server.config.ServerConfig;
import com.tankbattle.server.core.GameContext;

/**
 * 测试用的一局上下文（固定种子，便于回放复现）。
 */
public final class TestContexts {

    public static final long SEED = 20261105L;

    private TestContexts() {
    }

    /**
     * 测试用运行配置（增量关闭、经典道具随机）。
     */
    public static ServerConfig.Runtime runtime() {
        return new ServerConfig.Runtime(
                GameConfig.PORT,
                GameConfig.FRAME_MS,
                false,
                true,
                ServerConfig.DEFAULT_VOTE_TIMEOUT_SECONDS,
                ServerConfig.SLOW_FRAME_WARN_MS,
                ServerConfig.DEFAULT_NET_CHARSET);
    }

    public static GameContext create() {
        return create(true);
    }

    public static GameContext create(boolean classicPowerUpRoll) {
        return new GameContext(new ServerConfig.Runtime(
                GameConfig.PORT,
                GameConfig.FRAME_MS,
                false,
                classicPowerUpRoll,
                ServerConfig.DEFAULT_VOTE_TIMEOUT_SECONDS,
                ServerConfig.SLOW_FRAME_WARN_MS,
                ServerConfig.DEFAULT_NET_CHARSET), SEED);
    }
}
