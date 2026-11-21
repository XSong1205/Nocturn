# Nocturn (夜曲)

<p align="center">
  <strong>私有、现代、离线优先的网易云音乐第三方客户端</strong><br>
  深度遵循 Material Design 3 Expressive 设计规范 · 专为极致听歌体验打造
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-10%2B%20(API%2029%2B)-3DDC84?style=flat&logo=android&logoColor=white" alt="Android Version" />
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Navigation-Navigation%203-00C853?style=flat" alt="Navigation 3" />
  <img src="https://img.shields.io/badge/Media-Jetpack%20Media3%20(ExoPlayer)-FF6F00?style=flat" alt="Jetpack Media3" />
  <img src="https://img.shields.io/badge/Architecture-Offline--First%20%7C%20Embedded%20Node-0288D1?style=flat" alt="Embedded Node" />
</p>

---

## 📖 项目简介

**Nocturn** 是一款高颜值、流体动画、离线优先的网易云音乐客户端。应用深度融合 Google **Material Design 3 Expressive** 设计语言与 **Apple Music 风格的流体动力学歌词引擎**，提供从手机、折叠屏、平板到桌面大屏的自适应交互体验。

传统第三方客户端通常依赖远程自建的反向代理服务或粗暴的网页爬虫，不仅存在隐私泄露风险，而且经常因服务下线而失效。Nocturn 采用革命性的**本地内嵌架构**：通过原生 JNI 进程内托管轻量级 Node.js Mobile 运行时，在设备本地（`127.0.0.1` 环回接口）直接提供标准、安全、低延迟的网易云 API 支撑。

> ⚠️ **免责声明**：本应用为开源第三方网易云音乐客户端，仅供技术研究与交流使用，与网易云音乐官方无关联。应用不提供音源转存、不破解 VIP 权限，登录凭据与音源访问遵循网易云音乐官方服务协议。

---

## ✨ 核心特色

### 🎨 Material Design 3 Expressive 极致美学
- **专属调色盘**：精心配制的夜色系色彩语义（深色以夜空茄紫 `#100D14` 为底，搭配珊瑚橙 Coral `#FFB4A8`、荧光青柠 Lime `#CDF56E` 与天空浅蓝 Sky Cyan `#7BD8FF`），同时完整支持 Android 12+ 基于系统壁纸的 Monet 动态取色。
- **物理弹簧动效**：接入 `MaterialExpressiveTheme` 与 `MotionScheme.expressive()`，全界面交互均具备弹性势能反馈与大圆角层次过渡（`NocturnShapes`）。
- **原生预测性返回**：全面采用 AndroidX **Navigation 3 (`NavDisplay`)**，原生接入 Android 14+ 双侧边缘滑动手势预测返回（Predictive Back），页面缩放跟随手势平滑变形，取消手势即刻无缝回弹。

### 🎵 Apple Music 风格流体歌词引擎
- **动力学弹性跟随**：内置 Accompanist Lyrics UI，采用 580ms Apple Music 流体缓动曲线（`CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)`）与高阻尼连贯弹簧链，换行过渡顺滑自然。
- **逐字毫秒对齐**：原生支持 YRC 逐字高亮时间轴与普通 LRC 逐行平滑回退，保持阅读视口居中锚定。
- **单调时钟与跳变防护**：Media3 采样与渲染帧使用统一的硬件纳秒时钟（`elapsedRealtimeNanos`），消除高亮闪回；进度条倒带快退或选句跳播时自动旁路弹簧链，杜绝负向位移碰撞。
- **视距高斯模糊**：Android 12+ 基于硬件加速渲染非当前行景深距离模糊，手动滚动浏览时瞬间唤醒焦点。
- **双语与音译支持**：支持原文、译文与罗马音译一键切换，优雅双行对齐。

### 📱 全形态自适应布局 (Adaptive & Multi-Form-Factor)
针对不同屏幕宽度（Window Width Class）提供专门打造的视觉编排：
- **手机形态 (Compact < 600dp)**：单手操作优化的底部导航栏（`ShortNavigationBar`）、浮动迷你播放器（`MiniPlayer`）与可向上手势拖拽展开的全屏播放器浮层。
- **折叠屏与平板 (Medium 600–839dp / Expanded ≥ 840dp)**：左侧自适应导航轨（`NavigationRail`），大屏专属两栏双屏分栏（如左侧黑胶唱片 / 右侧双语歌词）、多列网格歌单。
- **桌面形态 (Desktop Scaffold)**：提供大屏工作区专属的 `DesktopScaffold` 布局体系，包含左侧可折叠功能侧栏（`DesktopSidebar`）、全宽常驻底部控制栏（`DesktopPlayerBar`）与右侧抽屉式歌词面板（`DesktopLyricsPanel`）。

### 🔊 现代系统级音频体验 & Lyricon 词幕集成
- **Google Jetpack Media3 标准**：基于 `MediaSessionService` 与 `ExoPlayer` 构建后台音频引擎，深度打通系统锁屏、通知栏媒体控制、耳机线控与音频焦点管理。
- **Lyricon 词幕状态栏歌词**：无缝对接 `io.github.proify.lyricon:provider` 协议，将播放进度、逐字歌词与翻译实时投射到系统状态栏（搭配 LSPosed / 词幕模块体验更佳）。

### 🔒 硬件级安全与离线优先架构
- **设备端内嵌 Node.js 服务**：将 LocalAPI 作为内部服务运行在应用沙盒内，完全杜绝第三方代理劫持。
- **高等级安全会话存储**：支持二维码扫码登录与验证码登录，Session Cookie 经由 **AndroidKeyStore (AES-GCM)** 硬件级隔离加密保存，不上传任何中间服务器。

---

## 🏛️ 系统架构

Nocturn 采用模块化与离线优先的设计思路，客户端通过标准 HTTP/JSON 与本地内嵌服务通信：

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        Nocturn 客户端 (:app)                            │
│   Jetpack Compose · Material 3 Expressive · Navigation 3 · Media3      │
│   发现 / 搜索 / 歌单 / 专辑 / 歌手 / 每日推荐 / 播放器 / 流体歌词 / 词幕   │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  Kotlin Multiplatform 客户端 (:local-api)               │
│                   基于 Ktor 的强类型客户端封装                          │
│        数据模型归一化 · 歌单分页游标 · 歌词解析 (YRC/LRC) · 统一异常包装   │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Bearer Token / HTTP 127.0.0.1 (Loopback)
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│               Android 原生运行时编排 (:local-api-android)               │
│   JNI C++ 桥接 · 进程内 Node.js Mobile VM · 随机端口与健康巡检 · 权限约束 │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   内置服务运行包 (:local-api-node)                      │
│      API Enhanced 4.41.1 · 63+ 端点白名单 · Android 沙盒数据存储适配    │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ HTTPS
                                     ▼
                                网易云音乐线上服务
```

### 模块分工

| 模块 | 职责与定位 |
| --- | --- |
| **`:app`** | **Nocturn 客户端核心层**。负责所有 UI 界面、M3 Expressive 设计规范实现、Navigation 3 路由管理、ExoPlayer 音频回放控制、流体歌词渲染、词幕同步与硬件加密凭据管理。 |
| **`:local-api`** | **Kotlin Multiplatform 客户端**。提供多平台通用的类型化 API、网易云数据 JSON 归一化清洗、歌单游标分页、YRC 歌词解析引擎及契约测试。 |
| **`:local-api-android`** | **Android 平台原生桥接**。使用 C++ JNI 加载并管理官方 Node.js Mobile 运行库（`libnode.so`），负责进程内生命周期、本地随机端口调度与网络安全隔离。 |
| **`:local-api-node`** | **内置 API 服务**。集成上游 API Enhanced 并裁剪至 63 个经过白名单审查的核心端点，处理 Android 沙盒路径适配与单文件打包。 |

---

## 📱 界面与功能一览

Nocturn 具备完整的网易云日常听歌与曲库管理能力：

- 🧭 **发现音乐**：动态问候语、编辑精选 Hero 轮播（`HorizontalCenteredHeroCarousel`）、分类推荐歌单横向画廊、热门榜单卡片，以及登录用户的私人专属每日推荐。
- 🔍 **双态搜索**：支持搜索栏折叠与全屏展开双态过渡（`TopSearchBar` ↔ `ExpandedFullScreenSearchBar`），提供热搜榜、实时输入建议、历史记录，单曲 / 歌单 / 专辑 / 歌手多维度分页聚合检索。
- 📑 **歌单与专辑**：沉浸式大封面 Hero 头部，支持一键播放全部、歌单收藏与取消、分批次触底分页加载。
- 👤 **个人资料库**：我的喜欢、创建与收藏的歌单、最近播放足迹，以及账号二维码极速登录。
- 🎧 **播放体验**：
  - 封面与流体歌词双模式一键切换；
  - 动态波浪进度条（`LinearWavyProgressIndicator`），支持任意精度拖动试听；
  - 播放模式自由切换（单曲循环、列表循环、随机播放）；
  - 底部抽屉式播放队列管理（`PlayerQueueSheet`）。
- ⚙️ **个性化偏好**：深色 / 浅色 / 跟随系统主题、标准～Hi-Res 极致无损音质设定、状态栏歌词集成开关与存储空间管理。

---

## 🛠️ 构建与开发

### 环境要求
- **JDK**：JDK 21（推荐 Eclipse Temurin 21）
- **Android SDK**：Compile SDK `37`，Target SDK `36`，Min SDK `29`（Android 10+）
- **NDK**：`28.2.13676358`
- **CMake**：`3.22.1`
- **Node.js**：Node 18+ (LTS) 与 `npm`
- **Gradle**：固定为 Gradle `9.8.2`，搭配 AGP `9.5.0` 与 Kotlin `2.4.21`

### 编译运行

在项目根目录下通过 PowerShell 执行构建：

```powershell
# 1. 编译 Debug APK
.\gradlew.bat :app:assembleDebug

# 2. 运行单元测试与 Android Lint 静态分析
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug

# 3. 通过 ADB 安装到连接的设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **构建提示**：
> - 首次编译时，Gradle 会自动执行 `npm ci` 并下载官方验证过的 Node.js Mobile 原生库解压至缓存目录。
> - 应用版本维护在 `app/version.properties` 中。调用完整打包命令（如 `assembleRelease`）时，构建脚本会自动递增 `versionCode` 与 `versionName` 的 patch 版本。

### 自动化构建发布 (CI/CD)

仓库配置了完善的 GitHub Actions 工作流（[`.github/workflows/release.yml`](.github/workflows/release.yml)）。每次合并至 `main` 分支时，CI 自动读取版本号、编译 Release APK 并发布为对应的 GitHub Release。

---

## 🧩 LocalAPI 模块说明

虽然 Nocturn 首先是一个面向最终用户的网易云音乐客户端，但其内部解耦的 `local-api` 系列模块亦可作为独立的离线 API 组件库使用：

### Android 原生端接入示例

```kotlin
import dev.nocturn.localapi.client.createLocalApi
import dev.nocturn.localapi.client.*

// 在应用协程作用域内调用（内部会自动在后台准备并拉起 Node VM）
val api = createLocalApi(applicationContext)

// 基础健康检查与检索
val health = api.health()
val searchResults = api.searchSongs("Nocturne", limit = 20)
val songDetail = api.songDetail(listOf(347230L))

// 获取逐字歌词 (YRC 优先，LRC 回退，包含翻译与罗马音)
val lyrics = api.songLyrics(1824020871L)

// 释放客户端 HTTP 资源
api.close()
```

### 运行时限制
- **内存页对齐**：内置 Node.js Mobile 18.20.4 二进制文件采用 **4 KB ELF 对齐**，支持主流 `arm64-v8a` 与 `x86_64` 架构设备（启动前会自动核验页大小）。
- **进程单例**：Node 虚拟机在每个 Android 应用进程生命周期内仅允许初始化一次。
- **回环网络安全**：原生 AAR 强制启用严苛的网络安全配置，明文 HTTP 流量严格限定在 `127.0.0.1` 环回接口。

---

## 🧪 自动化测试与工程质量

Nocturn 坚持高度确定性与全覆盖的工程质量标准：

- **客户端测试**：包含 160+ 项 Robolectric 与 Compose UI 测试，全量覆盖网易云 JSON 归一化清洗、歌单分页游标、加密密钥存储、播放调度与全部页面交互。
- **宿主视觉截图套件 (`ScreenshotGalleryTest`)**：在主机端利用 Robolectric 确定性渲染并输出 30 余张高精度界面截图（包括手机深浅主题、平板双栏视图、桌面三栏布局与中英日多语言流体歌词）。
- **LocalAPI 契约检验**：通过 `npm test` 与在线 Smoke 对照脚本对 18 类公开接口进行回归校验，保证字段与上游服务契约一致。

相关技术细节与深度设计规范可参阅 [`docs/`](docs/) 目录下的文档：
- [API 参考与契约对照](docs/API_REFERENCE.md)
- [Accompanist 歌词引擎与时钟设计](docs/LYRICS_UI.md)
- [Lyricon 词幕状态栏集成说明](docs/LYRICON.md)
- [Navigation 3 路由与预测返回设计](docs/NAVIGATION.md)
- [Android 内嵌 Node.js 运行时规格](docs/NODE_RUNTIME.md)

---

## 📄 开源许可证

本项目核心代码基于 **MIT License** 开放源码。
引用的第三方开源组件与协议说明参见各模块目录及许可清单文件。
