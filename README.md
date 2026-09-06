# Nocturn (夜曲) 🎵

> 专为 Android 打造的高颜值、现代感网易云音乐第三方客户端。  
> 采用 **Jetpack Compose** 与 **MIUIX (HyperOS 风格)** 深度打造，支持 YRC 逐字逐句歌词、原生内嵌 API 与增强型自建 API 双引擎、VIP 完整音源播放及现代系统通知中心联动。

---

## ✨ 核心特性

- 🎨 **HyperOS 美学设计**：采用 MIUIX 组件库打造连续平滑超椭圆（Squircle）卡片、优雅平滑模糊（Blur）与自适应深色/浅色主题。
- 🎤 **YRC 逐字逐句歌词**：支持网易云新版逐字动效歌词（YRC）、双语翻译及罗马音注音，提供 Apple Music 式智能自动回弹与点击歌词跳转播放。
- 🚀 **双引擎架构 (SPlayer 规范)**：
  - **内嵌原生 API (默认推荐)**：采用纯 Kotlin 实现的网易云原生加密算法（EAPI / WEAPI），直连官方服务，无需部署任何外部 Node.js 镜像。
  - **自建增强 API**：无缝兼容标准 NeteaseCloudMusicApi 实例，支持在线测速、自定义服务器与 Cookie 透传。
- 💎 **VIP 会员音源与高保真音质**：
  - 完整鉴权解析会员曲目，告别 30 秒试听限制。
  - 支持多档位音质自由切换：标准 (128k)、极高 (320k)、无损 FLAC (990k)、Hi-Res (24bit / 192kHz)。
- 🔄 **登录态与云端收藏双向同步**：
  - 支持 **短信验证码登录**、**APP 扫码授权** 以及 **Cookie 粘贴登录**。
  - 自动拦截同步 `MUSIC_U` 与 `__csrf` 会话凭据。
  - 登录后自动拉取云端「我喜欢的音乐」与用户歌单，支持播放界面一键点亮红心双向同步。
- 📱 **现代媒体通知与控制中心**：
  - 前台音频服务保活，集成 `MediaSessionCompat` 与 `NotificationCompat.MediaStyle`。
  - 支持系统媒体卡片大图封面、滑动进度条寻道、上一曲/下一曲/播放暂停及快捷红心收藏。

---

## 🛠️ 技术栈

- **语言 / 环境**：Kotlin 2.4 / JDK 21 (Target JVM 17) / Android Compile SDK 36 (Min SDK 26)
- **UI 框架**：Jetpack Compose Multiplatform + [MIUIX KMP](https://github.com/miuix-kmp/miuix)
- **歌词渲染**：`com.mocharealm.accompanist:lyrics-ui`
- **网络与并发**：OkHttp 5 + Kotlin Coroutines + Kotlinx Serialization
- **构建管理**：Gradle 9.5 Version Catalog (`libs.versions.toml`)

---

## 🚀 编译与运行

### 1. 前置准备
确保安装了 Android SDK，并在项目根目录创建 `local.properties`：
```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```

### 2. 编译打包 Debug APK
```bash
# Windows PowerShell
& "./gradlew.bat" :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```
产物输出路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 📖 开发与规范手册

关于架构细节、API 鉴权规范、避坑指南及提交规则，请参阅：
👉 [GEMINI.md](GEMINI.md)

---

## 📄 开源许可与致谢

- 灵感与 API 架构设计参考：[SPlayer-for-Android](https://github.com/SPlayer-Dev/SPlayer-for-Android)
- 设计语言与组件：[MIUIX KMP](https://github.com/miuix-kmp/miuix)
- 本项目仅供学习、交流与开源技术研究使用，音乐版权均归网易云音乐所有。
