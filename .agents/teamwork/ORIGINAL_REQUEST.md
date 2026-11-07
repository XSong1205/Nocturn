# Original User Request

## Initial Request — 2026-11-07T13:08:04Z

根据词幕开发者文档（https://tomakino.github.io/lyricon/zh-cn/developer/），为 Nocturn Android 客户端适配生产就绪的词幕歌词 Provider。

Integrity mode: development

## Requirements

### R1. 集成词幕 Provider SDK 与 Manifest 元数据配置
在 `app` 模块集成词幕 Provider 依赖（`io.github.proify.lyricon:provider:0.1.70`），并在 `AndroidManifest.xml` 的 `<application>` 节点下配置词幕识别必需的 `lyricon_module`、`lyricon_module_author`、`lyricon_module_description` 元数据及 `lyricon_module_tags`（声明支持 `$syllable` 逐字歌词与 `$translation` 翻译歌词）。

### R2. 词幕 Provider 生命周期与播放状态同步
通过 `LyriconFactory.createProvider` 创建并管理 Provider 实例生命周期。在播放过程中，向词幕同步当前歌曲信息（ID、标题、艺术家、时长）、播放/暂停状态以及实时播放进度与位置跳转（`seekTo`），并在播放停止或销毁时正确注销并释放资源。

### R3. 多层级歌词数据转换与推送
将 Nocturn 解析的歌词数据结构转换为词幕 `RichLyricLine` 与 `LyricWord` 模型：
- 支持逐行歌词时间戳与文本。
- 当歌曲具备逐字/音节时间轴（YRC）时，提供对应的 `LyricWord` 逐字切片时间数据。
- 支持附带翻译歌词（`translation`），并根据设置同步翻译显示状态（`setDisplayTranslation`）。
- 对于纯音乐或无歌词场景，支持纯文本或占位展示。

### R4. 应用设置开关
在 Nocturn 应用设置（Settings）中增加独立的词幕歌词 Provider 启用/禁用开关及状态持久化。关闭状态下不注册 Provider 或暂停推送数据。

### R5. 单元测试与验证覆盖
编写单元测试或 Robolectric 测试，验证 Provider 的注册与注销生命周期、播放状态同步逻辑、Settings 开关控制、以及 Nocturn 歌词数据模型到词幕 Provider 歌词模型的转换正确性。

## Acceptance Criteria

### 编译与静态检查
- [ ] 执行 `./gradlew.bat :app:assembleDebug :app:lintDebug` 成功无报错

### 功能与测试验证
- [ ] 执行 `./gradlew.bat :app:testDebugUnitTest` 全部测试通过，且新增的 Provider 生命周期、状态同步、设置开关及歌词转换测试用例执行通过
- [ ] `AndroidManifest.xml` 正确声明 `lyricon_module` 为 `true`，并包含作者、描述及 tags 元数据声明
- [ ] 当播放器切歌、播放/暂停、seek 或更新进度时，词幕 Provider 同步接收到对应的歌曲元数据、播放状态及时间戳
- [ ] 具备逐字歌词或翻译歌词的曲目能够正确转换并向词幕推送 `LyricWord` 列表与翻译文本
- [ ] 在设置中关闭词幕歌词 Provider 开关后，Provider 正确注销或停止向词幕推送状态
