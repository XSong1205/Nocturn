# Nocturn 项目规范与开发手册

> **Nocturn** 是一款基于 Android 平台的现代高颜值第三方网易云音乐播放器客户端，采用 Jetpack Compose + MIUIX (HyperOS 风格) 构建，原生支持 YRC 逐字逐句歌词、独立内嵌 API / 增强型自建 API 双模运行、VIP 无缝鉴权高保真播放以及现代系统媒体通知中心集成。

---

## 目录
1. [核心开发规则与提交规范](#1-核心开发规则与提交规范)
2. [项目架构与技术栈](#2-项目架构与技术栈)
3. [工程代码目录结构](#3-工程代码目录结构)
4. [核心功能实现机制与避坑指南](#4-核心功能实现机制与避坑指南)
   - [4.1 双引擎 API 架构设计](#41-双引擎-api-架构设计)
   - [4.2 VIP 会员歌曲播放与 30 秒试听避坑](#42-vip-会员歌曲播放与-30-秒试听避坑)
   - [4.3 登录态与 Cookie 全链路同步](#43-登录态与-cookie-全链路同步)
   - [4.4 逐字歌词 (YRC) 与 Apple Music 风格交互](#44-逐字歌词-yrc-与-apple-music-风格交互)
   - [4.5 媒体服务与系统通知中心集成](#45-媒体服务与系统通知中心集成)
5. [构建、打包与常用命令](#5-构建打包与常用命令)

---

## 1. 核心开发规则与提交规范

### 1.1 自动代码提交与远端同步
- **必须执行**：每次完成功能开发、Bug 修复或重大代码变更并验证通过（构建成功）后，**必须自动执行 `git commit` 提交代码，并自动执行 `git push` 推送至 GitHub 仓库**，保持本地与远端代码实时同步清晰可追溯。
- **Commit Message 规范**：遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范，清晰概括本次修改内容。格式如：
  - `feat: <新功能描述>`
  - `fix: <Bug 修复描述>`
  - `refactor: <重构描述>`
  - `perf: <性能优化描述>`
  - `docs: <文档更新描述>`
  - `style: <UI/代码样式微调>`

### 1.2 代码风格与开发准则
- **Compose 纯声明式规范**：状态提升（State Hoisting），全局单一状态源（SSOT）。UI 层禁止直接触发耗时阻塞 IO 操作，所有网络请求与媒体解析必须交由 `Repository` 在 `Dispatchers.IO` 执行。
- **MIUIX 设计语言**：统一使用 HyperOS 风格组件（连续平滑超椭圆 Squircle 卡片、流畅阻尼动效、标准深色/浅色自适应表面）。
- **容灾与鲁棒性**：任何网络交互与音频解析必须提供健全的 try-catch 与多级备用链路，防止客户端 Crash 或抛出未经捕获的协程异常。

---

## 2. 项目架构与技术栈

### 2.1 依赖与环境
| 模块 / 维度 | 选用技术 / 依赖 | 说明 |
| :--- | :--- | :--- |
| **编程语言** | Kotlin 2.4.10 | 启用现代化语言特性与强类型注解 |
| **运行平台** | Android (minSdk 26, compileSdk 36) | 完美覆盖 Android 8.0 至 Android 15+ |
| **JDK 版本** | OpenJDK 21 (Target JVM 17) | 现代化字节码与高效编译环境 |
| **UI 框架** | Jetpack Compose Multiplatform 1.11.1 | 声明式响应式界面开发 |
| **系统风格 UI 库** | MIUIX KMP (top.yukonga.miuix.kmp 0.9.3) | miuix-ui, miuix-preference, miuix-icons, miuix-blur, miuix-squircle |
| **歌词引擎** | accompanist-lyrics-ui (1.0.19) + lyrics-core (0.4.7) | 逐字时序渲染与平滑高亮 |
| **网络请求** | OkHttp 5.3.2 | 连接池复用、重定向跟随与持久化拦截 |
| **并发与序列化** | kotlinx-coroutines (1.10.2) + kotlinx-serialization-json (1.9.0) | 结构化并发与轻量级高效 JSON 解析 |
| **系统媒体集成** | AndroidX Media (`MediaSessionCompat`, `NotificationCompat.MediaStyle`) | 锁屏海报、滑动进度条、控制中心联动 |

### 2.2 分层架构模式
- **UI 层 (Presentation Layer)**：基于 Compose 实现响应式界面，通过 `collectAsState()` 订阅 `ViewModel` 或全局单例暴露的只读 `StateFlow`。
- **业务领域与仓库层 (Repository Layer)**：`MusicRepository` 负责歌曲播放源解析与双引擎降级调度、歌单/榜单管理、云端红心同步；`SettingsRepository` 负责本地首选项持久化（Cookie、用户信息、音频质量、搜索历史、主题模式）。
- **播放引擎层 (Player Engine)**：`NocturnPlayer` 管理 `MediaPlayer` 状态机与播放队列；`PlaybackService` 提供前台保活与跨进程媒体控制。
- **数据源层 (Data Source)**：
  - `EmbeddedNcmEngine`：原生逆向加解密（纯 Kotlin 实现 AES-CBC, AES-ECB, RSA, MD5），直连网易云官方服务端。
  - `EmbeddedHttpServer`：本地 127.0.0.1:1145 桥接服务器，兼容标准 SPlayer API 格式。
  - `NcmApiClient`：远程网易云 Node.js 增强 API 客户端，自适应 Cookie 注入。

---

## 3. 工程代码目录结构

```
d:\Nocturn\
├── app\
│   ├── build.gradle.kts                   # 模块构建配置与依赖引入
│   └── src\main\
│       ├── AndroidManifest.xml            # 清单文件 (前台服务、网络权限、音频焦点)
│       ├── java\com\nocturn\music\
│       │   ├── MainActivity.kt            # 应用入口主 Activity
│       │   ├── NocturnApp.kt              # Application 全局上下文初始化
│       │   ├── data\
│       │   │   ├── api\
│       │   │   │   ├── EmbeddedNcmEngine.kt   # 内嵌网易云原生协议引擎 (核心解析+鉴权)
│       │   │   │   ├── EmbeddedHttpServer.kt  # 本地内嵌轻量 HTTP 代理服务 (端口 1145)
│       │   │   │   ├── NcmCrypto.kt           # 官方加密算法实现 (WEAPI / EAPI 加解密)
│       │   │   │   ├── NcmApiClient.kt        # 远程 Node.js API 客户端
│       │   │   │   ├── LyricParser.kt         # YRC / LRC 歌词时序解析器
│       │   │   │   ├── NeteaseApi.kt          # 统一 API 契约接口
│       │   │   │   └── NeteaseCustomApi.kt    # 自定义 API 兼容层
│       │   │   └── repository\
│       │   │       ├── MusicRepository.kt     # 核心音乐数据仓库与双引擎降级调度
│       │   │       └── SettingsRepository.kt  # 首选项与用户状态持久化仓储
│       │   ├── model\
│       │   │   ├── Song.kt                # 歌曲实体模型
│       │   │   ├── Playlist.kt            # 歌单实体模型
│       │   │   ├── PlayerModels.kt        # 播放状态、播放模式、音质枚举、用户资料
│       │   │   └── ArtistAlbumBanner.kt   # 歌手、专辑、轮播图模型
│       │   ├── player\
│       │   │   ├── NocturnPlayer.kt       # 播放器核心单例控制器
│       │   │   └── PlaybackService.kt     # 前台播放服务与系统通知中心交互
│       │   └── ui\
│       │       ├── components\            # 可复用 UI 视图组件
│       │       │   ├── AsyncImage.kt      # 带两级内存/网络缓存的图片加载器
│       │       │   ├── BannerCarousel.kt  # 发现页平滑轮播图
│       │       │   ├── MiniPlayerBar.kt   # 底部常驻迷你播放条
│       │       │   ├── PlaylistCard.kt    # 矩形圆角歌单卡片
│       │       │   └── SongListItem.kt    # 歌曲列表单项组件
│       │       ├── navigation\
│       │       │   └── AppNavigation.kt   # 页面路由与底部导航栏调度
│       │       ├── screens\               # 一级与二级业务页面
│       │       │   ├── HomeScreen.kt      # 发现音乐主页
│       │       │   ├── SearchScreen.kt    # 综合搜索与热搜榜
│       │       │   ├── MyScreen.kt        # 个人中心、多模式登录与收藏歌单
│       │       │   ├── PlayerScreen.kt    # 全屏黑胶唱片与 YRC 逐字歌词页
│       │       │   ├── PlaylistDetailScreen.kt # 歌单详情曲目列表页
│       │       │   ├── QueueSheet.kt      # 播放队列底部抽屉弹窗
│       │       │   └── SettingsScreen.kt  # 系统设置、API 模式与音质切换
│       │       └── theme\                 # 视觉风格与排版主题
│       │           ├── NocturnTheme.kt    # MIUIX 动态调色盘
│       │           └── HyperTheme.kt      # 超椭圆 Squircle 与品牌主色
├── gradle\
│   └── libs.versions.toml                 # Version Catalog 统一依赖管理
├── GEMINI.md                              # 本开发指南与规范手册
└── README.md                              # 项目公开简介与说明文档
```

---

## 4. 核心功能实现机制与避坑指南

### 4.1 双引擎 API 架构设计
- **内嵌模式 (`ApiMode.EMBEDDED`) - 推荐默认**：
  - 不需要依赖任何第三方搭建的 API 外部服务器。
  - 直接在本地利用 `NcmCrypto` 将请求封装为官方标准的 `eapi` / `weapi` 加密数据，直接打向网易云官方网关 (`interface.music.163.com` 与 `music.163.com`)。
  - 启动轻量 Localhost 服务 (`127.0.0.1:1145`)，为本地及外部扩展提供一致的 RESTful 接口。
- **自定义远端模式 (`ApiMode.CUSTOM`)**：
  - 连接部署好的 `NeteaseCloudMusicApi` 实例（默认兼容 `https://ncmapi.rpixel.online`）。
  - 支持动态网络测速 Ping、Cookie 与自定义域名注入。

### 4.2 VIP 会员歌曲播放与 30 秒试听避坑（极为重要 ⚠️）
- **踩坑点**：
  - 网易云公开的 302 重定向外链 `https://music.163.com/song/media/outer/url?id=$songId.mp3` **只对 VIP 歌曲提供 30 秒预览试听片段**！绝对不能把该链接作为优先播放地址！
  - 许多第三方实现直接调用 EAPI `/song/enhance/player/url/v1` 时，若未在请求体的待加密参数中注入官方标准的 `data.header` 对象，网易云服务端会自动将其判定为匿名非会员请求，返回空 URL，从而被迫降级到 30 秒试听外链。
- **正确规范**：
  1. **构建官方规范的 `data.header`**：在 `EmbeddedNcmEngine.requestEapi` 中必须组装设备信息（`osver`, `deviceId`, `appver`, `requestId`）并将用户 Cookie 中的 `MUSIC_U` 与 `MUSIC_A` 显式放入 `header` 对象，然后再调用 `NcmCrypto.eapi`。
  2. **多层级高可用容灾解析链路**：
     - **第 1 顺位**：原生 Android EAPI `/song/enhance/player/url/v1`（指定音质档位 `standard`/`exhigh`/`lossless`/`hires`，校验并过滤 `freeTrialInfo` 试听标记）。
     - **第 2 顺位**：官方 Web 端 WEAPI `/weapi/song/enhance/player/url`（以高码率 `br=320000/999000` 鉴权解析）。
     - **第 3 顺位**：远端增强 API `/song/url/v1`（Header + Query 参数双重透传 Cookie）。
     - **第 4 顺位**：仅当上述解析均失败时，才作为最后的兜底策略使用外链。

### 4.3 登录态与 Cookie 全链路同步
- **Set-Cookie 拦截机制**：
  - 网易云官方登录接口（扫码轮询 `/weapi/login/qrcode/client/login` 与短信登录 `/weapi/login/cellphone`）成功后，核心鉴权凭证 `MUSIC_U` 与 `__csrf` 是返回在 HTTP 响应的 **`Set-Cookie` 头部**中，而不是在响应的 JSON Body 里！
  - `EmbeddedNcmEngine` 必须使用 `executeWeapiWithResult` 读取响应头并使用 `extractCookies` 提取完整 Cookie 字符串保存到 `SettingsRepository`。
- **云端收藏与红心双向同步**：
  - 用户登录成功后，系统自动调用 `MusicRepository.syncCloudFavorites()`：拉取用户的首个歌单（「我喜欢的音乐」）并将完整曲目同步写入本地 `SettingsRepository.favoriteSongs`。
  - 在播放器中点亮/取消红心时，本地状态切换的同时，必须异步发起 `MusicRepository.likeSong(songId, like)` 向网易云云端同步。

### 4.4 逐字歌词 (YRC) 与 Apple Music 风格交互
- **歌词时序精准驱动**：
  - 通过 `rememberUpdatedState(currentPositionMs)` 包装当前播放进度，供 `KaraokeLyricsView` 内的 `derivedStateOf` 建立响应式订阅，确保逐字高亮 Canvas 在 Draw 阶段持续刷新。
- **智能居中与手势防打扰**：
  - 用户拖拽歌词浏览时标记 `isUserScrolling = true` 并暂停自动滚动；
  - 停止滑动 3 秒后，自动触发平滑弹性动画吸附回正在播放的当前行；
  - 支持点击任意歌词行跳转播放并自动吸附。

### 4.5 媒体服务与系统通知中心集成
- **前台保活与音频焦点**：
  - `PlaybackService` 声明 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限；
  - 播放时注册 `MediaSessionCompat` 并更新 `PlaybackStateCompat`；
  - 构造包含上一曲、播放/暂停、下一曲及红心收藏 Action 的 `NotificationCompat.MediaStyle` 大图通知，无缝适配 HyperOS / Android 现代媒体控制中心及锁屏大图封面。

---

## 5. 构建、打包与常用命令

### 5.1 环境配置要求
- 本地配置好 Android SDK 并确保 `local.properties` 中指定了正确的 `sdk.dir`：
  ```properties
  sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
  ```
- 建议使用 Gradle 9.5+ 运行编译任务。

### 5.2 编译与产物位置
在项目根目录下通过 PowerShell 执行：
```powershell
# 编译打包 Debug APK
& "C:\Users\XSong\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat" :app:assembleDebug

# 产物默认生成位置：
# app/build/outputs/apk/debug/app-debug.apk
```

### 5.3 代码检查与依赖分析
```powershell
# 查看依赖树
./gradlew :app:dependencies --configuration debugRuntimeClasspath

# 检查 Git 状态
git status
```

