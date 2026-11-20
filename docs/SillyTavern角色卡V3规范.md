# SillyTavern 角色卡 V3（Character Card V3）规范中文整理

> **规范性来源**：Character Card V3 Specification（简称 CCv3），社区规范仓库 `kwaroran/character-card-spec-v3`，主文档 `SPEC_V3.md`，本文按其正文（`spec_version = "3.0"`）逐条整理。
> **SillyTavern 侧行为**：核对 SillyTavern `release` 分支源码 `src/character-card-parser.js`（PNG 读写）与 `src/validator/TavernCardValidator.js`（版本判定与校验）。
> **配套文件**：[example.json](./example.json) —— 覆盖全部 V3 字段的空模板。

---

## 0. 一页速览

| 项目 | 内容 |
| --- | --- |
| 顶层结构 | `{ "spec", "spec_version", "data" }` 三个键 |
| `spec` | 固定 `"chara_card_v3"` |
| `spec_version` | 固定 `"3.0"`（导入方不得因版本号不等于 3.0 就拒绝） |
| 文本字段位置 | 全部在 `data.*` 下（与 V2 相同，V3 是 V2 的超集） |
| V3 新增字段 | `nickname`、`creator_notes_multilingual`、`source`、`group_only_greetings`、`assets`、`creation_date`、`modification_date` |
| V3 强化字段 | `character_book`（角色书，语义收紧）、`creator_notes`（让位于多语言版本）、条目内 `use_regex` / `constant` |
| 承载形式 | PNG/APNG 内嵌（`ccv3` tEXt chunk）、纯 JSON、CHARX（zip） |
| 未知字段 | 忽略但**不得拒绝导入**，可原样保存以便再导出；不得自造顶层字段，私有数据放 `extensions` |

---

## 1. 版本关系与判定

### 1.1 三个版本

| 版本 | `spec` | `spec_version` | 字段位置 | 说明 |
| --- | --- | --- | --- | --- |
| V1 | 无 `spec` 字段 | 无 | 顶层 | `name/description/personality/scenario/first_mes/mes_example` 六个字段，全部必填、必须默认空串 |
| V2 | `"chara_card_v2"` | `"2.0"` | `data.*` | 引入 `creator_notes`、`system_prompt`、`alternate_greetings`、`character_book`、`tags`、`extensions` 等 |
| V3 | `"chara_card_v3"` | `"3.0"` | `data.*` | V2 的超集；新增多语言备注、昵称、来源、群聊开场白、资产、时间戳；重定义角色书 |

V3 规范原文表述：V3 的 `CharacterCard` 对象是 V2 `TavernCardV2` 的**超集**，只有被"修改"和"移除"的字段例外（3.0 没有真正移除任何字段）。

### 1.2 版本判定规则

```
有 spec 且 spec == "chara_card_v2"  → V2
有 spec 且 spec == "chara_card_v3"  → V3
无 spec，且顶层有 V1 六字段         → V1
```

- `spec_version` 用**浮点数**比较：`> 3.0` 视为更新版本（应提示用户"该卡由更新版本规范创建，部分新特性可能不被支持"，但**仍应允许导入**）；`< 3.0` 视为旧版本。
- 3.0 是首个版本，规范**没有定义任何默认字段**；未来版本新增字段时，旧应用应补默认值而不是报错。
- 规范要求：不认识的字段忽略、但不拒绝整卡导入；应用**可以**把这些字段原样存下来，以便导出时信息不丢。

### 1.3 SillyTavern 的实际判定顺序（已核对源码）

`TavernCardValidator.validate()` 依次尝试 V1 → V2 → V3，**V1 最先命中**：

- V1：顶层同时存在 `name/description/personality/scenario/first_mes/mes_example`（只判"存在"，不判类型）。
- V2：`spec === 'chara_card_v2'` 且 `spec_version === '2.0'`，且 `data` 里 14 个字段齐全、`alternate_greetings`/`tags` 为数组、`extensions` 为对象，另外校验 `character_book.extensions`/`entries`。
- V3：`spec === 'chara_card_v3'` 且 `3.0 <= Number(spec_version) < 4.0` 且 `data` 是对象 —— **`data` 内部字段一概不查**。

⚠️ 两点后果：

1. **ST 对 V3 的校验极其宽松**，`data` 为 `{}` 也能过。导入方若想健壮，必须自己做字段级补默认值。
2. **不要在 V3 卡里保留顶层 V1 字段**（例如把旧卡数据残留在顶层），否则 ST 会把它当 V1 卡解析，`data` 整块丢失。

---

## 2. 三种承载形式

### 2.1 PNG / APNG 内嵌（最常见）

- 卡对象必须写入 PNG/APNG 的 **`tEXt` chunk**，keyword 为 **`ccv3`**，value 为 **JSON 字符串经 UTF-8 → Base64** 后的内容。
- 可以回退读取 V2 的 `chara` chunk。若同时存在 `chara` 与 `ccv3`，**必须优先使用 `ccv3`**。
- **回填（backfill）**：只支持 V2 的应用，可以把 `ccv3` 里的卡降级成 V2 对象再使用。规范要求此时**在降级后的 `creator_notes` 里加一句提示**（除非这张卡本来就是专为 V2 导出的），英文原句为：

  > This character card is Character Card V3, but it is loaded as a Character Card V2. Please use a Character Card V3 compatible application to use this character card properly.

  降级生成/写入的 `chara` 内容应做裁剪。
- 附加资产也可塞进 PNG：tEXt keyword 为 `chara-ext-asset_:{path}`，value 为 Base64 二进制，可用 `__asset:{path}` 引用。**规范明确建议新应用不要实现这套，改用 CHARX。**

**SillyTavern 实测行为（源码 `character-card-parser.js`）**

- 写卡（`write`）：先删掉原有的 `chara`/`ccv3` tEXt chunk，写入 `chara = Base64(V2 JSON)`，随后**把同一份 V2 JSON 的 `spec`/`spec_version` 改成 V3 再写一份 `ccv3`**。
  → 结论：**ST 导出的 `ccv3` chunk 通常不含 V3 专有字段**（`nickname`、`assets`、`source` 等）。导入方不能假设"有 ccv3 chunk 就一定有 V3 新字段"。
- 读卡（`read`）：取出所有 `tEXt`，keyword **大小写不敏感**，优先 `ccv3`，其次 `chara`，都没有则抛错。
- ST 只从 **PNG** 读内嵌元数据（webp/jpeg 内嵌不在解析范围）。

**实现提示**

- `tEXt` 的 keyword 上限 79 字节、value 按 Latin-1 存；Base64 是纯 ASCII，不会有编码问题，但**不要把原始 JSON 直接塞进 tEXt**。
- 解析 PNG 时按 chunk 长度逐段跳读，忽略 CRC 校验失败以外的异常，遇到 `IEND` 停止；重复的 `chara` chunk 在真实卡里存在（可能是不同修订版），取第一个即可，但要意识到"存在多份"。

### 2.2 纯 JSON

文件内容**就是**卡对象本身（没有外层包装）。`.json` 文件为 UTF-8 编码。

### 2.3 CHARX（zip 容器）

| 规则 | 内容 |
| --- | --- |
| 扩展名 | `.charx` |
| 卡文件 | zip 根目录 `card.json`，内容为卡对象 |
| 资产引用 | `embeded://path/to/asset.png`（**规范原文就拼作 `embeded`**，少一个 d；大小写敏感，`/` 分隔） |
| 默认资产 | `ccdefault:` |
| 目录约定 | `assets/{type}/images/`、`audio/`、`video/`、`l2d/`、`3d/`、`ai/`、`fonts/`、`code/`、`other/` |
| `{type}` 取值 | 按用途：`icon`、`background`、`emotion`、`user_icon`，其余用 `other`；平台私有类型可用自有前缀 |
| 其它 | zip 不应加密；文件名/路径建议只用 ASCII；应用可在根目录放自己的 JSON（非卡数据）；应用可拒收超大/损坏/加密的文件 |

目录约定是 **SHOULD**，不是硬性要求 —— 应用不实现这些特性也没问题，但不能因此假定目录一定存在。

---

## 3. 顶层结构

```ts
interface CharacterCardV3 {
  spec: 'chara_card_v3'
  spec_version: '3.0'
  data: {
    // 来自 CCv2
    name: string
    description: string
    tags: Array<string>
    creator: string
    character_version: string
    mes_example: string
    extensions: Record<string, any>
    system_prompt: string
    post_history_instructions: string
    first_mes: string
    alternate_greetings: Array<string>
    personality: string
    scenario: string

    // 相对 CCv2 有变化
    creator_notes: string
    character_book?: Lorebook

    // CCv3 新增
    assets?: Array<{ type: string; uri: string; name: string; ext: string }>
    nickname?: string
    creator_notes_multilingual?: Record<string, string>
    source?: string[]
    group_only_greetings: Array<string>
    creation_date?: number
    modification_date?: number
  }
}
```

注意：虽然 ST 的校验很松，但**按规范**，上面列出的 V2 继承字段与 `group_only_greetings` 都应存在，字符串字段缺失时按空串处理、数组字段按空数组处理。

---

## 4. `data` 字段详解

### 4.1 总表

| 字段 | 类型 | 必填 | 空值/默认 | 用途 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | `""` | 角色名，卡片的唯一标识性名称 |
| `description` | string | 是 | `""` | 人设主体，默认应进入提示词 |
| `personality` | string | 是 | `""` | 性格摘要，默认应进入提示词 |
| `scenario` | string | 是 | `""` | 场景/背景，默认应进入提示词 |
| `first_mes` | string | 是 | `""` | 开场白，必须由角色先发 |
| `mes_example` | string | 是 | `""` | 对话示例（few-shot） |
| `creator_notes` | string | 是 | `""` | 创作者备注，**禁止**进入提示词 |
| `system_prompt` | string | 是 | `""` | 覆盖前端主系统提示词，空串则用前端默认 |
| `post_history_instructions` | string | 是 | `""` | 覆盖"越狱/后置指令"，空串则用前端默认 |
| `alternate_greetings` | string[] | 是 | `[]` | 备选开场白，对应"滑动/重开" |
| `tags` | string[] | 是 | `[]` | 分类标签，不参与提示词 |
| `creator` | string | 是 | `""` | 作者，不参与提示词 |
| `character_version` | string | 是 | `""` | 卡版本号，不参与提示词 |
| `extensions` | object | 是 | `{}` | 应用私有数据，必须保留未知键 |
| `character_book` | Lorebook | 否 | 省略 | 角色专属世界书 |
| `nickname` | string | 否 | 省略 | 提示词中替代 `name` 的称呼 |
| `creator_notes_multilingual` | object | 否 | 省略 | 多语言创作者备注 |
| `source` | string[] | 否 | 省略 | 来源 ID / URL，追加式 |
| `group_only_greetings` | string[] | **是** | `[]` | 仅群聊使用的额外开场白 |
| `assets` | object[] | 否 | 省略 | 图标/背景/表情等资产 |
| `creation_date` | number | 否 | 省略 | 创建时间，Unix 秒 |
| `modification_date` | number | 否 | 省略 | 修改时间，Unix 秒 |

### 4.2 继承自 V1 的六个文本字段

- 提示词替换规则（**大小写不敏感**）：`{{char}}` 与 `<BOT>` 替换为角色名，`{{user}}` 与 `<USER>` 替换为用户显示名；用户默认名必须存在。
- `{{char}}/{{user}}` 是否要在 `name` 字段内替换，规范未定义（边界情况）。
- `description`/`personality`/`scenario`/`first_mes`/`mes_example` 默认都应进入提示词。
- `first_mes`：对话必须由角色先开口，且第一条消息内容就是该字符串。
- `mes_example`：约定用 `<START>` 分隔多段示例对话，`<START>` 可被渲染成"开始新对话"之类的系统消息；实际对话占满上下文后，示例应被优先裁剪。

```text
<START>
{{user}}: hi
{{char}}: hello
<START>
{{user}}: hi
Haruhi: hello
```

### 4.3 V2 引入的字段

| 字段 | 关键约束 |
| --- | --- |
| `creator_notes` | **禁止**用于提示词；前端应显著展示（至少一段）。V3 起受多语言字段影响，见下 |
| `system_prompt` | 默认行为是用它替换用户设置的"系统提示词"；**空串时**必须回退到用户设置或内置默认；必须支持 `{{original}}` 占位符（替换为"原本会使用的系统提示词"） |
| `post_history_instructions` | 同上，作用于"越狱/ujb"位置；同样支持 `{{original}}` |
| `alternate_greetings` | 每个元素是一条可切换的首条消息（swipe） |
| `tags` | 任意字符串，仅用于排序/筛选，**不应**进入提示词 |
| `creator` | 展示用，禁止进入提示词 |
| `character_version` | 展示/排序用，禁止进入提示词 |
| `extensions` | **必须**默认 `{}`；内容任意；读写不得破坏未知键；建议用命名空间前缀（如 `agnai/voice`、`agnai_voice`、`agnai: { ... }`） |

### 4.4 V3 新增/变更字段

#### `character_book`

- 值必须是 Lorebook 对象或 `undefined`。
- 存在时应视为"角色专属世界书"，前端**默认必须启用**；编辑器必须以该格式保存。
- 角色书应与全局世界书**叠加**生效（V2 的表述是"角色书优先"）。

#### `creator_notes`（语义变化）

优先级规则：

1. 若 `creator_notes_multilingual` 不存在 → `creator_notes` 就是创作者备注。
2. 若 `creator_notes_multilingual` 存在但**没有 `en` 键** → `creator_notes` 被当作英文（`en`）备注。
3. 若 `creator_notes_multilingual` 存在且**有 `en` 键** → **忽略** `creator_notes`。

#### `nickname`

- string 或 undefined。
- 存在时，提示词中的 `{{char}}`、`<char>`、`<bot>` 应替换成 `nickname`，而不是 `name`。

#### `creator_notes_multilingual`

- 值必须是对象或 undefined；key 为 **ISO 639-1 语言码（不带地区码，如 `zh`、`en`、`ja`）**，value 为字符串。
- 前端应按用户客户端语言展示对应备注，也可以提供语言切换。

#### `source`

- string[] 或 undefined；元素是来源 ID 或 HTTP/HTTPS URL。
- **不应**让用户编辑。URL 元素可以提供"在新标签页打开"。
- 应用只应**追加**元素，不应删除/修改非自己添加的元素；自己添加的可以编辑。
- 但如果条目会显著拖慢应用、影响可用性，或来源有害，可以删除，并**应告知用户已删除**。

#### `group_only_greetings`

- **必填**，string 数组，可以为空。
- 语义是"仅群聊使用的额外开场白"。

#### `assets`

- 值必须是对象数组或 undefined。**为 undefined 时，必须等价于**：

```ts
[{ type: 'icon', uri: 'ccdefault:', name: 'main', ext: 'png' }]
```

- 每个元素含 `type`、`uri`、`name`、`ext`，都是字符串。
- `uri` 允许：HTTP/HTTPS URL、Base64 data URL、`embeded://...`（内嵌路径）、`ccdefault:`（应用默认资产）。应用可出于安全忽略非 HTTPS 的 URL；可忽略不支持的格式或过大的 Base64。
- `ext`：小写、不带点，未知格式写 `unknown`；规范建议至少支持 `png`、`jpeg`、`webp`；`uri` 为 `ccdefault:` 时忽略该字段。
- `type` 语义：

| `type` | 语义 | 关键规则 |
| --- | --- | --- |
| `icon` | 角色头像 | 多个 icon 时**必须**恰有一个 `name` 为 `main`；`ccdefault:` 指向卡图本身 |
| `background` | 角色背景图 | 多个 background 时 `main` **至多一个**；没有 main 就用第一个 |
| `user_icon` | 用户头像 | `ccdefault:` **不得**指向卡图；应用若支持 persona，应在存在 user_icon 时禁用 persona |
| `emotion` | 表情/差分 | `name` 作为表情名（`happy`/`sad`/…），默认用 `neutral` |
| `x_*` | 自定义 | 自定义类型必须以下划线前缀 `x_` 开头以避免冲突 |

- 不支持某种资产时**应保留数据**以便再导出；实在无法保存时可以不导出，但**必须提示用户**。

#### `creation_date` / `modification_date`

- Unix 时间戳（**秒**，UTC）。允许写 `0` 表示"未知"。
- `creation_date`：创建时写入，**不得**让用户编辑，已存在则不得修改。
- `modification_date`：导出时写入/更新，编辑时可更新，不得让用户编辑。

---

## 5. 角色书（Lorebook）

### 5.1 结构

```ts
type Lorebook = {
  name?: string
  description?: string
  scan_depth?: number
  token_budget?: number
  recursive_scanning?: boolean
  extensions: Record<string, any>   // 必填
  entries: Array<{
    keys: Array<string>
    content: string
    extensions: Record<string, any>
    enabled: boolean
    insertion_order: number
    case_sensitive?: boolean

    // V3 新增
    use_regex: boolean

    // V2 中可选，V3 起要求实现
    constant?: boolean

    // 可选
    name?: string
    priority?: number
    id?: number | string
    comment?: string

    selective?: boolean
    secondary_keys?: Array<string>
    position?: 'before_char' | 'after_char'
  }>
}
```

单独导出世界书时，格式为：

```json
{ "spec": "lorebook_v3", "data": { "extensions": {}, "entries": [] } }
```

### 5.2 世界书级字段

| 字段 | 语义 |
| --- | --- |
| `name` | 标识用，不参与提示词 |
| `description` | 注释用，不参与提示词 |
| `scan_depth` | 只扫描**最近 N 条**消息来匹配词条；非聊天环境可忽略 |
| `token_budget` | 词条总 token 超预算时，按 `priority`（无则 `insertion_order`）淘汰低优先词条 |
| `recursive_scanning` | `true` 时，被其它词条 `content` 命中也可算匹配（不受 `scan_depth` 限制）；`false` 时**禁止**；`undefined` 由应用自行决定 |
| `entries` | 必填，可为空数组 |

### 5.3 词条字段

| 字段 | 必填 | 语义 |
| --- | --- | --- |
| `keys` | 是 | 主关键词数组；聊天记录命中其一即可能激活 |
| `content` | 是 | 命中后注入的正文；**只注入一次**；空串则不注入任何内容；可含装饰器 |
| `extensions` | 是 | 应用私有数据，必须保留未知键 |
| `enabled` | 是 | `false` 时**任何情况下都不激活** |
| `insertion_order` | 是 | 数值越小越先插入（越靠前）；相同则顺序由应用决定 |
| `case_sensitive` | 否 | `true` 区分大小写，`false` 不区分，`undefined` 由应用决定 |
| `use_regex` | **是（V3）** | `true` 时把 `keys` 当正则；任一 pattern 非法则该词条**不得**视为匹配 |
| `constant` | 否（V3 起要求实现） | `true` 时只要装饰器条件满足就恒定注入，忽略 `keys`/`secondary_keys` |
| `secondary_keys` | 否 | 与 `selective` 配合：`selective === true` 时还须命中其中之一 |
| `selective` | 否 | 是否启用 `secondary_keys` 过滤 |
| `position` | 否 | `before_char` / `after_char`，相对角色定义的前后位置 |
| `name` / `comment` | 否 | 仅标识/注释，不参与提示词（AgnAI、Risu、ST 兼容字段） |
| `id` | 否 | 标识用（ST、Risu 兼容），`number` 或 `string` |
| `priority` | 否 | 超预算时**数值小的先被丢弃**（AgnAI 兼容） |

规范里两处反直觉的写法，实现时请注意：

- `secondary_keys` 的描述是"逗号分隔的多个值（字符串）"，但同时按数组类型定义 —— 实现按**字符串数组**处理即可。
- `use_regex` 为 `true` 时，规范要求忽略 `secondary_keys` 与 `constant`（原文如此）。若与目标前端实际行为冲突，以目标前端为准。

---

## 6. 装饰器（Decorators）

装饰器是写在词条 `content` 里、以 `@@` 开头、以换行结尾的特殊行，用来在不增加字段的前提下扩展语义。

### 6.1 语法与通用规则

```text
@@decorator_with_string_value value
@@decorator_with_number_value 4
@@decorator_with_multiple_values value1,value2,value3
@@decorator_without_value

Some lorebook content
```

- 名称全小写蛇形（`snake_case`），可带多个逗号分隔的值。
- **不认识或值非法的装饰器直接忽略**，不要因此丢弃整个词条。
- 同名装饰器**只取第一个**（例外：规范声明可重复的，如 `@@additional_keys`；或应用自定义的）。
- 注入前必须**剥离装饰器**，并裁掉装饰器前后多余的换行。
- **回填成 V2 时必须移除所有装饰器。**
- **回退装饰器**：在原装饰器后紧跟一行以 `@@@` 开头的同名替代。应用不认识前者时用后者。回退链可以多层，从上往下取第一个能识别的；规范要求至少支持 5 层。

```text
@@risu_only_decorator 4
@@@activate_after 4
@@depth 5
@@@instruct_depth 5
```

### 6.2 装饰器一览

| 装饰器 | 值 | 作用 |
| --- | --- | --- |
| `@@activate_only_after` | number | 助手消息数达到 N 之前不激活 |
| `@@activate_only_every` | number | 助手消息数对 N 取余为 0 时才激活 |
| `@@keep_activate_after_match` | 无 | 一旦激活过，之后恒定激活 |
| `@@dont_activate_after_match` | 无 | 一旦激活过，之后不再激活 |
| `@@depth` | number | 插入到倒数第 N 条消息处；N 从 1 起，`0` + `role: assistant` 且支持 prefill 时可作为预填充 |
| `@@instruct_depth` | number | 同上，但按 token 数计（非聊天场景用） |
| `@@reverse_depth` | number | 等价于 `@@depth (总消息数 - N)` |
| `@@reverse_instruct_depth` | number | 等价于 `@@instruct_depth (总 token 数 - N)` |
| `@@role` | `assistant` / `system` / `user` | 指定注入内容所属角色 |
| `@@scan_depth` | number | 覆盖世界书级扫描深度（按消息数） |
| `@@instruct_scan_depth` | number | 覆盖扫描深度（按 token 数） |
| `@@is_greeting` | number | 仅当当前开场白序号等于 N 时激活（`first_mes` 为 0，`alternate_greetings[0]` 为 1…） |
| `@@position` | `after_desc` / `before_desc` / `personality` / `scenario` | 指定注入位置；应用可扩展更多位置 |
| `@@ignore_on_max_context` | 无 | 上下文将满时不激活 / 优先被裁掉 |
| `@@additional_keys` | 逗号分隔值 | 追加匹配关键词（可重复出现）；`use_regex` 时按正则处理 |
| `@@exclude_keys` | 逗号分隔值 | 命中任一则**不**激活 |
| `@@is_user_icon` | string | 仅当当前用户头像 `name` 等于该值才激活 |
| `@@dont_activate` | 无 | 除非同时有 `@@activate`，否则不激活 |
| `@@activate` | 无 | 强制激活 |
| `@@disable_ui_prompt` | `post_history_instructions` / `system_prompt` | 让前端**可选地**禁用对应 UI 提示词 |

优先级与边界：

- `@@position` 存在时忽略 `@@depth`/`@@instruct_depth`；`@@depth` 存在时忽略 `@@reverse_depth`。
- `@@depth`/`@@instruct_depth` 的 N 大于消息（token）总数时，插到最旧一条之前；N 小于 1 时，插到最新一条之后。
- 无法把内容插入消息序列的环境，退化为"高/低优先级位置"放置：N 大于总数放低优先级，否则放高优先级。

---

## 7. 宏 / 花括号语法（CBS）

检测时**大小写不敏感**。应用可扩展，但新增宏必须用 `{{ }}` 包裹。

| 宏 | 替换结果 |
| --- | --- |
| `{{char}}` | `nickname`；`nickname` 为空或缺失时用 `name` |
| `{{user}}` | 用户显示名 / 当前 persona 名 |
| `{{random:A,B,C}}` | 随机取一个值，逗号可用 `\,` 转义 |
| `{{pick:A,B,C}}` | 随机取一个值，但同一提示词内应保持稳定（"抽签一次，全程一致"） |
| `{{roll:N}}` | 1..N 的随机整数；`N` 可写成 `d6` 形式（大小写不敏感） |
| `{{// A}}` | 替换为空串，且不展示给用户；**不**参与世界书匹配 |
| `{{hidden_key:A}}` | 同 `{{// A}}`，但递归扫描时**参与**匹配 |
| `{{comment: A}}` | 发送时为空串；在消息里可作为行内注释展示；**不**参与匹配 |
| `{{reverse:A}}` | 输出 A 的反转字符串 |

小坑：规范中 `{{pick}}` 的示例写成了 `{{pick::Hello,Hi,Hey}}`（双冒号），属于原文笔误，正确写法是 `{{pick:A,B,C}}`。

---

## 8. SillyTavern 兼容性要点

### 8.1 校验宽松

ST 对 V3 只校验 `spec`、`spec_version`、`data` 是三要素，`data` 内字段不校验。因此：

- 不能指望 ST **导出**的卡字段齐全；也不能指望 ST **接受**的卡符合规范。
- 导入方要自己补默认值：字符串补 `""`，数组补 `[]`，对象补 `{}`。

### 8.2 归一化

ST 内部把 V1/V2/V3 统一归一到 V2 规范形（`data.*`）再使用。自己做导入时，建议同样先归一化：

```
V1  → 顶层六字段搬进 data，spec=chara_card_v2，creatorcomment → creator_notes
V2  → 原样
V3  → 原样（新字段单独保存，老逻辑按 V2 字段消费）
```

### 8.3 ST 私有扩展字段（非规范）

ST 会把自身数据写进 `data.extensions`，键名随版本变化，导入时应**整体保留**：

| 键 | 含义 |
| --- | --- |
| `world` | 绑定的世界书名称 |
| `depth_prompt` | `{ prompt, depth, role }`，摘要/深度提示词 |
| `talkativeness` | 群聊发言倾向 |
| `fav` | 是否收藏 |
| `regex_scripts` | 角色级正则脚本数组 |

世界书词条的 `extensions` 里也常见：`position`（数值 0-7）、`selectiveLogic`、`depth`、`probability`、`useProbability`、`group`、`group_weight`、`group_override`、`exclude_recursion`、`prevent_recursion`、`delay_until_recursion`、`scan_depth`、`match_whole_words`、`case_sensitive`、`role`、`display_index`、`vectorized`、`sticky`、`cooldown`、`delay`、`automation_id`。

> 规范层的 `position` 只有 `before_char`/`after_char` 两值；ST 的"作者注/深度注入/出口"等更细位置放在词条 `extensions.position` 里。解析卡内 `character_book` 时应两处都看。

### 8.4 其它差异

| 项 | ST 行为 |
| --- | --- |
| 封面/头像 | 就是 PNG 卡图本身；`assets` 基本被忽略 |
| `group_only_greetings` | 仅在群聊中作为额外开场白 |
| `source` | 一般不做展示 |
| 时间戳 | `create_date` 等历史字段另有残留，不与 `creation_date` 混用 |
| CHARX | 新版支持导入，但生态里仍以 PNG 卡为主 |

---

## 9. 导入实现清单

### 9.1 流程

```text
1. 探测容器
   - 首字节 89 50 4E 47 0D 0A 1A 0A  → PNG，走 chunk 解析
   - 首字节 50 4B 03 04              → zip，按 CHARX 处理（找 card.json）
   - 其它                            → 当 JSON 文本解析
2. 取 JSON
   - PNG：遍历 tEXt，keyword 小写后优先 ccv3，其次 chara；base64 解码 → UTF-8
3. 判定版本
   - spec == chara_card_v3 → V3
   - spec == chara_card_v2 → V2
   - 否则顶层六字段齐全 → V1
4. 规范化
   - V1/V2 → 统一到 V3 形状（补 spec/spec_version/group_only_greetings/extensions 等）
   - 所有缺失必填字段补默认值，不因缺字段直接失败
5. 消费字段
   - description / personality / scenario / first_mes / mes_example / system_prompt
     / post_history_instructions → 提示词装配
   - alternate_greetings + first_mes + group_only_greetings → 开场白列表
   - character_book → 世界书（解析装饰器、剥离装饰器）
   - assets / PNG 原图 → 头像
6. 落库
   - 原始 JSON 原样保存一份（"底账"），便于无损再导出
   - 未知字段一律保留
```

### 9.2 安全检查

- **提示词注入**：`description`、`content` 等可能含 HTML/JS。展示层要转义或白名单净化；`<style>`、`javascript:`、`onerror=` 一律拦。
- **资产**：`uri` 为 `http:` 的默认不加载；`data:` URL 校验 MIME 与体积上限；`embeded://` 只允许读容器内路径，禁止 `..` 穿越。
- **体积**：PNG 卡里 tEXt 可能高达数 MB，Base64 解码前先按长度设限。
- **时间**：`creation_date` / `modification_date` 可能异常大或为负，做范围校验。
- **正则**：`use_regex` 的 pattern 可能造成灾难性回溯，加超时或长度限制。

### 9.3 常见坑

1. 把 V3 数据写到顶层（漏了 `data`）→ 被识别成 V1。
2. 保留顶层 V1 残留字段 → ST 优先判 V1，`data` 丢失。
3. 认为"有 `ccv3` chunk 就一定有 V3 字段"→ ST 导出的 chunk 只是 V2 换了 spec。
4. 直接拿 `assets[0]` 当头像 → `assets` 可能不存在或为空数组。
5. `enabled: false` 的词条仍被注入。
6. 装饰器没剥离就进了提示词。
7. `{{char}}` 替换用了 `name` 而忽略 `nickname`。
8. 丢弃未知字段，再导出时信息全丢。
9. 用 `spec_version === "3.0"` 严格比较 → 遇到 `3.1`/`3.0.0` 直接拒收。
10. 期望 `character_book.entries[].position` 一定是字符串 → 真实卡里常见缺失或数值。

---

## 10. 最小可用示例

```json
{
  "spec": "chara_card_v3",
  "spec_version": "3.0",
  "data": {
    "name": "角色名",
    "description": "角色的外观、身份、经历与行为方式。",
    "personality": "冷静、话少、偶尔毒舌。",
    "scenario": "深夜的旧书店，只有你和她在。",
    "first_mes": "「……还没关门。要进来吗？」",
    "mes_example": "<START>\n{{user}}: 你在看什么？\n{{char}}: 一本没有书名的书。",
    "creator_notes": "建议 temperature 0.8，可用中文对话。",
    "system_prompt": "",
    "post_history_instructions": "",
    "alternate_greetings": [],
    "tags": ["奇幻", "日常"],
    "creator": "your-name",
    "character_version": "1.0",
    "extensions": {},
    "group_only_greetings": []
  }
}
```

这就是"能跑"的最小集：`character_book`、`assets`、`nickname`、`source`、时间戳都可省略。

### 空模板字段取舍

[example.json](./example.json) 保留了全部 V3 字段（值为空），便于直接对照数据结构。实际使用时：

| 字段 | 建议 |
| --- | --- |
| `character_book` | 不需要角色书就整块删掉，留空 `entries` 也合法 |
| `assets` | 无图资产时删掉（规范回退为默认图标）；留 `[]` 则表示"明确没有图标" |
| `nickname` | 不需要别名就删掉，或留空串（等同未设置） |
| `creator_notes_multilingual` | 不写多语言就删掉或留 `{}` |
| `source` / 时间戳 | 导出时再补；`0` 表示"未知"而非"1970 年" |

---

## 11. 参考

- Character Card V3 规范（原文）：<https://github.com/kwaroran/character-card-spec-v3> / [SPEC_V3.md](https://github.com/kwaroran/character-card-spec-v3/blob/main/SPEC_V3.md)
- Character Card V2 规范：<https://github.com/malfoyslastname/character-card-spec-v2>
- SillyTavern 源码 `src/character-card-parser.js`：<https://github.com/SillyTavern/SillyTavern/blob/release/src/character-card-parser.js>
- SillyTavern 源码 `src/validator/TavernCardValidator.js`：<https://github.com/SillyTavern/SillyTavern/blob/release/src/validator/TavernCardValidator.js>
- SillyTavern 文档：<https://docs.sillytavern.app/>
