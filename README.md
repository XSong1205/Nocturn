# Nocturn

遵循 Material Design 3 Expressive 设计规范的网易云音乐 Android 客户端，内置本地 Node.js 运行时作为离线优先的 API 服务层。

<p align="center">
  <img src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84?style=flat&logo=android&logoColor=white" alt="Android Version" />
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material%203%20Expressive-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Navigation-Navigation%203-00C853?style=flat" alt="Navigation 3" />
  <img src="https://img.shields.io/badge/Media-Jetpack%20Media3-FF6F00?style=flat" alt="Media3" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat" alt="License" />
</p>

---

> **Android 兼容性**：最低安装版本为 Android 7.0（API 24）。Android 7.0～9 使用基础 Compose 歌词视图。Android 10 起使用 Accompanist Lyrics UI。

## 简介

**Nocturn** 是一个第三方的网易云音乐 Android 客户端，采用 Jetpack Compose、Material Design 3 Expressive 和 Navigation 3 构建。

常规第三方客户端通常需要用户自行搭建或连接远程代理服务。Nocturn 将 Node.js Mobile 运行时通过 JNI 嵌入在应用主进程中，在设备本地（`127.0.0.1`）提供 API 服务（LocalAPI），无需额外配置服务器或抓取网页数据。

> **免责声明**：本应用为个人开源项目，仅供学习与技术交流，与网易云音乐官方无关。本应用不提供音源转存与 VIP 破解，登录凭证与音频资源访问均遵循网易云音乐服务协议。

---

## 功能特性

- **界面与设计**：
  - 基于 Jetpack Compose 与 Material Design 3 Expressive 开发，包含完整的深浅色语义色彩体系，支持 Android 12+ 基于系统壁纸的动态取色（Monet）。
  - 使用 AndroidX Navigation 3，支持 Android 14+ 预测性返回手势（Predictive Back）。
  - 支持手机（Compact）、折叠屏/平板（Medium/Expanded）自适应布局，以及桌面端三栏布局（`DesktopScaffold`）。

- **播放与歌词**：
  - 基于 Jetpack Media3（`MediaSessionService` + `ExoPlayer`）实现后台播放、系统媒体通知与锁屏控制。
  - 正在播放背景通过封面量化与 Monet HCT 生成纯色底色，遵循外观设置中的 M3E/M3 与取色风格，随切歌平滑更新；无图或加载失败时使用主题回退色，适配手机、平板和大屏。
  - 接入 Accompanist Lyrics UI，支持 YRC 逐字高亮与 LRC 逐行回退，支持翻译与罗马音切换；采用统一硬件时钟与平滑滚动，Android 12+ 支持背景距离模糊。
  - 支持 Lyricon 词幕协议，可将播放进度与歌词推送到系统状态栏（需系统或模块支持）。

- **日常功能**：
  - **发现**：主页轮播推荐、推荐歌单、排行榜、每日推荐歌曲。
  - **搜索**：双态搜索栏，支持热门搜索、搜索建议、搜索历史，以及单曲、歌单、专辑、歌手的分类检索与分页加载。
  - **曲库与详情**：歌单/专辑详情页（支持批量播放与收藏）、歌手页、我喜欢的音乐及最近播放。
  - **账号**：支持二维码扫码登录与验证码登录，Session Cookie 使用 AndroidKeyStore (AES-GCM) 在本地加密存储。
  - **设置**：音质选择（标准至 Hi-Res，受账号权限约束）、深浅色主题切换、词幕开关与存储占用管理。

---

## 架构

Nocturn 由客户端应用及内置的 LocalAPI 服务组成：

```text
┌──────────────────────────────────────────────────────────────────┐
│                      Nocturn 客户端 (:app)                       │
│  Jetpack Compose · Material 3 Expressive · Navigation 3 · Media3 │
│  UI 界面 · 播放调度 · 歌词渲染 · 状态栏词幕 · 加密凭据存储       │
└────────────────────────────────┬─────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│             Kotlin Multiplatform 客户端 (:local-api)              │
│  Ktor HTTP Client · 数据模型归一化 · 歌单分页 · 歌词解析 (YRC/LRC)│
└────────────────────────────────┬─────────────────────────────────┘
                                 │ Bearer Token / HTTP 127.0.0.1 (Loopback)
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│             Android 原生运行时编排 (:local-api-android)          │
│  JNI C++ 桥接 · Node.js Mobile 进程管理 · 端口分配与健康检查     │
└────────────────────────────────┬─────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────┐
│                 内置服务打包 (:local-api-node)                   │
│  API Enhanced 4.41.1 · 端点白名单 · Android 本地沙盒路径适配     │
└────────────────────────────────┬─────────────────────────────────┘
                                 │ HTTPS
                                 ▼
                          网易云音乐线上服务
```

### 模块结构

| 模块 | 说明 |
| --- | --- |
| **`:app`** | 客户端应用层。负责 UI、导航路由、Media3 播放器集成、歌词渲染、词幕推送及本地加密会话管理。 |
| **`:local-api`** | Kotlin Multiplatform 客户端。提供类型化 API 接口、数据字段清洗归一化、歌词解析及跨平台契约测试。 |
| **`:local-api-android`** | Android 平台 JNI 模块。管理内嵌 Node.js Mobile 的启动与生命周期，限制环回网络策略并提供就绪检查。 |
| **`:local-api-node`** | Node.js 服务封装层。引入 API Enhanced 依赖，通过白名单限制端点，打包为单文件供 Android 端加载。 |

---

## 构建与运行

### 环境要求
- **JDK**：21
- **Node.js**：18+ 及 npm
- **Android SDK**：Compile SDK `37`，Target SDK `36`，Min SDK `29`
- **NDK**：`28.2.13676358`
- **CMake**：`3.22.1`
- **Gradle**：9.8.2（使用项目自带 wrapper）

### 常用命令

```powershell
# 编译 Debug APK
.\gradlew.bat :app:assembleDebug

# 运行单元测试与 Lint 检查
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug

# 构建并测试 LocalAPI 模块
.\gradlew.bat :local-api:hostTest :local-api-android:assembleDebug
```

首次构建时会自动执行 `npm ci` 并下载经过校验的官方 Node.js Mobile 原生库解压至缓存目录。

---

## LocalAPI 模块使用说明

如需将内置的 LocalAPI 模块作为独立库引入其他项目：

```kotlin
import dev.nocturn.localapi.client.createLocalApi
import dev.nocturn.localapi.client.*

// 初始化客户端（会在后台线程拉起进程内 Node 服务）
val api = createLocalApi(applicationContext)

// 基础调用
val health = api.health()
val results = api.searchSongs("Nocturne", limit = 20)
val detail = api.songDetail(listOf(347230L))

// 歌词解析（返回类型化歌词结构，优先包含 YRC 逐字时间与翻译）
val lyrics = api.songLyrics(1824020871L)

// 关闭客户端 HTTP 资源
api.close()
```

### 运行时说明
- **ABI 与页大小**：内置 Node 运行时为官方 Node.js Mobile 18.20.4 构建，支持 `arm64-v8a` 和 `x86_64`，采用 4 KB ELF 对齐（不支持 16 KB 页大小设备）。
- **进程限制**：Node 虚拟机在 Android 进程生命周期中只能启动一次。
- **网络策略**：本地服务绑定于 `127.0.0.1`，仅对环回地址开放明文 HTTP 通信，对外请求均走 HTTPS。

---

## 边界与已知限制

- 仅支持 4 KB 内存页的 Android 设备（大部分现有设备为 4 KB 页，不支持开启 16 KB 兼容模式的设备）。
- 音频播放地址由接口返回，部分曲目根据网易云版权策略需要对应 VIP 账号权限。
- 状态栏歌词（Lyricon）依赖系统或三方模块（如配合 LSPosed / 词幕模块）支持，未安装对应环境时不生效。

---

## 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。相关第三方依赖分别遵循其对应的开源协议。
