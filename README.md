# TankBattle 服务端（JDK 21）

经典坦克大战（Battle City）联机对战游戏的服务端权威模拟器。基于 Java 21 + Maven 多模块
（`common` / `server` / `client`），零第三方依赖（仅 slf4j/logback/junit），协议兼容 v1 客户端。

## 构建

```bash
mvn -pl server -am package     # 产出 server/target/server-1.0.0-SNAPSHOT.jar（shade fat jar）
mvn -pl server -am test        # 运行全部单元测试（含砖墙 200 组黄金值逐位对照）
```

## 运行

```bash
# GUI 监控模式（有显示环境）
java -jar server/target/server-1.0.0-SNAPSHOT.jar

# 无显示环境（容器 / CI / 远程 SSH；无显示时也会自动降级）
java -jar server/target/server-1.0.0-SNAPSHOT.jar --headless --port 9999

# 分代 ZGC（推荐，JDK 21 正式特性）
java -XX:+UseZGC -XX:+ZGenerational -jar server/target/server-1.0.0-SNAPSHOT.jar --headless
```

也可直接使用 `start-zgc.cmd`（Windows）/ `start-zgc.sh`（Linux/macOS）。

## 配置（server/src/main/resources/application.properties）

| 键                        | 默认  | 说明                                                                                                  |
|---------------------------|-------|-------------------------------------------------------------------------------------------------------|
| `server.port`             | 9999  | 监听端口（`--port` 覆盖）                                                                             |
| `server.frameMs`          | 30    | 固定步长帧间隔                                                                                        |
| `net.incremental`         | false | 增量同步开关；开启后仅对完成 `V2;`/`v2;` 握手的客户端追加 `d{seq};`，未握手的客户端始终使用 v1 全量帧 |
| `net.charset`             | UTF-8 | 会话收发字符集；对接 GBK 编码客户端时设为 `GBK`                                                       |
| `powerup.classicRoll`     | true  | true 为固定 300 帧；false 为 300~699 帧随机                                                           |
| `flow.voteTimeoutSeconds` | 30    | 重开投票超时                                                                                          |
| `log.slowFrameWarnMs`     | 5     | 单帧耗时告警阈值                                                                                      |

## 命令行参数

- `--headless`：无 GUI 直接开主机
- `--port <n>`：覆盖监听端口

## 协议

v1 分号文本协议（`L/n/i/t/o/b/w/s/p/g/x/j/a/m/e/m5位/…`）；
可选 v2 扩展（`V2;`(C→S)、`v2;`/`M{n}:{400符号};`/`d{seq};`(S→C)）仅在双方握手成功后启用。
详见 `docs/servertask/T03/T04/T06` 与 `common` 模块 `S2c/C2s`。

## 测试

`server/src/test` 覆盖：协议编码字节级断言、砖墙 200 组黄金值逐位对照、
命中规则管线与护甲护基地、8 关地形统计基线、
流程状态机全转移、固定种子 900 帧确定性回放。

## 文档

- 任务书：`docs/servertask/`（T01–T32 + README 总览）
- 评审：`docs/服务端代码评审报告.md`
