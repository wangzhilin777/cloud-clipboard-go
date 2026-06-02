# Cloudflare 部署文档

本文档说明如何将 Cloud Clipboard 部署到 Cloudflare Workers + Pages，并与当前仓库中的自动化脚本保持一致。

## 部署内容

执行 [cloudflare/deploy.sh](cloudflare/deploy.sh) 后，会依次完成这些步骤：

1. 创建或复用 D1 数据库 `cloud-clipboard-db`
2. 创建或复用 R2 存储桶 `cloud-clipboard-files`
3. 基于 [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template) 生成临时 `wrangler.toml`
4. 执行 [cloudflare/d1/schema.sql](cloudflare/d1/schema.sql) 远程迁移
5. 部署 Workers API
6. 基于 [cloudflare/pages/client/src/config.js.template](cloudflare/pages/client/src/config.js.template) 生成临时 `config.js`
7. 构建并部署 Cloudflare Pages 前端
8. 输出 Worker API 地址和 Pages 访问地址

## 前置要求

- Node.js
- npm
- Cloudflare 账号
- 已安装或可自动安装 Wrangler CLI

建议先确认：

```bash
node -v
npm -v
wrangler --version
```

如果未登录 Wrangler：

```bash
wrangler login
```

也可以手动确认当前登录状态：

```bash
wrangler whoami
```

## 一键部署

在仓库根目录执行：

```bash
cd cloudflare
bash deploy.sh
```

部署成功后，脚本会输出两类地址：

- Worker API 地址
- Cloudflare Pages 前端地址

## 可配置项

Cloudflare Workers 默认变量定义在 [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template)。

当前 [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template) 里的 `vars` 目前包括这些变量：

例如：

```toml
[vars]
AUTH_PASSWORD = "123"
ROOM_AUTH = "{\"private\":\"\",\"finance\":\"finance-pass\"}"
ROOM_LIST = "false"
HISTORY_LIMIT = "50"
TEXT_LIMIT = "4096"
FILE_LIMIT = "204857600"
FILE_EXPIRE = "3600"
DEBUG_LOG = "false"
SYNC_STATE_CLEANUP = "600"
SYNC_MESSAGE_EXPIRE = "86400"
SYNC_PAYLOAD_EXPIRE = "86400"
SYNC_PENDING_DEVICE_EXPIRE = "604800"
SYNC_TRUSTED_DEVICE_EXPIRE = "0"
```

| 变量 | 默认值 | 类型 | 说明 |
| --- | --- | --- | --- |
| `AUTH_PASSWORD` | `"123"` | 字符串或布尔语义 | 全局入口密码。只要设置了就对所有房间生效，保证旧密码升级后仍可用 |
| `ROOM_AUTH` | `{"private":"","finance":"finance-pass"}` | JSON 字符串 | 房间级密码映射。不会让 `AUTH_PASSWORD` 失效，而是为指定房间增加额外可用密码 |
| `ROOM_LIST` | `"false"` | 布尔语义字符串 | 是否启用房间列表功能，支持 `1`、`true`、`yes`、`on` |
| `HISTORY_LIMIT` | `"50"` | 整数字符串 | 每个房间保留的历史消息条数 |
| `TEXT_LIMIT` | `"4096"` | 整数字符串 | 单条文本消息最大长度 |
| `FILE_LIMIT` | `"204857600"` | 整数字符串 | 单个文件上传大小上限，单位字节 |
| `FILE_EXPIRE` | `"3600"` | 整数字符串 | 文件过期时间，单位秒 |
| `DEBUG_LOG` | `"false"` | 布尔语义字符串 | 是否输出 Worker 调试流程日志，发布使用默认关闭，排查时可临时开启 |
| `SYNC_STATE_CLEANUP` | `"600"` | 整数字符串 | 同步状态清理触发参考间隔，单位秒 |
| `SYNC_MESSAGE_EXPIRE` | `"86400"` | 整数字符串 | 同步文本历史保留时间，单位秒 |
| `SYNC_PAYLOAD_EXPIRE` | `"86400"` | 整数字符串 | 同步文件通知历史保留时间，单位秒 |
| `SYNC_PENDING_DEVICE_EXPIRE` | `"604800"` | 整数字符串 | 未批准同步设备离线后保留时间，单位秒 |
| `SYNC_TRUSTED_DEVICE_EXPIRE` | `"0"` | 整数字符串 | 已信任同步设备离线后保留时间，单位秒；0 表示不因离线时长自动移除 |

## 多端同步协议

Cloudflare Workers 版提供与 Go 服务端一致的一期同步入口：

- `/sync/server`：返回同步 WebSocket 地址与房间鉴权状态
- `/sync/ws`：同步 WebSocket 连接，处理 `hello`、`clipboardPublish`、`clipboardSync`、`payloadNotice`
- `/api/sync/devices`：同步设备列表
- `/api/sync/status`：同步房间状态
- `/api/sync/bootstrap`：当前设备、最近文本和最近文件通知
- `/api/sync/pair/request`：设备配对申请
- `/api/sync/pair/approve`：批准设备
- `/api/sync/device/:deviceId/trust`：切换设备信任状态
- `/api/sync/payload-notice`：发送图片或文件通知

同步协议与旧 `/api/push` 消息广播分离。旧网页文本、文件上传下载、房间列表和 WebSocket 推送继续走原链路；三端剪贴板同步和 Android 文件确认接收走独立同步链路。

同步入口会先复用现有房间访问规则校验房间密码或全局密码，校验通过后再执行同步设备 pending / trusted 校验。未批准设备可以连接并进入 pending 状态，但不能发布剪贴板内容，也不会收到 trusted-only 的同步内容。

Workers 版同步状态存放在 `WEBSOCKET_ROOM` Durable Object storage 中，包括同步设备、最近文本同步记录和最近 payload 通知。该状态不依赖 D1 或 R2；D1/R2 仍由原有历史消息和文件能力使用。

### roomAuth 说明

`ROOM_AUTH` 需要是一个 JSON 字符串，对应后端的 `server.roomAuth`。

示例：

```toml
ROOM_AUTH = "{\"private\":\"\",\"finance\":\"finance-pass\",\"ops\":\"ops-pass\"}"
```

含义：

- `private: ""` 表示 `private` 房间只接受全局 `AUTH_PASSWORD`
- `finance: "finance-pass"` 表示 `finance` 房间同时接受全局 `AUTH_PASSWORD` 和 `finance-pass`
- `ops: "ops-pass"` 表示 `ops` 房间同时接受全局 `AUTH_PASSWORD` 和 `ops-pass`

如果你想修改这些变量，有两种方式：

1. 在部署前直接编辑 [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template)，然后重新执行 [cloudflare/deploy.sh](cloudflare/deploy.sh)
2. 部署完成后，在 Cloudflare Dashboard 的 Workers 设置中修改变量

注意：除了这些 `vars`，模板里还有几类不是“环境变量”的部署配置：

- D1 绑定：`DB`
- R2 绑定：`R2_BUCKET`
- Durable Object 绑定：`WEBSOCKET_ROOM`

这些绑定项同样是运行所必需的，但它们不属于 `vars`，通常由部署脚本自动处理，不需要像密码或限制值那样日常调整。

## 前端配置来源

Pages 前端源码内置了默认配置文件 [cloudflare/pages/client/src/config.js](cloudflare/pages/client/src/config.js)，默认使用同源 API，便于本地构建和预览。

部署脚本会在构建前临时生成 `cloudflare/pages/client/.env.production`，把 Worker 地址注入为：

- `VUE_APP_API_BASE_URL`
- `VUE_APP_WS_BASE_URL`

构建完成后该临时环境文件会自动清理；正常情况下不需要手动修改前端配置文件。

## 数据库迁移

数据库结构定义在 [cloudflare/d1/schema.sql](cloudflare/d1/schema.sql)。

部署脚本会自动执行：

- 远程 D1 迁移：始终执行
- 本地 D1 迁移：仅在环境允许时执行

如果你后续修改了 schema，可以重新执行部署脚本，或单独运行：

```bash
cd cloudflare/workers
wrangler d1 execute cloud-clipboard-db --file=../d1/schema.sql --remote
```

## macOS 12 注意事项

当前脚本已经兼容较老的 macOS，但如果你使用的是 macOS 13.5 以下版本，本地 D1 迁移会被自动跳过，因为 `workerd` 本地运行有系统版本要求。

这不会影响远程部署。

如果你想显式跳过本地 D1 迁移，可以这样执行：

```bash
cd cloudflare
SKIP_LOCAL_D1=1 bash deploy.sh
```

## 重新部署

如果你只修改了 Workers 变量或逻辑，通常重新执行即可：

```bash
cd cloudflare
bash deploy.sh
```

脚本会自动：

- 复用已存在的 D1 数据库
- 复用已存在的 R2 存储桶
- 重新部署 Worker 和 Pages

## 常见问题

### 1. `wrangler whoami` 提示未登录

先执行：

```bash
wrangler login
```

### 2. 本地 D1 迁移失败

如果是 macOS 版本较低，可直接跳过本地迁移：

```bash
SKIP_LOCAL_D1=1 bash deploy.sh
```

### 3. 修改了 `wrangler.toml` 或 `.env.production`，但文件又消失了

这是正常行为。

部署脚本会在运行时临时生成：

- `cloudflare/workers/wrangler.toml`
- `cloudflare/pages/client/.env.production`

部署结束后会自动清理。

如果你要改默认值，请修改模板文件或源码默认配置，而不是改临时生成文件：

- [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template)
- [cloudflare/pages/client/src/config.js.template](cloudflare/pages/client/src/config.js.template)
- [cloudflare/pages/client/src/config.js](cloudflare/pages/client/src/config.js)

### 4. 修改了密码或 `ROOM_AUTH` 后未生效

确认你修改的是模板文件 [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template) 或 Cloudflare Dashboard 中的 Worker Variables，然后重新部署。

### 5. Pages 能打开，但 API 或 WebSocket 连接异常

优先检查：

1. Worker 是否部署成功
2. Pages 生成的 `config.js` 是否已写入正确的 Worker URL
3. Worker 变量中的 `AUTH_PASSWORD` / `ROOM_AUTH` / `ROOM_LIST` 是否符合预期
4. D1 schema 是否已经迁移到远程数据库

## 相关文件

- [cloudflare/deploy.sh](cloudflare/deploy.sh)
- [cloudflare/d1/schema.sql](cloudflare/d1/schema.sql)
- [cloudflare/workers/wrangler.toml.template](cloudflare/workers/wrangler.toml.template)
- [cloudflare/pages/client/src/config.js.template](cloudflare/pages/client/src/config.js.template)
- [cloudflare/workers/src/index.js](cloudflare/workers/src/index.js)
