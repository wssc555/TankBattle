# TankBattle 客户端（JDK 21）

## 构建

```bash
mvn -f G:\AiSpace\bishe50\TankBattle\pom.xml clean package
```

产物：`client/target/tank-client.jar`（含 common / slf4j / logback 的 fat jar）。

## 运行

```bash
# V2 模式（60FPS 渲染 + 30Hz 输入 + 插值）
java -XX:+UseZGC -XX:+ZGenerational -Xms64m -Xmx256m \
     -Dsun.java2d.opengl=true \
     -jar client/target/tank-client.jar --host 127.0.0.1 --port 9999

# 直连 GBK 编码服务端（避免聊天乱码）
java -jar client/target/tank-client.jar --host 192.168.1.10 --charset GBK
```

命令行参数：`--host` / `--port` / `--charset` / `--fps`，均覆盖 `client.properties`。

## 同步模式

- 首帧发送 `V2;m00000;`；1s 内收到 `v2;` → `FREE_RUNNING`，否则降级 `LOCKSTEP`（逐帧应答）。
- `LOCKSTEP` 下关闭插值、使用 common 的本地回退地图。

## 无头测试

```bash
mvn -pl client -am test -Dtest=ServerFrameDecoderTest,FrameAssemblerTest
```

## 关键目录

| 路径                                                  | 内容                                |
|-------------------------------------------------------|-------------------------------------|
| `client/src/main/java/com/tankbattle/client/protocol` | 解码 / 编码 / 组装                  |
| `client/src/main/java/com/tankbattle/client/state`    | 不可变快照与 views                  |
| `client/src/main/java/com/tankbattle/client/net`      | 虚拟线程接收、状态机、重连          |
| `client/src/main/java/com/tankbattle/client/render`   | 8 层渲染、静态层缓存、HUD           |
| `client/src/main/java/com/tankbattle/client/input`    | Key Bindings、InputState、UiActions |
| `common/src/main/java/com/tankbattle/common`          | 协议 / 地图 / 常量 / 88 张纹理      |
