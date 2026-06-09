# Cloud Clipboard 对话纪要与已完成内容

## 文档目的

本文档用于沉淀这段连续开发对话里已经确定的用户偏好、关键决策、迁移背景和已完成内容，方便后续继续开发时直接接上。

## 已明确的用户偏好

- 中文输出
- 优先直接动手，不要长篇空谈
- 每到一个可验证里程碑就提交一次中文 commit
- 保留旧 `/push`、旧网页文件上传下载逻辑，不要回归破坏
- 做完后给出实际验证结果，不要假装联调过

## 项目背景与迁移脉络

- 最早在旧 Node 基座上完成了一期同步能力的完整实现与多次里程碑提交
- 后续切到 `cloud-clipboard-go` 新基座，按“房间密码或全局密码 + 配对码”双层模型继续迁移
- Windows 端经历过 AHK 方案探索，随后因为可维护性与 bug 问题，改为 Go 桌面客户端路线
- 当前工作仓库已经回到 `E:\Workspace\VSCode\cloud-clipboard`

## 已完成的一期能力

### 服务端

- 新增独立同步协议，与旧 `/push` 分离
- 设备配对、批准、trusted 状态持久化
- `payloadNotice` 广播与 recent payload 返回
- recent 历史数量与状态清理参数已补齐一轮修正

### 网页端

- 网页端纯文本自动同步闭环已打通
- 浏览器权限不足时支持降级显示与手动复制
- 网页时间流记录合并与即时刷新做过修复
- 网页端可作为管理端批准设备

### Windows / 桌面端

- 早期已完成 Windows AHK 托盘客户端闭环
- 后续已开始并持续推进 `desktop-client-go` 跨平台桌面客户端
- 已补齐桌面端跨平台基础运行能力
- 已修复默认房间最新内容拉取、同步地址、下载地址、配置字段兼容性等问题

### Android 同步客户端

- Android 文本同步闭环已完成
- Android 图片/文件通知确认接收已完成一期版本
- 确认后下载到应用缓存，接收页支持打开、分享、另存、已处理
- 缓存默认保留 24 小时，并有清理触发器
- 已补过多轮地址解析、分享通知、无障碍后台剪贴板补传兼容性

## 已讨论并部分推进的二期方向

- Android 权限监测与设置引导
- Android 悬浮确认入口及可配置项
- Android 后台监听状态下的剪贴板获取问题处理
- Windows / 桌面端自动重连、快捷键录入、提示风格、缓存清理
- 文件 / 图片复制回传时的二次确认机制
- 服务端缓存清理与可配置策略评估

## 当前仓库最近一轮已完成的事项

- 修复网页时间流同步记录合并
- 增强同步协议参数校验兼容性
- 修复房间内容链接编码兼容性
- 修复最新文件下载链接生成
- 修复桌面端默认房间最新内容拉取
- 修复安卓分享文件接收通知地址
- 修复安卓同步接口地址拼接兼容性
- 实现网页时间流同步文本即时刷新
- 修复安卓接收下载地址解析兼容性
- 修复桌面端同步与下载地址兼容性
- 完善服务端下载文件名兼容性
- 修复安卓端同步地址解析兼容性
- 补齐桌面端跨平台基础运行能力
- 统一 Cloudflare 同步默认配置与历史配置默认值
- 修复同步 recent 历史数量配置生效
- 增强安卓无障碍后台剪贴板补传
- 优化桌面端配置字段兼容性
- 清理前端构建旧服务端同步路径
- 移除 Cloudflare Pages 无用构建依赖
- 曾短暂补齐 Android 16 本地模拟器联调环境，并在模拟器中构建、安装、拉起过 `android-sync-client` debug 包
- 由于 Android 模拟器环境与 VMware 虚拟化能力冲突，后续已按要求清理 Android SDK / AVD / 模拟器环境，并恢复为优先真机测试路线
- 已补充 Android 10 / 13 / 14+ 的后台剪贴板限制说明到权限页与运行建议中
- 已让前台服务 / 无障碍 / Shizuku 模式建议明确显示系统版本限制、通知影响与后台复制稳定性差异
- 已确认当前会话没有可直接使用的 `test-android-apps` 插件能力，后续安卓调试优先采用真机 + `adb`
- 已统一网页端同步设备列表、摘要、诊断信息的自动刷新入口，减少刷新后状态不同步和时间流不一致
- 已为安卓运行页补充后台复制诊断卡片，可直接查看最近一次剪贴板回传来源、补传结果和下一步建议，便于真机排查高版本后台限制
- 已在安卓权限页补充厂商后台保活设置入口，并针对澎湃 / MIUI、华为 / 荣耀、OPPO / OnePlus / realme、vivo / iQOO、三星补充后台保活提示
- 已在桌面端控制面板概览页补充连接诊断与缓存目录摘要，可直接识别回环地址、待批准、自动重连暂停和下载缓存位置
- 已在桌面端连接配置页补充连接前检查提示，保存前即可看到回环地址、待批准和自动重连暂停等下一步建议
- 已补强安卓运行页模式实现摘要与快捷处理按钮，会按当前模式和权限状态优先引导到通知、无障碍、电池优化或厂商后台保活设置
- 已在安卓运行页补充后台复制就绪度摘要，可直接看到当前模式在真机后台复制场景下的大致可用性、主要风险和下一步建议
- 已在安卓运行页补充后台复制动态排查按钮，会按当前校验状态直达通知权限、无障碍、电池优化或厂商后台保活等下一步入口
- 已在桌面端控制面板概览页补充平台能力摘要，可直接看到当前平台对文件选择、文件剪贴板、右键菜单和剪贴板文件确认的支持情况
- 已在桌面端控制面板概览页补充下一步建议卡片，会根据回环地址、待批准、重连暂停、待确认文件、通知模式、右键菜单和热键状态给出当前最该处理的动作
- 已同步更新仓库根 README、安卓同步客户端 README、桌面端 README，使当前一期能力、后台诊断和控制面板摘要与代码实现保持一致
- 已移除根 README / 英文 README 中的赞助与捐赠相关内容，并改为面向发布版的功能说明结构
- 已新增 `docs/03-sync-usage-and-effects.md`，集中说明自动同步怎么使用、启用后的效果、当前边界与常见问题
- 已在根 README 和英文 README 顶部快捷入口、自动同步章节、详细文档章节中补充 `docs/03-sync-usage-and-effects.md` 跳转，确保用户能明显找到使用与效果说明
- 已调整 README 中同步客户端说明位置，将自动同步能力独立成块，避免 Android 同步客户端混入服务端快速开始章节
- 已修正 `docs/01-current-plan-summary.md` 中当前远端地址，保持计划摘要与实际仓库远端一致
- 已同步补记 `b2fc42a` 文档里程碑，并将最新计划摘要、完成记录与最近提交重新对齐
- 已再次核对当前工作区为干净状态，确认 `server-node/`、`cloud-clip/uploads/`、`cloud-clip/config.json`、`cloud-clip/history.json` 为 `.gitignore` 管理的本地运行态或历史兼容目录，本轮未做破坏性清理
- 已再次审计 tracked 文件，确认当前主线桌面端仍为 `desktop-client-go`，没有需要继续迁回主线的旧 AHK 客户端代码残留
- 已将 Cloudflare Pages 前端同步设备页与主站 `client` 的刷新逻辑对齐，统一改为通过 `syncRefreshAll()` 刷新设备列表、bootstrap 摘要和诊断状态，减少两个网页前端在设备状态和自动刷新上的行为分叉
- 已补齐 Cloudflare Pages 前端同步诊断中的状态清理文案，和主站一致展示 `stateCleanup` 间隔
- 已修复 Cloudflare Pages 前端 `after-build.js` 缺少 `fs` 导入的问题，避免 `npm run build` 在产物压缩后处理阶段直接报错
- 已同步更新 `cloudflare/README.md`，补充 Pages 前端同步设备页当前已与主站统一刷新策略、`syncRefreshAll()` 覆盖范围，以及 `after-build.js` 会生成 `.gz` / `.br` 压缩产物的构建说明
- 已再次补齐 `docs/01-current-plan-summary.md` 的“最近已经完成的关键提交”列表，使其覆盖到 `161dff9` 为止，避免计划摘要落后于实际里程碑记录
- 已确认 Cloudflare Pages 前端 `UnifiedComposer.vue` 也已切到与主站一致的文件发送实现，包含直传、小文件表头上传、分片上传完成与失败清理、`payloadNotice` 广播，以及兼容 `msg/message` 的发送失败提示
- 已将 Cloudflare Pages 统一发送面板的发送按钮宽度与移动端换行样式对齐主站，减少两个网页前端在文件发送入口上的行为和展示分叉
- 已为桌面端 Go 控制面板概览页补充“最近远端文本同步”和“状态最近刷新”摘要，用户无需翻日志即可判断文本回流是否发生、状态页是否仍在持续刷新
- 已把桌面端最近远端文件通知摘要改成带时间的显示，便于区分“最近收到过什么”和“多久前收到过”
- 已同步更新 `desktop-client-go/README.md` 和 `docs/03-sync-usage-and-effects.md`，补充桌面端概览页新增的远端文本同步、远端文件通知时间和状态刷新摘要说明，避免文档落后于面板实现
- 已同步更新根 `README.md` 与 `README.en.md` 的桌面端说明，补齐控制面板概览页新增的远端文本同步、远端文件通知和状态刷新摘要，保持总览文档与子模块说明一致
- 已将桌面端控制面板概览页的“最近手动发送”补成带时间摘要，和远端文本、远端文件通知保持一致，便于判断最近一次主动动作发生在什么时候
- 已为桌面端状态持久化补充 `LastErrorAt`，并将控制面板概览页的“最近错误”改成带时间摘要，便于区分当前报错内容和最近一次报错时间
- 已将桌面端控制面板概览页的“待确认剪贴板文件”补成带检测时间的摘要，便于同时判断文件是什么、什么时候检测到以及剩余确认时间
- 已将桌面端控制面板概览页的“最近缓存清理”调整为时间优先摘要，便于更快看出上一次缓存清理发生在什么时候、清了多少项
- 已将桌面端控制面板概览页的“最近远端文件通知”摘要类型文案改成中文展示，避免直接暴露 `image`、`file`、`files` 这类协议字段
- 已在 Android 16 真机上定位并修复 Android 同步客户端重复回传问题：根因是无障碍补检查会反复发布同一剪贴板文本，并且快照兜底可能误采本 App 当前界面文本；现已改为启动时以当前剪贴板作为基线、同文本短时间去重、远端写入内容在本地变化前不回传、recent 恢复跳过本机来源、无障碍快照跳过本 App 界面
- 已同步补强网页端、Cloudflare Pages 前端和桌面端 Go 客户端的来源设备自检：收到 `clipboardSync` 时如果 `sourceDeviceId` 等于本机则直接忽略，网页端远端写入剪贴板后也会标记为已发送文本，避免后续轮询自激回传
- 已在 Go 服务端补充短时间重复文本兜底：同房间、同来源设备、同文本 30 秒内再次发布会返回 duplicate，不再写入 recent 历史；该兜底只防止异常客户端刷屏，不替代客户端防回环
- 已补回最小 Android SDK 到本机 `D:\Program Files\Android\Sdk`，仅安装命令行工具、platform 34、build-tools 和 platform-tools，不安装模拟器或 AVD，不触碰 VMware 虚拟化环境
- 已将新 Android debug 包覆盖安装到真机 `760435a8`，并在启动同步后观察约 35 秒，服务端 `lastMessageAt` 与 `recentMessageCount` 未继续增长，确认此前 `http://127.0.0.1:9501/#/` 重复刷屏已停止；历史中的旧重复记录为修复前已写入内容
- 本轮重新验证通过：`cloud-clip` 下 `go test ./lib`、`desktop-client-go` 下 `go test ./internal/syncclient ./internal/config`、`client` 下 `npm run build`、`android-sync-client` 下 `.\gradlew.bat assembleDebug` 均成功；验证后已停止 Gradle daemon，并清理 Android 构建目录、主站 `client/dist`、Cloudflare Pages 临时依赖与构建目录
- 已处理同步 recent 满 100 条后网页删除失败的问题：服务端新增同步文本单条删除与按房间清空接口，主站和 Cloudflare Pages 前端的同步文本删除按钮会走新同步接口，顶部清空会同时清旧 `/push` 历史和新同步 recent；本地默认房间的 100 条误回传同步历史已清空
- 已优化 Android 无障碍模式后台监听策略：无障碍模式不再通过轮询高频读取系统剪贴板，避免 Android 16 / 澎湃系统后台拒绝剪贴板访问日志刷屏；无障碍 pulse 改为直接走可见文本快照兜底
- 已在 Android 16 真机 `760435a8` 上继续验证后台复制：通过 Chrome 临时测试页复制 `codex-android-copy-test-*` 文本，App 退到后台后服务端 recent 成功收到 1 条来源为 `android-live-device` 的文本记录，继续观察 12 秒未重复刷屏；logcat 仍能看到系统对后台剪贴板读取的拒绝，这是 Android 16 受限行为，但本轮已通过无障碍快照兜底完成上传
- 已修复 Android 无障碍快照兜底触发范围：新增文本选择事件监听，减少普通点击、窗口变化、内容变化造成的泛 pulse；快照优先取选中文本、聚焦输入框和可编辑文本，避免把整页可见文字误当剪贴板内容；剪贴板 listener 后台读取失败时也会消费无障碍快照兜底，而不是直接放弃
- 已接入 Android 同步客户端的 Shizuku API 授权检测：新增 Shizuku Provider、安装/运行/授权/UID 状态读取、授权请求按钮和授权回调提示；真机当前 Shizuku 由 root 启动，已通过 App 弹窗选择“始终允许”，界面识别为 `Shizuku 已授权（UID 0）`
- 已修正 Android 运行页配置交互：切换剪贴板读取模式、自动续连和开机恢复时会即时保存并刷新提示，避免选择 Shizuku 后仍显示旧的无障碍模式说明
- 已将 Shizuku 授权后的运行提示改为“已授权，增强链路未开放”，明确当前阶段仍建议日常后台复制使用无障碍增强模式，不把尚未接完的 Shizuku 独立剪贴板主通道伪装成可用能力
- 已在真机上完成本轮回归：安装新包后切到 Shizuku 模式触发授权，授权成功后再切回无障碍增强模式并手动启动同步；服务端显示 `android-live-device` 在线 trusted；再次通过 Chrome 临时测试页复制 `codex-shizuku-regression-copy-*`，服务端 recent 成功收到来源为 Android 的文本；本轮产生的 `codex-*` 测试文本已全部从 recent 删除，未删除手机端任何非调试数据或文件
- 已为桌面端 Go 客户端补充自动化自测隔离开关：设置 `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 时会跳过全局热键、剪贴板监听和 Windows 右键菜单管理，便于后续 headless/CI/临时配置自测时不触碰用户真实系统级配置；普通运行不受影响
- 已补充桌面端隔离开关单元测试，确认只有显式开启环境变量时才跳过 OS 集成
- 已尝试使用临时服务端配置、临时桌面端配置和临时存储目录进行 Windows 桌面端进程级自测；服务端临时构建与启动、`internal/config` 固定测试二进制均通过，但本机对新构建的桌面端 exe 在进程创建阶段返回 `拒绝访问`，因此本轮未伪装成已完成完整 headless 面板联调
- 本轮重新验证通过：`cloud-clip` 下 `go test ./lib`、`desktop-client-go` 下 `go test ./internal/app ./internal/syncclient ./internal/transfer`、`client` 下 `npm run build` 均成功；`client` 构建仍有既有 bundle 体积与 Vuetify / Sass 弃用警告，但构建产物生成成功
- 本轮收尾已清理临时服务端、临时桌面端 exe、临时配置目录、临时测试文件和 `client/dist`，未删除手机端、Windows 端或服务端任何非调试数据
- 已将 Android Shizuku 模式从“授权后仍不开放启动”调整为“授权后可作为系统授权与剪贴板 AppOps 诊断辅助模式启动”：启动前置校验不再拦截已授权 Shizuku，运行页会展示剪贴板读取 / 写入 AppOps，权限页也会把 AppOps 纳入摘要和建议
- 已同步更新 Android 同步客户端 README 与 `docs/03-sync-usage-and-effects.md`，明确 Shizuku 辅助模式不承诺绕过系统后台剪贴板限制，后台复制稳定性仍以系统实际限制、无障碍、电池优化和厂商后台保活为准
- 已在 Android 16 真机 `760435a8` 上覆盖安装 debug 包并验证：临时把 App 私有配置中的 `clipboard_mode` 改为 `shizuku` 后启动 App，`SyncService` 能以 `isForeground=true` 前台服务运行，说明已授权 Shizuku 不再被启动前置校验拦截；验证后已恢复原配置为 `accessibility`，并清理 `/data/local/tmp` 调试文件、本地临时配置目录、Android 构建目录和 Gradle daemon，未删除手机端任何非调试数据或文件
- 已定位 Windows 桌面端本机新构建 exe 被拒绝启动的触发点：只导入传输 / app 包的临时程序可以正常启动，只导入 `internal/tray` 的临时程序会在进程创建阶段返回 `拒绝访问`，因此问题集中在托盘组件相关二进制内容，而不是 Go 构建、路径或配置本身
- 已新增不导入托盘组件的 `cloud-clipboard-panel` 命令入口，并把公共命令逻辑抽到 `internal/desktopcmd`：无托盘面板版可启动本地控制面板、同步连接、文件发送、最新文本 / 文件拉取和右键一次性动作；托盘版仍保留原入口
- 已用临时服务端、临时桌面配置和临时下载目录完成无托盘面板版 Windows 联调：面板 API 可访问，设备批准后 trusted，手动文本发送成功，文件发送成功并产生 `payloadNotice`，热键录入会规范化为 `Ctrl+Alt+P` / `Ctrl+Alt+C` / `Ctrl+Shift+V`，并确认 `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 会跳过热键、剪贴板监听和右键菜单管理
- 已同步更新根 README、英文 README、桌面端 README 与 `docs/03-sync-usage-and-effects.md`，补充无托盘面板版作为系统策略拦截托盘组件时的可用入口
- 本轮重新验证通过：`desktop-client-go` 下 `go test ./internal/app ./internal/syncclient ./internal/transfer ./internal/desktopcmd`、`go build ./cmd/cloud-clipboard-desktop`、`go build ./cmd/cloud-clipboard-panel`，以及 `cloud-clip` 下 `go test ./lib` 均成功；验证后已清理命令目录构建 exe、临时服务端、临时桌面端、临时配置、临时测试文件和 `C:\Temp` 自测目录
- 已继续补测无托盘面板版 `-shell-send` 一次性发送链路，该链路等价覆盖 Windows 右键菜单“复制到剪贴板服务器”的核心动作：使用临时服务端、临时桌面配置和临时测试文件验证 `.txt` 与 `.png` 均可通过旧 `/upload` 上传，并同步产生 `payloadNotice`
- 本轮 `.png` 验证中服务端 recent payload 正确记录为 `kind=image`、`sourceDeviceId=codex-shell-png-device`，旧 `content/latest` 也能返回最新图片文件信息；验证后已停止临时服务端，并清理临时 exe、临时配置、临时上传目录、临时下载目录和测试文件
- 已继续补测无托盘面板版 `-shell-download-dir` 一次性下载链路，该链路等价覆盖 Windows 右键菜单“从剪贴板服务器粘贴/下载到此处”的核心动作：先通过临时服务端上传测试 PNG，再下载到临时目录，文件名和大小均一致；本轮未写入系统剪贴板，验证后已清理全部临时进程与文件
- 已收口 Android 运行页和权限页的发布版文案：移除“当前阶段 / 后续 / 暂不开放启动 / 实现阶段”等开发态表达，Shizuku 已授权时改为明确展示“可启动同步 + AppOps 诊断辅助”，避免与当前实现不一致
- 已确认 Android 状态展示仍将 trusted 映射为“已连接”；代码中的“已信任”仅保留为旧状态输入兼容，不会作为当前用户可见状态文案展示
- 本轮重新验证通过：`android-sync-client` 下 `.\gradlew.bat assembleDebug` 成功；验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 `android-sync-client/build` 与 `android-sync-client/app/build`
- 已在 Android 16 真机 `760435a8` 上做只读权限巡检：App 已安装并运行，通知权限、悬浮窗、Shizuku API 授权、前台服务、剪贴板 AppOps、Shizuku root 服务均处于可用状态，且存在云剪同步前台通知
- 本轮真机巡检发现系统真正启用的无障碍服务列表里没有云剪同步，仅有一木记账和 GKD；当前 App 配置仍选择 `accessibility` 模式，因此后台补传不会按无障碍增强生效，代码侧会按 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 识别为未开启并阻止该模式启动
- 本轮只读读取了 App 私有配置，确认 `clipboard_mode=accessibility`、`last_desired_running_state=running`、`server_base=http://192.168.31.236:9501`；未修改手机配置、未卸载、未清理 App 数据，也未删除手机端任何非调试数据或文件
- 已基于真机巡检结果优化 Android 无障碍增强模式阻塞提示：明确要求在系统无障碍服务列表里启用“云剪同步”，并说明仅系统授权或 AppOps 允许不等于无障碍服务已经生效
- 本轮重新验证通过：`android-sync-client` 下 `.\gradlew.bat assembleDebug` 成功；验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 `android-sync-client/build` 与 `android-sync-client/app/build`
- 已进一步只读确认 Android 真机当前没有活跃的 `SyncService` ServiceRecord，但系统通知列表仍存在云剪同步前台通知；据此补充 `SyncService.onDestroy()` 显式移除前台通知，避免启动被阻止或服务销毁后留下误导性常驻通知
- 本轮重新验证通过：`android-sync-client` 下 `.\gradlew.bat assembleDebug` 成功；为避免触发真机重新授权，本轮未覆盖安装手机，仅做本地编译验证；验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 `android-sync-client/build` 与 `android-sync-client/app/build`
- 已为桌面端 `desktopcmd.RunShellAction` 补充自动化测试，覆盖 `-shell-send` 发送文件并广播 `payloadNotice`、`-shell-download-dir` 下载服务端最新文件到指定目录；测试使用 `httptest` 临时服务和 `t.TempDir()`，不会触碰真实右键菜单、系统剪贴板或用户文件
- 本轮重新验证通过：`desktop-client-go` 下 `go test ./internal/desktopcmd ./internal/transfer ./internal/config ./internal/syncclient ./internal/app` 成功；验证后已执行 `go clean -testcache` 清理测试缓存
- 已补强 Android 同步地址拼接单元测试，覆盖 HTTPS 服务端自动转 `wss://`、`/api` 前缀去重、已有 query 与 fragment 保留、空查询值跳过和中文房间名编码，降低后续 Cloudflare / 反代路径再次回归的风险
- 本轮重新验证通过：`android-sync-client` 下 `.\gradlew.bat testDebugUnitTest` 成功；验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 `android-sync-client/build` 与 `android-sync-client/app/build`
- 已补强 Go 服务端同步状态单元测试，覆盖 `payloadNotice` 默认 `payloadId` / `room` / `kind` / `createdAt` 补齐、重复 `payloadId` 幂等，以及消息、payload、pending 设备、trusted 设备按清理策略过期移除，降低后续服务端缓存和状态清理逻辑回归风险
- 本轮重新验证通过：`cloud-clip` 下 `go test ./lib` 成功；验证后已执行 `go clean -testcache` 清理 Go 测试缓存，测试只使用 `t.TempDir()` 临时状态文件，未触碰服务端真实配置、历史或上传数据
- 已补强 Go 服务端同步广播单元测试，使用真实 `gorilla/websocket` 临时连接覆盖 `Broadcast` 对来源设备、未 trusted 设备、其它房间设备的跳过逻辑，并确认同房间 trusted 目标可收到 `clipboardSync`，把此前真机联调关注的防回环规则固化为可回归测试
- 本轮重新验证通过：`cloud-clip` 下 `go test ./lib` 成功；验证后已执行 `go clean -testcache` 清理 Go 测试缓存，临时 WebSocket 服务由测试生命周期自动关闭，未启动真实服务端或写入真实运行态数据
- 本轮继续验证桌面端 Go 客户端核心包：`desktop-client-go` 下 `go test ./internal/desktopcmd ./internal/transfer ./internal/config ./internal/syncclient ./internal/app` 成功；验证后已执行 `go clean -testcache` 清理测试缓存，未触碰真实右键菜单、系统剪贴板或用户文件
- 本轮继续对 Android 16 真机 `760435a8` 做只读权限巡检：设备在线，系统无障碍启用列表仍不包含云剪同步；Shizuku 包 `moe.shizuku.privileged.api` 已安装且 Watchdog 前台服务运行中，App 的 `moe.shizuku.manager.permission.API_V23` 显示 `granted=true`；本轮未修改手机配置、未卸载、未覆盖安装、未删除任何手机端数据或文件
- 已补强 Android 无障碍服务启用判断单元测试：将 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 的冒号分隔匹配逻辑拆成可测纯函数，覆盖精确命中、大小写和空格兼容、同包错误服务不误判、空列表和空目标不误判，避免 Shizuku / AppOps 已授权时误把无障碍增强也判断为已开启
- 本轮只读探测 Android 16 真机 Shizuku 剪贴板主通道可行性：系统存在 `clipboard` Binder 服务，但 `cmd clipboard` 返回 `No shell command implementation`，`dumpsys clipboard` 未返回可用内容；因此本轮没有把 Shizuku 伪装成已完成的独立剪贴板主通道，仍保持为授权与 AppOps 诊断辅助
- 本轮重新验证通过：`android-sync-client` 下 `.\gradlew.bat testDebugUnitTest` 成功；验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 `android-sync-client/build` 与 `android-sync-client/app/build`
- 已优化桌面端通知模式兜底：`noticeMode` 为空或拼错时现在回退到推荐的右下角 `tip`，不再回退到系统通知 `popup`，避免错误配置导致系统通知堆积；显式配置 `popup / tip / log / off` 仍按用户选择生效
- 本轮重新验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd` 成功；验证后已执行 `go clean -testcache` 清理 Go 测试缓存，测试只使用临时配置目录，未触碰真实右键菜单、系统剪贴板或用户文件
- 已优化桌面端快捷键配置归一化：只有修饰键没有主键、或只有主键没有修饰键时会自动清空，避免面板显示已配置但实际热键注册层拒绝生效；常见组合仍会规范显示为 `Ctrl+Alt+C` 这类格式
- 本轮重新验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/hotkey ./internal/app` 成功；验证后已执行 `go clean -testcache` 清理 Go 测试缓存，未注册真实全局热键，未触碰系统剪贴板或用户文件
- 已优化桌面端控制面板快捷键录入提示：录入弹窗只按修饰键时会提示继续按主键，只按主键时会提示需要同时按 `Ctrl / Alt / Shift / Win`，避免静默忽略造成用户以为录入卡住
- 本轮重新验证通过：`desktop-client-go` 下 `go test ./internal/panel ./internal/config ./internal/app` 成功；验证后已执行 `go clean -testcache` 清理 Go 测试缓存，未启动真实面板服务或注册真实热键
- 已完成桌面端无托盘面板隔离 smoke：临时编译 `cloud-clipboard-panel` 到系统临时目录，使用临时配置、临时下载目录、随机本地端口和 `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动，验证 `/api/status` 正常返回、首页内已包含新的快捷键录入提示文案；验证后已停止临时进程并删除临时 exe、配置和下载目录
- 本轮补充 Android 构建验证：`android-sync-client` 下 `.\gradlew.bat assembleDebug` 成功，确认当前无障碍判断测试与权限代码改动可完整编译出 debug 包；本轮仅构建不安装，真机 `760435a8` 保持在线但未被修改；验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 `android-sync-client/build` 与 `android-sync-client/app/build`
- 已继续收口 README 发布版表述：根 README / 英文 README / Android 同步客户端 README 去掉“当前阶段目标”“暂不作为发送主入口”“Currently Android-only”等偏开发态表达，改为“提供以下能力”“以接收为主 / Receive-focused”等面向用户的描述，并补充 Android 文件通知可由网页端或桌面端触发
- 已在关闭本机安全软件拦截后完成桌面端托盘版隔离 smoke：临时编译 `cloud-clipboard-desktop` 到系统临时目录，使用临时配置、临时下载目录、随机本地端口启动，未启用右键菜单、热键和剪贴板文件确认；验证 `/api/status` 可访问，托盘版进程可正常启动，日志中仅因测试配置故意指向 `127.0.0.1:9` 产生一次连接失败并按配置暂停自动重连；验证后已停止临时进程并删除临时 exe、配置和下载目录
- 已继续完成桌面端托盘版临时服务端闭环验证：临时启动 Go 服务端与托盘版桌面端，使用房间密码连接同步房间，桌面端先进入 pending，调用 `/api/sync/pair/approve` 后变为 trusted，控制面板 `/api/status` 返回 `trusted=true`，再通过面板 `/api/send-text` 手动发送测试文本，服务端旧 `/content/latest` 可读到同一内容；验证配置关闭右键菜单、全局热键和剪贴板文件确认，未写入真实配置、真实服务端数据或用户文件，验证后已停止临时服务端和托盘进程并删除临时目录
- 已继续完成桌面端托盘版文件发送闭环验证：临时服务端和托盘版桌面端进入 trusted 后，通过控制面板 `/api/send-file` 发送临时 `.txt` 文件，旧 `/content/latest` 返回同名最新文件，`/api/sync/status` 的 `recentPayloads` 中出现 `kind=file`、`title=codex-tray-file-e2e.txt`、`sourceDeviceId=codex-tray-file-e2e-device` 的通知记录；验证后已停止临时服务端和托盘进程，并清理临时上传目录、下载目录、测试 exe、配置与测试文件
- 本轮继续对 Android 16 真机 `760435a8` 做只读权限巡检：设备在线，App 已安装，通知权限、Shizuku API 权限、剪贴板读写 AppOps 和电池白名单均可用，Shizuku Watchdog 前台服务正在运行；当前 `SyncService` 未运行，`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 未列出云剪同步，但 `dumpsys activity services` 可见云剪同步无障碍服务连接记录，说明澎湃 OS 上可能存在“设置字符串与实际系统枚举不一致”的诊断场景；本轮未修改手机配置、未覆盖安装、未清理 App 数据、未删除任何手机端非调试数据或文件
- 已优化 Android 无障碍启用状态判断：保留 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 精确匹配，同时增加 `AccessibilityManager.getEnabledAccessibilityServiceList()` 兜底枚举，减少厂商 ROM 上无障碍状态误判；并补充服务列表匹配单元测试，避免同包其它服务或 debug 包名误判为已启用
- 已继续优化 Android 权限摘要展示：无障碍状态现在会显示来源详情，例如“已开启（系统设置）”“已开启（系统服务枚举）”或“未开启”，方便在澎湃 OS 这类系统设置字符串与实际服务枚举不一致时快速判断当前 App 采用了哪一路判断；本轮补充了来源文案单元测试
- 已继续把 Android 无障碍来源详情同步到运行页：无障碍增强模式的就绪提示、模式说明、后台复制就绪度原因和后台复制诊断都会带上“系统设置 / 系统服务枚举”来源，避免权限页和运行页口径不一致
- 已优化桌面端控制面板快捷键概览：将“板 / 发 / 拉 / 贴 / 下”等缩写改为“面板 / 发送文本 / 拉取文本 / 拉取文件到剪贴板 / 下载文件”等完整动作名，同时把 Tip 和文件确认摘要改成更直观的“右下角提示 / 文件确认 / 提示位置”文案
- 已完成桌面端无托盘面板文案隔离 smoke：临时编译 `cloud-clipboard-panel`，使用临时配置、随机本地端口和 `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动，验证首页静态产物包含“面板 / 发送文本 / 拉取文本 / 拉取文件到剪贴板 / 下载文件 / 右下角提示 / 文件确认 / 提示位置”等新文案；验证后已停止临时进程并删除临时 exe、配置和下载目录
- 本轮完成核心回归验证：`cloud-clip` 下 `go test ./lib` 通过；`desktop-client-go` 下 `go test ./internal/desktopcmd ./internal/transfer ./internal/config ./internal/syncclient ./internal/app ./internal/panel` 通过；`android-sync-client` 下 `.\gradlew.bat testDebugUnitTest` 通过。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据；验证后已清理 Go 测试缓存、执行 Android `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录
- 已将 Android 同步客户端通知渠道名从英文 `Cloud Clipboard Sync` / `Cloud Clipboard Receive` 改为中文“云剪同步运行状态” / “云剪同步接收提醒”，减少系统通知设置里的英文残留；本轮曾发现当前 PATH 的 `apply_patch` 指向 WindowsApps 版 Codex 且被系统拒绝启动，已改用本地 OpenAI Codex bin 的补丁入口完成编辑，仓库未留下半截改动
- 已继续补充 Android 同步客户端通知渠道说明：运行状态渠道说明为“显示同步连接、重连和前台运行状态”，接收提醒渠道说明为“显示图片/文件接收确认、下载完成和同步失败提醒”，方便用户在系统通知设置里区分不同开关影响；本轮重新验证 `android-sync-client` 下 `./gradlew.bat testDebugUnitTest` 通过，验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 Android 构建目录
- 已在 Android 权限页补充通知渠道直达入口：新增“运行状态通知”和“接收提醒通知”按钮，Android 8 及以上会跳转到对应通知渠道设置，低版本回退到应用通知设置；本轮重新验证 `android-sync-client` 下 `./gradlew.bat testDebugUnitTest` 通过，验证后已执行 `gradlew clean`、停止 Gradle daemon，并清理 Android 构建目录
- 已继续收口发布版用户可见文案：桌面端控制面板首页改为直接说明可管理连接、同步状态、快捷动作、提示方式和缓存策略；Android 端 Shizuku、自动续连和权限建议文案去掉“验证 / 评估 / 暂不可用”等开发态表达，改成面向用户的使用提示。本轮验证通过：`desktop-client-go` 下 `go test ./internal/panel ./internal/config ./internal/app`、无托盘面板隔离 smoke、`android-sync-client` 下 `./gradlew.bat testDebugUnitTest`；验证后已清理 Go 测试缓存、执行 Android `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录，本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 已按用户手动开启无障碍后的状态做真机只读复查：`760435a8` 的 `enabled_accessibility_services` 已包含 `com.transparentlc.cloudclipboardsync/.ClipboardAccessAccessibilityService`，`dumpsys accessibility` 也显示“云剪同步”在 Bound services 与 Enabled services 中；本轮只读确认包 `lastUpdateTime=2026-06-08 02:46:08`，没有覆盖安装、没有修改手机配置、没有删除手机端任何非调试数据或文件
- 已继续收口发布版说明与诊断提示：根 README 的“当前新增能力 / 当前版本”改为更稳定的“新增同步能力 / 本项目”，桌面端 README 去掉“当前主收口 / 后续重新打包”等开发态表述，Android 本地剪贴板跳过诊断改成“已跳过本次...”的结果态提示。本轮验证通过：`android-sync-client` 下 `./gradlew.bat testDebugUnitTest`、`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd`；验证后已清理 Go 测试缓存、执行 Android `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录
- 本轮继续只读复查 Android 16 真机 `760435a8`：无障碍设置字符串、`dumpsys accessibility` 的 Enabled services 和 Bound services 均能看到“云剪同步”，Crashed services 为空；通知权限与 `moe.shizuku.manager.permission.API_V23` 均为 granted；进程列表显示 `shizuku_server` 以 root 身份运行，`moe.shizuku.privileged.api` App 也在运行；剪贴板 AppOps 中 `READ_CLIPBOARD` 有 allow / foreground 记录；当前 `SyncService` 未运行。本轮只读检查未覆盖安装、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续验证桌面端核心包：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient` 成功；验证后已执行 `go clean -testcache` 清理 Go 测试缓存。本轮同步更新 `docs/01-current-plan-summary.md`，补齐最近提交列表和 Android 当前真实权限状态，避免计划摘要继续落后于实际进度
- 已继续收口 Android 同步客户端发布版文案：运行页将未满足授权条件时的“启动状态：暂时被拦截”改为“启动状态：需要处理”，减少普通用户把正常待处理状态误解成程序故障；Android README 的限制说明去掉“当前不做 / 一期 / 继续补强”等开发态表达，改为稳定的能力边界说明。本轮验证 `android-sync-client` 下 `./gradlew.bat testDebugUnitTest` 通过；验证后已执行 `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 已提交 `6c2fa6b 优化安卓运行状态提示文案`，并同步补齐 `docs/01-current-plan-summary.md` 最近提交列表，避免计划摘要落后于实际里程碑；本轮仅做文档对齐，未运行新的手机写操作、未安装手机、未删除手机端任何非调试数据或文件
- 本轮继续做核心回归验证：`cloud-clip` 下 `go test ./lib` 通过；`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel` 通过；`android-sync-client` 下 `./gradlew.bat testDebugUnitTest` 通过。验证后已清理 `cloud-clip` 与 `desktop-client-go` 的 Go 测试缓存、执行 Android `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件；同时已补齐 `docs/01-current-plan-summary.md` 中 `265b3bc` 与 `4a0079e` 两笔提交记录
- 本轮继续只读复查 Android 16 真机 `760435a8`：无障碍设置字符串仍包含云剪同步，`dumpsys accessibility` 中“云剪同步”同时出现在 Bound services 与 Enabled services，Crashed services 为空；`shizuku_server` 仍以 root 身份运行，`moe.shizuku.privileged.api` App 进程也在运行；通知权限与 Shizuku API 权限仍为 granted；剪贴板 AppOps 中 `READ_CLIPBOARD` 保持 allow / foreground 记录；当前 `SyncService` 未运行。本轮只读检查未覆盖安装、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮完成桌面端无托盘面板隔离 smoke：使用临时 exe、临时配置、临时下载目录、随机本地端口和 `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动 `cloud-clipboard-panel`，验证 `/api/status` 能返回临时配置和 Windows 平台能力，首页能加载“云剪同步桌面端”与发布版说明文案。第一次 smoke 因脚本误校验状态接口不返回的 `deviceId` 字段失败，调整为校验实际返回的 `config.deviceName`、`config.room` 和 `capabilities.platform` 后通过；两次临时进程和临时目录均已清理，随后已执行 `go clean -cache` 清理 Go 构建缓存。本轮未触碰真实桌面端配置、真实右键菜单、真实全局热键或系统剪贴板
- 本轮重新审计 `docs/01-current-plan-summary.md`、主源码目录和最近提交记录，未发现新的明确 TODO、未完成实现或需要立即修改的用户可见缺口；源码扫描仅命中非 Windows 平台“当前平台暂不支持文件剪贴板写入”的能力提示，属于跨平台能力说明，不影响 Windows 主链路。已将 `77f8709 补记安卓与桌面隔离复查结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；本轮未运行手机写操作、未安装手机、未删除手机端任何非调试数据或文件
- 用户补充 Android 真机联调规则：手机已有 root 环境，后续确有必要时可以覆盖安装安卓包；如果安装后无障碍授权丢失，可打开爱玩机工具箱，用搜索按钮搜索“无障碍”，进入“无障碍助手”自主恢复授权。后续不再把“不能安装手机”作为绝对限制，但仍需避免清理或删除手机端非调试数据和文件
- 本轮继续审计工作区、计划摘要、最近提交和主要源码目录：当前工作区干净，源码未发现新的明确 TODO、待实现或用户可见缺口；英文 README 的边界说明中剩余 `not implemented` 开发态表述已改为稳定的能力范围说明；同时已将 `2139f1a 记录安卓无障碍授权恢复方式` 补入 `docs/01-current-plan-summary.md` 最近提交列表。本轮未运行手机写操作、未安装手机、未删除手机端任何非调试数据或文件
- 本轮继续做轻量收口复查：工作区干净，主源码和用户文档未命中明确 TODO / 待实现 / 开发态边界残留；Android 16 真机 `760435a8` 只读状态显示云剪同步无障碍仍在启用列表，`shizuku_server` root 进程和 `moe.shizuku.privileged.api` App 进程仍在运行，剪贴板 AppOps 中 `READ_CLIPBOARD` 保持 allow / foreground 记录；`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient` 通过，验证后已执行 `go clean -testcache` 清理 Go 测试缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件；同时已将 `7874e22 收口英文说明并同步计划状态` 补入 `docs/01-current-plan-summary.md` 最近提交列表
- 本轮继续补齐计划摘要并验证 Android 打包链路：已将 `775dcd2 补记轻量收口复查结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；`android-sync-client` 下 `./gradlew.bat assembleDebug` 构建通过。本轮只构建不安装，未修改手机配置、未删除手机端任何非调试数据或文件；验证后已执行 `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录
- 本轮继续审计工作区、最近提交、计划摘要、用户文档和主源码目录：当前工作区干净，未发现新的明确 TODO、待实现或用户可见开发态残留；扫描仅命中 Android README 中的 Debug APK 输出位置说明，属于构建说明。已将 `7ed4407 补记安卓打包验证结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；`cloud-clip` 下 `go test ./lib` 通过，验证后已执行 `go clean -testcache` 清理 Go 测试缓存。本轮未修改或删除服务端真实运行态数据，未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续补齐计划摘要并验证桌面端核心链路：已将 `9985b14 补记服务端轻量验证结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer` 通过，覆盖配置归一化、应用层、一次性右键动作、同步客户端、面板包、热键包和传输包；验证后已执行 `go clean -testcache` 清理 Go 测试缓存。本轮未触碰真实桌面端配置、真实右键菜单、真实全局热键或系统剪贴板，也未安装手机或删除手机端任何非调试数据
- 本轮继续补齐计划摘要并验证前端构建链路：已将 `ed18cbc 补记桌面端核心验证结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；`client` 下 `npm run build` 通过，构建过程中仅出现既有 Webpack 体积提示、Vuetify / Sass 弃用提示和 npm mirror 配置提示。构建后已清理临时输出 `client/dist`，未修改服务端真实运行态数据，未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮已切到并推送 `develop-codex` 分支，继续在该分支做收口验证；已将 `57bd9b0 补记前端构建验证结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表，并把计划摘要中的当前开发分支从 `main` 修正为 `develop-codex`。本轮验证通过：`cloud-clip` 下 `go test ./lib`，`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer`，`android-sync-client` 下 `./gradlew.bat testDebugUnitTest`；Android 16 真机 `760435a8` 只读巡检显示无障碍服务在系统设置和服务枚举中均启用、Crashed services 为空、Shizuku root 进程与 App 进程在运行、通知权限与 Shizuku API 权限为 granted、剪贴板读取 AppOps 有 allow / foreground 记录、电池白名单包含本 App。验证后已清理 Go 测试缓存、执行 Android `gradlew clean`、停止 Gradle daemon，并删除 Android 构建目录；本轮未覆盖安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未触碰真实桌面端系统集成
- 本轮继续补测桌面端无托盘面板最小 smoke：临时编译 `cloud-clipboard-panel` 到系统临时目录，使用临时配置、临时下载目录、随机本地面板端口和 `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动；服务端地址故意指向不可用端口，验证 `/api/status` 仍可返回配置与 `retrying` 状态，首页能加载“云剪同步桌面端 / 面板 / 发送文本 / 拉取文本 / 下载文件 / 右下角提示”等关键文案，覆盖服务未连通时控制面板仍可打开和查看配置的场景。验证后已停止临时面板进程、删除临时 exe / 配置 / 下载目录，并执行 `go clean -cache -testcache` 清理 Go 构建与测试缓存；本轮未注册真实全局热键、未写入真实右键菜单、未触碰系统剪贴板或真实桌面端配置
- 本轮继续做收口审计：`develop-codex` 工作区开局干净且领先远端 2 个提交，Android 真机 `760435a8` 仍在线；进程检查未发现残留 `cloud-clipboard` / `cloud-clip` 临时进程。源码与文档扫描未发现新的主链路 TODO / FIXME / 未完成实现；命中的内容主要是文档中的后续协作说明、Cloudflare 部署脚本的后续步骤提示、非 Windows 平台本地文件选择/文件剪贴板写入能力提示，以及仓库自带 Android 服务端 App 模板 `data_extraction_rules.xml` 的默认 TODO，均不属于当前同步客户端收口阻塞项。已将 `af9d9e4` 与 `e896f47` 两笔本地里程碑补入 `docs/01-current-plan-summary.md` 最近提交列表；本轮未运行手机写操作、未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续做轻量复查：已将 `0946e75 补记收口审计结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient` 通过，覆盖配置、应用层、命令入口和同步客户端核心包；Android 真机 `760435a8` 只读巡检显示云剪同步无障碍服务仍在 `enabled_accessibility_services` 中，`shizuku_server` 以 root 身份运行，`moe.shizuku.privileged.api` 与云剪同步 App 进程均在运行，通知权限和 `moe.shizuku.manager.permission.API_V23` 仍为 granted。验证后已执行 `go clean -testcache` 清理桌面端 Go 测试缓存；本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未触碰真实桌面端右键菜单、热键或系统剪贴板
- 本轮继续做服务端与 Android 回归：`cloud-clip` 下 `go test ./lib` 通过；`android-sync-client` 下 `./gradlew.bat testDebugUnitTest` 通过，构建过程仅有既有 Gradle 9 弃用提示。Android 16 真机 `760435a8` 只读巡检显示云剪同步无障碍服务仍在 `enabled_accessibility_services` 中，`shizuku_server` 以 root 身份运行，`moe.shizuku.privileged.api` 与云剪同步 App 进程均在运行，通知权限和 `moe.shizuku.manager.permission.API_V23` 仍为 granted。验证后已执行 `go clean -testcache`、`gradlew clean` 和 `gradlew --stop` 清理 Go / Android 缓存与构建目录；本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未触碰真实桌面端右键菜单、热键或系统剪贴板
- 本轮继续做桌面端核心包与 Android 权限状态复查：已将 `ce890a7 补记服务端与安卓回归结果` 补入 `docs/01-current-plan-summary.md` 最近提交列表；`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer` 通过，覆盖配置、应用层、命令入口、同步客户端、面板包、热键包和传输包。Android 真机 `760435a8` 只读巡检显示剪贴板 AppOps 仍有 `READ_CLIPBOARD allow / foreground` 记录，电池白名单包含云剪同步，无障碍服务在 Bound services 与 Enabled services 中均可见且 Crashed services 为空。验证后已执行 `go clean -testcache` 清理桌面端 Go 测试缓存；本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未触碰真实桌面端右键菜单、热键或系统剪贴板
- 本轮继续做状态追齐判定：`develop-codex` 工作区开局干净且领先远端 6 个提交，Android 真机 `760435a8` 仍在线，进程检查未发现残留 `cloud-clipboard` / `cloud-clip` 临时进程；`docs/01-current-plan-summary.md` 仅缺最新 `9b3b441 补记桌面核心与安卓权限复查` 记录，已补入最近提交列表。本轮未发现需要立即新增代码修改的阻塞项，因此没有重复运行重测试；未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未触碰真实桌面端右键菜单、热键或系统剪贴板
- 本轮继续做状态追齐判定：`develop-codex` 工作区开局干净且领先远端 7 个提交，Android 真机 `760435a8` 仍在线，进程检查未发现残留 `cloud-clipboard` / `cloud-clip` 临时进程，`client/dist`、`android-sync-client/build` 和 `android-sync-client/app/build` 均不存在；`docs/01-current-plan-summary.md` 仅缺最新 `61e0f3c 补记最新状态追齐结果` 记录，已补入最近提交列表。本轮未发现需要立即新增代码修改的阻塞项，因此没有重复运行重测试；未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未触碰真实桌面端右键菜单、热键或系统剪贴板
- 本轮开始发版前真实联调：先用现有服务端配置启动临时编译的 `cloud-clip`，再启动临时编译的 `cloud-clipboard-panel`；过程中发现桌面端真实 `config.json` 带 UTF-8 BOM 时会报 `invalid character 'ï' looking for beginning of value` 并导致面板启动失败。已修复桌面端配置加载器，读取配置后先剥离 UTF-8 BOM，并补充 `TestLoadAcceptsUTF8BOMConfig` 单元测试；随后使用完全临时的服务端配置和带 BOM 的临时桌面端配置完成进程级验证：面板可启动、`/api/status` 可读、可通过面板向临时服务端发送 `codex-bom-e2e-*` 文本，且规范化保存后 BOM 被移除。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer`；验证后已停止临时服务端和面板进程，删除临时 exe / 配置 / 下载目录，并执行 `go clean -cache -testcache` 清理 Go 缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件；真实服务端数据未做清理或删除操作
- 本轮继续真实配置联调：先使用现有服务端配置和真实桌面端 `config.json` 启动临时编译的 `cloud-clip` 与 `cloud-clipboard-panel`，确认 BOM 修复后真实面板可启动，`/api/status` 返回 `trusted=true`、`connected=true`、设备名 `WingLin`。随后做最小真实文本闭环：通过桌面端面板 `/api/send-text` 发送 `codex-release-real-text-*` 调试文本，服务端旧 `/content/latest?json=true` 可读到同一内容；验证后仅通过 `/revoke/{id}` 删除这条 `codex-*` 调试文本，并确认同步 recent 中没有残留同文本。验证后已停止临时服务端和面板进程，删除临时 exe / 日志 / 配置目录，并在 Windows 短暂占用释放后完成 `go clean -cache -testcache`；本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未清理真实服务端非调试历史或上传文件
- 本轮继续一次性补齐自动化可覆盖的发版前联调：使用真实服务端配置启动临时编译的 `cloud-clip`，通过临时编译的桌面端面板一次性命令验证 `-shell-send` 和 `-shell-download-dir`，覆盖 Windows 右键菜单“复制到服务器 / 下载到此处”的核心动作；临时 `codex-release-file-*` 文件可上传到旧 `/content/latest`，同步 `recentPayloads` 中出现对应 payloadNotice，随后可下载到临时目录且内容一致，最后仅通过 `/revoke/{id}` 删除这条调试文件记录和关联上传文件。Android 真机 `760435a8` 只读复查显示云剪同步无障碍服务在 Bound / Enabled services 中均可见且 Crashed services 为空，剪贴板 AppOps 有 `READ_CLIPBOARD allow / foreground` 记录，通知权限和 Shizuku API 权限均为 granted。回归验证通过：`cloud-clip` 下 `go test ./lib`、`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer`、`android-sync-client` 下 `./gradlew.bat testDebugUnitTest`、`client` 下 `npm run build`；前端构建仅有既有 bundle 体积提示、Vuetify / Sass 弃用提示和 npm mirror 配置提示。验证后已停止所有临时进程，删除临时 exe / 下载目录 / 测试文件，清理 `client/dist`、Android build 目录、Gradle daemon 和 Go 缓存；本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据，也未清理真实服务端非调试历史或上传文件
- 本轮根据用户真机反馈修复 Windows 面板“发送输入文本”只更新网页历史、安卓端收不到的问题：根因是桌面端 `SendText` 仍走旧 `/text` 上传链路，只会进入网页历史和旧 WebSocket `receive`，不会产生新同步协议的 `clipboardPublish / clipboardSync`。现已让桌面端 App 保存当前同步 WebSocket client，并新增显式 `PublishText` 通道；面板/热键发送文本会先写入网页历史，再通过当前 trusted 同步连接广播给 Android / 其它桌面端，如同步连接未就绪会返回明确错误。动作页同时新增独立结果提示区，发送、拉取、下载等动作都在当前页显示成功或失败原因。真实联调中已用新编译桌面端发送 `codex-panel-sync-fixed-*`，服务端 `/api/sync/status` 能查到对应 `recentMessages`，桌面日志出现“文本已提交到同步服务”；随后仅清理本轮 `codex-*` 调试文本与同步消息，未删除手机端或服务端非调试数据。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer`；验证后会清理 Go 测试缓存。当前为方便用户继续联调，真实服务端与新桌面端临时进程暂时保持运行
- 用户确认 Windows 桌面端下一步体验方向：不再满足于“托盘 + 打开网页面板”，要做成普通客户端体验。计划采用内嵌客户端窗口承载现有本地面板，托盘左键单击直接打开/唤起窗口；同时替换当前不稳定托盘实现，修复托盘右键点过一次后后续无响应的问题。右下角 Tip 现有能力只是拖动位置和按钮确认，尚未实现拖文件直接发送；后续需补齐拖文件到 Tip 卡片直接发送，原“立即发送”按钮继续发送剪贴板待确认文件
- 本轮继续收 Windows 客户端主链路：桌面端 `OpenPanel()` 已从默认浏览器打开改为统一调用窗口启动器；Windows 下现在优先使用 Edge / Chrome 的 `--app=<panelURL>` 独立应用窗口承载本地面板，不再默认打开普通浏览器标签页。托盘端同时先移除了每 2 秒动态修改菜单 `Label/Disabled` 的刷新逻辑，只保留 tooltip 刷新，降低“托盘右键点过一次后后续无响应”的风险。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer`、`go build .\cmd\cloud-clipboard-desktop`、`go build .\cmd\cloud-clipboard-panel`；并用临时 `cloud-clipboard-panel` 配置对 `/api/open-panel` 做了 Windows smoke，确认会拉起 Edge 独立 app 窗口。验证后会清理临时面板进程、临时目录和 Go 缓存；本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮继续补齐 Windows 右下角 Tip 实用能力：现有 Tip 在“立即发送 / 打开面板 / 位置拖动记忆”基础上，新增拖文件到 Tip 卡片直接发送，复用本地面板 `/api/send-file` 上传链路，不改服务端协议，也不影响原有待确认文件按钮逻辑。拖入目录或空拖放会弹出明确提示“请拖入文件，暂不支持目录直接发送”；同时补充单测锁定 Tip 脚本中已包含 `FileDrop` 拖放上传逻辑和 `/api/send-file` 地址。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer`、`go build .\cmd\cloud-clipboard-desktop`、`go build .\cmd\cloud-clipboard-panel`；验证后将清理 Go 测试缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮继续补强 Windows 右键菜单诊断链路：`internal/shellmenu` 现已保留最近一次同步状态，区分“已关闭 / 已同步到资源管理器 / 注册失败”，并记录失败原因；控制面板“平台能力”摘要会直接展示右键菜单当前状态和最近错误，便于排查“开启后没生效”或“后续右键没反应”究竟是注册失败还是 Explorer 侧缓存问题。同时补充 Windows 单测覆盖右键命令拼接、缺少路径时报错，以及启用 / 关闭 / 失败三类状态分支。验证通过：`desktop-client-go` 下 `go test ./internal/shellmenu ./internal/app ./internal/panel`、`go build ./cmd/cloud-clipboard-panel`；验证后已执行 `go clean -cache -testcache` 清理 Go 构建与测试缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮只读复查 Android 16 真机 `760435a8` 时，`dumpsys accessibility` 的 Enabled services 已包含 `com.transparentlc.cloudclipboardsync/com.transparentlc.cloudclipboardsync.ClipboardAccessAccessibilityService`，`Crashed services` 为空，说明当前无障碍授权状态正常；本轮未覆盖安装、未改手机配置、未删除任何手机端非调试数据或文件
- 本轮继续推进桌面端托盘替换主线：由于公网拉取 `github.com/getlantern/systray` 仍超时，已把本地已有的 `getlantern/systray` 源码内置到 `desktop-client-go/third_party/getlantern-systray`，裁掉外部 `golog` 依赖并保留 Windows 左键事件回调；桌面端 `internal/tray` 已切到新接口，仍保留左键打开控制面板、右键菜单常用动作与悬停状态刷新。验证通过：`desktop-client-go` 下 `go mod tidy`、`go test ./internal/tray ./internal/app ./internal/panel`、`go build ./cmd/cloud-clipboard-desktop`、`go build ./cmd/cloud-clipboard-panel`，并额外使用临时配置启动托盘版 `cloud-clipboard-desktop.exe` 做 5 秒进程级 smoke，确认进程在观察窗口内保持存活；验证后已删除本轮构建产物 exe，并执行 `go clean -cache -testcache` 清理 Go 缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮继续只读复查 Android 真机 `760435a8` 的权限状态：`POST_NOTIFICATIONS` 与 `moe.shizuku.manager.permission.API_V23` 仍为 `granted=true`；本轮未覆盖安装、未改手机配置、未删除任何手机端非调试数据或文件
- 本轮继续补齐新托盘实现的真实链路 smoke：重新临时编译 `cloud-clip`、`cloud-clipboard-desktop` 和 `cloud-clipboard-panel`，用临时上传目录、历史文件、下载目录和桌面端配置启动“临时服务端 + 新托盘版桌面端”；联调中验证本地面板首页可访问，`/api/status` 返回的平台为 `windows`、设备名为 `CodexTrayE2E`，并且托盘版进程与服务端进程在观察窗口内持续存活，设备状态进入 `pending`。验证后已停止临时服务端和托盘进程，删除临时目录、根目录生成的 `.exe` 构建产物，并执行 `go clean -cache -testcache` 清理 Go 缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮继续补强托盘实现的可测性：已将托盘 tooltip 组装逻辑抽成 `buildTrayTooltip`，并新增 `desktop-client-go/internal/tray/tray_test.go`，覆盖状态归一化、tooltip 文案和文本预览截断，避免后续继续迭代托盘实现时把中文状态摘要改坏。验证时本机默认 `GOCACHE` 出现文件缺失与拒绝访问，因此改为使用临时 `GOCACHE` 重新执行 `go test ./internal/tray ./internal/app ./internal/panel` 和 `go build ./cmd/cloud-clipboard-desktop`，确认代码链路正常；验证结束后已删除临时 `GOCACHE` 目录。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮继续补强托盘动作的可测性：已将托盘事件循环中的“打开面板 / 发送剪贴板文本 / 发送待确认文件 / 拉取文本 / 拉取文件 / 下载文件 / 打开下载目录 / 清空缓存 / 发送文件”等动作拆成独立处理函数，并补充 `fakeBackend` 单测覆盖成功结果、失败返回和摘要格式，减少后续再调托盘实现时把动作链路一起带坏。验证继续使用临时 `GOCACHE` 重新执行 `go test ./internal/tray ./internal/app ./internal/panel` 与 `go build ./cmd/cloud-clipboard-desktop`，均已通过；验证后已执行 `go clean -cache -testcache` 清理 Go 缓存。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据；同时只读复查 Android 真机 `760435a8` 时，云剪同步无障碍服务仍在 Enabled services 中，Crashed services 为空
- 本轮继续收口安卓接收页和 Windows 右键菜单体验：Android `ReceivedPayloadActivity` 现在改为按本地缓存文件是否真实存在来判定“已缓存”状态，并据此启用打开 / 分享 / 另存按钮、切换下载 / 重新下载文案和图片预览，减少通知确认下载后按钮仍不可点的情况；Windows `internal/shellmenu` 在注册或移除右键菜单后会主动调用 `SHChangeNotify(SHCNE_ASSOCCHANGED)` 通知 Explorer 刷新 shell 关联，尽量降低资源管理器缓存导致的“刚开始能用、后面没反应”概率。验证通过：`desktop-client-go` 下 `go test ./internal/shellmenu ./internal/app ./internal/panel`，`android-sync-client` 下 `./gradlew.bat testDebugUnitTest`；验证后已执行 `go clean -cache -testcache`、`gradlew clean` 和 `gradlew --stop` 清理 Go / Android 缓存与构建目录，并删除根目录误留的临时 `config.json` 与 `cloud-clip/cloud-clip.exe` 构建残留。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件，也未删除服务端非调试数据
- 本轮继续做 Android 16 真机只读巡检：设备 `760435a8` 的 `enabled_accessibility_services` 仍包含 `com.transparentlc.cloudclipboardsync/.ClipboardAccessAccessibilityService`，`dumpsys accessibility` 中“云剪同步”同时出现在 Bound services 与 Enabled services，Crashed services 为空；`shizuku_server` 仍以 root 身份运行，`moe.shizuku.privileged.api` 与云剪同步 App 进程均在线；包信息显示 `versionName=0.1.0`、`lastUpdateTime=2026-06-08 02:46:08`，通知权限与 `moe.shizuku.manager.permission.API_V23` 仍为 granted。本轮仅做 USB / adb 只读检查，未覆盖安装、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续优化桌面端托盘状态提示：`desktop-client-go/internal/tray` 的 tooltip 组装逻辑已去掉“已连接 / 已连接”这类重复文案；trusted 且已连通时现在显示为“Cloud Clipboard / 已连接 / 设备名”，pending 但链路已建立时会明确显示“Cloud Clipboard / 待批准 / 链路已连 / 设备名”，便于直接区分“还没批准”与“链路根本没连上”。验证通过：`desktop-client-go` 下 `go test ./internal/tray ./internal/app ./internal/panel`；同时只读复查 Android 真机 `760435a8` 时，`shizuku_server`、`com.transparentlc.cloudclipboardsync` 和 `moe.shizuku.privileged.api` 进程均在线。本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续统一桌面端面板与托盘的状态表达：`desktop-client-go/internal/panel/static/index.html` 的概览页“连接状态”现在和托盘 tooltip 使用同一套摘要逻辑，pending 且链路已建时会显示“待批准 / 链路已连”，避免面板仍显示“待批准 / 已连接”这种不够直观的组合；同时“最近手动发送”会把超长文本或路径压成较短预览，减少概览卡片被一整段内容撑坏。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer ./internal/tray`，并额外用临时配置 + `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动 `cloud-clipboard-panel` 做最小 smoke，确认 `/api/status` 可访问、首页仍包含“连接状态”和“最近手动发送”等关键文案。验证后已执行 `go clean -cache -testcache` 清理 Go 缓存；Android 真机本轮仅只读复查无障碍授权字符串，未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续对齐桌面端按钮文案与实际行为：`desktop-client-go/internal/panel/static/index.html` 和 `desktop-client-go/README.md` 中原先还保留了“系统浏览器打开”一类旧说法，但当前 Windows 实现实际优先通过 Edge / Chrome `--app=` 拉起独立控制面板窗口。现已统一改成“打开控制面板窗口”及对应反馈文案，减少用户看到按钮后误以为会新开普通浏览器标签页。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer ./internal/tray`，并额外用临时配置 + `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动 `cloud-clipboard-panel` 做最小 smoke，确认首页静态内容已包含“打开控制面板窗口”等新文案；Android 真机本轮仅只读复查 `shizuku_server`、`com.transparentlc.cloudclipboardsync` 和 `moe.shizuku.privileged.api` 进程均在线，未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续做桌面端口径清理：进一步统一“控制面板窗口 / 独立面板窗口”两套残留叫法，确保面板首页按钮、动作摘要和 README 使用同一套用户可理解的名称；同时把 `docs/01-current-plan-summary.md` 中连续多轮追记留下的重复“当前待继续收口的重点”段落压回一版，避免后续继续接手时同一待办在计划文件里重复堆积。验证延续前一轮最小桌面 smoke 与真机只读巡检结论，本轮未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续优化桌面端概览页摘要语气：`desktop-client-go/internal/panel/static/index.html` 中原先还保留了“右键菜单：开”“成功提示：开”“文件确认：30s”“待启动 / 房间名”这类偏技术化、略生硬的摘要；现已统一改成更自然的中文展示，例如“右键菜单：已启用”“成功提示：已开启”“文件确认：30 秒”“尚未开始同步 / 房间名”，减少联调时需要自己翻译界面含义。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer ./internal/tray`，并额外用临时配置 + `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动 `cloud-clipboard-panel` 做最小 smoke，确认首页静态内容已包含“右键菜单：已启用”“成功提示：已开启”“文件确认：30 秒”等新文案；Android 真机本轮仅只读复查 `shizuku_server`、`com.transparentlc.cloudclipboardsync` 和 `moe.shizuku.privileged.api` 进程均在线，未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件
- 本轮继续把概览页摘要往“人话”方向收：`desktop-client-go/internal/panel/static/index.html` 的“缓存与目录”摘要之前还保留了 `24h` 这类英文单位缩写，现在已改成“缓存保留 24 小时 / 路径 / 最近清理时间”的中文口径，和前几轮已经收掉的“30 秒”“已启用”“已开启”保持一致。验证通过：`desktop-client-go` 下 `go test ./internal/config ./internal/app ./internal/desktopcmd ./internal/syncclient ./internal/panel ./internal/hotkey ./internal/transfer ./internal/tray`，并额外用临时配置 + `CLOUD_CLIPBOARD_DESKTOP_DISABLE_OS_INTEGRATIONS=1` 启动 `cloud-clipboard-panel` 做最小 smoke，确认首页静态内容已包含“缓存保留 24 小时”等新文案；Android 真机本轮仅只读复查 `shizuku_server`、`com.transparentlc.cloudclipboardsync` 和 `moe.shizuku.privileged.api` 进程均在线，未安装手机、未修改手机配置、未删除手机端任何非调试数据或文件

## 当前判断还在继续推进的部分

- Android 真机后台复制和受限权限场景体验，后续重点转向更多真实 App 场景覆盖，以及 Shizuku 独立剪贴板增强主通道可行性评估；当前 Shizuku 已先收口为可启动的诊断辅助模式，无障碍启用状态已增加 AccessibilityManager 兜底判断和贯穿权限页 / 运行页的来源详情展示
- Android 10 / 13 / 14+ 高版本系统限制下的提示文案与模式引导仍可继续细化，但当前基础提示链路已补齐
- 桌面端 Go 客户端进一步收口；无托盘面板版已完成本机闭环，托盘版在关闭本机安全软件拦截后已完成启动、临时服务端连接、设备批准、面板 trusted 状态、手动文本发送、文件发送和 `payloadNotice` 闭环，后续可继续围绕真实用户配置、托盘菜单和右键系统集成做完整联调
- 二期交互与配置增强

## 与 `docs/01` 对齐后的当前判断

- 一期架构主线已经具备：独立同步协议、设备批准、网页文本同步、Android 同步客户端、桌面端 Go 客户端基础链路都已落地
- Android 模拟器 / AVD 环境已为恢复 VMware 而清理，后续高版本剪贴板权限与后台限制问题优先走真机联调；本机已恢复最小 Android SDK，可完成 `android-sync-client` 本地构建，但不再启用安卓模拟器
- `docs/01` 中“一期核心模块”描述的大方向目前没有发现需要推翻的地方，更多是围绕收口项继续联调和修正
- 当前更像是“一期收尾 + 二期增强入口”阶段，而不是重新开一期主功能开发
- 当前 README / docs 说明已覆盖自动同步使用入口；Cloudflare Pages 前端剩余的统一发送面板分叉也已补齐。如果继续推进，应优先处理真机联调暴露的实际问题或二期交互增强，而不是重复补同一类总览文档
- 本轮再次比对 `docs/01` 待收口项后，已继续补齐服务端同步测试、Android 权限判断测试、桌面端通知与快捷键体验、无托盘面板隔离 smoke，以及 Android 构建验证；后续更适合围绕 Android 真机真实 App 后台复制、Shizuku 独立剪贴板主通道可行性、托盘版在不拦截环境下的完整联调继续推进

## 后续协作建议

- 后续继续以里程碑节奏推进
- 每次优先处理真实联调中暴露的问题
- 文档与代码同步更新，避免再次出现上下文混乱









