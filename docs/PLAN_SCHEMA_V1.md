# 简程计划文件 Schema v1

计划导入文件是 UTF-8 JSON，顶层 `format` 固定为 `jiancheng.plan`，`schemaVersion` 固定为整数 `1`。这是“计划”而不是备份：不得包含状态、完成时间、批注、内部 UUID 或数据库字段。

## 可直接使用的示例

```json
{
  "format": "jiancheng.plan",
  "schemaVersion": 1,
  "timezone": "Asia/Shanghai",
  "days": [
    {
      "date": "2026-07-20",
      "tasks": [
        {
          "id": "2026-07-20-backend-01",
          "title": "学习 Spring 事务",
          "details": "理解事务传播和常见失效场景",
          "start": "09:00",
          "end": "10:30",
          "category": "后端"
        },
        {
          "id": "2026-07-20-exercise-01",
          "title": "力量训练",
          "start": "17:00",
          "end": "18:00",
          "category": "锻炼"
        }
      ]
    }
  ]
}
```

## 字段

| 路径 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `format` | string | 是 | 必须等于 `jiancheng.plan` |
| `schemaVersion` | integer | 是 | 必须等于 `1` |
| `timezone` | string | 是 | 有效 IANA 时区，例如 `Asia/Shanghai` |
| `days` | array | 是 | 日期对象数组，可为空 |
| `days[].date` | string | 是 | 严格 `YYYY-MM-DD`，必须是真实日期 |
| `days[].tasks` | array | 是 | 任务数组，可为空 |
| `tasks[].id` | string | 是 | 去除首尾空白后非空，最多 200 个 Unicode 码点；文件内唯一 |
| `tasks[].title` | string | 是 | 去除首尾空白后非空，最多 120 个 Unicode 码点 |
| `tasks[].details` | string | 否 | 最多 1000 个 Unicode 码点；空白值按未填写处理 |
| `tasks[].start` | string | 是 | 严格 24 小时制 `HH:mm` |
| `tasks[].end` | string | 是 | 严格 24 小时制 `HH:mm`，必须晚于 `start` |
| `tasks[].category` | string | 否 | 最多 40 个 Unicode 码点；空白值按未填写处理 |

限制：文件不超过 1 MiB；任务总数不超过 2000；最早与最晚任务日期的跨度不超过 365 天（即最多覆盖 366 个日历日）；JSON 嵌套深度不超过 24；不允许跨午夜。未知字段、未知版本、错误类型、非法 UTF-8 和 NUL 等危险控制字符会使整个文件失败。

所有文本只按普通文本显示，不解释为 HTML、Markdown、脚本、命令或路径。时间按文件中的墙上时间原样导入，不做时区换算；文件时区与设备时区不同时，应用会警告并要求用户确认。

## 重复、冲突和重叠

- 同一文件中重复 `id`：拒绝整个文件。
- 数据库中已有相同 external ID 且计划内容完全一致：预览标记为重复，确认后跳过。
- external ID 相同但内容不同：标记为冲突，禁止导入且不覆盖。
- 同日时间段重叠：只警告；用户确认后可导入。
- 任何验证或事务失败：零条写入，原数据不变。

## 给生成式 AI 的约束

只输出一个 JSON 对象，不要使用 Markdown 代码围栏、注释或尾随逗号。不要添加本文未定义的字段。为每个任务生成稳定、清楚且在文件内唯一的 `id`。同一天的任务可按开始时间排列；生成前检查所有日期、时间、长度、任务数和日期跨度。

## 常见错误

- 把 `schemaVersion` 写成字符串 `"1"`。
- 使用 `9:00`、`24:00` 或不存在的日期。
- `end` 等于或早于 `start`，或用次日时间表达跨午夜。
- 添加 `status`、`annotation`、`completedAt` 等备份/执行字段。
- 重复使用同一个任务 ID，或把旧 ID 用于内容不同的新任务。
