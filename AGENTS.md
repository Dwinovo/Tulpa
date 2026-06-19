# AGENTS.md

> 面向 AI 编码代理的项目说明。简体中文。**以代码为准**——本文与 `common/src/main/java/com/dwinovo/tulpa/` 下的实现冲突时，相信代码。

## 项目愿景（Tulpa）

`Tulpa` 是一个 **完全由 LLM 驱动的 Minecraft AI 同伴**。召唤一个同伴，然后**直接和它说话**：用任意自然语言下达意图，本地运行的 agent 调用**你自己的 API key**，让 LLM 规划并驱动同伴在世界里干活——寻路、挖矿、建造、战斗、合成、用容器，全程自主。

四个设计支点：

1. **大脑在客户端，身体在服务端。** agent loop 跑在 owner 的客户端、用 owner 的 key，服务端不调 LLM（不让服主替所有人付 token，玩家也不必上交 key）。
2. **身体是真·假玩家，不是自定义 Mob。** 同伴是服务端的 `ServerPlayer`（fake player），所以走的是**原生玩家代码路径**——和其它 mod、红石、怪物 AI、容器天然兼容，不需要逐功能写适配。
3. **三层严格分离：LLM 决策层 / Task 执行层 / Tool 桥接层。** Tool 描述"LLM 看到什么"，Task 描述"世界里发生什么"，ToolCall 是两者之间唯一的接口。不要在 Task 里写策略，也不要在 prompt 里写底层位移逻辑。
4. **零第三方运行时依赖。** LLM 传输只用 JDK `java.net.http.HttpClient` + Gson（MC 自带）。mod jar ~260KB。

## 架构总览

```
   你的客户端（owner）                          服务端
   ─────────────────────                       ──────────────────────────
   chat / G 面板 ─▶ EntityAgentLoop ─▶ LLM API
                        │  (你的 key、你的 model)
                        │  tool_calls
                        ▼
                  ExecuteToolPayload ─────────▶  TaskQueue（挂在 fake player 身上）
                        ▲                            │  CompanionTickDispatcher 每 tick 驱动
                        │   results                  ▼
                  TaskResultPayload ◀──────────  寻路 · 挖矿 · 战斗 · 放置 · 容器 …
```

**端到端流程**（客户端 LLM）：

```
玩家右键 / G 面板 → AgentLoopRegistry.getOrCreate(uuid).submitPrompt
   → TulpaLlmClient.chat（异步 HTTPS via JDK HttpClient，玩家自己的 key，默认 SSE streaming）
   → AssistantTurn（含 toolCalls；provider 专属字段如 reasoning_content 在 extras 里透传）
   → 按工具类别分发：
       · local（todowrite / load_skill）：EntityAgentLoop 客户端同步执行，不过网
       · query / async-query（感知类）：ExecuteToolPayload(C→S) → 服务端同步/预算化答复，不占身体
       · world-action：ExecuteToolPayload(C→S) → entity.taskQueue.enqueue(TaskRecord)
   → 服务端 TulpaPlayer.findByUuid（全维度）+ UUID owner 校验 + schema 校验
   → CompanionTickDispatcher 每 tick：start() → tick()… → 终态 → buildResult → TaskResultPayload(S→C)
   → 客户端 EntityAgentLoop.onToolResult → 写回 ConvoState → 下一轮 LLM
```

## 仓库结构（MultiLoader）

```
common/      # 共享逻辑，只能用 vanilla MC API（实体、任务、agent、寻路、tool 注册表都在这里）
fabric/      # Fabric 入口 + 平台特定胶水代码
neoforge/    # NeoForge 入口 + 平台特定胶水代码
buildSrc/    # 共享的 multiloader-common / multiloader-loader gradle 约定
docs/        # 设计笔记（寻路、molang/render-controller 历史文档、CurseForge 文案）
tools/       # 离线工具：ui-textures（HyperFrames 生成 GUI 贴图）+ FRAME.md（UI 视觉设计系统）
```

关键约定：

- **绝大多数代码写在 `common/`。** 实体、任务、ToolCall、LLM 客户端、寻路——都属于 common。
- **加载器特有 API 必须通过 Service 抽象。** 在 common 定义接口（`platform/services/`），在 `fabric/` + `neoforge/` 各放一个实现，经 `META-INF/services/<全限定名>` 注册。当前三个 Service：`IPlatformHelper` / `INetworkChannel` / `ITulpaConfig`（见 [`Services.java`](common/src/main/java/com/dwinovo/tulpa/platform/Services.java)）。
- **不要**在 common 里 `import net.fabricmc.*` 或 `net.neoforged.*`——编译能过也是错的（运行期另一侧会炸）。

## 同伴实体：fake ServerPlayer

`TulpaPlayer extends ServerPlayer`（[`entity/TulpaPlayer.java`](common/src/main/java/com/dwinovo/tulpa/entity/TulpaPlayer.java)）——**不是 Mob、不是 `PathfinderMob`**。因此：

- 走原生玩家的交互/战斗/容器代码路径（通用 mod 兼容）；有自己的玩家背包；作为列表内玩家天然享受免费区块加载 + `playerdata/*.dat` 持久化。
- **owner 模型**：fake player 不能带自定义 `SynchedEntityData`，所以 owner 是一个服务端字段（NBT key `TulpaOwner`），通过 `addAdditionalSaveData` 持久化到同伴自己的 `.dat`。**owner 校验一律用 UUID 比较**——绝不用 vanilla `getOwner()/isOwnedBy`（它按实体所在 level 解析，跨维度静默失效）。
- **任务宿主**：实体身上挂 `TaskQueue` + `PathTally` + `activeTask`（从旧 Mob 提上来）。
- **死亡可恢复**：vanilla 死亡正常跑（掉落 / keepInventory / 墓碑 mod 都生效）；`TulpaDeathPayload(S→C)` 挂起 owner 的 agent loop（用死因 resolve 在途 tool call），延时后由 `Companions` 重生于 owner 处，`TulpaRespawnPayload(S→C)` 恢复 loop。
- **以真玩家渲染**：自带皮肤、出现在 tab 列表里。**没有自研 Bedrock 渲染管线**（旧 `anim/` 包已废弃，因为它就是个真玩家）。

**召唤 / 注销**（[`entity/Companions.java`](common/src/main/java/com/dwinovo/tulpa/entity/Companions.java) + [`CompanionFactory`](common/src/main/java/com/dwinovo/tulpa/entity/CompanionFactory.java) + [`CompanionRegistry`](common/src/main/java/com/dwinovo/tulpa/entity/CompanionRegistry.java)）：每个同伴有稳定的 per-companion UUID（写进 `GameProfile`）；`FakeConnection` 提供 fake player 所需的连接桩。注意 fake player 不是通过自定义 entity-type 注册的，所以两个 loader 都**没有** entity-type 注册路径。

**命令**（[`entity/TulpaCommands.java`](common/src/main/java/com/dwinovo/tulpa/entity/TulpaCommands.java)，仿 Carpet `/player`，纯服务端）：

| 命令 | 作用 |
|---|---|
| `/tulpa player summon <name>` | 召唤命名同伴（幂等，复用已存在的） |
| `/tulpa player despawn <name>` | 永久注销（连注册表条目一起删，登录不再回来） |
| `/tulpa settings` | 在调用者客户端打开设置 GUI（经 `ClientUiActionPayload`） |
| `/tulpa reset` | 清空调用者的对话 loop |

## 任务执行框架（服务端）

旧的 vanilla `Goal` / `GoalSelector` / `LlmTaskGoal` 已**全部退役**，改由 `CompanionTickDispatcher` 直接拥有任务生命周期。

- [`CompanionTask`](common/src/main/java/com/dwinovo/tulpa/task/CompanionTask.java)：一个运行中的任务接口，三个方法——`start()`（首 tick 设置）、`tick()`（每服务端 tick 推进，返回 `RUNNING` 或终态）、`buildResult(finalState)`（给 LLM 的结果信封）。
- [`TaskState`](common/src/main/java/com/dwinovo/tulpa/task/TaskState.java)：`PENDING → RUNNING → {SUCCESS | FAILED | TIMEOUT | CANCELLED}`，单向、tick 驱动。
- [`TaskQueue`](common/src/main/java/com/dwinovo/tulpa/task/TaskQueue.java)：**单 FIFO，强制串行**——同一时刻一个 body 只跑一个任务。强 agent 连挂很多任务是正常的；真要并行（"边走边看"）以后是把它拆成 per-channel 队列，不是现在的事。
- [`CompanionTickDispatcher`](common/src/main/java/com/dwinovo/tulpa/task/CompanionTickDispatcher.java)：两个 loader 的 end-of-tick hook 都注册它。每 tick 遍历每个活着的 `TulpaPlayer`：拉队头 → 跑对应 `CompanionTask` 到终态（deadline 受限）→ 把结果作为 `TaskResultPayload(S→C)` 发回 owner。也负责 `tickRespawns`（定时死亡恢复）。
- [`CompanionTaskFactory`](common/src/main/java/com/dwinovo/tulpa/task/CompanionTaskFactory.java)：把队列里的 `TaskRecord` 按 `instanceof` 映射到具体 `CompanionTask`（无反射）。未映射的落到 [`UnsupportedCompanionTask`](common/src/main/java/com/dwinovo/tulpa/task/UnsupportedCompanionTask.java)。
- **超时**：用 `level.getGameTime()`（`/tick freeze`、`/tick rate` 都正确响应）。`TaskRecord.deadlineGameTime` 在 tool 翻译时算好（now + 工具声明的默认 timeout）。
- **寻路副作用回报**：走路途中挖掉的障碍 / 放下的脚手架由 `entity.pathTally()`（[`PathTally`](common/src/main/java/com/dwinovo/tulpa/pathing/exec/PathTally.java)）按类型计数，统一折进任务结果 message（如 "reached target (en route: dug 4x dirt; placed 6x cobblestone)"）——让模型知道导航不是免费的。
- 辅助：[`PlayerInv`](common/src/main/java/com/dwinovo/tulpa/task/PlayerInv.java) / [`PlayerPlace`](common/src/main/java/com/dwinovo/tulpa/task/PlayerPlace.java)（背包 / 放置的玩家身体动作）。具体原子任务在 [`task/tasks/`](common/src/main/java/com/dwinovo/tulpa/task/tasks/)，每个工具一对 `XxxTaskRecord`（数据）+ `XxxCompanionTask`（执行）。

> **要新建原子任务时**：实现 `CompanionTask`（`start` / `tick` / `buildResult`），加一个 `TaskRecord`，在 `CompanionTaskFactory.create` 里加一行 `instanceof` 分发。可调参数尽量上提到对应 `TulpaTool.parameterSchema()`，别写死在 Task 里。

## LLM / Agent 层

- **传输**（[`agent/http/`](common/src/main/java/com/dwinovo/tulpa/agent/http/)）：`HttpLlmTransport` = JDK `HttpClient` POST + Gson，支持 SSE streaming；`LlmHttpException` 包装错误。future 在 HttpClient 的 daemon 线程完成。
- **Provider 抽象**（[`agent/provider/`](common/src/main/java/com/dwinovo/tulpa/agent/provider/)）：[`LlmProvider`](common/src/main/java/com/dwinovo/tulpa/agent/provider/LlmProvider.java) 是单点 wire-format 适配——build 各 role 的 wire message、build request body、parse response、round-trip `extras`、SSE chunk 累积。**只做 JSON in/out，不做 HTTP、不管对话状态、不控 loop。** 现有实现：`OpenAIProvider`（默认，OpenAI chat-completions 方言）、`DeepSeekProvider`（继承，保留 `reasoning_content` round-trip 修 thinking 模式 400）、`DashScopeProvider`、`MinimaxProvider`、`MoonshotProvider`、`SiliconFlowProvider`、`VolcengineProvider`、`ZhipuProvider`。`provider.defaultBaseUrl()` 给出留空时的默认 base（含 `/v1`、`/beta`、`/compatible-mode/v1` 等前缀，不含 `/chat/completions`）。内部类型：`AssistantTurn`（content + toolCalls + extras）、`LlmToolCall`、`StreamAccumulator`。
- **ModelRegistry**（[`agent/model/ModelRegistry.java`](common/src/main/java/com/dwinovo/tulpa/agent/model/ModelRegistry.java)）：已知模型 id 与各站点默认 base-URL 的目录（从 litellm 刷新，含 Gemini / Grok 等 OpenAI-compatible 站点）。新增 OpenAI 方言 backend 通常只改这里 + 配 base URL，不必新写 Provider 类。
- **客户端 agent loop**（[`client/agent/`](common/src/main/java/com/dwinovo/tulpa/client/agent/)）：
  - [`EntityAgentLoop`](common/src/main/java/com/dwinovo/tulpa/client/agent/EntityAgentLoop.java)：**per-entity 单层**编排循环（刻意从短命的 PlayerAgent+EntityAgent 双层回滚——单层好调试）。一个实体一段对话，owner 直接和它聊。
  - [`AgentLoopRegistry`](common/src/main/java/com/dwinovo/tulpa/client/agent/AgentLoopRegistry.java)：**UUID → loop** 映射（跨维度 int-id churn 也稳）。[`ClientTulpaLookup`](common/src/main/java/com/dwinovo/tulpa/client/agent/ClientTulpaLookup.java) 把 UUID 解析到当前 body。
  - [`ConvoState`](common/src/main/java/com/dwinovo/tulpa/agent/llm/ConvoState.java)：per-entity 对话历史（含上下文接近窗口时的自动压缩/摘要）。[`ConvoLog`](common/src/main/java/com/dwinovo/tulpa/agent/llm/ConvoLog.java)：JSONL 持久化（`conversations/<uuid>.jsonl`）。
  - [`WorkBlockMemory`](common/src/main/java/com/dwinovo/tulpa/client/agent/WorkBlockMemory.java)：记住摆出的工作方块坐标，注入系统提示词 `<known_blocks>` 供复用。[`TulpaRoster`](common/src/main/java/com/dwinovo/tulpa/client/agent/TulpaRoster.java)：owner 的同伴名册。`ClientDeaths`：客户端侧死亡态。
- **线程规则（single-writer）**：所有 convo / 网包发送只在 client tick 线程发生。唯一的跨线程关口是 `HttpLlmTransport` 的 future（daemon 线程）→ 经 `Minecraft.getInstance().execute(...)` 投回 client tick 线程。
- **死循环防护**：**无 tool 调用次数硬上限**；唯一自主停止是循环检测（连续两次完全相同的 tool 批次就停）。真跑飞了靠 owner 的 **Stop**（`CancelTasksPayload`）中断。`turnCount` 仅用于日志编号。

## ToolCall 清单（LLM 能力面）

注册于 [`CommonClass.registerTools()`](common/src/main/java/com/dwinovo/tulpa/CommonClass.java)，实现在 [`agent/tool/tools/`](common/src/main/java/com/dwinovo/tulpa/agent/tool/tools/)。**共 27 个**。每个工具 = LLM 看到的 schema（[`TulpaTool`](common/src/main/java/com/dwinovo/tulpa/agent/tool/TulpaTool.java)）+ 一个翻译器。

> **注意**：早期文档里的 `craft` / `load_furnace` / `check_furnace` / `collect_furnace` / `deposit_items` / `take_items` 工具**已不存在**。合成与容器操作改由通用 GUI 原语完成：`interact_at` 打开菜单 → `inspect_gui` 读槽 → `transfer` 按意图搬运 → `close_gui`。

**四种执行类别**（见 `TulpaTool` 注释）：

- **行动类（world-action，默认）**——走 `TaskQueue`，占身体，结果经 `TaskResultPayload` 回：`move_to` · `auto_mine` · `break_block` · `place_block` · `hunt` · `shoot` · `locate_structure` · `locate_biome` · `collect_items` · `drop_items` · `equip_item` · `eat_item` · `interact_at` · `interact_entity` · `wait`
- **查询类（`isQuery`）**——服务端同步答复、不排队、不占身体（同伴可能在几千格外的票据加载地形里，客户端看不到，所以读自服务端）：`get_self_status` · `get_owner_status` · `get_world_info` · `inspect_block` · `scan_nearby_entities` · `lookup_recipe` · `inspect_gui` · `transfer` · `close_gui`（后两者作用于已打开的菜单，所以同步执行而非排队）
- **异步查询（`isAsyncQuery`）**——读但太贵、跨 tick 预算化分片：`scan_blocks`
- **本地类（`isLocal`）**——纯客户端 agent 簿记、不过网：`todowrite` · `load_skill`

**按功能分组的工具表**：

| 组 | 工具 | 作用 |
|---|---|---|
| 移动 / 导航 | `move_to` / `locate_structure` / `locate_biome` | 寻路到坐标（搭桥/挖障/跳）；定位最近结构 / 群系 |
| 挖矿 / 建造 | `auto_mine` / `break_block` / `place_block` | 意图级采集 N 个方块；精确破坏单块；按坐标放置（朝向正确） |
| 战斗 | `hunt` / `shoot` | 近战 / 弓射 N 个指定怪（原生玩家战斗：真冷却、武器 mod、暴击） |
| 库存 / 物品 | `collect_items` / `drop_items` / `equip_item` / `eat_item` | 捡掉落 / 扔物 / 穿戴装备 / 吃食物回血 |
| 容器 / 交互 | `interact_at` / `interact_entity` / `inspect_gui` / `transfer` / `close_gui` | 原生准心交互（方块/空气列 + 实体列）；读 / 搬 / 关已开容器 |
| 合成 | `lookup_recipe` | JEI 式配方查询（合成由 `transfer`+`interact_at` 手动摆格完成） |
| 感知 / 状态 | `get_self_status` / `get_owner_status` / `get_world_info` / `scan_blocks` / `scan_nearby_entities` / `inspect_block` | 读自身 / 主人 / 世界 / 范围方块 / 附近实体 / 单块 |
| 规划 / 元 | `todowrite` / `load_skill` | 待办清单（同时只一个 in_progress）；按名加载 SKILL.md 工作流 |

> **要新建 Tool 时**：实现 `TulpaTool` 接口，在 `CommonClass.registerTools` 注册。一个 Tool 可发多种 `TaskRecord`（命名→类型映射在 `CompanionTaskFactory` 做 `instanceof` 分发）。注册顺序在 registry 里保留，便于后端 prompt-cache 稳定命中。

## 自研寻路（`pathing/`）

`move_to` 与战斗追击**不用** vanilla `PathNavigation`，改用 Baritone 风格 A\* over 移动原语（[`pathing/`](common/src/main/java/com/dwinovo/tulpa/pathing/)，分 `calc/` `exec/` `movement/` `util/` `cache/` `viz/`）：会用背包里 `tulpa:scaffolds` tag 的方块**搭桥 / 垫脚 / 搭柱上升**、挖穿障碍、下挖楼梯、游泳，按真实 tick 成本规划。**时间片化**（A* 跨 tick 续算，多实体同规划不卡服务端）、**实时重规划**（被推离节点明显变远即 `NEEDS_REPLAN`）。清障挖掘的可行性判据集中在 `NavContext.costOfBreaking`（基岩 / 熔岩火 / 会引发液体流动 / 功能方块即任何 BlockEntity / 落沙 / 无效破坏均 `COST_INF` 绕开）。详细设计见 [`docs/BARITONE_PATHFINDING.md`](docs/BARITONE_PATHFINDING.md) / [`docs/PATHFINDING_ASYNC.md`](docs/PATHFINDING_ASYNC.md)。

## 平台抽象 / 网络 / 配置

**Service（`platform/services/`）**——三个，经 `ServiceLoader` 解析（[`Services.java`](common/src/main/java/com/dwinovo/tulpa/platform/Services.java)）：

| Service | Fabric 实现 | NeoForge 实现 |
|---|---|---|
| `IPlatformHelper` | `FabricPlatformHelper` | `NeoForgePlatformHelper` |
| `INetworkChannel` | `FabricNetworkChannel` | `NeoForgeNetworkChannel` |
| `ITulpaConfig` | `FabricTulpaConfig`（`config/tulpa.json`） | `NeoForgeTulpaConfig`（`tulpa-common.toml` via `ModConfigSpec`） |

**网络**（[`network/TulpaNetwork.java`](common/src/main/java/com/dwinovo/tulpa/network/TulpaNetwork.java) + `network/payload/`）：

| Payload | 方向 | 作用 |
|---|---|---|
| `ExecuteToolPayload` | C→S | 客户端 LLM 的 tool_call，在 owner 的同伴上执行（含 schema 校验） |
| `CancelTasksPayload` | C→S | owner 按 Stop，取消在跑 + 排队任务 |
| `TaskResultPayload` | S→C | tool 执行结果回给 owner 的 loop |
| `TulpaDeathPayload` / `TulpaRespawnPayload` | S→C | 同伴死亡挂起 / 重生恢复 loop |
| `TulpaEventPayload` | S→C | 异步世界事件（维度变更 / 危险等）喂给大脑 |
| `CompanionListPayload` | S→C | owner 名册（UUID+名），登录 / 召唤时推 |
| `PathVizPayload` | S→C | 当前寻路计划，给世界内路径叠加层 |
| `ClientUiActionPayload` | S→C | `/tulpa settings` `reset` 等需作用于调用者客户端的动作 |
| `LocateTulpaPayload` / `TulpaLocationsPayload` | C→S / S→C | 名册面板查（可能跨维度的）宠物位置 |
| `RequestInventoryPayload` / `TulpaInventoryPayload` | C→S / S→C | Items 标签页请求 / 返回同伴背包 |
| `SummonRequestPayload` | C→S | 面板"+"按钮按名召唤 |

**配置**（[`ITulpaConfig`](common/src/main/java/com/dwinovo/tulpa/platform/services/ITulpaConfig.java)，跨 loader 读写面）字段：`provider`（默认 `openai`，未知值回退）、`apiKey`、`model`、`baseUrl`（留空用 provider 默认）、`proxy`（`host:port`，留空直连）、`systemPrompt`。客户端 `SettingsScreen` 经 setter + `save()` 写回各 loader 原生配置文件。

## 渲染与 UI（客户端）

同伴**以真玩家渲染**（自带皮肤），无自研模型管线。客户端 UI 在 [`client/`](common/src/main/java/com/dwinovo/tulpa/client/) 下：

- `client/screen/`：`TulpaScreen`（G 键控制面板，Chat / Items / Settings 三标签）、`SettingsScreen`（provider / key / model / baseUrl / 站点）、`LlmProviders` / `ProviderDropdown` / `Dropdown` / `SimpleButton` / `Nb` / `UiTheme` 等控件与主题。
- `client/hud/TulpaToasts`：左边缘 HUD 头像 rail + 说话气泡。
- `client/path/`（`ClientPathViz` / `PathVizRenderer`）：世界内寻路叠加层。
- `client/data/`（`ClientTulpaInventory` / `ClientTulpaLocations`）：客户端缓存的背包 / 位置快照。
- GUI 贴图（`assets/tulpa/textures/gui/sprites/`）由 [`tools/ui-textures/`](tools/ui-textures/)（HyperFrames + HTML 模板）离线生成，视觉设计系统见 [`tools/ui-textures/FRAME.md`](tools/ui-textures/FRAME.md)。

## 构建与运行

```powershell
./gradlew build                  # 全量构建（fabric + neoforge）
./gradlew :fabric:runClient      # 启动 Fabric 客户端
./gradlew :fabric:runServer      # Fabric 专用服务端
./gradlew :neoforge:runClient    # 启动 NeoForge 客户端
./gradlew :neoforge:runServer    # NeoForge 专用服务端
./gradlew :fabric:runDatagen     # Fabric 数据生成 → fabric/src/generated/resources/
./gradlew :neoforge:runData      # NeoForge 数据生成 → neoforge/src/generated/resources/
```

- **Java 25**——IDEA Gradle JVM 与 Project SDK 都要切到 25。
- Gradle daemon 在 [`gradle.properties`](gradle.properties) 关掉了（`org.gradle.daemon=false`），第一次构建慢是正常的。
- **数据生成**：source of truth 在 Java 里（[`data/ModLanguageData`](common/src/main/java/com/dwinovo/tulpa/data/ModLanguageData.java) / `ModBlockTagData` / `ModItemTagData` / [`init/InitTag`](common/src/main/java/com/dwinovo/tulpa/init/InitTag.java)），`generated/` 被 `.gitignore` 排除。**clone 后第一次构建要先跑一次上面两条 datagen**，否则 jar 里没 lang/tag 资源（游戏内显示原始 key）。tag 含 `tulpa:scaffolds`（寻路搭桥/垫脚用料）。
- **新增 `gradle.properties` 字段**必须同步加进 `buildSrc/.../multiloader-common.gradle` 的 `expandProps` map，否则 `processResources` 不替换占位符。`fabric.mod.json` / `neoforge.mods.toml` / mixin json 里的 `${mod_id}` 等保留占位符，别手工展开。

**Mixin**（刻意只用 3 个，设计靠 fake-player 而非侵入式 patch）：common `MixinMinecraft` + `MenuDataSlotsAccessor`；fabric / neoforge 各一个 `MixinTitleScreen`。配置文件 `tulpa.mixins.json` / `tulpa.fabric.mixins.json` / `tulpa.neoforge.mixins.json`。

## 命名与配置（已落定）

| 项 | 值 | 来源 |
|---|---|---|
| `mod_id` | `tulpa` | [gradle.properties](gradle.properties) |
| `mod_name` | `Tulpa` | gradle.properties |
| `group` / 包根 | `com.dwinovo.tulpa` | gradle.properties |
| `mod_author` | `dwinovo` | gradle.properties |
| 入口类（两 loader） | `com.dwinovo.tulpa.TulpaMod` | `fabric.mod.json` / `neoforge.mods.toml` |
| `version` | `26.1.2.0` | gradle.properties |
| `license` | `MIT` | gradle.properties / [LICENSE](LICENSE) |

| 技术栈 | 值 |
|---|---|
| Minecraft | 26.1.2 |
| Java | 25 |
| Loader | Fabric (loom) + NeoForge (moddev) |
| Fabric API | 0.148.2+26.1.2 · Fabric Loader 0.19.2 |
| NeoForge | 26.1.2.50-beta |
| Mixin | + MixinExtras |

## 给 AI 代理的关键指引

### 在添加代码前先问自己

1. **用到加载器特有 API 吗？** 否→写在 `common/`；是→common 定义接口 + 两 loader 各实现 + `META-INF/services/` 注册。
2. **这是 LLM 决策层、Task 执行层、还是 Tool 桥接层？** 别混。分层：
   - `task/` — 任务抽象（`CompanionTask` / `TaskRecord` / `TaskQueue` / `TaskState`）+ `CompanionTickDispatcher`（服务端）
   - `task/tasks/` — 具体原子任务（`XxxTaskRecord` + `XxxCompanionTask`，服务端）
   - `agent/http/` — JDK `HttpClient` 包装
   - `agent/provider/` — `LlmProvider` + 各 backend wire-format 适配
   - `agent/llm/` — `TulpaLlmClient` + `ConvoState` + `ConvoLog`
   - `agent/tool/` — `TulpaTool` + `ToolRegistry`（两侧共用：客户端构 tool 列表，服务端校验 payload）
   - `client/agent/` — `EntityAgentLoop`（客户端编排）+ `AgentLoopRegistry`（UUID→loop）
   - `network/payload/` — C↔S 网包
   - `entity/` — `TulpaPlayer` + 召唤 / 命令 / 名册

### 核心原则：tool 结果即给模型的"游戏说明书"

**每个 tool_call 的返回，无论成功失败，都要承担"教 LLM 怎么玩 Minecraft"的职责。** 模型只能从工具反馈里学会玩——干巴巴的 `success:false` 等于什么都没教。

- **失败要可执行**：缺料 / 缺前置 → 明确说缺什么、为什么、下一步干嘛，而不是只报状态。
- **挖掘先判"有效挖掘"，按当前主手判定，绝不自动换装**：开挖前判断**当前主手**能否真正 harvest 目标（产出掉落），不能就**直接失败**并告知最低工具（如 `iron_ore can't be harvested with bare hands — need at least a stone pickaxe`）。刻意不做"自动换最优工具"——那会误导模型对工具等级的认知；必须让模型自己 `equip_item`。判定链见 `MineBlockTaskRecord` / `MineCompanionTask`。
- **harvest 判定只作用于 LLM 的目标方块**，不套到寻路清障的挖掘上（清路不在乎掉落，徒手挖开挡路的土/石该允许）。
- **成功也要带可决策数据**：如实回报实际产出/采集数（可能少于请求），让 LLM 决定换地方还是收手。
- 能在 tool **description** 里前置引导的（如"挖矿前先 equip 对的镐"），别只靠失败后才教。

### 调 LLM 时打开 DEBUG 日志

INFO 已含足够诊断（每次调用耗时 / token / finish reason / content 摘要 / tool dispatch / tool result）。真出问题时在对应 loader run dir 的 `log4j2.xml` 加一行 `<Logger name="Tulpa" level="DEBUG"/>` 看 HTTP 链路 id、SSE chunk、loop 跳过原因、服务端收包、drain outbox 等。日志前缀按子系统 grep（`[tulpa-llm]` / `[tulpa-http]` / `[tulpa-agent#N]` / `[tulpa-net]` 等）。

### 引入外部依赖

**默认拒绝。** 当前零第三方运行时依赖，jar ~260KB。要加先论证：JDK / vanilla 能不能做？jar 涨多少？要不要 relocate？两 loader classpath 一致吗？历史教训：早期内嵌 OpenAI SDK（+kotlin/okhttp/jackson…）= 50MB，砍成 JDK `HttpClient`+Gson 约 200 行、零依赖、降 99.5%。真要嵌外部 AI 框架，优先放进 mod 外的 sidecar HTTP service，mod 只指向 `baseUrl`。

### 不要做的事

- 不要在 common 里 `import net.fabricmc.*` 或 `net.neoforged.*`。
- 不要在 Task 里写决策树 / if-else 策略堆——决策属于 LLM。
- 不要 `git push --force`、`reset --hard`，删 `runs/` 之外的生成目录前先确认。
- 不要凭空给文档/代码加"还不存在的子系统"——以现有代码为准。

## 许可证

[MIT](LICENSE)——可自由使用、修改、分发（含商用），保留版权声明即可。`gradle.properties` 的 `license=MIT` 会写进 `fabric.mod.json` / `neoforge.mods.toml`，在 mod 菜单展示。
