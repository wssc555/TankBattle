# TankBattle（JDK 21）

经典坦克大战（Battle City）联机双人对战游戏。 **服务端权威模拟 + 客户端纯渲染**：主机端玩家直接在服务窗口操作，另一名玩家通过
TCP 连接到服务端；服务端持有全部游戏状态，30ms 固定步长推进逻辑帧并广播帧快照。

Java 21 + Maven 多模块，零业务第三方依赖（仅 slf4j / logback / junit）。

## 项目架构

```
TankBattle/
├── pom.xml                 父 POM：模块聚合、Java 21、依赖版本管理
├── common/                 共享层：协议常量与文本编解码、8 关地图数据、双端常量、纹理资源
│   └── src/main/resources/image/  88 张纹理（打包进 jar）
├── server/                 服务端：权威游戏逻辑、敌人 AI、碰撞/战斗/道具/流程系统、
│   │                             网络服务、帧快照编码、Swing 主机端界面（主机玩家可直接操作）
│   └── src/main/resources/application.properties
├── client/                 客户端：连接与自动重连、协议解码、不可变状态快照、
│   │                             插值、分层渲染、键盘输入采集
│   └── src/main/resources/client.properties
├── docs/                   设计与代码评审文档
├── start-zgc.cmd / .sh     分代 ZGC 启动脚本（服务端）
└── README.md
```

| 模块     | 入口类                             | 可执行产物                                |
|----------|------------------------------------|-------------------------------------------|
| `common` | —（被依赖，无 main）               | `common/target/common-1.0.0-SNAPSHOT.jar` |
| `server` | `com.tankbattle.server.GameServer` | `server/target/server-1.0.0-SNAPSHOT.jar` |
| `client` | `com.tankbattle.client.GameClient` | `client/target/tank-client.jar`           |

两个可执行产物均为 shade fat jar，已包含 `common` 与日志依赖，可直接 `java -jar` 运行。

### 数据流

```
客户端键盘 → InputState → CommandSender ──TCP 文本指令──> 服务端 ClientSession
                                                          ↓
                                   GameLoop(30ms) → Input/Movement/Collision/
                                   Combat/Spawn/PowerUp/Flow → Snapshot
                                                          ↓
客户端 GamePanel ← StateStore ← FrameAssembler ← ServerFrameDecoder ←── 帧广播
```

- **权威与确定性**：碰撞、命中、道具、关卡、胜负全部由服务端计算；客户端只解码快照并渲染，不做逻辑判定。
- **协议**：默认 v1 分号分隔文本帧；可选 v2 增量同步（`V2;` / `v2;` 握手后追加 `M{n}:…;` 地图与 `d{seq};` 差分），未握手的客户端始终收到
  v1 全量帧。握手成功走自由运行 + 插值，超时降级 LOCKSTEP 逐帧应答。定义见
  `common/src/main/java/com/tankbattle/common/protocol/`（`C2s` / `S2c`）。
- **地图**：8 关 20×20 符号地图内置于 `common/.../LevelRepository`，无需外部资源文件。
- **并发**：accept 循环与会话 IO 走虚拟线程，游戏逻辑单线程固定步长；客户端网络线程、渲染 Timer 与 EDT 分离。

## 环境要求

| 项       | 要求                                                       |
|----------|------------------------------------------------------------|
| JDK      | **21**（虚拟线程、`record`、分代 ZGC、文本块）             |
| Maven    | 3.9+                                                       |
| 显示环境 | 客户端必需；服务端 GUI 模式必需，`--headless` 可无显示运行 |
| 网络     | 服务端监听 TCP `9999`（可改），客户端能访问该端口          |

## 构建

在仓库根目录执行：

```bash
mvn clean package        # 全量构建 + 全部单元测试，产出两个 fat jar
mvn test                 # 仅运行测试
mvn -pl server -am package   # 只构建服务端（含 common）
mvn -pl client -am package   # 只构建客户端（含 common）
```

## 运行

**1. 启动服务端**（先启动）

```bash
# GUI 主机模式：打开窗口后点击「建立主机」
java -jar server/target/server-1.0.0-SNAPSHOT.jar

# 无界面自动开主机（容器 / CI / SSH；无显示环境时也会自动降级）
java -jar server/target/server-1.0.0-SNAPSHOT.jar --headless --port 9999

# 分代 ZGC（推荐）
java -XX:+UseZGC -XX:+ZGenerational -jar server/target/server-1.0.0-SNAPSHOT.jar --headless
```

也可直接运行 `start-zgc.cmd`（Windows）/ `start-zgc.sh`（Linux/macOS），脚本会检查 jar 是否存在并透传全部参数，例如
`start-zgc.cmd --headless --port 9999`。

**2. 启动客户端**

```bash
# 同机对战：默认 127.0.0.1:9999，窗口内点「连接主机」
java -jar client/target/tank-client.jar

# 指定远端主机 / 端口 / 字符集 / 渲染帧率
java -jar client/target/tank-client.jar --host 192.168.1.10 --port 9999 --charset UTF-8 --fps 60
```

`--host` 会预填窗口的 IP 输入框，可在界面上再修改。两端字符集必须一致；对接旧版 GBK 客户端时把服务端 `net.charset` 设为
`GBK`（或客户端用 `--charset GBK`）。

**3. 开始游戏**

服务端 GUI 点「建立主机」→ 客户端点「连接主机」→ 双方进入战场。主机端玩家在服务窗口操作，另一名玩家在客户端窗口操作。客户端断线会自动退避重连。

| 按键      | 作用                                   |
|-----------|----------------------------------------|
| 方向键    | 移动坦克                               |
| `S`       | 开火                                   |
| `Y` / `N` | 结束时重开投票（赞成 / 反对）          |
| `Enter`   | 发送聊天消息                           |
| `Tab`     | 切换到聊天输入框（方向键仍可控制坦克） |
| 窗口按钮  | 暂停/继续、帮助、退出、连接主机        |

## 配置

配置文件打包在各自 fat jar 的 classpath 中，修改 `src/main/resources/*.properties` 后需重新 `mvn package`；运行期优先用命令行参数覆盖。

**服务端** `server/src/main/resources/application.properties`

| 键                        | 默认  | 说明                                         |
|---------------------------|-------|----------------------------------------------|
| `server.port`             | 9999  | 监听端口（`--port` 覆盖）                    |
| `server.frameMs`          | 30    | 固定步长帧间隔                               |
| `net.incremental`         | false | 开启 v2 增量同步（仅对握手成功的客户端）     |
| `net.charset`             | UTF-8 | 会话字符集，旧版客户端用 `GBK`               |
| `powerup.classicRoll`     | true  | 道具时长 fixed 300 帧；false 为 300~699 随机 |
| `flow.voteTimeoutSeconds` | 30    | 重开投票超时                                 |
| `log.slowFrameWarnMs`     | 5     | 单帧耗时告警阈值                             |

**客户端** `client/src/main/resources/client.properties`（常用项）

| 键                                      | 默认             | 说明                                      |
|-----------------------------------------|------------------|-------------------------------------------|
| `client.net.host` / `client.net.port`   | 127.0.0.1 / 9999 | 目标主机（`--host` / `--port` 覆盖）      |
| `client.net.charset`                    | UTF-8            | `--charset` 覆盖，需与服务端一致          |
| `client.render.fps` / `client.input.hz` | 60 / 30          | 渲染帧率 / 输入采样率（`--fps` 覆盖前者） |
| `client.render.interpolation`           | true             | 帧间插值                                  |
| `client.net.reconnect*`                 | 500 / 8000 / 6   | 重连退避基数 / 上限 / 最大次数            |
| `client.input.exclusiveDirection`       | true             | 方向键互斥                                |

**命令行参数**

| 程序   | 参数                                                                                 |
|--------|--------------------------------------------------------------------------------------|
| 服务端 | `--headless`、`--port <n>`                                                           |
| 客户端 | `--host <ip>`、`--port <n>`、`--charset <name>`、`--fps <n>`（也支持 `--key=value`） |

## 测试与文档

`mvn test` 覆盖协议编码字节级断言、砖墙 200 组黄金值、命中与护甲规则、8 关地形基线、流程状态机、固定种子 900
帧确定性回放、客户端解码/渲染/连接状态机。

- `client/README.md` — 客户端同步模式、推荐 JVM 参数、字符集说明
- `docs/客户端设计重构文档.md` — 架构设计、协议指令总表（附录 A）、关键常量（附录 B）
- `docs/服务端代码评审报告.md`、`docs/客户端代码评审报告.md` — 代码评审结论
