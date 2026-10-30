# Nocturn · LocalAPI

Nocturn 是计划采用 **Compose Multiplatform + miuix** 的 Android 网易云音乐客户端。当前阶段只实现独立的 LocalAPI 和 Kotlin 客户端，尚未创建音乐界面或播放器。

没有复制 SPlayer 实现；独立编写 JNI、Android 生命周期管理、本机 HTTP 服务和 Kotlin 客户端，使用上游 Node.js Mobile 与 API Enhanced 作为依赖。

## 当前模块

| 模块 | 职责 |
| --- | --- |
| `local-api-node` | 内置 API Enhanced **4.36.2**，46 个端点白名单，Node18 单文件打包 |
| `local-api-android` | Kotlin/JNI 启动 Node、本机随机端口、就绪与鉴权检测、资源校验、运行状态 |
| `local-api` | KMP HTTP 客户端、Android 启动工厂、共享契约测试 |

```text
未来 Compose 界面 / Repository
         ↓
local-api（Ktor）
         ↓ Bearer / HTTP 127.0.0.1
local-api-android（Node.js Mobile / JNI）
         ↓
local-api-node（API Enhanced）→ 网易云在线服务
```

`host` JVM 目标用于运行共享客户端测试，不代表增加桌面客户端。

## 构建

需要 JDK 21、Node.js 18+ / npm、Android SDK 36、NDK `28.2.13676358`、CMake `3.22.1`。Android 最低 API 29，当前支持 `arm64-v8a` 和 `x86_64`。

在 `local.properties` 中配置本机 Android SDK 路径，或设置 `ANDROID_HOME`。该文件不应提交版本控制。

```powershell
.\gradlew.bat :local-api:assemble :local-api:hostTest :local-api-android:assembleDebug :local-api-android:lintDebug
```

首次构建自动执行锁定依赖的 `npm ci`，下载并校验官方 Node Android 运行库，打包 API 资产和第三方许可证。下载与生成文件保存在已忽略的缓存／build 目录。

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

底层 API 返回 `JsonObject` 保留上游业务字段，调用方仍需判断 `code` 及播放地址是否为 `null`。新增类型化方法提供歌曲字段归一化、歌单分页、歌词解析、二维码状态映射和会话恢复；这类方法会将上游业务失败转为 `MusicServiceException`，而不是返回空列表。账号 Cookie 由后续账号层安全保存，客户端不会自行持久化。

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

在线 smoke 测试覆盖 12 类公开接口：搜索、云搜索、移动端搜索建议、默认关键词、歌曲详情、普通／逐字歌词、音质详情、可用性、歌单详情／分页、批量播放地址。不会登录真实账号或修改账号数据；上游 HTTP 请求附带时间戳避开缓存。对照服务在验证时运行 API Enhanced `4.36.2`，内置依赖固定为相同版本。

- Node **18.20.4**：22 项离线测试通过，覆盖跨语言路由一致性、批量参数和上游布尔值转换。
- Kotlin 共享客户端：31 项测试通过，包含模型、分页、登录状态与歌词解析。
- 12 类公开接口的本地／参考服务共 24 项在线契约检查通过；另外验证了实际游客会话及设备标识／密钥持久化的一致性。真实账号登录、短信和收藏操作仅执行隔离测试。
- 原生 JNI：两个 ABI 已编译与链接；Android AAR 和设备测试 APK 可构建。
- 设备测试位于 `local-api-android/src/androidTest`，覆盖并发启动、实际健康检查与鉴权。连接 4 KB 页 Android 设备后执行 `:local-api-android:connectedDebugAndroidTest`。当前环境无设备，因此尚未执行 Android 端的 JNI 启动测试。

## 当前运行时边界

官方 Node.js Mobile `18.20.4` 的两个 `libnode.so` 均只有 **4 KB ELF 对齐**。启动前会检测设备页大小，在 16 KB 设备上明确返回失败，尚不支持该类设备。Node 18 已停止维护，后续发布前需要替换或重建维护中的运行时；本阶段产物用于功能开发与验证。

API 在手机本地运行，在线音乐请求仍需网络。就绪文件、Token 和缓存保存在应用私有目录；Token 不写入就绪文件或日志。请求协议、端点及依赖许可证见 [Node 层文档](local-api-node/README.md)，原生库版本、SHA-256 与兼容性见 [运行时文档](docs/NODE_RUNTIME.md)。

部署服务文档的查阅记录、现有封装差异与后续补齐项见 [API 文档对照](docs/API_REFERENCE.md)。
