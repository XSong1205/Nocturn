# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Nocturn 是一个第三方网易云音乐（NetEase Cloud Music）客户端。当前目录仍是空项目，本文件描述的是既定技术方案与应遵循的架构方向。

技术栈（用户指定）：
- **UI**: [Miuix](https://github.com/compose-miuix-ui/miuix) — MIUI/HyperOS 风格的 Compose Multiplatform UI 组件库（Kotlin）
- **后端**: [api-enhanced](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced) — 网易云音乐 Node.js API（Binaryify/NeteaseCloudMusicApi 的增强分支）

## 后端：api-enhanced

作为独立服务运行，客户端通过 HTTP 调用它获取所有网易云数据。

- **运行环境**: Node.js 22+，依赖管理用 pnpm
- **启动**: `node app.js`（默认端口 3000，可用 `PORT` 环境变量改）
- **鉴权**: cookie 机制——请求参数/body 中带 `cookie` 字段即视为已登录（`login_cellphone` 等登录接口会返回 `cookie`）
- **集成方式二选一**：
  1. 本地/自建服务（开发时最常用）
  2. 直接用 npm 包 `@neteasecloudmusicapienhanced/api` 在 Node 侧调用（如 `login_cellphone({ phone, password })` 返回 `res.body.cookie`）

关键环境变量（config 文件 / env）：`ENABLE_PROXY`、`ENABLE_FLAC`（无损音质）、`SELECT_MAX_BR`、`CORS_ALLOW_ORIGIN`（默认 `*`）。敏感 cookie 用环境变量配置，勿写死进代码或提交。

- 接口文档: <https://neteasecloudmusicapienhanced.js.org/>
- 测试: `pnpm test`

## 前端：Miuix（Compose Multiplatform）

- 依赖坐标（Maven Central）: `top.yukonga.miuix.kmp:miuix-ui:<version>`，可选模块 `miuix-preference`（设置页）、`miuix-icons`（图标）、`miuix-blur`（模糊）、`miuix-squircle`（平滑圆角）、`miuix-shader`（shader）。注意 `miuix-nav` 未发布到 Maven Central，不可用
- 官方文档（以此为准）: <https://compose-miuix-ui.github.io/miuix/zh_CN/guide/getting-started> —— 用全 Miuix 附属库，不要自造轮子：模糊玻璃用 `miuix-blur` 的 `textureBlur`/`rememberLayerBackdrop`，图标用 `MiuixIcons` 扩展属性（需同时 `import top.yukonga.miuix.kmp.icon.MiuixIcons` 与具体图标如 `top.yukonga.miuix.kmp.icon.extended.Forward`）
- 目标平台：Android / iOS / Desktop(JVM) / Web(JsCanvas / WasmJs) —— 首次搭建时需确认主推平台
- 主题：根组件包一层 `MiuixTheme(colors = ...)`，用 `lightColorScheme()` / `darkColorScheme()` 配合 `isSystemInDarkTheme()` 切换；`ThemeController(ColorSchemeMode.MonetSystem, keyColor = ...)` 可支持 Monet 动态取色
- 注意：库标注为 experimental，API 可能变动；多平台共享代码放 `commonMain`

## 架构约定（待落地）

客户端/服务端分离：Compose 客户端只做 UI 与状态，所有网易云数据走 HTTP 请求到 api-enhanced 服务。登录态 = 服务端返回的 cookie，需持久化并在后续请求中携带。

## 常用命令

项目尚未初始化。搭建时的预期命令：

```bash
# 初始化 Compose Multiplatform + Miuix 依赖
./gradlew build          # 构建
./gradlew :composeApp:run # 运行桌面端（模块名以实际为准）
```

api-enhanced 侧：

```bash
pnpm i
node app.js              # 启动 API 服务（端口 3000）
```

## 语言约定

项目目标用户为中文用户，UI 文案与注释用中文。
