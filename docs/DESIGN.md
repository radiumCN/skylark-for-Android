# Skylark for Android — 技术设计文档

> 一个基于 **sing-box** 内核的 Android 多协议代理客户端。
> 技术栈：Kotlin + Jetpack Compose (Material 3)。

---

## 1. 项目定位与目标

Skylark 是一个运行在 Android 上的代理客户端，核心目标：

- **内核**：以 [sing-box](https://github.com/SagerNet/sing-box) 为唯一代理内核，通过 `gomobile` 将其 `experimental/libbox` 编译为 `libbox.aar`，以**进程内 JNI** 方式集成。
- **多协议**：借助 sing-box 原生能力，尽可能覆盖全部主流协议（见 §4）。
- **多订阅格式**：支持 **Clash / Clash.Meta YAML**、sing-box JSON、分享链接（`vmess://`、`vless://`、`ss://`、`trojan://`、`hysteria2://`、`tuic://` …）、Base64 聚合订阅。
- **全局代理**：`VpnService` + TUN，支持分应用代理与规则分流。
- **自动选择（Auto）**：内置 Auto 节点组，基于 sing-box `urltest` 定期测速，自动选用延迟最低的可用节点并在故障时自动切换（auto-failover）。
- **UI**：Jetpack Compose + Material 3（最新版本），支持深色/浅色模式（**不使用动态取色**，采用固定品牌主题）。
- **开源与分发**：GitHub 开源（MIT，仓库 `radiumCN/skylark-for-Android`），GitHub Actions 自动打包/签名/发布 Release，App 内支持**稳定版 / 测试版**双通道检查更新。

### 关键技术决策（已确认）

| 决策项 | 选择 | 说明 |
|---|---|---|
| 内核集成方式 | 进程内 JNI（libbox） | 直接调用 `BoxService`，TUN fd 由 `VpnService` 交给内核，避免本地端口/gRPC 复杂度 |
| 内核来源 | 官方 `SagerNet/sing-box` | 通过 CI（gomobile）从官方仓库构建 `libbox.aar` |
| Clash 转换 | 纯 Kotlin 本地转换 | 离线、隐私、可控，不依赖外部 subconverter 服务 |
| 协议范围 | 尽可能全 | 以 sing-box 支持的全部 outbound 为目标 |
| 节点选择 | 手动 + **Auto** | Auto 组用 `urltest` 自动测速择优 + 故障自动切换 |
| UI 主题 | 固定品牌主题 | Material 3 最新版，**不启用动态取色**，深/浅色 |
| minSdk | **35（Android 15）** | 仅支持 Android 15 及以上设备 |

---

## 2. 整体架构（分层）

```
┌───────────────────────────────────────────────┐
│  UI 层 (Jetpack Compose + Material 3)           │
│  首页/连接 · 配置组 · 节点列表 · 路由 · 设置 · 日志 │
├───────────────────────────────────────────────┤
│  ViewModel 层 (StateFlow, 单向数据流 MVVM/MVI)   │
├───────────────────────────────────────────────┤
│  Domain / UseCase 层                            │
│  订阅更新 · 配置生成 · 节点测速 · 连接控制         │
├───────────────────────────────────────────────┤
│  Data 层                                         │
│  Room(配置组/节点/规则/设置) · DataStore · 订阅HTTP │
│  converter/ (Clash · 分享链接 → sing-box outbound) │
├───────────────────────────────────────────────┤
│  Service 层                                      │
│  SkylarkVpnService(VpnService) · BoxService      │
│  PlatformInterfaceImpl (libbox 回调桥接) · CommandClient │
├───────────────────────────────────────────────┤
│  Native: libbox.aar (sing-box Go core, JNI)     │
└───────────────────────────────────────────────┘
```

**数据流原则**：UI 只消费 `ViewModel` 暴露的 `StateFlow`；用户事件单向下发；Service 层通过 `CommandClient` 把内核实时状态（日志/连接/流量）以 Flow 形式回流到 UI。

---

## 3. sing-box 内核集成

### 3.1 构建 libbox.aar

从官方 `SagerNet/sing-box` 源码，用 gomobile 构建：

```bash
# 依赖：Go 1.24+、gomobile、Android NDK
go run ./cmd/internal/build_libbox -target android
# 等价的手动命令：
gomobile bind -target=android/arm64,android/arm,android/amd64,android/386 \
  -tags "with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,with_dhcp,with_reality_server" \
  -o libbox.aar ./experimental/libbox
```

- 产物置于 `app/libs/libbox.aar`，在 `build.gradle.kts` 中 `implementation(files("libs/libbox.aar"))`。
- **构建策略**：使用 GitHub Actions 工作流固定 sing-box 版本 tag，自动产出多架构 `.aar` 作为构建产物/Release 附件；App 仓库消费产物。首次可手动构建一份提交，后续接入 CI。
- **build tags 说明**：`with_gvisor`(TUN 用户态栈)、`with_quic`(Hysteria2/TUIC)、`with_wireguard`、`with_utls`(uTLS/REALITY)、`with_clash_api`(App 内查询/切换)、`with_dhcp`。

### 3.2 与内核的交互接口

libbox 暴露的核心类型（Kotlin 侧调用）：

- `Libbox.setup(...)` / `Libbox.newService(configJson, platformInterface)`：创建 `BoxService`。
- `BoxService.start()` / `close()`：内核生命周期。
- `PlatformInterface`（我方实现）关键回调：
  - `openTun(options: TunOptions): Int` → 用 `VpnService.Builder` 建 TUN，返回 fd。
  - `useProcFS()` / `findConnectionOwner(...)` → 分应用/进程归属。
  - `writeLog(...)`、`usePlatformAutoDetectInterfaceControl()` 等。
- `CommandClient`：订阅 **日志、连接列表、流量统计（up/down）、内存**，以及**切换 selector 节点、urltest 触发**。

### 3.3 TUN 建立流程

```
用户点连接
  → SkylarkVpnService.prepare() 申请 VPN 权限
  → 生成 config.json（ConfigBuilder）
  → Libbox.newService(config, platformInterface)
  → BoxService.start()
      → 内核回调 PlatformInterface.openTun(options)
          → VpnService.Builder 配置 地址/路由/DNS/MTU/分应用
          → establish() 得到 ParcelFileDescriptor.fd
          → 返回 fd 给内核
  → 内核接管 TUN 读写，前台通知显示状态/速率
```

---

## 4. 多协议支持

以 sing-box 支持的 outbound 为目标，分批实现：

| 协议 | 优先级 | 备注 |
|---|---|---|
| Shadowsocks | P0 | 含 SIP003 插件、多种加密 |
| VMess | P0 | ws/grpc/h2/tcp + TLS |
| VLESS | P0 | 含 REALITY / XTLS-Vision |
| Trojan | P0 | TLS + transport |
| Hysteria2 | P1 | QUIC，需 `with_quic` |
| TUIC | P1 | QUIC |
| WireGuard | P1 | 需 `with_wireguard` |
| ShadowTLS | P2 | v1/v2/v3 |
| AnyTLS | P2 | |
| SOCKS / HTTP | P2 | 基础代理 |
| Hysteria(v1)、SSR | P3 | 兼容旧订阅 |

传输层（transport）：`tcp` / `ws` / `grpc` / `http` / `httpupgrade` / `quic`；TLS 相关：标准 TLS、uTLS 指纹、REALITY、ECH。

---

## 5. 订阅解析与转换（核心模块 `converter/`）

### 5.1 设计要点：统一中间模型

所有输入格式先解析为**协议无关的中间模型 `ProxyNode`**，再统一映射到 sing-box outbound，避免 N×M 转换爆炸。

```
converter/
├── SubscriptionParser        # 探测订阅类型并分发
├── clash/
│   └── ClashYamlParser        # Clash/Clash.Meta YAML → List<ProxyNode>
├── link/
│   └── ShareLinkParser        # xxx:// 分享链接 → ProxyNode
├── base64/
│   └── Base64SubParser        # Base64 解码 → 分享链接列表
├── singbox/
│   └── SingboxJsonParser      # 直接提取/合并 outbounds
├── model/
│   ├── ProxyNode              # 统一中间模型（sealed class 按协议区分）
│   └── ProxyGroup             # 代理组（对应 selector/urltest）
├── mapper/
│   └── OutboundMapper         # ProxyNode → sing-box outbound JSON
└── ConfigBuilder              # 拼装完整 config.json
```

### 5.2 订阅类型探测

```
SubscriptionParser.parse(raw):
  1. trim 后尝试 JSON.parse → 含 "outbounds" ⇒ sing-box JSON
  2. 含 "proxies:" 且 YAML 可解析 ⇒ Clash YAML
  3. 单行/多行匹配 ^\w+:// ⇒ 分享链接列表
  4. 纯 Base64（无换行/合法字符集）⇒ 解码后回到 3
  5. 均失败 ⇒ 抛可读错误
```

### 5.3 Clash YAML → ProxyNode 映射

- 读取 `proxies:` 列表，按 `type` 分发到各协议解析器。
- 读取 `proxy-groups:` → `ProxyGroup`：
  - `select` ⇒ sing-box `selector`
  - `url-test` / `fallback` / `load-balance` ⇒ sing-box `urltest`
- YAML 解析库：**kaml**（kotlinx.serialization 生态）或 SnakeYAML。字段用宽松反序列化（未知字段忽略、类型容错）。

**字段映射示例（VMess，ws+tls）：**

```
Clash:
  { name, server, port, uuid, alterId, cipher, network: ws,
    tls: true, servername, ws-opts: { path, headers: { Host } } }
        ↓ OutboundMapper
sing-box outbound:
  { type: "vmess", tag: name, server, server_port: port, uuid,
    alter_id: alterId, security: cipher,
    tls: { enabled: true, server_name: servername },
    transport: { type: "ws", path, headers: { Host } } }
```

> 映射逻辑参考成熟实现（如 `xmdhs/clash2singbox` 的字段对应关系）在 Kotlin 中重写，覆盖各协议关键字段与 transport/tls 变体，并对缺省值做健壮处理。

### 5.4 分享链接解析

- `ss://`：Base64 + SIP002（`plugin`）
- `vmess://`：Base64 JSON（v2rayN 格式）
- `vless://` / `trojan://`：URI query（`type`、`security`、`sni`、`fp`、`pbk`、`sid`、`flow` …）
- `hysteria2://` / `tuic://`：URI query
- 统一处理 URL 编码、`#fragment` 作为节点名。

---

## 6. 配置生成（ConfigBuilder）

运行时根据「内置模板 + 用户设置 + 选中节点」动态生成 sing-box `config.json`：

- **inbounds**：`tun`（全局模式，`stack` 可选 gvisor/system/mixed）+ 可选 `mixed`（本地 http/socks，供其他 App 使用）。
- **outbounds**：转换出的全部节点 + `selector`（手动选）+ **`urltest`（Auto 组：自动测速择优 + 故障自动切换）** + `direct` / `block` / `dns-out`。
  - Auto 组参数：`url`（探测地址，默认 `https://www.gstatic.com/generate_204`）、`interval`（测速间隔，如 `300s`）、`tolerance`（容差，避免抖动频繁切换）；成员用 `{all}` 展开为全部节点。
  - Clash 的 `url-test` / `fallback` / `load-balance` 组统一映射为该 Auto（`urltest`）能力。
- **route**：
  - 规则集（`rule_set`，geosite/geoip，remote 或本地）
  - 分流模式：规则 / 全局 / 直连
  - 绕过：局域网、大陆直连（可选）
  - 分应用代理：结合 `TunOptions.include/excludePackage`
- **dns**：`fakeip` + 分流 DNS（国内/国外解析分离）+ DNS 泄漏防护。
- **experimental**：`clash_api`（供 App 内查询/切换）+ `cache_file`（fakeip/规则缓存）。

模板以资源文件形式内置，用户设置通过覆盖/合并写入。

---

## 7. VpnService / TUN 实现

`SkylarkVpnService : VpnService`：

1. 权限：`VpnService.prepare()`，未授权跳系统授权页。
2. 前台服务：Android 8+ 必须前台通知（连接状态、实时上下行速率、断开按钮）。
3. `openTun` 回调：用 `TunOptions` 配置 `Builder`：
   - `addAddress`（IPv4/IPv6）、`addRoute`（含 route range 计算）
   - `addDnsServer`、`setMtu`
   - 分应用：`addAllowedApplication` / `addDisallowedApplication`
   - `setSession` / `setBlocking(true)`
4. `establish()` → `ParcelFileDescriptor` → 返回 `fd`。
5. 生命周期：`onStartCommand` 启动内核；`onRevoke` / `onDestroy` 关闭内核并释放 fd。
6. 网络切换：监听 `ConnectivityManager`，通知内核 `usePlatformDefaultInterfaceMonitor`。

---

## 8. 数据层

### Room 实体

| 实体 | 主要字段 |
|---|---|
| `ProfileEntity` | id, name, type(url/local/manual), url, userAgent, autoUpdateInterval, lastUpdated, rawContent |
| `NodeEntity` | id, profileId, tag, protocol, outboundJson, latencyMs, lastTestAt |
| `RuleSetEntity` | id, name, type, format, url/local, enabled |
| `GroupEntity` | id, profileId, name, type(select/urltest), memberTags |

### DataStore（Preferences）

应用设置：TUN stack、MTU、DNS 策略、路由模式、分应用列表、启动即连、外观（深/浅色主题，无动态取色）、日志级别。

---

## 9. UI（Compose + Material 3）

| 页面 | 内容 |
|---|---|
| 首页 / 连接 | 连接/断开大按钮、当前节点（含 **Auto** 自动选择）、实时上下行速率、连接时长、模式切换 |
| 配置组 | 订阅列表、添加/更新订阅、导入（剪贴板/二维码/文件）、自动更新 |
| 节点列表 | 分组展示、**Auto 自动组**、批量测速、手动选择、按延迟排序、搜索/过滤 |
| 路由 / 分流 | 模式（规则/全局/直连）、规则集管理、分应用代理选择器 |
| 设置 | 内核参数、DNS、外观、启动即连、**检查更新（稳定/测试通道）**、关于（版本号） |
| 日志 | 内核实时日志流、级别过滤、复制/清空 |

- 导航：Navigation-Compose。
- 主题：Material 3（最新版本），固定品牌配色，支持深/浅色切换；**不启用动态取色（Dynamic Color）**，保证跨设备视觉一致。
- 二维码：ZXing（导入/生成节点）。

---

## 10. 技术栈与依赖

| 领域 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.x + Jetpack Compose (BOM) + Material 3（`androidx.compose.material3` 最新稳定版） |
| 架构 | MVVM/MVI + StateFlow + ViewModel |
| 异步 | Coroutines + Flow |
| DI | Hilt |
| 本地存储 | Room + DataStore(Preferences) |
| 网络 | OkHttp（拉订阅，支持自定义 UA / 重定向） |
| JSON | kotlinx.serialization |
| YAML | kaml 或 SnakeYAML |
| 二维码 | ZXing |
| 导航 | Navigation-Compose |
| 内核 | libbox.aar（sing-box，gomobile 构建） |
| 构建 | Gradle Kotlin DSL + Version Catalog（libs.versions.toml） |
| SDK | **minSdk 35（Android 15）**，targetSdk 最新，NDK abiFilters: arm64-v8a, armeabi-v7a, x86_64 |
| 应用标识 | `applicationId` / 包名：**`com.radium.skylark`** |

---

## 11. 目录结构

```
app/src/main/java/com/radium/skylark/   # applicationId / package: com.radium.skylark
├── ui/            # Compose 页面 + 主题 + 导航
│   ├── home/  profile/  nodes/  route/  settings/  logs/
│   └── theme/  navigation/  component/
├── viewmodel/
├── domain/        # UseCase：UpdateSubscription, BuildConfig, TestLatency, Connect...
├── data/
│   ├── db/        # Room DAO + Entity + Database
│   ├── datastore/
│   ├── remote/    # 订阅拉取
│   └── repository/
├── converter/     # 订阅 → sing-box 转换（核心，见 §5）
│   ├── clash/  link/  base64/  singbox/  model/  mapper/
│   └── ConfigBuilder.kt
├── service/       # SkylarkVpnService, BoxService, PlatformInterfaceImpl, CommandClient
├── di/            # Hilt Modules
└── util/          # UpdateChecker（GitHub Releases 检查更新）, ...
app/libs/libbox.aar
.github/workflows/ # CI：build-libbox.yml, ci.yml, release.yml
```

---

## 12. 开发里程碑

| 阶段 | 内容 | 产出 |
|---|---|---|
| **M0** 脚手架 | Gradle/Compose/Hilt/Room/Navigation 骨架 + Material3 主题 | 可运行空壳 App |
| **M1** 内核接入 | CI 构建 `libbox.aar`；`VpnService` + `BoxService`；硬编码一条 config 跑通连接 | 能连上一个节点 |
| **M2** 转换核心 | `ProxyNode` 模型 + `ShareLinkParser` + `OutboundMapper`（SS/VMess/VLESS/Trojan） | 分享链接可连 |
| **M3** Clash YAML | `ClashYamlParser` + proxy-groups → selector/urltest | Clash 订阅可用 |
| **M4** 订阅管理 | Room + 订阅拉取/更新/自动更新 + 节点列表 UI | 完整订阅流程 |
| **M5** 测速 / 分流 / 分应用 | urltest、路由规则集、分应用代理 | 生产级分流 |
| **M6** 全协议 + 打磨 | 补齐 Hysteria2/TUIC/WireGuard/ShadowTLS/AnyTLS；日志/通知/二维码/深色模式；多架构打包签名 | 可发布版本 |
| **M7** CI/CD + 更新检查 | GitHub Actions 自动打包、签名、发 Release；App 内检查更新（稳定/测试通道） | 可持续发布 |

---

## 13. CI/CD 与发布（GitHub Actions）

仓库：https://github.com/radiumCN/skylark-for-Android （开源，MIT）

### 13.1 工作流划分

| 工作流 | 触发 | 职责 |
|---|---|---|
| `build-libbox.yml` | 手动 / 定时 / sing-box tag 更新 | Go + gomobile + NDK 构建多架构 `libbox.aar`，作为 artifact 或缓存供后续复用 |
| `ci.yml` | `push` / `pull_request` | 拉取 `libbox.aar`，`./gradlew lint test assembleDebug`，跑单测（含 converter 转换用例） |
| `release.yml` | 推送 tag `v*` | 复用内核产物 → 签名打包 Release APK（分架构 + universal）→ 创建 GitHub Release 并上传产物 |

### 13.2 发布流程

1. 更新版本号（见 §14），提交并打 tag：
   - 稳定版：`v0.1.0`
   - 测试版：`v0.1.0-beta.1`
2. `release.yml` 按 tag 判定通道：**含 `-beta.`/`-rc.` 等预发布标识 → GitHub Release 勾选 `prerelease`；否则为正式 Release**。
3. 用 `secrets`（keystore Base64、别名、密码）做**签名打包**；产物：
   - `skylark-<version>-arm64-v8a.apk`、`-armeabi-v7a.apk`、`-x86_64.apk` 及 `-universal.apk`
   - 随附 `latest.json`（版本清单，供 App 检查更新，见 §14.3）与 `SHA256SUMS`
4. Release notes 由 Conventional Commits 自动生成（`fix:`→patch、`feat:`→minor、`feat!:`/`BREAKING CHANGE:`→major）。

### 13.3 签名与安全

- 签名 keystore 与口令仅存于 GitHub `Secrets`，不入库；CI 内解码到临时文件，构建后清理。
- `libbox.aar` 建议由独立工作流产出并按 sing-box tag 缓存，避免每次全量编译 Go。

---

## 14. 版本号与更新检查

### 14.1 版本号规范（SemVer + 预发布）

- **稳定版**：`MAJOR.MINOR.PATCH`，如 `0.1.0`
- **测试版**：`MAJOR.MINOR.PATCH-beta.N`，如 `0.1.0-beta.1`
- Git tag 统一加前缀 `v`（`v0.1.0`、`v0.1.0-beta.1`）。
- `versionName` 直接采用上述字符串；`versionCode` 单调递增（可由 tag 或 CI 运行号推导，保证 beta→stable 递增，例如 `0.1.0-beta.1` < `0.1.0`）。

### 14.2 单一版本源（Single Source of Truth）

版本号**只在一处定义**，其余全部引用，杜绝多处手改导致不一致。

- **唯一来源**：`gradle/libs.versions.toml` 的 `[versions]` 段（与依赖版本共用同一 Version Catalog）：

```toml
[versions]
appVersionName = "0.1.0-beta.1"   # 唯一版本源
# versionCode 不手写，由构建脚本从 appVersionName 计算
```

- **App 构建**：`app/build.gradle.kts` 读取该值写入 `versionName`；`versionCode` 由脚本从 `versionName` 解析计算（如 `major*10000 + minor*100 + patch`，预发布再叠加规则），**不手动维护**。
- **运行时**：代码一律通过 `BuildConfig.VERSION_NAME` / `VERSION_CODE` 读取，**禁止在源码/资源中另写版本常量**。
- **CI 校验**：`release.yml` 校验 git tag（去掉 `v` 前缀）与 `libs.versions.toml` 中 `appVersionName` **完全一致**，不一致则 fail，从机制上保证 tag、构建产物、清单三者同源。
- **更新清单**：`latest.json`（§14.3）由 CI 从同一版本源生成，**不手写**。

> 结论：日常只改 `libs.versions.toml` 一行；`versionCode`、Release tag 校验、`latest.json`、`BuildConfig` 全部自动派生。

### 14.3 更新通道（Channel）

- App 设置提供两个通道：**稳定（Stable）** 与 **测试（Beta）**。
- Stable 通道只提示正式 Release；Beta 通道同时提示预发布（prerelease）与正式版。
- 通道选择持久化到 DataStore。

### 14.4 检查更新实现

两种可选数据源（推荐 A，可回退 B）：

- **方案 A（清单文件）**：读取 Release 附带的 `latest.json`（或仓库固定路径），结构示例：

```json
{
  "stable": { "version": "0.1.0", "versionCode": 100, "url": "<apk_download_url>", "notes": "...", "sha256": "..." },
  "beta":   { "version": "0.2.0-beta.1", "versionCode": 201, "url": "<apk_download_url>", "notes": "...", "sha256": "..." }
}
```

- **方案 B（GitHub API）**：调用 `GET /repos/radiumCN/skylark-for-Android/releases`，按通道过滤 `prerelease` 字段，取最新一条。注意未认证请求有速率限制，需缓存与错误兜底。

**比较逻辑**：解析远端 `version` 为 SemVer（含 prerelease 段），与当前 `versionName` 比较（遵循 SemVer 预发布优先级：`0.1.0-beta.1` < `0.1.0`）；有更新则弹出对话框展示版本、更新说明与下载入口。

**下载与安装**：跳转浏览器/系统下载，或应用内下载后校验 `sha256` 再触发安装（`REQUEST_INSTALL_PACKAGES` 权限，Android 15 走 PackageInstaller 会话）。

---

## 15. 关键风险与注意事项

- **内核构建链**：需 Go + NDK + gomobile 环境，建议全程用固定版本的 CI，锁定 sing-box tag，避免内核 API 变动导致 `PlatformInterface` 不兼容。
- **libbox API 版本漂移**：不同 sing-box 版本 `PlatformInterface`/`TunOptions` 方法可能变化，升级内核时需回归 Service 层桥接代码。
- **Clash 字段覆盖度**：Clash.Meta 扩展字段多，映射需渐进覆盖并对未知字段容错，保留原始配置便于排查。
- **协议 build tags**：QUIC 类协议（Hysteria2/TUIC）依赖 `with_quic`，构建时勿遗漏对应 tag，否则运行期报未知 outbound。
- **Android 后台限制**：前台服务通知、电池优化白名单、网络切换重连需专门处理。
- **minSdk 35（Android 15）**：设备覆盖面显著收窄（仅 Android 15+），可利用较新系统 API（如更完善的 VpnService/权限模型），但需评估目标用户设备是否满足；如后续需扩大覆盖，可再下调 minSdk。
- **Auto 组抖动**：`urltest` 需合理设置 `interval` 与 `tolerance`，避免节点延迟波动导致频繁切换；建议对切换做去抖并在 UI 展示当前实际选中节点。
- **更新检查限流/兜底**：GitHub API 未认证有速率限制，优先用 `latest.json` 清单并做缓存；网络失败时静默降级，不阻塞主流程。
- **应用内安装权限**：Android 15 上安装 APK 需 `REQUEST_INSTALL_PACKAGES` 并走 `PackageInstaller` 会话；下载后务必校验 `sha256`，避免中间人替换。
- **签名一致性**：稳定版与测试版需使用同一签名 keystore，否则用户无法覆盖安装升级；keystore 与口令仅存于 CI Secrets。
- **许可证**：sing-box 为 **GPLv3**，本项目分发需遵循相应开源义务；本仓库整体开源需与 GPLv3 兼容。

---

## 16. 参考

- 本项目仓库：https://github.com/radiumCN/skylark-for-Android
- sing-box 官方仓库：https://github.com/SagerNet/sing-box
- libbox 移动库文档（experimental/libbox）
- Clash → sing-box 字段映射参考：`xmdhs/clash2singbox`、`oluceps/clash2sing-box`
