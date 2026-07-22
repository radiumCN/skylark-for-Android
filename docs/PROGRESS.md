# Skylark 开发进度

> 最后更新：2026-07-22 · 状态：**暂停开发**
> 版本源：`gradle/libs.versions.toml` → `appVersionName = "0.1.0-beta.1"`
> 包名：`com.radium.skylark` · minSdk 35 / compileSdk 36 / targetSdk 36

本文件记录当前实现状态、下次接续的入口与关键决策点。设计与架构见 `docs/DESIGN.md`。

---

## 一、构建状态

- `./gradlew :app:assembleDebug` ✅ 通过
- `./gradlew :app:testDebugUnitTest` ✅ 通过
- JDK：使用本地 JDK 21，Kotlin/Java 目标字节码 17（`jvmTarget = JVM_17`）
- Gradle Wrapper：8.14.3 · AGP 8.12.0 · Kotlin 2.3.10

---

## 二、已完成功能（真实可用，非占位）

| 模块 | 页面/能力 | 关键文件 |
| --- | --- | --- |
| 首页 | 连接/断开、状态机、连接计时、当前订阅卡片、VpnService 授权流程 | `ui/screen/HomeScreen.kt`、`viewmodel/HomeViewModel.kt` |
| 订阅 | 增/删/刷新、URL 与粘贴文本导入、点选选中（高亮 ✓） | `ui/screen/ProfilesScreen.kt`、`viewmodel/ProfilesViewModel.kt`、`data/repository/SubscriptionRepository.kt` |
| 节点 | 展示已解析节点列表 | `ui/screen/NodesScreen.kt`、`viewmodel/NodesViewModel.kt` |
| 日志 | 应用内日志流、等级过滤、自动滚底、清空 | `ui/screen/LogsScreen.kt`、`viewmodel/LogsViewModel.kt`、`bg/LogRepository.kt` |
| 设置 | 当前版本展示、稳定/测试通道切换、检查更新（GitHub Releases API）、下载/前往发布页 | `ui/screen/SettingsScreen.kt`、`viewmodel/SettingsViewModel.kt`、`update/*` |
| 订阅转换 | Clash YAML / 分享链接 / Base64 → sing-box config；Auto(urltest) + selector | `converter/**` |
| 后台服务 | VpnService 前台服务、连接状态单例、内核抽象 + 桩实现 | `bg/*`、`di/CoreModule.kt` |
| 持久化 | Room（订阅）、DataStore（选中订阅、更新通道） | `data/db/*`、`data/datastore/*` |
| CI/CD | ci.yml（测试/lint/assemble）、build-libbox.yml、release.yml（tag 校验+签名+发布） | `.github/workflows/*` |

### 已通过的单元测试
- `converter/`：ShareLinkParser、OutboundMapper、ClashYamlParser、SubscriptionParser
- `update/SemVerTest`：版本解析、`v` 前缀、非法输入、预发布/正式排序、beta 号排序、默认通道推断

---

## 三、里程碑进度

- **M0 项目骨架**：✅ 完成（Compose + Material3 固定品牌主题、导航、主题、图标、Hilt）
- **M2 订阅转换器**：✅ 完成（`ProxyNode` 中间模型 + 各协议解析 + OutboundMapper + ConfigBuilder）
- **M3 Clash YAML**：✅ 完成（SnakeYAML 解析 proxies/proxy-groups）
- **M4 订阅拉取 + 落库 + 列表 UI**：✅ 完成
- **检查更新（稳定/测试通道）**：✅ 完成
- **日志页**：✅ 完成
- **M1 真实内核接线**：⏸ **未开始（关键阻塞点，见下）**
- **路由页（规则/全局/直连 + 分应用代理）**：⏳ 未开始（当前为占位）

---

## 四、下次接续入口：M1 真实内核（关键决策点）

目前代理**尚未真正生效**：`ProxyCore` 由 `bg/StubProxyCore.kt` 桩实现，仅模拟连接与状态、写日志，不建立真实代理。

要让代理生效，需要 `libbox.aar`（由 `SagerNet/sing-box` 经 gomobile 构建，需 Go + Android NDK 工具链）。**决策尚未做出**：

- **方案 A（走 CI）**：完善 `.github/workflows/build-libbox.yml` 产出并发布 `libbox.aar`；`app/build.gradle.kts` 增加 `implementation(fileTree("libs") { include("*.aar") })`，把 aar 放入 `app/libs/` 即生效。
- **方案 B（本地已有 aar）**：直接放入 `app/libs/` 并接线。

### 接线待办（拿到 aar 后）
1. `app/build.gradle.kts` 装配 `app/libs/*.aar`。
2. 新增 `bg/LibboxProxyCore.kt` 实现 `ProxyCore`（`libbox.Libbox.newService(...)` / start / close）。
3. `di/CoreModule.kt` 把绑定从 `StubProxyCore` 改为 `LibboxProxyCore`。
4. `bg/SkylarkVpnService.kt` 启用 `openTun()`：`VpnService.Builder().establish()` 得到 fd，交给内核；`PlatformInterface` 实现（保护 socket、网络查询）。
5. 内核日志回调接入 `LogRepository.append(...)`；`clash_api`（`127.0.0.1:9090`，ConfigBuilder 已配置）用于流量/节点延迟。

预留位：`SkylarkVpnService.openTun()` 已写好被注释的 Builder 代码与 `TODO(libbox)` 标记。

---

## 五、其他可独立推进（不依赖内核）

- **路由页**：规则/全局/直连模式切换、分应用代理（`VpnService` allow/disallow 应用）。
- **节点测速/延迟**：依赖内核 `clash_api` 起来后展示。
- **首页实时速率**：依赖内核。

---

## 六、关键约定备忘

- **版本单一源**：仅改 `libs.versions.toml` 的 `appVersionName`；`versionCode` 由 `app/build.gradle.kts` 的 `deriveVersionCode` 自动推导（beta < 对应 stable）。
- **发布**：推送 `v<版本>` tag 触发 `release.yml`，会校验 tag 与 `appVersionName` 一致；含 `-` 视为预发布。
- **UI**：Material 3，固定品牌主题，**不使用动态取色**。
- **签名**：release 走环境变量（`SKYLARK_KEYSTORE_PATH` 等），CI 从 secrets 解码 keystore。
