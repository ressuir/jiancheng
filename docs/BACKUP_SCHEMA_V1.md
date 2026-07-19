# 简程完整备份 Schema v1

完整备份是应用生成、可由应用恢复的 UTF-8 JSON。顶层 `format` 为 `jiancheng.backup`，`schemaVersion` 为 `1`。用户不应手工修改；恢复采用“完整替换”，不会合并。

## 顶层结构

```json
{
  "format": "jiancheng.backup",
  "schemaVersion": 1,
  "exportedAt": 1784512800000,
  "timezone": "Asia/Shanghai",
  "tasks": [],
  "importBatches": []
}
```

`exportedAt`、所有 `*At` 字段均为 Unix epoch 毫秒。文件最大 10 MiB，最多 10000 个任务，JSON 嵌套深度最多 32。未知字段、错误类型、未知版本、非法 UTF-8、非法 UUID 或不一致引用会被拒绝。

## `tasks[]`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | UUID string | 应用内部稳定任务 ID；恢复时原样保留 |
| `date` | `YYYY-MM-DD` | 当前计划日期 |
| `title` | string | 1–120 Unicode 码点 |
| `details` | string/null | 最多 1000 码点 |
| `plannedStartMinutes` | integer | 0–1439 |
| `plannedEndMinutes` | integer | 1–1439，且大于开始分钟 |
| `category` | string/null | 最多 40 码点 |
| `source` | enum | `MANUAL` 或 `IMPORT` |
| `externalId` | string/null | 导入任务必填，手动任务为 null |
| `importBatchId` | UUID string/null | 导入任务必须引用现有批次，手动任务为 null |
| `createdAt`、`updatedAt` | integer | 非负 epoch 毫秒 |
| `execution` | object | 唯一执行记录 |
| `revisions` | array | 计划修改前快照，编号从 1 连续递增 |

`execution` 包含：`status`（`PLANNED`、`COMPLETED`、`SKIPPED`、`NOT_DONE`、`CANCELED`）、`statusChangedAt`、可选 `completedAt`、可选 `annotation`、可选 `annotationCreatedAt` 和 `annotationUpdatedAt`。只有 `COMPLETED` 允许且要求 `completedAt`。无批注时三个批注字段均为空；有批注时创建/更新时间必填，正文最多 500 个 Unicode 码点。

`revisions[]` 包含 `id`、`revisionNumber`、`previousTitle`、`previousDetails`、`previousDate`、`previousStartMinutes`、`previousEndMinutes`、`previousCategory`、`changedAt` 和 `changeSource`（`USER_EDIT` 或 `IMPORT_UPDATE`）。快照应用与当前计划相同的日期、时间和文本限制。

## `importBatches[]`

每个批次包含 `id`（UUID）、`schemaVersion`（计划版本 1）、`format`（`jiancheng.plan`）、`importedAt`、`contentHash`（64 位十六进制 SHA-256）、`taskCount`，以及可选的 `sourceFileName`（仅可见文件名，最多 128 码点；不是绝对路径）。

## 恢复安全规则

应用先在内存中完整解析和验证，再显示任务、日期、批注和修改记录数量。当前数据库非空时，本次启动中必须先成功导出当前备份。用户第二次确认后，任务、执行、修改记录和批次才会在一个 Room 事务中替换；验证或事务失败时原数据保持不变。恢复不会删除数据库文件，也不绕过 Room Migration。
