# 简程 AI 分析导出 Schema v1

AI 分析导出是用户主动创建的只读 UTF-8 JSON，格式为 `jiancheng.analysis`、版本为 `1`。它不能用于恢复，且不包含内部 UUID、设备标识、账号、数据库路径、原导入路径、日志或构建信息。

## 顶层字段

| 字段 | 说明 |
| --- | --- |
| `format` | 固定 `jiancheng.analysis` |
| `schemaVersion` | 固定整数 `1` |
| `exportedAt` | 导出时刻，Unix epoch 毫秒 |
| `timezone` | 导出时设备的 IANA 时区 |
| `range` | `startDate`、`endDate`，均为 ISO 本地日期 |
| `includedFields` | `title`、`details`、`annotation`、`revisions` 四个布尔值 |
| `tasks` | 日期范围和可选分类过滤后的任务 |

## 任务字段

每条任务使用本次文件内的局部引用 `T001`、`T002`……，不暴露数据库 UUID。字段如下：

- `taskRef`：局部引用。
- `category`：可选分类。
- `originalPlan`：第一次编辑前的计划；从未编辑时等于当前计划。
- `currentPlan`：当前日期、开始/结束时间和用户选择包含的文本。
- `plannedDurationMinutes`：当前计划分钟数。
- `execution`：`finalStatus`、`statusChangedAt`、可选 `completedAt`、按导出时刻计算的 `overdueUnresolved`。
- `planModified`：是否有计划修改记录。
- `revisions`：用户开启时存在；每项含 `revisionNumber`、`changedAt` 和 `planBeforeChange`。
- `annotation`：只有用户主动开启“包含批注”时存在；它是用户主观备注，不是客观事实。

`originalPlan`、`currentPlan` 和 `planBeforeChange` 均包含 `date`、`start`、`end`，并按导出选项决定是否包含 `title` 与 `details`。时间为 `HH:mm`，时间戳为 Unix epoch 毫秒。

## 默认隐私选项

- 标题：开启。
- 说明：关闭。
- 简单批注：关闭。
- 计划修改记录：开启。
- 分类：默认不过滤。

导出前界面显示日期范围、所含字段、批注开关、预计文件名和下一步由系统文件界面选择的位置。保存由 Storage Access Framework 完成。

## AI 使用提示

把 `execution` 当作执行记录，把 `annotation` 当作用户的可选主观描述；不要把批注推断为诊断或客观事实。比较 `originalPlan`、`currentPlan` 和 `revisions` 时按 `revisionNumber` 理解计划演变。`PLANNED` 且 `overdueUnresolved=true` 表示时间已过但用户尚未处理，并不等于 `NOT_DONE`。
