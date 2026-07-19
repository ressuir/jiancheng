# 测试报告

最近更新：2026-07-19

## 环境

- Windows
- JDK 21.0.11，源码与字节码目标 Java 17
- Android SDK Platform 37
- Build Tools 36.0.0
- Android Gradle Plugin 9.3.0
- Gradle 9.5.0
- 设备测试：Pixel 7 配置、Android 15 / API 35、Default Android x86_64 system image

## 构建与静态检查

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

- JVM 单元测试：26 个，26 通过，0 失败，0 跳过。
- Lint：0 错误，1 个 Gradle 版本提示。
- Debug 应用 APK 与 Android Test APK 构建成功。
- unsigned Release、R8、资源压缩和 release Lint Vital 校验成功；unsigned Release 不作为安装包分发。

APK 静态审计结果：

- 权限集合为空。
- 只有 launcher `MainActivity`，没有 Service、Receiver 或 Provider。
- `allowBackup=false`，传统与 Android 12+ 备份规则均排除所有应用数据域。
- Debug APK 的 Android Signature Scheme v2 验证通过，签名者为标准 Android Debug 证书。
- 依赖树中未发现广告、遥测、崩溃上传、HTTP 客户端或云 SDK。

## JVM 测试覆盖

26 个 JVM 测试覆盖：

- 任务字段、Unicode、日期、时间、跨午夜与重叠规则。
- 状态转换、过期未处理、批注和计划快照。
- 计划 JSON 的合法输入、缺字段、错类型、未知字段/版本、重复 ID、大小、数量、跨度、嵌套和 UTF-8 限制。
- 完整备份往返、状态一致性、批注长度、过深嵌套和非法批次。

## Android 设备测试

有效执行命令：

```powershell
$env:ANDROID_SERIAL = "emulator-5556"
.\gradlew.bat --no-daemon --console=plain connectedDebugAndroidTest
```

API 35 x86_64 模拟器实际执行 18 项测试：

| 测试类 | 总数 | 通过 | 失败 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| `MigrationInstrumentedTest` | 1 | 1 | 0 | 0 |
| `RepositoryInstrumentedTest` | 8 | 8 | 0 | 0 |
| `ComposeComponentTest` | 2 | 0 | 2 | 0 |
| `MainActivityUiTest` | 7 | 5 | 2 | 0 |
| **合计** | **18** | **14** | **4** | **0** |

### 已通过的数据与 Migration 场景

- Migration 1→2 保留已有行并新增 nullable 字段。
- 增删改查、时间排序、状态、批注、修改历史和级联删除。
- 状态精确撤销。
- 合法导入、完全重复和 ID 冲突识别。
- 导入事务失败不留下部分数据。
- 完整备份往返恢复。
- 恢复事务失败回滚原数据。
- AI 分析导出默认排除私密字段并使用局部引用。
- 文件数据库关闭后重新打开仍持久化。

### 4 个 Compose UI 失败

1. `validImportPreviewShowsCountsAndConfirmation`
   - 测试精确查找“导入预览”，实际界面文案是“计划导入预览”。
2. `darkThemeRendersPrimaryTodayText`
   - 顶部标题和底部导航都包含“今天”，测试错误地要求唯一节点。
3. `startsOnTodayAndShowsEmptyState`
   - 同样因“今天”存在两个合法语义节点而失败。
4. `addCompleteAndUndoCoreFlow`
   - 状态已经更新，但测试立即查找“撤销”时 Snackbar 尚未出现。单一事件 collector 会等待此前的“任务已保存”Snackbar，暴露了测试等待不足和快速操作下提示延迟的边界情况。

没有删除测试或降低导入、恢复、Migration 与隐私验证标准来制造绿色结果。

## 手工模拟器验证

- 全新 API 35 AVD 在约 152 秒完成启动，`sys.boot_completed=1`。
- Debug APK 安装并启动成功。
- 使用 Android Storage Access Framework 选择并导入一份包含 19 条纯虚构日程的合法 `jiancheng.plan` 文件。
- 导入预览显示新增 19、重复 0、ID 冲突 0、重叠警告 0。
- 确认导入后，“今天”页面按时间显示高密度任务列表。
- 未连接或操作真实手机。

尚未完整手工走通 AI 导出、完整备份、替换恢复和所有异常文件组合，因此不能声称完整端到端闭环全部通过。

## 本地报告路径

构建后可在以下位置查看本机生成的报告：

- JVM：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint：`app/build/reports/lint-results-debug.html`
- Android 设备测试：`app/build/reports/androidTests/connected/debug/index.html`

这些构建产物和原始 Emulator/ADB 日志不提交到仓库。
