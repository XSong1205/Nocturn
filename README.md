# Nocturn · LocalAPI

Nocturn 提供可嵌入 Android 的网易云音乐 LocalAPI，包含本机 Node 服务、Android 运行时、Kotlin Multiplatform 客户端，以及一个 Material 3 Expressive 的网易云音乐第三方客户端。

没有复制 SPlayer 实现；独立编写 JNI、Android 生命周期管理、本机 HTTP 服务和 Kotlin 客户端，使用上游 Node.js Mobile 与 API Enhanced 作为依赖。

## 当前模块

| 模块 | 职责 |
| --- | --- |
| `app` | Material 3 Expressive 网易云音乐第三方客户端：发现 / 搜索 / 歌单 / 专辑 / 歌手、全屏播放器与同步歌词、二维码登录、Media3 后台播放；详见 [应用说明](app/README.md) |
| `local-api-node` | 内置 API Enhanced **4.41.1**，63 个端点白名单，Node 18+ 兼容单文件打包 |
| `local-api-android` | Kotlin/JNI 启动 Node、本机随机端口、就绪与鉴权检测、资源校验、运行状态 |
| `local-api` | KMP HTTP 客户端、Android 启动工厂、共享契约测试 |

```text
宿主应用
         ↓
local-api（Ktor）
         ↓ Bearer / HTTP 127.0.0.1
local-api-android（Node.js Mobile / JNI）
         ↓
local-api-node（API Enhanced）→ 网易云在线服务
```

`host` JVM 目标用于运行共享客户端测试，不代表增加桌面客户端。

## 构建

需要 JDK 21、Node.js 18+ / npm、Android SDK 37、NDK `28.2.13676358`、CMake `3.22.1`。Android 最低 API 29，当前支持 `arm64-v8a` 和 `x86_64`。Gradle 由 wrapper 固定为 9.8.2，Android Gradle Plugin 9.5.0，Kotlin 2.4.21。

在 `local.properties` 中配置本机 Android SDK 路径，或设置 `ANDROID_HOME`。该文件不应提交版本控制。

```powershell
.\gradlew.bat :local-api:assemble :local-api:hostTest :local-api-android:assembleDebug :local-api-android:lintDebug
```

首次构建自动执行锁定依赖的 `npm ci`，下载并校验官方 Node.js Mobile Android 运行库，并打包 API 资产和第三方许可证。下载与生成文件保存在已忽略的缓存／build 目录。

应用版本保存在 `app/version.properties`。使用完整任务名执行应用的 `assemble*`、`bundle*`、`install*`、`package*` 或 `build`（含 `buildNeeded` / `buildDependents`）时，每次 Gradle 调用将 `versionCode` 加 1、`versionName` 的 patch 加 1（例如 `0.1.9` → `0.1.10`）；同次调用的多个变体共用一个版本。单独测试、Lint、IDE 同步及 dry-run 不递增。版本在生成打包清单前预留，后续构建失败也保留已用编号；`clean` 不重置计数。需要改起点时编辑该文件，跨机器构建需同步它。

原生模块 AAR：`local-api-android/build/outputs/aar/local-api-android-debug.aar`。接入应用时优先使用项目依赖 `implementation(project(":local-api"))`，让 Gradle 同时解析 Kotlin、Ktor、运行时模块及其依赖；单独复制 AAR 不包含这些 Maven 依赖。

## Android 接入

```kotlin
import dev.nocturn.localapi.client.createLocalApi

// 在应用主进程的协程中调用；内部准备与启动均在后台线程执行。
val api = createLocalApi(applicationContext)
val health = api.health()
val results = api.searchSongs("Nocturne", limit = 20)
val detail = api.songDetail(listOf(347230L))
val lyrics = api.lyrics(347230L)
val urlResult = api.songUrl(347230L)

// 释放这个客户端的 HTTP 资源；不会停止进程内的 Node VM。
api.close()
```

新增扩展方法位于 `dev.nocturn.localapi.client`，可按需导入：

```kotlin
import dev.nocturn.localapi.client.*

val urls = api.songUrls(listOf(347230L, 4278232L), AudioQuality.Dolby)
val searchPage = api.searchSongPage("Nocturne", ApiPage(limit = 30, offset = 0))
val playlistPage = api.playlistSongPage(24381616L, ApiPage(limit = 50, offset = 0))
val lyrics = api.songLyrics(1824020871L) // YRC 优先、LRC 回退、翻译／罗马音
// playlistPage.nextOffset 指向下一批 trackIds；不能依赖详情中的 tracks 预览。
```

底层 API 返回 `JsonObject` 保留上游业务字段，调用方仍需判断 `code` 及播放地址是否为 `null`。新增类型化方法提供歌曲字段归一化、歌单分页、歌词解析、二维码状态映射和会话恢复；这类方法会将上游业务失败转为 `MusicServiceException`，而不是返回空列表。账号 Cookie 由宿主应用安全保存，客户端不会自行持久化。

```kotlin
val runtime = dev.nocturn.localapi.LocalApiRuntime.get(applicationContext)
runtime.state.collect { state -> /* Idle / Preparing / Starting / Ready / Failed */ }
```

并发调用 `start()` 共用一次启动，取消某个调用方不会取消应用级启动。Node 每进程只能启动一次；进入原生运行时后退出或启动超时，需重新启动应用进程。进入 Node 前发生的资源准备错误可以重试。

库 Manifest 合并 `INTERNET` 权限和网络安全配置，明文 HTTP 仅允许 `127.0.0.1`。如果宿主已有 `android:networkSecurityConfig`，需在宿主配置中合并该精确本机域规则，并通过 Manifest merger 明确使用宿主配置；不要全局开启明文流量。

## 验证

```powershell
cd local-api-node
npm.cmd ci
npm.cmd test
$env:NOCTURN_REFERENCE_API = 'https://ncmapi.rpixel.online'
npm.cmd run smoke
```

在线 smoke 测试覆盖 18 类公开接口、19 个场景：在搜索、歌曲、歌词与播放地址之外，新增分类歌单、精品歌单及其下一页游标、歌手全部歌曲分页。不会登录真实账号或修改账号数据；上游 HTTP 请求附带时间戳避开缓存。上一轮对照时参考服务为 `4.36.2`，本次 Docs 重抓已升至 `4.41.1`；内置依赖为 `4.41.1`，同版本号仍需核对模块及响应。

- 本轮 Node **24.19.0**：35 项离线测试通过，新增歌单维护、收藏库、游标、元数据与单次提交回归；bundle 仍以 Node 18 为构建目标。
- Kotlin 共享客户端：40 项测试通过，包含模型、分页、登录状态、歌词及新增接口契约。
- Android 客户端：120 项 JVM / Robolectric 测试通过，覆盖网易云 JSON 归一化、歌单分页、歌词、加密会话、播放映射与全部屏幕交互；`ScreenshotGalleryTest` 在宿主机渲染 13 张界面截图到 `app/build/screenshots/`。
- 无线 ADB 真机（Redmi / Android 17 API 37、`arm64-v8a`、4 KB 页）验证：内置 Node 运行时启动、首页实时数据、热榜搜索、音源播放、锁屏 / 系统媒体通知与后台播放均正常；同时定位并修复了上游在 Android 上读取 `/tmp/anonymous_token` 导致启动失败的问题（打包适配层改用 `NOCTURN_DATA_DIR`）。
- 本轮 18 类公开接口、19 个场景的本地／参考服务共 38 项在线契约检查通过，包括精品歌单真实下一页游标。分类歌单 `order=new` 当前可以返回合法空页，按列表与分页字段核对。此前已验证实际游客会话及设备标识／密钥持久化一致性；本轮账号写操作仅执行隔离测试。
- 本轮 `:local-api:assemble`、`:app:compileDebugKotlin` 与 `:local-api-android:lintDebug` 通过。新增 LocalAPI 接口未执行设备端验证。
- 原生 JNI：两个 ABI 已编译与链接；Android AAR 和设备测试 APK 可构建。
- 设备测试位于 `local-api-android/src/androidTest`，覆盖并发启动、实际健康检查与鉴权，需要连接 4 KB 页 Android 设备后执行 `:local-api-android:connectedDebugAndroidTest`。本次无线设备只做了应用层端到端验证；该 instrumented 任务首次运行需要从 Google Maven 下载 UTP 依赖，受当前网络 TLS 握手失败影响未完成。

## 当前运行时边界

内置运行库使用官方 Node.js Mobile **18.20.4** 预编译包，支持 `arm64-v8a` / `x86_64`。两个 `libnode.so` 均为 **4 KB ELF 对齐**；启动前会检测设备页大小，并在 16 KB 设备上明确失败。本项目不再以 16 KB 设备兼容为目标。

API 在手机本地运行，在线音乐请求仍需网络。就绪文件、Token 和缓存保存在应用私有目录；Token 不写入就绪文件或日志。请求协议、端点及依赖许可证见 [Node 层文档](local-api-node/README.md)，原生库版本、SHA-256 与兼容性见 [运行时文档](docs/NODE_RUNTIME.md)。

部署服务文档的查阅记录、现有封装差异与后续补齐项见 [API 文档对照](docs/API_REFERENCE.md)。2026-11-01 重抓发现的 42 个新文档路径、音质及搜索契约变化见 [Docs 重抓记录](docs/DOCS_REFRESH_2026-11-01.md)。
