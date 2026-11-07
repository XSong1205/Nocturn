# Docs 重抓与新增接口核对

2026-11-01 19:21（Asia/Shanghai）重新抓取 [线上 Docs](https://ncmapi.rpixel.online/docs/)，并于 19:26 再次核对入口、正文哈希与服务版本。Docsify 配置仍为 `homepage: 'home.md'`；正文没有链接到其他 Markdown 页面，`_sidebar.md` 与 `_navbar.md` 均返回 404，封面也已检查。

相较上次保存的正文，新增 **41 个正式接口声明**，另有仅在调用示例中新增的 **`/comment/add`**，共 **42 个新文档路径**；没有删除原有声明。11 个已有接口章节发生变化，其中 9 个涉及内容、2 个只是格式。这里的“新增”指相对旧文档新出现，不代表这些能力都在 npm 4.41.1 才首次实现。

参考服务 `/inner/version` 已由此前观察到的 `4.36.2` 升至 **`4.41.1`**。本地依赖也是 `4.41.1`，但模块集合并不相同：新文档路径中有 34 个可找到同名 npm 模块，8 个没有。重抓时 LocalAPI 为 62 个接口；之后为首页“根据你喜爱的歌曲推荐”开放了旧文档已有的 `/personalized/newsong`（推荐新音乐），现为 63 个。下面 42 个新路径均尚未开放。

## 抓取与计数依据

| 项目 | 旧快照 | 新快照 |
| --- | --- | --- |
| 正文字节数（UTF-8） | 146845 | 165861 |
| 显式“接口地址”唯一声明 | 376 | 417 |
| 声明和调用示例中的唯一字面路径 | 379 | 421 |
| SHA-256 | `6e00b1ab1bcc04a5fa3cba4b530e993cdb9cf9cb2543992bc01a2142c1b407aa` | `c85d24646a6c21bf4e755f45df43f079a4c30b0b94114b2b61f1cdcd7560e02a` |

计数处理了跨行的 `/msg/private/history` 声明，以及只有示例的 `/login/refresh`、`/logout`。`/album_songsaleboard` 声明与 `/album/songsaleboard` 示例存在旧有拼写差异，字面路径统计保留两者；合并它们后为 378→420 项能力。`/personal_fm` 对应本地既有别名 `personal/fm`，当前 63 个本地接口均有文档依据（`/personalized/newsong` 见“推荐新音乐”章节，只有可选 `limit`，不支持 `offset`）。

抓取证据保存在 `build/reference/recrawl-2026-11-01/`：`index.html`、`_coverpage.md`、`previous-home.md`、`home.md`、`fetch.json`、`inventory.json` 和 `live-checks.json`。快照与分析中间文件位于已忽略的 build 目录。正文在带时间戳和不带时间戳的两次请求中哈希一致；响应的 Last-Modified 固定为 2018 年，不能用它判断文档是否更新。

## 新增接口完整清单

下表“模块有”仅表示当前锁定依赖存在对应文件，不代表已由 LocalAPI 开放或完成账号验证。参数摘自新文档，已发现的不一致在后文单独说明。

### 搜索、首页与评论展示（7 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/search/suggest/mobile` | App 搜索联想；`keyword`；可含歌曲卡、联想词、高亮信息 | 缺失 |
| `/search/song/list/page` | 新版单曲搜索；`keyword`、`limit/offset`、`scene=normal`；返回 `resources` 与 `hasMore` | 缺失 |
| `/search/lyric` | 按歌词搜索；`keyword`、`limit/offset`；匹配歌词及歌曲详情 | 缺失 |
| `/event/search` | 动态搜索；`keyword`、`limit/offset` | 缺失 |
| `/comment/default/text` | 评论输入框默认文案；`threadId` | 缺失 |
| `/comment/hotwall/square/info` | 当日热评墙广场配置；无业务参数 | 缺失 |
| `/banner/v3` | v3 首页轮播；`clientType=android` | 缺失 |

### 音乐、推荐与喜欢（7 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/recommend/songs/v1` | 新版每日推荐；`ispush` 布尔刷新标志，默认 false；需要登录 | 有 |
| `/like/v1` | 新版喜欢／取消喜欢；`id`，可选 `like`，默认 true | 有 |
| `/song/wiki/info` | 音乐百科；`id` | 有 |
| `/song/simi/get` | 插播相似歌曲；`id` | 有 |
| `/recommend/category/songs` | 每日指定风格歌曲推荐 | 有 |
| `/recommend/category/configs` | 获取可选风格及分类 | 有 |
| `/recommend/category/config` | 设置风格；文档正文写 `tags/category`，示例写 `categoryId` | 同名模块缺失；实际模块及路由为 `recommend_category_set`、`/recommend/category/set` |

### 登录、设备与会话（5 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/captcha/sent/v1` | 新版发送验证码；`phone`，可选 `ctcode=86` | 有 |
| `/device/list` | 获取当前登录账号的在线设备 | 有 |
| `/captcha/safe/sent` | 发送设备安全验证码；`phone` | 有 |
| `/device/kickoff` | 强制下线设备；`key=deviceKey`、`captcha` | 有 |
| `/deviceinfo/center/upload` | 修改当前设备显示名；`deviceName`，兼容别名 `name` | 有 |

### 动态枚举与权限（2 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/user/event/all` | 从当前账号自动枚举全部可取得动态；不接受 `limit/lasttime`；返回 `retrievedCount/unavailableCount/sizeMismatch/pageCount` | 有 |
| `/event/privacy` | 修改本人单条动态权限；`evId`、`privacy=0/1/2/6` | 有 |

### 乐迷团（4 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/fans/group/user/groups` | 获取当前账号加入的乐迷团 | 有 |
| `/fans/group/detail` | 乐迷团详情；`groupId`，可选 `scene` | 有 |
| `/fans/group/user/group/detail` | 当前用户的乐迷团成员详情；`groupId` | 有 |
| `/fans/group/feed/recommend` | 乐迷团推荐笔记；`fansGroupId`，可选 `cursor=0/size=10` | 有 |

### 云贝与广告（6 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/yunbei/task/list/v1` | 云贝广告任务当日状态；返回次数和金额字段 | 有 |
| `/yunbei/task/recommend/song` | 云贝任务推荐歌曲；`limit=10/offset=0` | 有 |
| `/yunbei/task/finish/v1` | 完成任务领取云贝；`yunbeiAmount` | 有 |
| `/ad/get` | 获取广告 | 有 |
| `/ad/listening/rights/gain` | 领取广告权益；`reqUid/uid/rightsGainMethod/type_ids/creativeType` | 有 |
| `/ad/listening/rights` | 查询免费听歌剩余时长 | 有 |

### 云小编与会员活动（10 个）

| 文档路径 | 用途与关键约定 | npm 4.41.1 模块 |
| --- | --- | --- |
| `/rep/ugc/user/get` | 云小编用户详情 | 有 |
| `/rep/ugc/user/sign` | 云小编签到 | 有 |
| `/rep/ugc/user/vip` | 查询会员任务状态 | 有 |
| `/rep/ugc/activity/get` | 查询活动信息 | 有 |
| `/thinktank/audit/resource/detail` | 获取审核任务；`type=1/2/3/4`，默认 4 | 有 |
| `/thinktank/audit/resource/update` | 提交审核；`taskId/judgement`，可选 `type` | 有 |
| `/rep/ugc/activity/collect` | 领取任务积分；可选 `activityId` | 有 |
| `/rep/ugc/user/collect-vip` | 领取一日会员；可选 `activityId` | 有 |
| `/middle/play/lottery/remain/chance` | 查询剩余抽奖次数；可选 `activityId` | 有 |
| `/middle/play/do/lottery` | 抽奖；`activityId/drawCount/checkToken` | 有 |

### 仅在新增示例中出现的路径（1 个）

`/comment/add`：新增重复的“发送/删除评论”章节用它发送／回复评论；参数为 `type/id/content`，回复时增加 `cid`。npm 存在 `comment_add.js`。该章节没有独立“接口地址”声明，且删除示例仍使用 `/comment`，与前面旧章节的 `t/commentId` 约定不一致，不能直接据此替换既有评论契约。

## 已有接口及通用说明的变化

| 路径或章节 | 变化 | 当前 LocalAPI 影响 |
| --- | --- | --- |
| `/song/url/v1` | 新增 `vivid=臻音全景声`，`jyeffect` 更名“高清臻音”；新增 `immerseType=c512/ste2/aac2/c51/ste/aac`，只对 `level=sky` 生效，默认 `c51` | 当前九档枚举及参数白名单未开放 `vivid/immerseType`；安装的 4.41.1 模块已支持 |
| `/login/cellphone` | 新增 `sca` 滑块验证 Token；验证码来源增加 `/captcha/sent/v1` | 当前未暴露 `sca`，上游源码将其映射为 `secureCaptcha`；旧短信接口仍可保留 |
| `/playlist/subscribe` | 删除“必须传 timestamp”的专属警告 | 通用 HTTP 缓存／POST 时间戳说明仍保留；本地直接调用模块的协议无需照搬 HTTP 缓存参数 |
| `/song/music/detail` | 更新音质名称，并明确杜比、臻音全景声等详情 | 原始 JSON 返回方式兼容；文档中的九档描述需要更正 |
| `/user/event` | 强调 `fromRN=true`、本人可见的非公开动态，以及账户统计 `size` 不等于可枚举数量 | 本地未开放；将来应保留数量不一致语义 |
| `/song/url/v1/302` | 同步音质列表中的 `vivid` 与名称变化 | 本地未开放 |
| `/song/download/url/v1` | 同步音质列表中的 `vivid` 与名称变化 | 本地未开放 |
| `/voice/upload` | 明确 multipart `songFile` 为必选，支持 `imgFile`，并优先于 `coverImgId` | 本地未开放；现有 JSON 协议不能直接承担文件上传 |
| `/personal/fm/mode` | 模式与场景列表扩展，包含 `PUZZLE_MODE_RCMD`，不再列 `aidj` | 本地只开放基础私人 FM |
| `/comment/report` | 示例 Markdown 格式修正 | 无业务变化 |
| `/song/cloud/download` | 示例末尾增加一个孤立单引号 | 无业务变化，属于文档格式问题 |
| 调试说明 | 新增 `neapi` 压缩加密通道，并提到 `/register/neapikey` 自动获取／刷新配置 | 该路径是通用说明中的引用，未计入 41 个新增正式声明；不应向客户端开放任意加密参数覆盖 |

二维码登录三接口、歌单分页、精品歌单 `before`、歌手歌曲分页等原有约定未变。通用“调用前须知”章节也未变化。

## 线上只读核对与真实响应差异

共进行了 9 项不带账号 Cookie 的只读观察，7 项返回 HTTP 200，2 项风格推荐接口返回 HTTP／业务码 301（需登录）。没有执行验证码发送、喜欢、领取、抽奖、设备下线或任何配置修改。完整请求参数及结构记录见本地 `live-checks.json`；早期观察只有日期和记录时间，未保存精确请求时间，记录中已明确标注。

| 路径 | 观察结果与接入注意 |
| --- | --- |
| `/search/song/list/page` | `data.resources[]`、`totalCount`、`hasMore`、`hlWords`；歌曲在 `baseInfo.simpleSongData`，资源 ID 是字符串，不能套用当前 `result.songs` 解析器 |
| `/search/suggest/mobile` | `data.suggests[]`；首项实测可以是普通联想词，并非文档所说必有歌曲卡；`highLightInfo` 是 JSON 字符串，需按实际类型解析 |
| `/search/lyric` | `data.resources[].song/lyric/matchedLyric/highlightText`；分页字段实测为 `more`，不是新版单曲搜索的 `hasMore` |
| `/banner/v3` | 顶层 `banners[]`，业务码 200 |
| `/song/simi/get` | 返回 `data.commonResourceList[]` 与 `extraMap`，并非完整 `songs[]`；资源 ID 为字符串 |
| `/recommend/category/songs`、`/recommend/category/configs` | 未登录均返回 HTTP 301／业务码 301；不能把它们当作公开发现页的匿名数据源 |
| `/song/creators` | 旧文档已存在；返回 `data.songCreatorsRoleVos[].creatorMetaVOS[]`，本次观察未完整记录业务码 |
| `/song/copyright/rcmd` | 旧文档已存在；`data.rcmd` 实测为单个替代歌曲对象，不是数组 |

新版单曲搜索、移动端建议、歌词搜索与 banner 的模块在上游 GitHub `main` 可以取得，但锁定 npm 4.41.1 中不存在。已检查 [单曲搜索源码](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/search_song_list_page.js)、[移动端建议源码](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/search_suggest_mobile.js)、[歌词搜索源码](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/search_lyric.js)、[banner 源码](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/banner_v3.js)。`main` 是可变引用，后续接入需锁定发布版或具体提交，不能仅依据线上自报版本。

风格设置还有独立契约问题：文档路径为 `/recommend/category/config`，npm 实际文件为 `recommend_category_set.js`，自动注册为 `/recommend/category/set`；源码读取 `query.category`，不是示例的 `categoryId`，且没有对应别名。此设置接口未被调用。风格推荐模块使用 xeapi，将来接入时也需扩展现有按需密钥初始化，并校验类别与标签归属及安全整数。

## 对后续 LocalAPI 的优先级建议

1. 先对齐已支持播放接口的 `vivid/immerseType`，并核对手机登录 `sca` 的明确传入方式；这是已有契约的新缺口。
2. 接入新版单曲搜索、歌词搜索、移动端建议和 banner v3 前，先解决发布包缺失模块与版本锁定，再增加各自的数据归一化和分页测试。
3. 当前依赖中可以优先考虑 `/recommend/songs/v1`、`/like/v1`、`/song/wiki/info`、`/song/simi/get`；风格推荐依赖账号且存在路径和参数文档错误，需要单独适配。

本次交付为文档重抓、完整增量目录与契约核对。
