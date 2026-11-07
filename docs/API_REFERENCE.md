# API Enhanced 文档查阅与 LocalAPI 对照

查阅与实现更新日期：2026-11-01。

来源：[部署服务文档](https://ncmapi.rpixel.online/docs/)；Docsify 页面实际加载 [home.md](https://ncmapi.rpixel.online/docs/home.md)。2026-11-01 19:21 重新抓取完整正文，发现相较上次快照新增 41 个正式接口声明，以及仅在示例中新增的 `/comment/add`，共 42 个新文档路径。完整目录、响应核对和证据见 [Docs 重抓记录](DOCS_REFRESH_2026-11-01.md)。本文保留现有实现对照。

参考服务此前返回 `4.36.2`，本次重抓时 `/inner/version` 已返回 `4.41.1`，与当前内置 npm 版本号相同。但新文档中的新版搜索、banner v3 等模块仍不在锁定 npm 包中，不能只按版本号推断模块集合。当前白名单共 63 个接口；上轮新增的 16 个接口重点覆盖歌单发现、歌单维护及收藏库，本轮新增首页用的 `/personalized/newsong`。

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

文档说明 `/login/refresh` **不支持刷新二维码登录的 Cookie**，因此宿主应用不能假定所有会话都能通过 refresh 续期。

游客接口为 `/register/anonimous`（上游路径使用此拼写），可获取游客 Cookie。手机号登录使用 `/captcha/sent`、`/captcha/verify`、`/login/cellphone`；`login/cellphone` 的 `captcha` 与 `password` 必须二选一，发送短信需要由用户操作触发。

已封装二维码三接口、登录状态和退出，以及游客登录、刷新、短信验证码发送／验证／登录、手机号密码登录、`/user/account`、`/user/subcount`。短信方式需要显式调用。Android 默认 HTTP 引擎禁用连接失败自动重试，避免重复提交登录、短信和收藏操作。

`pollQrLogin` 返回类型化扫码状态，只有 803 才接受 Cookie，toString 会脱敏。`restoreSession` 验证已有 Cookie，不重新登录或自动刷新；安全持久化凭证由宿主应用负责。

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

最新文档列出十档音质：`standard`、`higher`、`exhigh`、`lossless`、`hires`、`jyeffect`、`dolby`、`vivid`、`jymaster`、`sky`，其中 `vivid` 为臻音全景声，`jyeffect` 更名高清臻音。文档还新增仅对 `level=sky` 生效的 `immerseType=c512/ste2/aac2/c51/ste/aac`，默认 `c51`。Dolby 仍涉及设备能力和 Cookie 中的 `os=pc`；不能只按用户选择的最高音质判断实际可用性。

当前 Kotlin `AudioQuality` 与 Node 白名单仍为上次文档的九档，尚未开放 `vivid` 和 `immerseType`。锁定 4.41.1 的上游模块已经支持二者，因此这是本次重抓发现的现有契约缺口。已有离线测试覆盖当前枚举，在线批量 URL 测试使用 standard；尚不能将这些测试解读为已覆盖最新十档约定。

搜索类型白名单已加入综合搜索 `1018`。共享 `Song` 模型统一 `artists/album/duration` 与 `ar/al/dt` 等字段，`searchSongPage` 返回类型化分页数据。原有 `searchSongs` 保留旧 search 调用行为。

## 歌单、音乐库与推荐

- `/playlist/detail` 的 `tracks` 可能不完整，不能当成完整歌单。`playlistSongPage` 已使用完整 `trackIds` 选择一页，再请求详情并恢复歌曲顺序，返回下一页偏移量。
- 文档称 `/playlist/track/all` 默认取歌单歌曲数量，但固定 npm `4.41.1` 的模块实际默认 `limit=1000`。`playlistSongs` 现已显式传入 `ApiPage`，不依赖默认值。
- `/toplist` 获取榜单，榜单内容复用歌单详情；文档已废弃旧 `/top/list` 方案。当前实现方向一致。
- `/recommend/resource`、`/recommend/songs` 与私人 FM 需要登录；未登录的发现页应优先使用 `/personalized`、榜单等公开内容。本地白名单已把 `/recommend/resource` 的 `cookie` 标为必填；宿主在已登录时用它填充首页“为你推荐”歌单（账号相关的每日推荐歌单），仅当失败或结果为空时才回退到公开的 `/personalized`，回退请求同样携带 Cookie。
- 推荐新音乐 `/personalized/newsong` 已封装为 `personalizedNewSongs`：文档只列可选 `limit`（默认 10，不支持 `offset`），本地白名单同样只开放 `limit`（1–200）与可选 `cookie`，上游模块固定 `type=recommend`、`areaId=0`。未登录可用，携带 Cookie 时按账号偏好推荐。响应为 `result[]`，每项外层带 `picUrl` 封面，歌曲字段在嵌套的旧版 `song`（`artists/album/duration`）中。宿主应用以此填充首页“根据你喜爱的歌曲推荐”网格，结果为空或失败时回退到榜单热歌；Cookie 会随请求传入上游模块，Node 契约测试已覆盖。
- 首页“编辑精选”横幅已移除：Android banner 的跳转目标（网页/活动/MV）在当前宿主内不可达，接口仍保留在白名单中但不再被首页调用。
- `/recommend/songs` 已支持布尔 `afresh`；`dailySongs` 默认 false。
- `/like` 的 `like` 已改为可选并遵循默认 true；新版 `/song/like` 已支持 `id/uid/like`。适配层修复了上游把 JSON false 误当作喜欢的问题：只将 like 转换成上游比较所需的字符串，不影响其他布尔参数。
- `/user/playlist/create`、`/user/playlist/collect` 已封装；`userPlaylists` 可选择 All／Created／Collected，显式分页。
- 云端最近播放 `/record/recent/song` 已封装；它与宿主应用的本地播放历史是两个来源。
- 另外补齐 `/homepage/block/page`、Android banner、`/toplist/detail` 和 `/artist/detail`，供首页和详情页接入。
- 原始私人 FM 路径为 `/personal_fm`；Nocturn 自定义本地路径为 `/api/personal/fm`。本地鉴权封装具有自己的路径契约，不能直接替换 base URL 连接远程 API Enhanced。

### 上轮补齐

| 能力 | 新增接口与约定 |
| --- | --- |
| 分类歌单 | `/playlist/catlist`、`/playlist/hot`、`/top/playlist`；分类 `cat`、`hot/new` 排序和 `limit/offset` 分页 |
| 精品歌单 | `/playlist/highquality/tags`、`/top/playlist/highquality`；下一页传前页最后一个歌单的 `updateTime` 为 `before`，不接受 `offset` |
| 创建与删除 | `/playlist/create` 支持 `privacy=0/10` 和 `NORMAL/VIDEO/SHARED`；`/playlist/delete` 接受 1–200 个逗号分隔 ID |
| 歌单歌曲维护 | `/playlist/tracks` 使用 `pid`、`op=add/del`、`tracks`（1–200 个歌曲 ID）；保留大 ID 字符串 |
| 歌单元数据 | `/playlist/name/update`、`/playlist/desc/update`、`/playlist/tags/update`；名称 1–40 字符，简介最多 1000 字符，标签最多 3 个、每个最多 40 字符，以分号分隔；空简介／空标签用于清除 |
| 专辑与歌手收藏 | `/album/sub`、`/artist/sub` 统一 `t=1/2` 为收藏／取消；`/album/sublist`、`/artist/sublist` 显式 `limit/offset` 分页 |
| 歌手全部歌曲 | `/artist/songs` 支持 `hot/time` 排序及 `limit/offset`；不受 `/artists` 热门歌曲预览数量限制 |

新增写接口与收藏列表要求非空账号 Cookie，所有调用均显式触发。读取分类、精品歌单和歌手歌曲可以不传 Cookie。Kotlin 操作返回原始 `JsonObject`，保留上游业务 `code`，调用方仍需判断是否成功。

固定版上游 `playlist_tracks.js` 会嵌套首次成功响应，并在业务码 512 时重复提交歌曲。本地独立适配器只提交一次，直接返回标准响应信封；512 作为业务结果保留，不自动重试。网络错误继续走已有脱敏错误处理。文档中的 `/playlist/track/add` 和 `/playlist/track/delete` 用于视频歌单，不能替代音乐歌单增删接口。

上游组合 `/playlist/update` 直接将文本插值到 JSON 字符串，本地只开放独立元数据接口，名称或简介含引号、换行时仍按对象值传递。

## 歌词

- `/lyric` 获取普通歌词。
- `/lyric/new` 的逐字内容位于 `yrc.lyric`，不是普通 LRC。
- YRC 行示例：`[16210,3460](16210,670,0)还(16880,410,0)没...`，分别包含行开始时间、行时长、各字开始时间与时长，单位为毫秒。
- 内容前可能夹有 JSON 元数据行；解析器需要区分元数据与歌词，不应直接按 LRC 正则处理全文。
- 歌曲可能没有逐字歌词，播放页应回退到普通歌词，并处理翻译、罗马音、纯音乐及无歌词状态。

共享 `LyricParser` 已实现普通 LRC、YRC 的绝对毫秒逐字时间、多时间戳、全局 offset、JSON 元数据跳过、翻译与罗马音对齐，并在无有效 YRC 时回退到 LRC。`songLyrics` 返回类型化歌词。

## 验证与范围

- 本次 Docs 重抓：正文哈希二次核对一致，逐项对比 42 个新文档路径、11 个变化章节及安装模块，完成 9 项只读线上观察；详细结果见重抓记录。
- 上轮功能扩展验证：Node 24.19.0 的 35 项离线测试、Kotlin 的 40 项共享客户端测试通过，覆盖分页方式、参数白名单、文本清除、收藏操作、批量大 ID、512 单次提交及 Kotlin／Node 路由一致性。
- 上轮公开 smoke 的 18 类接口、19 个检查场景（精品歌单额外检查下一页游标）共 38 项本地／参考检查通过；当时参考服务为 4.36.2。分类歌单 `order=new` 可返回合法空页，按列表、总数及 `more` 核对。
- 上轮 `:local-api:assemble`、`:app:compileDebugKotlin`、`:local-api-android:lintDebug` 通过；新增接口未做设备验证。
- 官方 Node.js Mobile 18.20.4 运行库仅支持 4 KB 页设备；已有设备验证记录见 `NODE_RUNTIME.md`，本轮 LocalAPI 扩展的设备状态单独记录。
- 短信、真实账号登录、收藏与凭证恢复目前仅做隔离测试，真实账号集成验证需由宿主应用完成。

下载管理和其余非首版接口仍不在当前实现范围内。
