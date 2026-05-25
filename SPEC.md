# NeoForge 1.21.1 队伍共享 EMC 转化桌 Mod 需求精简版

## 1. 项目目标

开发一个 Minecraft 1.21.1 + NeoForge 模组，实现类似等价交换 / ProjectE 的基础转化桌系统，但不依赖 ProjectE，不复制 ProjectE 源码。

核心功能：

- 给物品分配 EMC 值。
- EMC 余额由“队伍”共享。
- 添加两个物品/方块：
  - 转化桌 `transmutation_table`
  - 便携式转化桌 `portable_transmutation_table`
- 二者右键后打开同一个 GUI。
- 玩家可以放入物品，将其转化为 EMC。
- 玩家只能取出本队伍曾经放入并转化过的物品。
- 所有关键逻辑必须服务端权威，防止刷物品。

建议 modid：

```text
teamemc
```

如果当前项目已有 modid，则沿用当前项目 modid。

## 2. 队伍共享 EMC 规则

EMC 账户按队伍共享。

账户 key 规则：

```text
如果玩家在 scoreboard team 中：
team:<teamName>

如果玩家不在 scoreboard team 中：
player:<uuid>
```

同一个账户共享：

- EMC 余额
- 已解锁物品列表

例如：

- 玩家 A 和玩家 B 在同一个 scoreboard team。
- A 转化圆石。
- B 打开转化桌后能看到 EMC 增加，并且能取出圆石。

## 3. 已解锁物品规则

物品只有在本队伍曾经放入转化桌并成功转化后，才会被解锁。

解锁只记录基础物品的 registry id：

```java
BuiltInRegistries.ITEM.getKey(stack.getItem())
```

不记录：

- NBT
- data components
- 自定义名称
- 附魔
- 损耗状态

未解锁物品不能取出，即使 EMC 足够。

取出物品时永远生成默认 ItemStack：

```java
new ItemStack(item, count)
```

## 4. EMC 值系统

实现 `EmcValueManager`。

### 4.1 基础 EMC 值

支持通过数据文件或配置文件定义基础 EMC 值，例如：

```text
data/teamemc/emc/base_values.json
```

示例：

```json
{
  "minecraft:cobblestone": 1,
  "minecraft:stone": 1,
  "minecraft:dirt": 1,
  "minecraft:oak_log": 32,
  "minecraft:coal": 128,
  "minecraft:iron_ingot": 256
}
```

第一版至少提供：

```text
minecraft:cobblestone = 1
minecraft:stone = 1
minecraft:dirt = 1
minecraft:oak_log = 32
minecraft:coal = 128
minecraft:iron_ingot = 256
```

### 4.2 配方推导 EMC

支持从 shaped / shapeless crafting recipe 推导 EMC。

规则：

- 如果 recipe 的所有 ingredient 都有 EMC，则输出物品候选 EMC 为：

```text
输入 EMC 总和 / 输出数量
```

示例：

```text
8 个圆石 -> 1 个熔炉
furnace EMC = 8
```

多配方时取最小正 EMC。

对于 tag ingredient 或多候选 ingredient，取候选物品中最小 EMC。

需要反复迭代 recipe，直到没有新 EMC 产生，或达到安全迭代上限。

暂时只处理普通 crafting recipe，不处理复杂 NBT / data component recipe。

### 4.3 数值类型

EMC 使用 `long`。

所有加法和乘法必须检查溢出。

推荐使用：

```java
Math.addExact(...)
Math.multiplyExact(...)
```

或使用 `BigInteger` 临时计算后判断是否超过 `Long.MAX_VALUE`。

溢出时拒绝操作，并提示玩家。

## 5. NBT / Data Component 规则

带 NBT 的物品允许转化，但 NBT 不计价。

允许转化：

- 带自定义名称的物品
- 带附魔的物品
- 带 NBT 的物品
- 带特殊 data components 的物品

但 EMC 计算只按基础物品处理。

例子：

- 带自定义名称的圆石，仍按普通圆石计算 EMC。
- 附魔铁剑，仍按普通铁剑基础 EMC 和当前耐久计算。
- 附魔不增加 EMC。
- 自定义名称不增加 EMC。
- NBT / data components 不增加 EMC。

转化成功后只解锁基础物品。

取出时只生成默认物品，不能恢复 NBT、附魔或名称。

目的：防止玩家通过转化桌复制特殊 NBT 物品。

## 6. 耐久物品 EMC 规则

有耐久的物品按剩余耐久比例折算 EMC。

公式：

```text
actualEmc = floor(baseEmc * currentDurability / maxDurability)
```

其中：

```text
baseEmc = 满耐久基础 EMC
currentDurability = 当前剩余耐久
maxDurability = 最大耐久
```

如果使用 Minecraft 常见 API：

```java
currentDurability = stack.getMaxDamage() - stack.getDamageValue();
```

规则：

- 满耐久物品 EMC = baseEmc。
- 半耐久物品 EMC = floor(baseEmc * 剩余耐久 / 最大耐久)。
- 剩余耐久小于等于 0 时拒绝转化。
- 折算 EMC 小于等于 0 时拒绝转化。
- 不可损耗物品直接使用 baseEmc。
- 附魔、名称、NBT 不改变耐久折算结果。
- 计算时必须防止 long 溢出。

建议方法：

```java
long getBaseItemEmc(Item item);
OptionalLong getSingleStackItemEmc(ItemStack stack);
OptionalLong getStackEmc(ItemStack stack);
boolean isDamageable(ItemStack stack);
long getRemainingDurability(ItemStack stack);
long getMaxDurability(ItemStack stack);
```

`getStackEmc(ItemStack stack)` 应计算：

```text
单个物品实际 EMC * stack count
```

并检查溢出。

## 7. 数据存储

实现：

```java
TeamEmcSavedData extends SavedData
```

数据存在 Overworld 的 `SavedData` 中，不存在某个转化桌方块实体中。

文件名建议：

```text
teamemc_accounts.dat
```

建议结构：

```java
class TeamAccount {
    long emcBalance;
    Set<ResourceLocation> learnedItems;
}

Map<String, TeamAccount> accounts;
```

要求：

- 保存 account key。
- 保存 EMC 余额。
- 保存 learnedItems。
- 读取时跳过无效 registry id。
- 读取时修正或跳过负数 EMC。
- 修改余额或 learnedItems 后调用 `setDirty()`。
- 服务器重启后数据仍然存在。

建议方法：

```java
static TeamEmcSavedData get(MinecraftServer server);
TeamAccount getOrCreateAccount(ServerPlayer player);
long getBalance(ServerPlayer player);
boolean isLearned(ServerPlayer player, Item item);
void learn(ServerPlayer player, Item item);
boolean addEmc(ServerPlayer player, long amount);
boolean trySpendEmc(ServerPlayer player, long amount);
void setBalance(ServerPlayer player, long amount);
```

## 8. 转化桌方块

注册方块：

```text
transmutation_table
```

要求：

- 可放置。
- 有对应 BlockItem。
- 玩家右键方块打开 GUI。
- 转化桌本身不保存 EMC。
- 关闭 GUI 时，输入槽剩余物品归还玩家。
- 不允许吞物品或复制物品。

## 9. 便携式转化桌

注册物品：

```text
portable_transmutation_table
```

要求：

- 手持右键打开同一个 GUI。
- 与方块转化桌使用同一套菜单和数据。
- 自身不保存 EMC。
- 优先禁止转化本模组的转化桌和便携式转化桌，避免递归和刷物品问题。

## 10. GUI / Menu

实现：

```java
TransmutationMenu extends AbstractContainerMenu
TransmutationScreen extends AbstractContainerScreen<TransmutationMenu>
```

注册：

```java
MenuType<TransmutationMenu>
```

GUI 至少包含：

- 输入槽
- “转化”按钮
- 当前队伍 EMC 余额显示
- 已解锁物品列表
- 分页
- 左键取 1 个
- Shift + 左键取 64 个或该物品最大堆叠数
- 右键放入物品到输入槽

## 11. 输入槽规则

输入槽可以放入有 EMC 的物品。

允许放入带 NBT / 附魔 / 自定义名称 / data components 的物品，但这些特殊数据不参与 EMC。

如果物品没有 EMC，优先拒绝放入。

支持：

- 手动放入
- 右键放入
- shift-click 放入
- 输入槽已有同类物品时合并

关闭 GUI 时，输入槽剩余物品必须归还玩家。

## 12. 转化流程

点击“转化”按钮后：

1. 客户端发送 `RequestConvertPacket`。
2. 服务端读取当前 `TransmutationMenu` 输入槽。
3. 服务端计算 EMC。
4. 服务端验证：
   - player 存在
   - 当前 menu 是 `TransmutationMenu`
   - 输入槽非空
   - 物品有 EMC
   - 耐久有效
   - 计算无溢出
5. 验证通过后：
   - 增加队伍 EMC
   - 解锁基础物品 registry id
   - 清空输入槽
   - SavedData `setDirty()`
   - 同步 GUI 数据
6. 验证失败：
   - 不删除物品
   - 不增加 EMC
   - 提示玩家

## 13. 取出物品规则

已解锁物品列表中：

- 左键点击：取出 1 个。
- Shift + 左键点击：取出 64 个。
- 如果物品最大堆叠数小于 64，则取最大堆叠数。
- 右键不用于取出，右键用于放入物品。

服务端验证：

- player 存在。
- 当前 menu 是 `TransmutationMenu`。
- itemId 存在于 registry。
- 该物品已被当前队伍解锁。
- 该物品有 EMC。
- count 合法。
- count 需要 clamp：

```java
count = Math.min(requestedCount, item.getDefaultMaxStackSize());
```

- EMC 足够。
- 玩家背包空间足够。
- 只有成功放入背包后才扣 EMC。
- 背包空间不足时取消整个操作，不扣 EMC。
- 客户端伪造未解锁物品取出必须被拒绝。

## 14. 右键放入规则

目标行为：

- 玩家打开转化桌 GUI。
- 右键点击玩家背包中的物品栈。
- 尝试移动该物品栈到输入槽。
- 输入槽为空则放入。
- 输入槽已有同类物品则合并。
- 输入槽已有不同物品时优先拒绝，避免复杂 bug。
- 放入后不自动转化，仍需点击“转化”按钮。

实际物品移动必须由服务端完成。

客户端不能直接删除物品或增加 EMC。

## 15. Shift-click 规则

必须实现 `quickMoveStack`。

规则：

- 从玩家背包 shift-click 到输入槽：只移动有 EMC 的物品。
- 从输入槽 shift-click 回玩家背包：正常移回。
- 关闭 GUI：输入槽剩余物品归还玩家。
- 不能出现：
  - 输入槽物品被同时转化又返回
  - 取出物品不扣 EMC
  - 客户端伪造取出
  - 背包和输入槽同时保留同一物品栈

## 16. 网络包

实现 NeoForge 1.21.1 networking。

### 16.1 RequestConvertPacket

方向：

```text
client -> server
```

字段：无。

服务端直接读取当前 menu 输入槽，不信任客户端物品信息。

### 16.2 RequestWithdrawPacket

方向：

```text
client -> server
```

字段：

```java
ResourceLocation itemId;
int count;
```

规则：

- 左键发送 `count = 1`。
- Shift + 左键发送 `count = 64`。
- 服务端 clamp 到最大堆叠数。
- 服务端重新验证 learned、EMC、count、背包空间。

### 16.3 SyncEmcDataPacket

方向：

```text
server -> client
```

字段：

```java
long balance;
List<ResourceLocation> learnedItems;
```

用途：

- 打开菜单时同步。
- 转化成功后同步。
- 取出成功后同步。
- debug 命令修改后同步。
- 同队伍多人同时打开 GUI 时尽量同步所有人。
- 如果实现复杂，至少保证执行操作的玩家立即同步，其他玩家重新打开后正确。

## 17. 打开 GUI

### 方块转化桌

右键方块时：

- 客户端返回 success。
- 服务端使用 `ServerPlayer#openMenu(...)`。
- 使用 `SimpleMenuProvider` 创建 `TransmutationMenu`。
- 打开后同步 EMC 和 learnedItems。

### 便携式转化桌

物品 `use` 时：

- 客户端返回 success。
- 服务端使用 `ServerPlayer#openMenu(...)`。
- 使用同一个 `TransmutationMenu`。
- 打开后同步 EMC 和 learnedItems。

## 18. Tooltip

给物品 tooltip 添加 EMC 显示。

规则：

- 有 EMC 时显示：

```text
EMC: <value>
```

- 耐久物品显示当前 stack 折算后的 EMC。
- 没有 EMC 的物品优先不显示。
- Tooltip 只用于显示，不能作为服务端逻辑依据。

## 19. Debug 命令

添加 OP-only 命令。

### `/teamemc balance`

显示当前玩家账户 EMC。

### `/teamemc set <amount>`

设置当前玩家账户 EMC。

要求：

- amount 不能小于 0。
- 修改后保存 SavedData。
- 如果玩家打开 GUI，同步数据。

### `/teamemc learn <item>`

让当前玩家账户解锁指定物品。

要求：

- item 必须存在。
- item 最好有 EMC。
- 修改后保存 SavedData。
- 同步 GUI。

### `/teamemc reload`

重新加载 EMC 值。

如果实现复杂，可以留 TODO，但不能影响编译。

## 20. 推荐类结构

至少拆分为：

```text
ModItems
ModBlocks
ModMenus
ModNetworking
EmcValueManager
TeamEmcSavedData
TransmutationMenu
TransmutationScreen
TransmutationTableBlock
PortableTransmutationTableItem
```

可以根据项目已有结构调整类名，但职责必须清晰。

## 21. 服务端权威要求

必须保证以下逻辑只在服务端执行：

- EMC 增加
- EMC 扣除
- 物品删除
- 物品生成
- 解锁物品
- 保存数据

客户端只负责：

- 显示 GUI
- 发送请求
- 接收同步数据

不能信任客户端传来的：

- EMC 余额
- 解锁状态
- 物品价值
- 是否有足够余额
- 是否能取出

## 22. 验收标准

必须通过以下测试：

1. 项目能通过 Gradle 编译。
2. 游戏能启动。
3. `transmutation_table` 可以放置。
4. 右键转化桌方块可以打开 GUI。
5. 手持 `portable_transmutation_table` 右键可以打开同一个 GUI。
6. 圆石 EMC = 1。
7. 8 个圆石可推导出熔炉 EMC = 8。
8. 放入 1 个圆石并转化后，队伍 EMC +1，圆石被解锁。
9. 放入 64 个圆石并转化后，队伍 EMC +64。
10. 同队伍玩家共享 EMC 和 learnedItems。
11. 玩家不能取出从未转化过的物品。
12. 客户端伪造未解锁物品取出时，服务端拒绝。
13. 带自定义名称的圆石可以转化，EMC 仍为 1。
14. 取出的圆石是普通无名称圆石。
15. 附魔铁剑可以转化，但附魔不增加 EMC。
16. 附魔铁剑转化后，取出的是普通无附魔铁剑。
17. 满耐久铁剑 EMC = 铁剑基础 EMC。
18. 半耐久铁剑 EMC = floor(铁剑基础 EMC * 剩余耐久 / 最大耐久)。
19. 剩余耐久为 0 或折算 EMC 为 0 时拒绝转化。
20. 左键点击已解锁物品取出 1 个。
21. Shift + 左键点击已解锁物品取出 64 个或最大堆叠数。
22. 右键可以把物品放入输入槽。
23. Shift-click 可以把可转化物品移动到输入槽。
24. 关闭 GUI 时输入槽物品不会消失。
25. 背包空间不足时，取出失败且不扣 EMC。
26. 服务器重启后 EMC 和 learnedItems 仍然存在。
27. 转化桌方块本身不保存队伍 EMC。
28. 便携式转化桌和方块转化桌使用同一 GUI 和同一数据。
29. 本模组转化桌与便携式转化桌优先禁止被转化，避免递归与刷物品。

## 23. 代码质量要求

- 不要留下无法编译的伪代码。
- 不要只写示例，必须真正修改项目代码。
- 如果 NeoForge 1.21.1 API 名称不确定，请查当前项目依赖中的真实 API。
- 不要把所有逻辑塞进一个类。
- 关键逻辑写注释，尤其是：
  - 队伍 key 如何生成
  - EMC 如何推导
  - 为什么 NBT 不计价
  - 耐久 EMC 如何折算
  - 服务端如何验证取出请求
  - 为什么背包满时不扣 EMC
- 玩家可见提示尽量使用 translatable component。
- 基础 EMC 值应通过数据文件或集中表管理，不要散落硬编码。
- TODO 不能影响编译和基础功能。

## 24. 给 Codex 的最终执行要求

请直接修改项目代码，并在完成后总结：

1. 新增了哪些文件。
2. 修改了哪些文件。
3. 如何运行和测试。
4. 当前已经实现的功能。
5. 仍然保留哪些 TODO。
6. 是否存在 NeoForge 1.21.1 API 兼容性风险。
