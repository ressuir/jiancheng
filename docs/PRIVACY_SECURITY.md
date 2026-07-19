# 隐私与安全设计

## 本地与离线边界

简程没有账号、服务器、AI、广告、分析、崩溃上传、远程配置、WebView、HTTP 客户端或运行时网络代码。构建阶段由 Gradle 从 Google Maven、Maven Central 和 Gradle Plugin Portal 获取开源依赖；这与已构建 App 的运行时能力分离。

最终 Debug APK 经 `aapt2 dump permissions` 检查，权限集合为空；没有 `INTERNET`、网络状态、广泛存储、通知、位置、相机、麦克风、通讯录、蓝牙或其他敏感权限。

## 数据位置与文件访问

- Room 数据库存放在 Android 为本应用分配的内部数据库目录，其他普通 App 不能直接读取。
- 当前版本没有 SharedPreferences 或 DataStore 设置数据。
- 计划导入、备份导入和导出只通过 Storage Access Framework 访问用户明确选择的文档 URI。
- 输入/输出流用 `use` 关闭；读取有硬性字节上限；不会扫描共享存储，也不会永久缓存导入文件副本。
- 导出文件位于用户在系统“创建文档”界面选择的位置，离开应用私有目录后由用户和所选存储提供程序负责保管。
- 任务标题、说明、批注和文件正文不写入日志；正式代码中没有 `Log`、`println` 或 `printStackTrace`。

## Android 备份与迁移

Manifest 显式设置 `android:allowBackup="false"`，同时引用 `@xml/backup_rules` 和 `@xml/data_extraction_rules`。两套规则都排除 database、sharedpref、file、root 和 external 域；Android 12+ 的 cloud-backup 与 device-transfer 均排除这些域。缓存没有加入任何备份规则。

这构成应用能声明的防线，但部分厂商的整机迁移实现可能不完全遵循应用规则。卸载通常会删除内部 Room 数据；卸载、清理应用数据或换机前，应主动导出 `jiancheng.backup` 完整备份。

## 组件边界

最终 APK 仅声明 `com.zookie.simpleschedule.MainActivity`。它因 Android 启动器入口而 `exported=true`，只有 MAIN/LAUNCHER intent-filter；没有 Deep Link 或 App Link。最终 APK 没有 Service、Receiver 或 Provider。AndroidX 传递依赖中与本应用无关的 Startup Provider、Room 多进程失效服务、Profile Installer Receiver、调试 Preview Activity 和动态 Receiver 兼容权限均从最终 APK 移除。

## 数据完整性

- Room 版本升级使用显式 `MIGRATION_1_2`；未使用 `fallbackToDestructiveMigration`。
- 任务创建同步创建确定的 `PLANNED` 执行记录。
- 编辑计划字段前保存旧计划快照；状态和批注不修改计划历史。
- 计划导入和完整备份恢复均使用单个 Room 事务。
- 导入先做 UTF-8、大小、嵌套、结构、类型、字段、日期、时间、长度、重复和现有 ID 冲突验证，不静默覆盖。
- 完整备份恢复额外验证 UUID、引用关系、来源、执行状态、批注时间戳、批次哈希和修改记录。
- AI 分析导出默认不含批注，不导出内部 UUID、设备标识、路径或日志。

## 依赖审计

运行时直接依赖仅包含 AndroidX、Jetpack Compose、Room、Kotlin Coroutines 与 Kotlin Serialization。它们不在本应用中注册遥测或广告功能；依赖树中未发现 Firebase、Crashlytics、Analytics、广告、HTTP 客户端或云数据库。Kotlin、Coroutines、Serialization、KSP 和 AndroidX 均采用 Apache License 2.0；JUnit 4（仅测试）采用 Eclipse Public License 1.0。

## 威胁边界与剩余风险

本设计不防御已 root 设备、恶意系统镜像、用户主动把导出文件交给不可信应用，或存储提供程序自身的数据处理。Debug APK 使用标准 Android Debug 证书，仅适合开发/个人测试；正式长期分发应使用用户自行安全保管的 release 密钥重新签名，项目不生成或保存该密钥。
