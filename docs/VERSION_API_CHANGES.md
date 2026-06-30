# Minecraft 版本间 API 变动记录（移植手册）

Numen 采用**分支即版本**模型：每个受支持的 MC 版本一条分支（`1.21.1`、`1.21.4`、…、`26.1.2`），
Fabric + NeoForge 同源，**api 与 core 各自同名分支**。向上移植（把低版本分支搬到高版本）时，绝大多数
改动是**机械的映射/签名替换**——本文件逐版本记录这些 MC/loader API 变动，作为移植配方。

> 规则：每完成一档移植（`A → B`），把这一档碰到的**每一个** API 变动追加到对应小节。
> 宁可啰嗦：一条记录省下的是下一个人（或下一个 MC 版本）重新踩坑的时间。

约定：❗=编译期破坏性变更；📦=构建/依赖。代码示例用 `旧 → 新`。

---

## 版本阶梯

`1.20.1 → 1.20.2 → 1.20.4 → 1.20.6 → 1.21.1 → 1.21.4 → 1.21.5 → 1.21.8 → 1.21.10 → 1.21.11 → 26.1.2`

新架构（numen-api 拆分 + 调度器 + raw `NumenTool` + skill 体系）基线在 **`1.21.1`**，正逐档向上移植。
**已移植：1.21.1 → 1.21.4 ✓**

## 每档的流程

1. **api 先**：从下一档低版本分支开新分支 → 改构建旋钮 → 编译修 → `publish` 本地 maven **并 push numen-maven**。
2. **core 后**：开/重置同名分支为低版本内容 → 改构建旋钮 + api 依赖坐标指向目标 MC → 编译修 → 出包验证（内嵌 api）。
3. 边修边把变动追加到本文件。

> CI 在干净环境从远程 maven 取 api，所以 api 制品**必须 push 到 numen-maven**，否则 CI 编不过。
> 同坐标重发后 core 端若编不到新符号，删 `.gradle/loom-cache/remapped_mods/.../com/dwinovo/numen` 再编（Loom remap 缓存；新坐标无此问题）。下载 MC 用 BMCLAPI 镜像。

## 每档都要改的构建旋钮 📦

`gradle.properties`（core 与 api 各一份；core 还要改 `fabric/build.gradle`、`neoforge/build.gradle` 里的 `numen-api-*-<mc>` 坐标）：

| 键 | 1.21.1 | 1.21.4 |
|---|---|---|
| `minecraft_version` | 1.21.1 | 1.21.4 |
| `minecraft_version_range` | `[1.21.1, 1.21.2)` | `[1.21.4, 1.21.5)` |
| `neo_form_version` | 1.21.1-20240808.144430 | 1.21.4-20241203.161809 |
| `fabric_version` | 0.116.7+1.21.1 | 0.117.0+1.21.4 |
| `neoforge_version` | 21.1.233 | 21.4.123 |

---

## 1.21.1 → 1.21.4 ✓（已验证，双 loader 编译 + 出包通过）

### 通用（common，api 与 core 都有）

**注册表按 id 取值** ❗ — 方法整体改名：
```java
BuiltInRegistries.ITEM.get(rl)        → BuiltInRegistries.ITEM.getValue(rl)
BuiltInRegistries.BLOCK.get(rl)       → BuiltInRegistries.BLOCK.getValue(rl)
BuiltInRegistries.ENTITY_TYPE.get(rl) → BuiltInRegistries.ENTITY_TYPE.getValue(rl)
// 1.21.1 .get(ResourceLocation) 返回 T；1.21.4 返回 Optional<Reference<T>>，要 .getValue() 拿 T
```

**registryAccess 查注册表** ❗：
```java
registryAccess().registryOrThrow(Registries.STRUCTURE) → registryAccess().lookupOrThrow(Registries.STRUCTURE)
```

**高度访问器** ❗ — `LevelHeightAccessor` 方法改名（实现类的 `@Override` 方法名也要跟着改）：
```java
level.getMinBuildHeight() → level.getMinY()
level.getMaxBuildHeight() → level.getMaxY()
// getHeight() 不变
```

**Entity.teleportTo** ❗ — 末尾新增 boolean 参数：
```java
e.teleportTo(level, x, y, z, Set.of(), yRot, xRot) → e.teleportTo(level, x, y, z, Set.of(), yRot, xRot, false)
```

**spawnAtLocation** ❗ — 新增首个 `ServerLevel` 参数：
```java
player.spawnAtLocation(stack) → player.spawnAtLocation(serverLevel, stack)
```

**物品使用动画枚举改名** ❗：
```java
import net.minecraft.world.item.UseAnim;   → import net.minecraft.world.item.ItemUseAnimation;
UseAnim.CROSSBOW                            → ItemUseAnimation.CROSSBOW
```

**配方系统大改** ❗（`QueryExtraTools` / 老 `LookupRecipeTool`）：
```java
level.getRecipeManager().getRecipes()       → level.recipeAccess().getRecipes()
// 通用配料：1.21.4 走 PlacementInfo（新增 import net.minecraft.world.item.crafting.PlacementInfo）
cr.getIngredients().isEmpty() || allMatch(Ingredient::isEmpty)
                                            → PlacementInfo info = cr.placementInfo();
                                              info.isImpossibleToPlace() || info.ingredients().isEmpty()
recipe.getIngredients()                     → recipe.placementInfo().ingredients()  // 无空位，去掉 isEmpty 判断
// 单输入配方（切石/熔炼）：
sc.getIngredients().get(0)                  → sc.input()
cookingRecipe.getIngredients().get(0)       → cookingRecipe.input()
// shaped 网格：类型变了，空位由 Ingredient.EMPTY 变 Optional.empty()
NonNullList<Ingredient> = shaped.getIngredients()  → List<Optional<Ingredient>> = shaped.getIngredients()
cells.get(i).isEmpty() ? "." : describe(cells.get(i))
                                            → cells.get(i).map(X::describe).orElse(".")
// 配料里的物品：
Arrays.stream(ing.getItems()).map(s -> ...s.getItem()...)   // ItemStack[]
                                            → ing.items().map(h -> ...h.value()...)  // Stream<Holder<Item>>
cookingRecipe.getCookingTime()              → cookingRecipe.cookingTime()
```

### 客户端 / UI（api）

**GuiGraphics.blitSprite** ❗ — 新增首参（RenderType 函数）：
```java
g.blitSprite(sprite, x, y, w, h) → g.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, sprite, x, y, w, h)
```
波及 6 个文件 22 处：`NumenScreen`、`Dropdown`、`ProviderDropdown`、`FlatEditBox`、`SimpleButton`、`NumenToasts`。

### NeoForge loader

**客户端资源重载事件** ❗（`NumenNeoForgeClient`）：
```java
import ...client.event.RegisterClientReloadListenersEvent; → import ...client.event.AddClientReloadListenersEvent;
event.registerReloadListener(listener)
   → event.addListener(ResourceLocation.fromNamespaceAndPath(MOD_ID, "skill_loader"), listener)
```

**数据生成** ❗（`DataGenerators` / `ModBlockTagsProvider`）：
```java
gatherData(GatherDataEvent event)        → gatherData(GatherDataEvent.Client event)
// 标签 provider 不再要 ExistingFileHelper：
new ModBlockTagsProvider(out, lookup, event.getExistingFileHelper())  → new ModBlockTagsProvider(out, lookup)
super(output, lookup, MOD_ID, existingFileHelper)                     → super(output, lookup, MOD_ID)
```
（`ModItemTagsProvider` 不吃 EFH，无需改。）

---

## 1.21.4 → 1.21.5
<!-- 来源参考：v0.0.2-1.21.4-beta ↔ v0.0.2-1.21.5-beta（约 16 文件）。移植时填写。 -->
_待移植时填写_

## 1.21.5 → 1.21.8
<!-- 约 24 文件 -->
_待移植时填写_

## 1.21.8 → 1.21.10
_待移植时填写_

## 1.21.10 → 1.21.11
_待移植时填写_

## 1.21.11 → 26.1.2
_待移植时填写_
