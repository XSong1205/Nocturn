# API Enhanced 文档查阅与 LocalAPI 对照

查阅与实现更新日期：2026-10-30。

来源：[部署服务文档](https://ncmapi.rpixel.online/docs/)；Docsify 页面实际加载 [home.md](https://ncmapi.rpixel.online/docs/home.md)。已查看完整接口目录，重点阅读通用调用约定、登录、搜索、歌曲、歌单、歌词、推荐、榜单和音乐库章节。本文是摘记与实现对照，不是完整文档副本。

参考服务通过 `/inner/version` 返回 `4.36.2`，当前内置 npm 依赖同样固定为 `4.36.2`。线上文档可能更新或存在缓存；不能仅根据文档页面就认为所有描述已在固定版本中验证。当前已补齐首版所需的 46 个接口，以下记录实际实现和边界。

## 通用调用规则

- 上游 HTTP 服务同时支持 GET/POST；Cookie 可放在 POST JSON 的 `cookie` 字段中。原生客户端不依赖浏览器 Cookie 自动携带。
- 文档明确提示上游 HTTP 服务默认有两分钟缓存，POST 与二维码登录请求需通过 URL 时间戳区分请求；`301` 通常与未登录、Cookie 未携带或缓存有关。
- 当前内置服务直接调用 npm 模块，未加载上游 Express 和缓存中间件，因此不需要照搬 HTTP 缓存规则。将来如实现远程服务客户端，必须单独处理 `timestamp`。
- 文档的 `noCookie=true` 用于控制上游 HTTP 服务是否在响应头设置 Cookie；已在固定版本 `server.js` 中核对。当前内置服务不设置 Cookie 响应头，登录 Cookie 保留在响应体。
- `code` 与 HTTP 状态需分别处理；二维码业务状态、需登录、试听限制和 `url: null` 不能笼统变为网络错误。
- 分页规则因接口而异：常见 `limit/offset`、`more/hasMore`；首页发现使用 `cursor`。推荐歌单 `/personalized` 不支持 `offset`。
- 封面可使用 `?param=宽y高` 请求合适尺寸，避免列表加载原图。

## 登录与账号

二维码流程：

1. `/login/qr/key` 获取 key。
2. `/login/qr/create` 通过 key 取得二维码内容；`qrimg` 可请求 base64 图片，也可以客户端自行渲染。
3. `/login/qr/check` 轮询状态：`800` 过期、`801` 等待扫码、`802` 等待确认、`803` 授权成功并返回 Cookie。
4. 保存凭证后通过 `/login/status` 检查状态，获取用户信息；恢复现有会话，避免反复登录。

文档说明 `/login/refresh` **不支持刷新二维码登录的 Cookie**，因此后续账号层不能假定所有会话都能通过 refresh 续期。

游客接口为 `/register/anonimous`（上游路径使用此拼写），可获取游客 Cookie。短信登录涉及 `/captcha/sent`、`/captcha/verify`、`/login/cellphone`；发送短信需要由用户操作触发。

已封装二维码三接口、登录状态和退出，以及游客登录、刷新、短信验证码发送／验证／登录、`/user/account`、`/user/subcount`。短信方式需要显式调用，不接受密码参数。Android 默认 HTTP 引擎禁用连接失败自动重试，避免重复提交登录、短信和收藏操作。

`pollQrLogin` 返回类型化扫码状态，只有 803 才接受 Cookie，toString 会脱敏。`restoreSession` 验证已有 Cookie，不重新登录或自动刷新；安全持久化凭证留给后续账号层。

## 找歌、歌曲与播放地址

| 文档接口 | 关键约定 | 当前 Nocturn 情况 |
| --- | --- | --- |
| `/search` | `keywords/type/limit/offset`；含单曲、专辑、歌手、歌单等 | 已封装；便利方法默认单曲 |
| `/cloudsearch` | 文档称结果更全 | 已封装；新 `search` 默认使用它 |
| `/search/suggest` | `type=mobile` 为移动端建议 | 已封装；便利方法默认 mobile |
| `/search/default` | 默认关键词 | 已封装 |
| `/search/hot/detail` | 详细热搜 | 已封装 |
| `/song/detail` | `ids` 支持逗号分隔多个 ID | 已封装，客户端每批最多 200 个 |
| `/song/url/v1` | `id`、`level`；支持多个 ID | 已支持 1–200 个 ID；Kotlin 使用字符串传输 ID，避免 JS 精度损失 |
| `/check/music` | `id` 与可选码率 `br`，返回可用性结果 | 已封装 |
| `/song/music/detail` | 各种音质的文件参数 | 已封装 |
| `/song/download/url/v1` | 下载链接，与试听链接接口不同 | 尚未封装，下载功能留后续阶段 |

文档列出的音质：`standard`、`higher`、`exhigh`、`lossless`、`hires`、`jyeffect`、`sky`、`dolby`、`jymaster`。文档特别指出 Dolby 涉及设备能力，且 Cookie 中 `os=pc` 会影响返回结果；不能仅按用户选择的最高音质判断实际可用性。

Kotlin `AudioQuality` 与 Node 参数白名单现已统一为上述九档；非文档值 `vivid` 不再接受。各音质的真实可用性仍依赖设备与账号，离线测试覆盖全部枚举，在线批量 URL 测试使用 standard。

搜索类型白名单已加入综合搜索 `1018`。共享 `Song` 模型统一 `artists/album/duration` 与 `ar/al/dt` 等字段，`searchSongPage` 返回类型化分页数据。原有 `searchSongs` 保留旧 search 调用行为。

## 歌单、音乐库与推荐

- `/playlist/detail` 的 `tracks` 可能不完整，不能当成完整歌单。`playlistSongPage` 已使用完整 `trackIds` 选择一页，再请求详情并恢复歌曲顺序，返回下一页偏移量。
- 文档称 `/playlist/track/all` 默认取歌单歌曲数量，但固定 npm `4.36.2` 的模块实际默认 `limit=1000`。`playlistSongs` 现已显式传入 `ApiPage`，不依赖默认值。
- `/toplist` 获取榜单，榜单内容复用歌单详情；文档已废弃旧 `/top/list` 方案。当前实现方向一致。
- `/recommend/resource`、`/recommend/songs` 与私人 FM 需要登录；未登录的发现页应优先使用 `/personalized`、榜单等公开内容。
- `/recommend/songs` 已支持布尔 `afresh`；`dailySongs` 默认 false。
- `/like` 的 `like` 已改为可选并遵循默认 true；新版 `/song/like` 已支持 `id/uid/like`。适配层修复了上游把 JSON false 误当作喜欢的问题：只将 like 转换成上游比较所需的字符串，不影响其他布尔参数。
- `/user/playlist/create`、`/user/playlist/collect` 已封装；`userPlaylists` 可选择 All／Created／Collected，显式分页。
- 云端最近播放 `/record/recent/song` 已封装；它与未来本地播放历史是两个来源。
- 另外补齐 `/homepage/block/page`、Android banner、`/toplist/detail` 和 `/artist/detail`，供首页和详情页接入。
- 原始私人 FM 路径为 `/personal_fm`；Nocturn 自定义本地路径为 `/api/personal/fm`。本地鉴权封装具有自己的路径契约，不能直接替换 base URL 连接远程 API Enhanced。

## 歌词

- `/lyric` 获取普通歌词。
- `/lyric/new` 的逐字内容位于 `yrc.lyric`，不是普通 LRC。
- YRC 行示例：`[16210,3460](16210,670,0)还(16880,410,0)没...`，分别包含行开始时间、行时长、各字开始时间与时长，单位为毫秒。
- 内容前可能夹有 JSON 元数据行；解析器需要区分元数据与歌词，不应直接按 LRC 正则处理全文。
- 歌曲可能没有逐字歌词，播放页应回退到普通歌词，并处理翻译、罗马音、纯音乐及无歌词状态。

共享 `LyricParser` 已实现普通 LRC、YRC 的绝对毫秒逐字时间、多时间戳、全局 offset、JSON 元数据跳过、翻译与罗马音对齐，并在无有效 YRC 时回退到 LRC。`songLyrics` 返回类型化歌词。尚未创建播放界面。

## 验证与下一阶段

- Node 18.20.4：22 项测试通过，包含上游 like 布尔转换和 Kotlin／Node 路由一致性。
- Kotlin：31 项测试通过，覆盖共享数据模型、分页、登录状态和歌词。
- 12 类公开接口在本地与参考服务的 24 项在线契约检查通过；实际游客会话及轮换后设备标识／密钥持久化一致性也已验证。
- Android 库、测试 APK 和 Lint 检查通过；仍无连接设备，JNI 启动设备测试尚未执行。现用官方 Node 库的 4 KB 页限制仍然存在。
- 短信、真实账号登录、收藏与凭证恢复目前仅做隔离测试，后续结合账号界面进行真实账号集成验证。

接下来可以在这些接口和模型上实现 Compose Multiplatform + miuix 页面、播放服务及账号凭证安全存储。下载管理、密码登录和其余非首版接口仍不在当前实现范围内。
