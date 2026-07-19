# 简程

简程是一款简约、完全离线的 Android 时间段日程应用。它适合已经知道“今天要做什么”的人：把计划拆成清晰的时间段，随手更新执行状态、补一句批注，之后再回看历史或主动导出数据。

应用不包含账号、云同步、广告、遥测或内置 AI。日程默认只保存在设备本地，导入导出均通过 Android 系统文件选择器完成。

## 功能

- **今天**：按时间排序查看任务，识别当前时间段和过期未处理任务。
- **快速记录**：手动添加、编辑、删除任务，支持分类、说明和时间冲突提醒。
- **执行状态**：待处理、已完成、已跳过、未完成、已取消，并支持撤销最近的状态修改。
- **轻量批注**：每个任务可附加一段最多 500 个 Unicode 码点的简单批注；没有批注时不占界面空间。
- **历史回看**：按日期查看计划与执行结果，并保留计划修改记录。
- **计划导入**：严格校验 `jiancheng.plan` v1 JSON，先预览新增、重复、冲突、时区差异与时间重叠，再以单个事务写入。
- **完整备份**：导出或替换恢复 `jiancheng.backup` v1；危险恢复操作需要二次确认。
- **AI 分析导出**：主动生成 `jiancheng.analysis` v1，标题、说明、批注、分类和修改记录均可单独控制，批注默认关闭。
- **系统适配**：Material 3、浅色/深色模式、系统字体缩放和简体中文界面。

## 隐私设计

- 最终 APK 不申请 Android 权限，包括 `INTERNET` 和广泛存储权限。
- 没有 Service、Receiver、Provider、Deep Link、广告 SDK、统计 SDK 或崩溃上传 SDK。
- Room 数据库位于应用私有目录；应用不会扫描公共存储。
- 导入导出只访问用户在 Storage Access Framework 中明确选择的文档 URI。
- Manifest 设置 `allowBackup=false`，并通过 Android 12+ 与旧版备份规则排除所有应用数据域。

卸载或清除应用数据通常会删除本地日程。换机或卸载前，请先在“数据”页面导出完整备份。更多信息见[隐私与安全说明](docs/PRIVACY_SECURITY.md)。

## 界面结构

应用只有三个主要入口：

```text
今天  → 当天任务、状态、批注、添加与编辑
历史  → 按日期回看计划和执行结果
数据  → 计划导入、AI 分析导出、完整备份与恢复
```

## 环境要求

| 项目 | 要求或当前版本 |
| --- | --- |
| Android | 8.0（API 26）及以上 |
| compile / target SDK | 37 / 37 |
| JDK | 21，源码与字节码目标 Java 17 |
| Android Gradle Plugin | 9.3.0 |
| Gradle Wrapper | 9.5.0 |
| Kotlin | 2.3.21 |
| Compose BOM | 2026.06.00 |
| Room | 2.8.4 |

建议使用近期稳定版 Android Studio，并在 SDK Manager 中安装 Android SDK Platform 37 与 Build Tools 36.0.0。

## 获取与构建

```bash
git clone https://github.com/ressuir/jiancheng.git
cd jiancheng
```

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

Debug APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

该 APK 使用标准 Android Debug 证书，只适合开发和个人测试。仓库不包含 release keystore、密码、已签名发布包或长期签名材料。

在 Android Studio 中也可以直接打开仓库、等待 Gradle Sync，然后选择模拟器或开发设备运行 `app` 配置。

## 常用验证命令

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

`connectedDebugAndroidTest` 会操作当前连接的 Android 测试设备，请先确认目标是专用模拟器或明确授权的开发设备。

## 导入计划

计划文件是 UTF-8 JSON。顶层格式固定为 `jiancheng.plan`，当前 Schema 版本为 1：

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
        }
      ]
    }
  ]
}
```

限制包括：文件不超过 1 MiB、任务不超过 2000 个、日期跨度不超过 366 个日历日、不允许跨午夜。未知字段、非法 UTF-8、重复 ID、错误类型或冲突内容会阻止整个导入；同日时间重叠只会警告。

- [计划 Schema v1](docs/PLAN_SCHEMA_V1.md)
- [基础示例](samples/jiancheng-plan-example.json)
- [高密度日程示例](samples/jiancheng-plan-dense-demo-2026-07-19.json)

## 备份与分析数据

- [完整备份 Schema v1](docs/BACKUP_SCHEMA_V1.md)：覆盖任务、状态、完成时间、批注、修改记录和必要的导入批次，可由应用完整替换恢复。
- [AI 分析导出 Schema v1](docs/ANALYSIS_EXPORT_SCHEMA_V1.md)：使用局部引用区分原计划、当前计划、修改、执行结果与主观批注。

“AI 分析导出”只负责生成本地 JSON 文件。应用不会联网，也不会把文件自动发送给任何 AI 服务。

## 项目结构

```text
app/src/main/java/.../data     Room、计划导入、备份和分析导出
app/src/main/java/.../domain   任务校验与状态规则
app/src/main/java/.../ui       Compose 页面、导航、主题和 ViewModel
app/src/test                   JVM 单元测试
app/src/androidTest            Room、Migration 与 Compose 仪器测试
app/schemas                    Room 导出的数据库 Schema
docs                           数据格式、隐私与测试文档
samples                        可安全使用的虚构计划示例
```

整体依赖方向：

```text
Compose UI → ViewModel → Repository / import-export services → Room DAO → local database
```

## 测试状态

最近一次验证结果：

- JVM 单元测试：26/26 通过。
- Lint：0 错误，1 个 Gradle 版本提示。
- Debug APK、Android Test APK 和 unsigned Release 校验构建成功。
- API 35 x86_64 模拟器 Instrumentation：18 项均实际执行，14 通过、4 失败、0 跳过。
- Room/Repository/Migration：9/9 通过，包括 Migration 1→2、数据库重开、导入/恢复回滚、备份往返和分析导出隐私默认值。
- Compose UI：5/9 通过；4 个已知失败来自文案断言、非唯一语义节点和 Snackbar 等待时序，详见[测试报告](docs/TEST_REPORT.md)。
- 已通过真实 Storage Access Framework 流程导入 19 条纯虚构高密度日程，并在“今天”列表中显示。

当前状态不应被理解为“所有端到端测试均已通过”。数据层与 Migration 已在设备上通过，UI 自动化断言仍需收紧选择器和等待条件。

## 已知限制

- 没有登录、云同步、提醒通知、系统日历同步、重复任务或跨午夜任务。
- 导入文件时区与设备不同时只警告，不自动换算墙上时间。
- 时间重叠允许用户确认后导入或保存。
- 备份恢复是完整替换，不支持合并。
- 没有 Play Store 发布包或正式 release 签名。
- 当前 4 个 Compose UI 自动化断言仍待修正，但不影响已通过的 Room/Migration 结果。

## 参与开发

欢迎通过 Issue 报告可复现问题，或提交聚焦、可验证的 Pull Request。涉及导入、备份、Migration 和隐私默认值的改动应同时提供相应测试。

当前仓库尚未附带开源许可证；在许可证明确之前，默认版权规则仍然适用。
