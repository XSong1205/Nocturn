# Nocturn · 网易云音乐第三方客户端

使用 Jetpack Compose 与 **Material 3 Expressive** 重写的网易云音乐第三方 Android 客户端。数据全部来自仓库内置的 LocalAPI（API Enhanced 4.36.2，经本机 Node 运行时提供），不抓取网页、不转存音源。最低 Android 10（API 29）。

> 本应用为第三方客户端，与网易云音乐官方无关联，也不代表官方立场。登录信息提交给网易云音乐，本机仅加密保存会话 Cookie。

## 功能

- **发现**：问候头图、可滑动 / 可点圆点的编辑精选 Hero 轮播、为你推荐的歌单横向画布、排行榜卡片、登录后的每日推荐歌曲；下拉刷新、加载 / 失败 / 空态齐全。
- **搜索**：真实 `TopSearchBar` → 全屏 `ExpandedFullScreenSearchBar` 双态搜索；默认关键词、热门搜索、搜索历史、输入建议；单曲 / 歌单 / 专辑 / 歌手四个分类结果，支持分页加载与错误重试。
- **歌单 / 排行榜**：沉浸式 Hero 头部（大封面、创建者、播放量、标签、可展开简介）、播放全部、收藏 / 取消收藏、歌曲列表与触底分页。
- **专辑 / 歌手 / 我喜欢的音乐**：专辑信息、歌手热门歌曲与专辑横向列表、点赞歌曲全量列表。
- **全屏播放器**：底部 Dock 切换封面与 Accompanist 歌词（YRC 逐字高亮、LRC 逐行回退、距离模糊与轻量逐行跟随动画、进度条右上方“译 / 音”开关、点句跳转、手动浏览与恢复跟随）、波浪进度条与可拖动进度、上一首 / 播放暂停 / 下一首、喜欢 / 随机 / 循环 / 播放队列（底部抽屉）；顶部仅显示歌曲与歌手。
- **我的**：扫码、短信验证码或手机号密码登录（Keystore 加密保存 cookie）、头像 / 昵称 / 签名 / 统计、我的歌单、最近播放、退出登录；未登录时提供登录入口、最近播放与外观 / 音质设置。
- **后台播放**：Media3 `MediaSessionService` + `ExoPlayer`，系统媒体通知、锁屏控制与音频焦点交给 Media3；离开应用后继续播放。
- **设置**：浅色 / 深色 / 跟随系统、标准～Hi-Res 音质选择（实际可用性取决于账号 / 会员）。

## Material 3 Expressive 设计

- `MaterialExpressiveTheme` + `MotionScheme.expressive()`：弹簧空间位移、强调式排版（`headlineLargeEmphasized` 等）。
- 页面与全屏播放器使用 AndroidX Navigation 3 `NavDisplay`：普通切换采用 Material 3 Expressive `fastEffectsSpec`，手势返回使用导航库内置的预测返回缩放过渡，随手势缩小当前页并露出上一页，取消时恢复；重复详情页分别保存滚动状态，返回优先收起播放器。
- 珊瑚橙 / 荧光青柠 / 天空蓝在茄紫色夜景上的品牌调色板，浅色与深色两套完整 `ColorScheme`。
- 组件：`ShortNavigationBar`、`LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar`、`HorizontalCenteredHeroCarousel`、`HorizontalMultiBrowseCarousel`、`ButtonGroup`、`SplitButton`、`HorizontalFloatingToolbar`、`ToggleButton`、`SegmentedButton`、`LinearWavyProgressIndicator`、`ContainedLoadingIndicator`、`MaterialShapes` 绘制、`ModalBottomSheet`、`PullToRefreshBox`。
- 封面在无网络时使用按歌曲 ID 稳定生成的原创渐变几何插画，离线、加载中与测试截图都不会出现空白占位。

## 架构

```text
MainActivity
  └─ NocturnApp（导航壳 / 底部栏 / 迷你播放器 / 播放器浮层 / 登录面板）
       └─ NocturnViewModel（导航、各屏状态、账号、播放编排）
            ├─ MusicRepository → LocalMusicRepository → LocalApiClient → 内置 LocalAPI（Node）
            │    ├─ NeteaseJson（legacy / cloudsearch / 歌单分页 / 歌词 / 账号归一化）
            │    └─ SessionStore + SessionCipher（AndroidKeyStore AES-GCM 加密 cookie）
            └─ PlaybackEngine → MediaSessionPlaybackEngine → MediaController
                 └─ PlaybackService（MediaSessionService + ExoPlayer + 媒体通知）
```

- 屏幕均为无状态 Composable：接收 `ui/state` 中的不可变状态与回调，便于测试与截图。
- `AppNavigation.kt` 承载页面栈和播放器浮层的导航展示；播放器覆盖期间保留底层页面，支持下拉收起时露出原页面。
- 所有网络错误统一映射为 `MusicRepositoryException` 的中文短句，不向 UI 泄漏上游响应体、cookie 或带鉴权参数的 URL。

## 运行与验证

在仓库根目录执行（JDK 21、Android SDK platform 37；Compose 1.12 / Coil 3.6 的 AAR 元数据要求 `compileSdk = 37`，`targetSdk` 仍为 36）：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.nocturn.app/.MainActivity
```

也可在 Android Studio 中选择 `app` 运行配置。

- JVM / Robolectric 测试覆盖数据解析（legacy / cloudsearch、歌单分页、歌词、账号与二维码）、加密会话、播放映射、全部屏幕交互，以及返回手势取消 / 提交、页面状态恢复、播放器返回优先级与快速导航。
- `ScreenshotGalleryTest` 在宿主机渲染 15 张界面截图到 `app/build/screenshots/`（`home-light`、`search-results-light`、`player-lyrics-dark`、`login-qr-light` 等），这些是 Robolectric 渲染，不是设备截图；歌词截图等待异步文字准备完成后采集。
- 2026-11-01 歌词接入验证：`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 通过，166 项测试、Lint 0 错误（17 项现有警告）。包含 YRC 逐字时间 / 译文 / 音译保留、LRC 行边界、间奏、极端时间值、点句跳转、字幕开关、换曲重置、惯性滚动中手动恢复跟随。新 APK 已在连接的 API 37 手机安装并启动；自动 UI 检查无法取得稳定空闲状态，尚未完成真机逐字同步验证。
- 构建需要联网首次下载依赖；本地 Node 运行时需要 4 KB 页设备与 `arm64-v8a` / `x86_64` ABI。

## 当前边界

- 启动应用不会自动登录，也不会在后台静默刷新凭证；二维码登录后 cookie 以 AndroidKeyStore AES-GCM 加密存放在应用私有目录。
- 播放地址由服务返回，部分歌曲需要登录 / 会员；无可用音源时会给出明确提示。
- 已在无线 ADB 连接的 Redmi 设备（Android 17 / API 37、`arm64-v8a`、4 KB 页）上验证：内置 Node 运行时启动、首页真实数据、搜索、音源播放、锁屏 / 系统媒体通知与后台播放。二维码登录、账号数据全流程与 16 KB 页设备仍需单独验证。
- 媒体流地址会强制升级为 HTTPS（保持 LocalAPI 仅允许 loopback 明文的网络安全策略）；若个别 CDN 仅支持 HTTP，该曲目会播放失败。
- 歌词依赖固定发布版 [Accompanist Lyrics UI](https://github.com/6xingyv/accompanist-lyrics-ui) `2.0.0-rc.3`（候选版）与 Lyrics Core `0.5.0`，许可证为 Apache-2.0；构建通过 Maven Central 解析，不拉取可变分支。现有共享解析器保留 YRC 绝对毫秒逐字时间、行时长、译文与音译，UI 层转换为 Accompanist 模型；普通 LRC 使用整句高亮。
- 歌词采用 28 sp 主文 / Material 正文字号译文、视口 38% 阅读锚点、上下边缘渐隐与 540 ms 缓动滚动，配合阻尼弹簧的轻量逐行滞后。Android 12+ 在缓存的歌词行图层启用距离模糊（每行 2 px 增量），当前行清晰，手动浏览时移除焦点模糊；Android 10 / 11 保留透明度与缩放效果。使用 SrcOver 保持深浅色对比，仅保活视口外 24 dp 内容。手动拖动和惯性滚动结束后等待 3.5 秒恢复跟随，也可点“回到当前歌词”立即恢复。
- 播放器仍以 500 ms 采样；歌词场景保持单个时钟状态，通过 snapshotFlow 接收新采样，避免重建逐帧订阅。缓存歌词转换、排版与滚动参数，使用稳定内容键避免每次采样哈希整首歌词；无障碍文本只在换行时更新。采样与绘制统一使用 elapsedRealtimeNanos，补偿传递延迟并按实际播放速度推进；普通采样校正保持时间单调，避免逐字闪回，只有拖动预览及 Media3 明确的位置跳变（跳转 / 循环 / 换曲）允许回退。暂停、缓冲和进度预览时停止补间，最多向前推算 750 ms。无时间戳或纯音乐显示空态；译文 / 音译取决于上游数据。
- 2026-11-02 歌词调优：Dock 歌词开关、下方紧凑字幕开关、隐藏音质标、双主题双语排版、时钟身份 / 暂停 / 后退校正与渐进换行均有 Compose 回归。Debug 独立离线采样入口及设备采样限制见 [歌词验证说明](../docs/LYRICS_UI.md)。
