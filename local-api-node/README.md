# Nocturn LocalAPI — Node 层

独立实现的 loopback HTTP 服务与 Android 资源打包工具；未复制 SPlayer 的实现。通过精选模块使用 `@neteasecloudmusicapienhanced/api@4.36.2`，与用户提供的参考服务当前版本一致。运行时只需要 Node.js 18+；已在 **Node.js 18.20.4** 上实际执行测试与在线调用。

## 开发与构建

```powershell
cd local-api-node
npm.cmd ci
npm.cmd test
```

`test` 自动先构建 bundle。构建可单独运行：

```powershell
npm.cmd run build
```

生成 `dist/main.cjs`，并同步到 Android 资产目录 `../local-api-android/build/generated/nodeAssets/nocturn-local-api/`。输出包含 SHA-256 清单 `manifest.json`、依赖许可证 `THIRD_PARTY_NOTICES.txt` 与上游运行时数据文件。清单格式：

```json
{
  "version": "全部文件哈希的 SHA-256",
  "files": ["main.cjs", "THIRD_PARTY_NOTICES.txt", "api/data/…"],
  "sha256": { "main.cjs": "文件 SHA-256" }
}
```

构建只打包白名单端点和请求工具，不包含上游 Express 服务、完整动态模块加载器或解锁实现。上游库的 console 日志在打包时移除，防止响应中的 Cookie 等信息进入 Android 日志。未使用到的上游 CLI 依赖不进入 bundle。

## 启动协议

由 Android JNI 在调用 Node 之前提供环境变量：

| 变量 | 要求 |
| --- | --- |
| `NOCTURN_API_TOKEN` | 每次进程启动生成；32–256 位 URL-safe 字符，不含 `=` padding |
| `NOCTURN_API_PORT` | 可选，默认 `0`，由系统分配空闲端口 |
| `NOCTURN_DATA_DIR` | 必填，应用私有数据目录的绝对路径 |

服务始终只监听 `127.0.0.1`。上游缓存密钥和设备标识保存在私有数据目录中，`TMPDIR` 等在加载上游模块前设置。启动不依赖网络；只有歌曲 URL 等需要新密钥的请求会按需联网初始化。

监听成功后，先原子写入 `<NOCTURN_DATA_DIR>/runtime-ready.json`，然后写出 stdout 标记：

```text
NOCTURN_LOCAL_API_READY {"port":12345,"version":"0.1.0"}
```

就绪文件内容与标记中的 JSON 一致，不包含 token。Android 在启动前删除旧就绪文件，并在读到新端口后请求 `/health` 确认服务可用。初始化失败只写 `NOCTURN_LOCAL_API_FAILED STARTUP_FAILURE`，不打印任意依赖错误。Node 生命周期不能在同一进程内反复重启，具体由 Android 模块管理。

## HTTP 契约

- `GET /health`：无需鉴权，返回 `{"app":"Nocturn LocalAPI","version":"0.1.0","status":"ready"}`。
- `POST /api/<endpoint>`：必须发送 `Authorization: Bearer <token>` 和 `Content-Type: application/json`。
- 请求体是参数对象；所有端点可选接收 `cookie` 字符串。Cookie 不经 URL、响应头或日志传递；登录接口本身的响应体仍保留上游 Cookie 字段，交由客户端安全保存。
- 原生客户端不发送 `Origin`；携带 `Origin` 的浏览器请求会被拒绝。
- 不接受查询字符串、压缩请求体、传输配置覆盖或任意动态端点。
- 上游成功响应体原样返回，业务 code（例如二维码轮询 801/802/803、登录失效、播放 URL 为 null）需由客户端继续判断。HTTP 200 不等于歌曲一定可以播放。

核心请求示例：

```http
POST /api/search
Authorization: Bearer <per-run-token>
Content-Type: application/json

{"keywords":"Nocturne","limit":20,"offset":0}
```

| 端点 | 主要参数 |
| --- | --- |
| `login/qr/key` | `{}` |
| `login/qr/create` | `key`，可选布尔 `qrimg` |
| `login/qr/check` | `key` |
| `login/status`、`logout` | `{}` |
| `login/refresh`、`register/anonimous` | `{}`，刷新登录需要 Cookie，二维码会话不支持刷新 |
| `captcha/sent` | `phone`，可选 `ctcode` |
| `captcha/verify` | `phone`、`captcha`，可选 `ctcode` |
| `login/cellphone` | `phone`、`captcha`，可选 `countrycode`；仅开放短信方式 |
| `search`、`cloudsearch` | `keywords`，可选 `type`（含 1018）、`limit`、`offset` |
| `search/suggest` | `keywords`，可选 `type=mobile/web` |
| `search/default` | `{}` |
| `search/hot/detail` | `{}` |
| `song/detail` | `ids`：最多 200 个逗号分隔 ID，必须是字符串 |
| `song/url/v1` | `id` 为单个正 ID 或最多 200 个逗号分隔 ID，`level` 为音质 |
| `check/music` | `id`，可选 `br` |
| `song/music/detail` | `id` |
| `lyric`、`lyric/new` | `id` |
| `personalized` | 可选 `limit` |
| `recommend/resource`、`toplist`、`toplist/detail` | `{}` |
| `recommend/songs` | 可选布尔 `afresh` |
| `homepage/block/page` | 可选布尔 `refresh`、字符串 `cursor` |
| `banner` | 可选 `type=0/1/2/3`，Android 使用 1 |
| `playlist/detail` | `id`，可选 `s` |
| `playlist/track/all` | `id`，可选 `limit`、`offset` |
| `playlist/subscribe` | `id`、`t`（1 收藏／2 取消） |
| `user/detail`、`likelist` | `uid` |
| `user/account`、`user/subcount` | `{}` |
| `user/playlist`、`user/playlist/create`、`user/playlist/collect` | `uid`，可选 `limit`、`offset` |
| `record/recent/song` | 可选 `limit` |
| `like` | `id`，可选布尔 `like`，默认 true |
| `song/like` | `id`、`uid`、布尔 `like` |
| `album`、`artists`、`artist/detail` | `id` |
| `artist/album` | `id`，可选 `limit`、`offset` |
| `personal/fm` | `{}` |

精确参数白名单见 `src/endpoints.mjs`。分页 `limit` 为 1–200，`offset` 为 0–100000，正文上限 64 KiB。未知字段、`proxy`、`domain`、`crypto`、`headers`、`unblock` 等均拒绝。

音质统一为 `standard/higher/exhigh/lossless/hires/jyeffect/sky/dolby/jymaster`。非文档音质 `vivid` 已移出白名单。数字形式 ID 必须为安全整数；大 ID 与批量 ID 应传字符串。适配层只把 `like` 转成上游需要的字符串形式，避免 JSON `false` 被上游误当作喜欢；`afresh/refresh/qrimg` 保留布尔语义。

游客注册会轮换上游设备标识，适配层将新标识写回私有目录，并在后续需要时更新对应的 xeapi 密钥。所有登录及账号写操作均为显式调用，不会在服务启动时执行。

传输层错误统一为：

```json
{"error":{"code":"UPSTREAM_FAILURE","message":"Music service request failed."}}
```

常见 HTTP 状态：400 参数／JSON 错误，401 token 错误，403 浏览器来源，404 路由不存在，405 方法错误，413 正文过大，415 内容类型不支持，429 并发超限，502 上游连接失败，504 超时。每次上游网络请求限时 12 秒，本地请求含密钥初始化总限时 25 秒；最多 8 个并发 API 请求。

## 在线契约验证（手动启用）

```powershell
$env:NOCTURN_REFERENCE_API = 'https://ncmapi.rpixel.online'
npm.cmd run smoke
```

该脚本临时启动真实 bundle，调用 12 类公开接口，逐项检查本地与参考服务的响应结构。它不登录、不传 Cookie、不调用收藏等写操作。参考 HTTP 请求附加时间戳；仅只读请求的瞬时传输故障允许重试一次，业务或断言失败不会重试。没有设置参考服务时，只检查本地。网络冒烟测试不属于 `npm test`，避免离线构建受外部服务影响。

已验证：Node 18.20.4 的 22 项离线测试、12 类接口的本地／参考服务共 24 项在线契约检查全部通过；另外实际验证了游客会话与轮换后的设备标识／密钥一致性。批量 URL 为 null 或有效地址均符合契约，具体可用性由账号、地区和网易云服务决定。发送短信和真实账号登录／收藏只做隔离测试，不触发真实账号操作。
