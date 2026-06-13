# Cloud Clipboard 当前计划摘要

## 文档目的

本文档用于整理当前 `cloud-clipboard` 仓库已经明确的计划内容，方便后续继续开发、联调、提测和阶段提交时快速对齐。

## 当前项目基线

- 工作目录：`E:\Workspace\VSCode\cloud-clipboard`
- 当前开发分支：`develop-codex`
- 当前远端：`origin = https://github.com/wangzhilin777/cloud-clipboard-go.git`
- 当前基座：`Jonnyan404/cloud-clipboard-go` 路线上的本地持续开发版本

## 一期范围

一期范围已经明确为：

- 三端纯文本自动同步
- Android 图片/文件通知确认接收
- 确认后下载到缓存并在 App 内处理

一期不做：

- 富文本同步
- 第三方输入框自动粘贴
- 图片自动写系统剪贴板
- Windows / Web 非文本接收

## 一期已锁定的架构原则

- 新同步协议与原有 `/push` 逻辑分离
- 继续保留原仓库已有 `/push`、房间列表、房间密码、文件上传下载能力
- 新同步能力走独立 HTTP / WebSocket 协议层
- 同步接入采用“房间密码或全局密码 + 设备配对批准”的双层校验模型
- 纯文本同步只走 `clipboardPublish` / `clipboardSync`
- 非文本通知走 `payloadNotice`

## 一期核心模块

### 服务端

- 独立同步协议层
- 设备注册、pending / trusted 状态持久化
- 配对批准
- `clipboardPublish` / `clipboardSync`
- `payloadNotice`
- `recentPayloads`

### 网页端

- 网页接入新同步协议
- 浏览器可用时监听本地文本剪贴板并自动上报
- 收到远端文本时自动写入系统剪贴板
- 权限不足时退化为显示 + 一键复制
- 网页端可发送文件/图片通知给 Android

### 桌面端

- 已从早期 AHK 方案迁移为 `desktop-client-go`
- 保留托盘常驻、配置面板、文本同步、文件通知发送
- 后续继续走 Go 跨平台桌面客户端方向

### Android 同步客户端

- 独立目录：`android-sync-client/`
- 负责文本同步、通知确认、缓存下载、接收页处理
- 保持与仓库自带 `android/` 服务器端 APK 分离

## 二期方向

二期讨论中已经基本收敛为以下方向：

- Android 权限检测、引导和后台剪贴板补偿策略增强
- Android 悬浮确认入口、位置/大小/显示时长等配置
- Windows / 桌面端更多配置项、缓存清理、重连策略、提示风格、快捷键增强
- Windows 桌面端普通客户端化：使用内嵌客户端窗口承载现有本地面板，托盘左键单击直接打开/唤起窗口，不再默认跳系统浏览器
- Windows 托盘稳定性收口：替换当前不稳定托盘实现，右键菜单改为稳定事件循环和静态菜单结构，避免点过一次后后续右键无响应
- Windows 右下角 Tip 增强：保留拖动位置记忆，并新增拖文件到 Tip 卡片直接发送能力；原“立即发送”按钮继续用于发送剪贴板待确认文件
- 文件/图片复制后的二次确认策略增强
- Android / Windows 缓存清理策略细化
- 服务端缓存与状态清理策略继续评估

## 最近已经完成的关键提交

- `8d9f2a2` `收口安卓首页沉浸式适配`
- `22769e0` `修复网页时间流同步记录合并`
- `b76525d` `增强同步协议参数校验兼容性`
- `91e9022` `修复房间内容链接编码兼容性`
- `ee8e565` `修复最新文件下载链接生成`
- `2f5085f` `修复桌面端默认房间最新内容拉取`
- `394affa` `修复安卓分享文件接收通知地址`
- `212a9e8` `修复安卓同步接口地址拼接兼容性`
- `5245ad6` `实现网页时间流同步文本即时刷新`
- `7a0e49f` `修复安卓接收下载地址解析兼容性`
- `efff613` `修复桌面端同步与下载地址兼容性`
- `60b4b39` `完善服务端下载文件名兼容性`
- `b4c426b` `修复安卓端同步地址解析兼容性`
- `380f3fd` `补齐桌面端跨平台基础运行能力`
- `aa175ef` `统一 Cloudflare 同步默认配置`
- `25ae58e` `修复同步 recent 历史数量配置生效`
- `04064c1` `统一 Cloudflare 历史配置默认值`
- `6ab8b41` `增强安卓无障碍后台剪贴板补传`
- `5bb27ba` `优化桌面端配置字段兼容性`
- `5e9292c` `清理前端构建旧服务端同步路径`
- `8ae0408` `移除 Cloudflare Pages 无用构建依赖`
- `c31529c` `更新说明文档并移除赞助内容`
- `97d6020` `补充新同步功能说明`
- `a58d8f5` `补充自动同步能力说明`
- `aec702b` `调整同步客户端说明位置`
- `1450486` `补充自动同步使用效果说明`
- `b2fc42a` `同步当前计划与完成记录`
- `8319dd2` `补充当前计划审计结论`
- `99e039b` `统一 Cloudflare 同步状态刷新逻辑`
- `161dff9` `同步 Cloudflare 部署文档说明`
- `007858c` `优化桌面端缓存清理摘要展示`
- `264e43f` `优化桌面端远端文件通知摘要文案`
- `e7d391a` `修复安卓剪贴板重复回传与同步回环`
- `fb844f5` `补齐同步历史删除并优化安卓后台监听`
- `dfa7ed7` `修复安卓后台复制快照兜底`
- `11e84b9` `接入安卓 Shizuku 授权检测`
- `5dbb1f5` `补充桌面端自测隔离开关`
- `46fc62d` `完善安卓 Shizuku 辅助模式诊断`
- `9634d62` `新增桌面端无托盘面板入口`
- `3bb7a79` `补记桌面端右键发送联调结果`
- `ec516eb` `补记桌面端右键下载联调结果`
- `f67dd23` `优化安卓运行权限发布版文案`
- `37821f9` `同步当前计划提交记录`
- `7ff3c00` `补记安卓真机权限巡检结果`
- `b6b7b5e` `优化安卓无障碍授权提示`
- `66ac6a9` `同步安卓权限收口提交记录`
- `ae90612` `清理安卓同步服务残留通知`
- `370d394` `同步安卓通知清理提交记录`
- `cc0e8fb` `补充桌面端右键动作自动化测试`
- `1d22ab2` `同步桌面端测试提交记录`
- `d951ed5` `补充安卓同步地址单元测试`
- `86ad26c` `补充服务端同步状态清理测试`
- `3c4ea45` `补充服务端同步广播隔离测试`
- `4fa10c1` `补记桌面端与安卓权限巡检结果`
- `8fd1786` `补充安卓无障碍启用判断测试`
- `ed62d4c` `优化桌面端通知模式默认兜底`
- `ccc3385` `优化桌面端快捷键配置校验`
- `b0e6fec` `优化桌面快捷键录入提示`
- `c7ec2a3` `补记桌面面板隔离验证结果`
- `4a3172c` `补记安卓构建验证结果`
- `57797de` `修正当前收口状态摘要`
- `03be0c2` `优化同步客户端发布版说明`
- `10ca0dc` `同步发布版说明提交记录`
- `d887ab7` `补记桌面托盘版隔离验证结果`
- `5199b6c` `补记桌面托盘版服务端联调结果`
- `a92e26a` `补记桌面托盘版文件发送联调结果`
- `3d6cc97` `优化安卓无障碍启用状态判断`
- `a0b691d` `优化安卓无障碍状态来源提示`
- `07f2658` `同步安卓运行页无障碍来源提示`
- `f7a2893` `优化桌面快捷键概览文案`
- `ab9f3ff` `补记桌面快捷键文案验证结果`
- `ed3b44b` `补记三端核心回归验证结果`
- `ce491bc` `优化安卓通知渠道中文名称`
- `cfef550` `补充安卓通知渠道说明`
- `c9cdee6` `新增安卓通知渠道设置入口`
- `2167486` `收口同步客户端发布版提示文案`
- `7a411b1` `补记无障碍状态并收口发布说明`
- `265b3bc` `同步安卓权限复查与计划状态`
- `6c2fa6b` `优化安卓运行状态提示文案`
- `4a0079e` `同步安卓提示文案提交记录`
- `55f81d1` `补记核心回归验证与计划状态`
- `77f8709` `补记安卓与桌面隔离复查结果`
- `2139f1a` `记录安卓无障碍授权恢复方式`
- `7874e22` `收口英文说明并同步计划状态`
- `775dcd2` `补记轻量收口复查结果`
- `7ed4407` `补记安卓打包验证结果`
- `9985b14` `补记服务端轻量验证结果`
- `ed18cbc` `补记桌面端核心验证结果`
- `57bd9b0` `补记前端构建验证结果`
- `af9d9e4` `补记分支与三端巡检结果`
- `e896f47` `补记桌面面板最小巡检结果`
- `0946e75` `补记收口审计结果`
- `c3cc475` `补记桌面与安卓轻量复查结果`
- `ce890a7` `补记服务端与安卓回归结果`
- `9b3b441` `补记桌面核心与安卓权限复查`
- `61e0f3c` `补记最新状态追齐结果`
- `9a6b361` `补记状态追齐判定结果`
- `568387e` `修复桌面端配置BOM兼容`
- `1591f5f` `补记真实配置文本联调结果`
- `f1c2b2f` `补齐桌面端右键菜单状态诊断`
- `fb39a9a` `切换桌面端托盘实现并完成最小联调`
- `6ce6c32` `补记托盘版真实联调结果`
- `ba91e97` `补齐托盘状态文案测试`
- `71851cc` `修复安卓接收页按钮状态并刷新右键菜单`
- `89b213e` `优化托盘状态提示文案`
- `dbe6448` `统一面板状态提示文案`
- `738497c` `统一面板打开文案`
- `31aa0c7` `统一控制面板窗口口径`
- `e996c08` `优化面板概览摘要文案`
- `b5d2bd0` `优化缓存摘要中文文案`
- `40fc5dc` `统一面板表单中文提示`
- `ce967dd` `优化面板错误提示文案`
- `c699493` `优化面板动作页交互体验`
- `e238fd6` `优化面板多行文本发送体验`
- `2ce5ae0` `补充面板动作结果快捷操作`
- `765b2bf` `记住面板分组与文本草稿`

## 当前待继续收口的重点

- Android 接收页本轮真机联调已进一步收口：同步服务“已启动但不重连”缺口已补上，手动再次点“启动同步”会立即触发重连；`ReceivedPayloadActivity` 的“下载到缓存”也已改为应用内直接下载，不再依赖旧的确认链路。真机实测中，最新文件通知 `cloud-clipboard-codex-send-2.txt` 已可成功下载到应用缓存，`open / share / save` 三个按钮会随本地缓存文件存在而立即点亮；旧历史条目 `startup2.3.bat` 重新回归时已不再报 `ConnectException /127.0.0.1:9501`，而是转为 `HTTP 404`，说明本轮补的 loopback 绝对地址改写已经生效，剩余只是历史文件在服务端已失效，不再是安卓端错误连到手机本机回环地址
- Android 真机后台复制与权限受限场景体验已经做过 Chrome 等场景验证，Shizuku 安装/运行/授权状态、无障碍来源详情、通知渠道中文收口、接收页缓存状态兜底等也已补齐；但结合后续真机证据与最新产品决策，Shizuku 不再继续评估为正式后台复制主通道，只保留系统授权与 AppOps 诊断辅助定位。后续 Android 侧重点转为覆盖更多真实 App 的复制与发送兜底场景，并把“不替换原键盘”的新方案做成可真机闭环
- Android 后台复制方案当前已不再只局限于“无障碍增强 / Shizuku 诊断”两条线；但结合本轮进一步确认的用户要求，之前那条 `ime` 专用输入法路线不再继续作为正式方案推进。当前仓库里与 `ClipboardInputMethodService` 相关的实现，只保留为已经验证过的一段探索记录；后续正式方向转为 `foreground + floating + 不替换原键盘的新方案`
- Android 二期方向现已按最新要求重写为更准确的产品定义：后续不再追求“用户切换到云剪同步输入助手后再发送文本”的专用键盘方案，而是优先做“保留原键盘、不切系统默认输入法”的文本发送兜底能力。悬浮窗模式的目标仍是“复制后的快速发送入口”，优先提供低侵入浮标、拖动位置记忆、快速发送和打开详细面板，不承诺绕过系统后台剪贴板限制；新方案则要满足“不替换原键盘、显式用户动作优先、继续复用现有文本发布主链路”三条硬约束
- Android 二期后续接入范围也随之重写，不要再按专用输入法思路往下走：一是继续扩展 `SettingsStore`、`RuntimeModeValidator`、`ClipboardModeSupportHelper`、权限页、运行页、快捷处理按钮、模式说明文案与状态徽章，让后续新方案与 `foreground / floating` 的就绪判断和引导口径统一；二是重点评估不替换原键盘前提下的实现形态，候选方向包括悬浮发送入口、分享入口、通知动作、输入框内显式唤起入口等；三是真机联调要覆盖 Chrome、QQ、微信、笔记类等真实输入场景，重点验证“不切默认输入法也能完成文本发送兜底”“不会误发普通打字”“不会影响现有文件接收悬浮卡片”“Windows / 服务端 recent 可正常收到文本”
- Android 后台复制路线在本轮已经再次明确收口，不再维持“`foreground / ime / floating` 三条都并列为正式主模式”的口径：结合 Android 10+ 尤其 Android 13/14/15/16 的后台剪贴板限制、当前真机 `logcat` 与 ROM 行为验证，以及用户对“不替换原键盘”的明确要求，后续正式发布口径应改为 `foreground / floating / 不替换原键盘的新方案` 三条主路径；其中 `foreground` 继续作为系统限制下最稳的默认基础模式，`floating` 负责复制后的快捷发送入口，新方案负责不替换原键盘前提下的显式文本发送兜底
- `accessibility` 本轮已明确降级为“辅助工具能力”而非正式同步主模式：保留系统无障碍启用检测、设置引导、来源诊断、必要时的辅助点击/辅助授权与特定场景补偿能力，但不再继续承诺它是稳定、可长期依赖的后台剪贴板自动同步通道。后续实现要同步收口：运行页和权限页的说明文案改成“辅助能力已就绪 / 未就绪”，不要再把它和正式主路径放在同一层级宣传；旧配置若仍落在 `clipboard_mode=accessibility`，需要评估自动迁移到 `foreground` 或在 UI 中明确提示这是兼容保留态
- `shizuku` 本轮已明确从产品主路径中移除，而不是继续做“也许再修一修就能成为后台模式”的承诺：既有真机证据已经确认它在当前实现和当前 ROM 下不能稳定绕过后台剪贴板限制，因此后续计划应按“删除同步模式入口、删除独立 mode 文案、删除 reader / probe / runtime validator 中与正式模式绑定的逻辑、清理 README 与说明中的主模式表述、保留必要兼容迁移处理”推进；如仍需保留少量调试能力，也应作为内部诊断开关而不是面向用户的常规模式
- Android 配置迁移策略也需要随这次收口一并落地，避免后续真机升级后出现历史模式残留导致的误判：如果用户本地仍保存 `clipboard_mode=accessibility` 或 `clipboard_mode=shizuku`，后续版本应优先迁移到 `foreground`；如果仍保存当前探索态 `clipboard_mode=ime`，也要在 UI 上明确提示该路线已不再作为正式方案，建议改用 `foreground / floating / 不替换原键盘的新方案`。这部分除了 `SettingsStore` 与运行时校验，还要覆盖主界面模式选择、状态摘要、权限页建议和必要的首次启动提示
- Android 二期剩余实现边界也在本轮重新收紧，后面不要再散回去：`ime` 这条专用输入助手路线不再继续做产品化打磨，后续只作为历史探索证据保留；真正要继续推进的是“保留原键盘、不切默认输入法”的新方案。`floating` 继续补独立的复制后快捷发送助手服务与位置/显示时长/交互收口；`accessibility` 只保留辅助引导与少量补偿链路；`shizuku` 按移除方向清理。后续若再参考 `E:\Workspace\Users\局域网同步-Android-0.2.46`，重点也应放在“不替换原键盘”的交互结构与悬浮窗方案，而不是继续追加专用输入法或新的 Shizuku 主通道探索
- Android 主界面这条全面屏 / 沉浸式问题目前已从“待定位”推进到“第二轮真机修复已完成”：第一轮虽然补上了 edge-to-edge 和 inset 分发，但用户现场继续指出视觉上仍像“状态栏黑条 + 下方悬空卡片”，不够一体化；本轮已继续把 `MainActivity`、`activity_main.xml` 和顶部背景 drawable 一起重构，头部改成独立 `homeHeaderShell` 沉浸背景层、运行时把 `statusBarColor` 设为透明，并新增只保留底部圆角的 `home_header_background.xml`，避免状态栏两侧露白和“假沉浸”观感
- Android 首页这轮又继续往“瘦长屏视觉收口”推进了一步，不再沿用前一版那种“强行把连接卡片高度拉满”的做法：`contentContainer` 与 `connectionSection` 已改回 `wrap_content` 主导，`MainActivity.syncVisibleSectionViewportHeight()` 也取消了对连接页整卡强制设高，改成只保留非连接页的视口填充；连接页则通过头图和首卡的重叠量、卡片顶部内边距、底部轻量 spacer 来调层次，避免再次出现“卡片被拉爆、底部按钮被挤出屏幕”或“停止同步下面留一大截空白”的问题
- 这轮 Android 首页长屏修正已经做过真机复看，而不是只在本地 XML 上猜：新版 debug 包已重新 `assembleDebug`、`adb install -r` 并冷启动到 `MainActivity`，随后多次使用 `adb exec-out screencap -p` 抓取首页，对比确认本轮收掉了两类问题：一是连接卡片不再被强制拉高到把底部动作区裁掉；二是头图和首卡之间的断层感较前一版继续减轻。当前状态仍保留少量可继续细抠的比例空间，但已经从“布局策略错误导致长屏失衡”收敛到“常规视觉打磨”级别，不再是阻塞性 bug
- Android 主界面这轮又继续补完了一层跨页统一性，而不是只顾首页：`contentContainer` 左右公共外边距已移除，四个 tab 现在统一按全宽内容外轮廓布局，避免出现“连接页和底部导航宽度吃满，但运行 / 权限 / 接收页还额外缩进去一圈”的不一致感；同时首页头图也已改成不再额外收底部圆角，只让白色内容卡片承担主圆角层次，进一步减少蓝白交界处的视觉打架
- 非首页三个 tab 的顶部沉浸也已补齐到和首页同一完成度：`runtimeSection / permissionSection / receiveSection` 先前还各自保留 `layout_marginTop`，因此真机上会在状态栏下方残留一条“差一点没铺满”的白缝；现在这层外边距已去掉，并把系统状态栏 inset 改为加到三个 section 自己的内容顶部 padding 中。真机再次切到“运行 / 接收”页抓图后，顶部内容卡片已贴到状态栏下方，不再保留那条肉眼可见的过渡缝
- 本轮安卓真机继续联调后，已确认“悬浮确认预览卡片本身点不了”暂未复现：`预览悬浮确认` 可正常弹出，`详情`、`稍后` 等按钮都能触发界面变化，因此这条更像不是当前主因；同时也确认用户这次现场里本机 `http://127.0.0.1:9501` 一度未监听，且手机当前系统 `enabled_accessibility_services` / `dumpsys accessibility` 中并没有真正启用云剪同步无障碍服务，导致 `accessibility` 模式下后台复制未推送时，不能直接归因为悬浮窗不可点或客户端重复回环。后续真机后台复制联调应先优先确认三件事：本机 9501 服务端已启动、手机同步服务确实在运行、系统无障碍列表里已真实启用云剪同步
- 桌面端 Go 客户端继续替换早期遗留交互问题；本地已补充自动化自测隔离开关，并新增无托盘面板版用于系统策略拦截托盘组件时继续使用控制面板、同步、文件发送和拉取能力；在关闭本机安全软件拦截后，托盘版已可用临时配置启动、访问本地控制面板状态接口，并完成临时服务端 pending / approve / trusted / 手动文本发送 / 文件发送与 payloadNotice 闭环；控制面板快捷键概览已改为完整中文动作名，并已通过无托盘面板隔离 smoke 验证新文案可加载；右键菜单最近一次同步结果与失败原因展示、Explorer shell 刷新通知、托盘 tooltip / 状态文案单测、托盘核心动作可测化都已补齐；最近几轮又把托盘 tooltip 和面板概览页的连接状态统一成同一套中文摘要，避免出现“已连接 / 已连接”这类重复提示，并把“待批准但链路已连”明确显示出来；概览页“最近手动发送”也会自动截断过长文本，减少一长串内容把总览挤坏；控制面板首页按钮与反馈文案也已统一成“打开控制面板窗口”，避免界面提示与当前优先拉起独立窗口的实际行为不一致；最近几轮又把概览页里的“右键菜单：开 / 成功提示：开 / 文件确认：30s / 待启动 / 24h”这类偏技术化摘要、缓存保留时长、表单英文占位统一成更自然的中文表达；前几轮已补上动作页按钮执行中态、空文本拦截、错误提示中文化、多行文本输入、`Ctrl+Enter` 快捷发送，以及动作结果区“复制结果 / 清空结果”；本轮继续补上控制面板本地状态记忆，会记住上次停留的分组和未发送的手动文本草稿，减少切页或重开窗口后还得重新找分组、重新粘贴长文本的问题；同时已在当前托盘实现的 Windows 右键弹出逻辑里补上标准 `WM_NULL` 收尾消息，降低“第一次右键有反应、之后菜单不再弹出”的经典托盘失焦问题。下一步继续围绕真实用户环境验证新托盘实现的右键稳定性，并继续推进普通 Windows 客户端体验
- 本轮继续对真实 `http://127.0.0.1:9501` 服务做桌面端闭环自动验证，且不再只停留在临时服务端或静态代码判断：复用当前已 trusted 的桌面设备身份，使用隔离 `cloud-clipboard-panel` 进程直连真实 9501；`POST /api/send-text` 成功发送唯一测试文本 `codex-auto-send-text-20260609-162507`，随后在 `bootstrap.recentMessages` 中确认该文本已落到最新记录；接着再通过 `POST /api/send-file` 发送临时文件 `codex-auto-send-file-20260609-1626.txt`，`bootstrap.recentPayloads` 中同步出现新的 `payloadNotice` 记录，说明当前桌面端“手动发送文本”和“手动发送文件”两条真实服务主链路都已打通。后续如果用户现场仍出现“点发送没效果”，优先排查的就不再是服务端主发送链路，而应转向 UI 触发、托盘事件、右键菜单壳层交互或本地运行态差异
- 本轮继续把桌面端真实 UI 层也跑通：按用户要求改用 Chrome 打开隔离控制面板页面 `http://127.0.0.1:19534/`，切到“动作”分组，在页面里真实输入测试文本 `codex-chrome-ui-send-20260609-164108` 并点击“发送输入文本”；Chrome 页面动作结果区返回“文本发送成功：codex-chrome-ui-send-20260609-164108”，随后在真实 `9501` 的 `bootstrap.recentMessages` 中确认新增同名文本，同时本地 `api/status` 里的 `lastActionType=manual-text`、`lastActionDetail=codex-chrome-ui-send-20260609-164108` 也同步刷新。说明当前不仅面板接口能调通，桌面控制面板网页上的真实发送按钮也已验证生效；如果用户现场仍出现“按钮按了没反应”，更应继续聚焦具体运行态差异、浏览器宿主窗口、托盘交互或现场配置，而不是优先怀疑发送主链路本身
- 本轮继续收桌面端“普通客户端窗口”这条链路，并按当前协作要求把 Windows 面板宿主优先级从 Edge 调整为 Chrome：`panel_window_windows.go` 现在会先查找本机 Chrome，再回退到 Edge，避免电脑端浏览器联调又偏回 Edge。已补充最小单测锁定该顺序，并完成定向验证：`desktop-client-go` 下 `go test ./internal/app ./internal/tray ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-panel` 通过；随后再用临时 `cloud-clipboard-panel` 配置启动隔离面板 `127.0.0.1:19541`，调用 `POST /api/open-panel` 返回 `{"ok":true}`，同一隔离面板状态里 `lastActionType=open-panel`、`lastActionDetail=http://127.0.0.1:19541/` 也同步刷新，说明“打开控制面板窗口”这条本地动作链路仍然有效，且后续浏览器宿主将优先走 Chrome
- 本轮继续补齐桌面端托盘 / 右键菜单自动回归，并把验证环境切到仓库内独立 `GOCACHE` / `GOTMPDIR`，避免再受系统全局 `go-build` 占用影响：`desktop-client-go` 下 `go test ./internal/tray ./internal/shellmenu ./internal/app ./internal/panel` 通过；随后使用临时 `cloud-clipboard-desktop-codex.exe` 和测试配置启动隔离桌面端实例，进程日志明确输出“已同步 Windows 右键菜单”，同时本地 `http://127.0.0.1:19543/api/status` 返回 `shellMenuEnabled=true`、`capabilities.shellMenuStatus=Windows 右键菜单已同步到资源管理器`。这说明当前右键菜单状态诊断、注册动作和控制面板状态摘要三者已经自动联调对齐；如果用户现场后续仍反馈“右键菜单开了没效果”，优先排查的应转向 Explorer 刷新时机、现场权限或宿主系统差异，而不再是当前代码没有完成注册
- 本轮继续把 Windows 右键菜单“启用 / 关闭”两段都补成真实启停回归，而不是只看内部状态：先用 `shellMenuEnabled=true` 的隔离桌面端实例直连真实 `127.0.0.1:9501`，随后直接用 `reg query` 验证 `HKCU\Software\Classes\*\shell\CloudClipboard`、`HKCU\Software\Classes\Directory\shell\CloudClipboard`、`HKCU\Software\Classes\Directory\Background\shell\CloudClipboard` 三条父键都已写入，且 `api/status` 同步返回 `shellMenuStatus=Windows 右键菜单已同步到资源管理器`；再切到 `shellMenuEnabled=false` 的隔离实例回归关闭链路，`api/status` 返回 `shellMenuStatus=Windows 右键菜单已关闭`。最后一轮想再补一条注册表“已删除”只读确认时，PowerShell 查询超时，因此本轮不把“已肉眼确认三条键全部删除”说满；但从启用实例已真实写入注册表、关闭实例状态已明确回到“已关闭”来看，当前右键菜单启停主链路已经跑通
- 本轮继续按真机当前现场状态回归 Android 后台复制：手机当前配置确认仍是 `clipboard_mode=shizuku`，而系统 `enabled_accessibility_services` 里并没有启用云剪同步无障碍服务。基于这个真实运行态，我用系统命令直接改写手机剪贴板并同时观察服务端 recent 与 `logcat`，结果服务端 `recentMessages` 没有新增，`logcat` 连续出现 `ClipboardService: Denying clipboard access to com.transparentlc.cloudclipboardsync, application is not in focus`。这说明当前所谓 `shizuku` 模式并没有接入独立的后台剪贴板读取主通道，真机上仍然受系统前后台剪贴板限制；因此本轮已先把它的代码行为和文案一起收口成“诊断模式”：`SyncService` 在 `shizuku` 模式下不再额外轮询系统剪贴板，避免后台持续刷拒绝日志和误导用户“看起来像在工作”；运行页和诊断文案也改为明确说明 Shizuku 当前只用于系统授权与 AppOps 诊断，不承诺后台复制回传。后续若要真正兑现 Shizuku 后台复制能力，需要另行实现独立于系统 `ClipboardManager` 的 Shizuku 剪贴板读取链路；在此之前，后台复制主推荐模式仍应是无障碍增强
- 本轮已继续完成上面这条 Shizuku 收口后的真机回归闭环：保持手机当前 `clipboard_mode=shizuku`、同步前台服务运行、Chrome 处于前台后，清空 `logcat` 再用 `adb shell cmd clipboard set text codex-shizuku-regression-20260609-165635` 写入一条全新文本；结果真实服务端 `bootstrap.recentMessages` 仍没有新增该文本，说明后台复制能力本身依旧没有被 Shizuku 模式兜底打通；但这次 `logcat` 已不再出现上一轮那种持续刷新的 `ClipboardService Denying clipboard access...` 拒绝日志，只剩前台服务通知和系统常规日志，代码侧全文检索也未再发现 `shizuku` 模式下额外触发后台读剪贴板的其它路径。可以确认这轮收口修复已经达到预期目标：把“后台自轮询制造噪音”压住了；当前残留问题是 Android 系统限制下 `shizuku` 诊断模式不会自动回传后台复制，而不是同步服务还在后台高频偷读剪贴板
- 本轮继续沿这条主线推进，但没有把“代码编过”冒充成“真机已验证”：已新增 `android-sync-client/app/src/main/java/com/transparentlc/cloudclipboardsync/sync/ShizukuClipboardReader.kt` 的一轮实现补强，当前会通过 Shizuku binder 直接尝试调用系统 `IClipboard.getPrimaryClip(...)`，并把命中的方法签名、实参数组和异常摘要一并写回诊断结果，避免真机回来后还只能看到笼统的“读取失败”；同时把之前一律传 `0` 的整型参数修正为优先带上当前 `userId` / `deviceId`，减少因为参数不对导致的假失败。本轮本地 `android-sync-client` 下 `./gradlew.bat assembleDebug` 已重新通过，`desktop-client-go` 下 `go test ./internal/app ./internal/tray ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-panel` 也继续通过；但 `adb kill-server` / `adb start-server` 后 `adb devices -l` 仍然枚举不到真机，所以这轮还不能宣称 Shizuku 独立剪贴板主通道已经打通，当前未闭环点依旧明确收缩为“等待设备重新上线后安装现有 debug 包并做真机回归”
- 本轮继续把 Windows 端“按钮没效果 / 右键后来没反应”与真实发送主链路拆开验证，而且尽量避免再碰现场真实配置：先确认本机真实 `http://127.0.0.1:9501` 仍在监听，`/api/sync/bootstrap` 返回 200；随后发现之前残留了一条临时 `cloud-clipboard-panel` 进程，占用 `127.0.0.1:19534`，而真实默认面板端口 `127.0.0.1:9530` 当时没有监听。清掉这条残留进程后，再用“真实桌面端配置的克隆版”启动隔离面板实例，仅把本地面板端口改到 `127.0.0.1:19536`、通知改为 `off`，其余继续复用当前已 trusted 的真实桌面设备身份；结果 `POST /api/send-text` 成功把测试文本写入真实 `9501` 的 `recentMessages`，`POST /api/send-file` 也成功把临时文件写入 `recentPayloads`。这说明当前 Windows 端“手动发送文本 / 文件”的真实服务主链路仍然是通的，用户现场若再次出现“点发送没效果”，优先排查方向应继续收缩到“当前运行实例混乱、残留面板进程、托盘事件或壳层交互状态异常”，而不是先怀疑发送主链路本身
- 本轮继续对 Windows 托盘“第一次右键有反应，后面又没反应”的壳层兼容再补一刀：当前内置 `getlantern/systray` Windows 事件分发原先只把 `WM_RBUTTONUP` 当成“弹出菜单”，这对部分系统/托盘运行态过窄，容易出现右键动作没有再次命中菜单分支。现在已在 `desktop-client-go/third_party/getlantern-systray/systray_windows.go` 补成统一的 `trayMouseAction(...)` 映射，同时接住 `WM_RBUTTONUP` 和 `WM_CONTEXTMENU` 作为右键菜单弹出事件，左键仍走现有打开面板回调；并新增最小单测锁定这三类消息映射。定向验证里，`desktop-client-go` 下 `go test ./internal/tray ./internal/app ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-desktop ./cmd/cloud-clipboard-panel` 均继续通过。由于三方托盘子模块自身缺少本地 `go.sum` 且当前网络拉取 `golang.org/x/sys@v0.1.0` 超时，本轮没有把“子模块目录单独 `go test`”伪装成已通过；但主模块下依赖这层托盘实现的相关回归和构建已经继续通过
- 本轮继续把 Windows 侧自动可测部分再往“完整客户端”压一层，而不是只停在无托盘面板：使用 `cloud-clipboard-desktop -headless` 启动隔离桌面客户端实例，继续复用当前已 trusted 的真实桌面设备身份，仅把面板端口切到 `127.0.0.1:19538` / `127.0.0.1:19539` 并关闭通知与 OS 集成。两轮回归里，`api/status` 启动后都立即返回 `trusted=true`、`connected=true`；`POST /api/send-text` 可把测试文本写入真实 `9501` 的 `recentMessages`，`POST /api/send-file` 可把测试文件写入 `recentPayloads`；其中后一轮还继续调用了 `POST /api/open-panel`，并在系统进程级确认 Chrome 实际拉起了 `--app=http://127.0.0.1:19539/` 的独立窗口，同时 `lastActionType=open-panel`、`lastActionDetail=http://127.0.0.1:19539/` 同步刷新。说明当前不只是“独立面板能发”，而是完整桌面客户端主进程在 headless 运行态下也已经验证通过“连接、文本发送、文件发送、打开 Chrome 面板窗口”四条主链路
- 本轮继续对 Android 收口代码做只读复查时，又抓到一个真实冲突并已直接修掉：`SyncService` 里的 `clipboardPollRunnable` 先前仍把 `CLIPBOARD_MODE_SHIZUKU` 也纳入了 1.5 秒轮询条件，这和前面已经收口成“Shizuku 模式不再额外轮询系统剪贴板，只保留显式诊断读取分支”的目标不一致。现在已把后台定时轮询条件收窄为仅 `foreground` 模式；`shizuku` 模式仍保留 `publishLocalClipboardIfNeeded(...)` 中的显式读取分支，但不再被后台轮询持续驱动。修复后已重新通过 `android-sync-client` 下 `./gradlew.bat testDebugUnitTest` 与 `./gradlew.bat assembleDebug`，并完成 `gradlew --stop` 与 `gradlew clean` 收尾。由于手机 `adb` 仍未回连，这轮还不能把它冒充成已做完真机回归，但至少本地代码路径和此前的产品收口口径已经重新对齐
- 本轮自动验证也进一步收敛出当前 Android 真机唯一硬阻塞：`adb devices -l` 现在仍然返回空列表，所以无法把已经准备好的多身份 Shizuku 远程探针版本重新安装并做真机回归。也就是说，代码、构建和 Windows 侧可自动验证部分都已经继续往前推进；当前真正还没闭环的点，仍然只有“等待手机重新通过 USB / adb 回连后，执行最新版 Shizuku 后台复制真机联调”
- 服务端、网页端、桌面端、安卓端说明文档继续同步更新
- README 与 `docs/03-sync-usage-and-effects.md` 已补充自动同步使用入口，后续功能变化仍需同步维护
- Cloudflare Pages 前端已补齐同步设备页刷新逻辑、诊断文案和统一发送面板上传链路；后续如主站继续调整发送入口或同步页交互，需要同步维护，避免再次分叉
- 当前工作区最近一次审计时保持干净；`server-node/`、`cloud-clip/uploads/`、`cloud-clip/config.json`、`cloud-clip/history.json` 属于 `.gitignore` 管理的本地运行态或历史兼容目录，后续清理前需避免误删真实联调数据
- 本轮已继续自动收口 Android 无障碍后台复制主链路，并把一个真实误判问题从“感觉像没推送”收敛成已验证修复：在 Chrome 地址栏复制场景里，无障碍快照原先会把聚焦输入框的占位提示“在 Google 中搜索或输入网址”错误地当成最高优先级文本发送到服务端，导致 `recentMessages` 收到的是提示词而不是真实链接。现在 `ClipboardAccessAccessibilityService` 已新增快照筛选器：对可编辑输入框里的占位词做过滤，遇到 URL / 路径 / 带查询参数的结构化文本时优先选它；并补充 `AccessibilitySnapshotSelectorTest` 锁定“Chrome 占位词 + 实际 URL”场景。真机回归已重新跑通：重新安装 debug 包、恢复同步前台服务、用 Chrome 打开 `https://example.com/?q=codex-accessibility-url-20260610-1`，点击地址栏联想行里的“复制链接”按钮后，真实 `9501` 的 `bootstrap.recentMessages` 已新增来源为 `android-live-device` 的 `example.com/?q=codex-accessibility-url-20260610-1`，不再误发占位提示词

## 执行原则

- 每个里程碑形成一个可验证闭环
- 每个里程碑完成后立即提交中文 commit
- 优先真实可用，避免假装联调成功
- 不破坏原有 `/push` 和旧网页文件上传下载逻辑
- Android 真机联调允许在确有必要时覆盖安装；若安装导致无障碍授权丢失，可通过爱玩机工具箱搜索“无障碍”，进入“无障碍助手”自主恢复授权

## 本轮新增进展补记

- 本轮已把 Android “不替换原键盘”的新方向从计划口径继续落实到主干代码，而不是只停在文档纠偏：`ShareReceiveActivity` 已新增 `PROCESS_TEXT` 接入，系统“选中文本 -> 发送到云剪同步”和系统分享文本现在都会复用 `ManualClipboardSender.sendText(...) -> SyncService.sendManualText(...)` 主链路发送文本；也就是说，新方案当前已明确落地为“系统分享 / 选中文本处理 / 主界面手动发送”三条显式发送入口，而不是继续推进专用输入法产品化
- 本轮同时完成了 Android 运行页和模式校验的语义收口：`RuntimeModeValidator` 不再把当前 `clipboard_mode=ime` 解释成“必须去输入法设置完成授权”，而是改成“显示显式发送说明”；`MainActivity` 中与 `CLIPBOARD_MODE_IME` 相关的快捷处理、就绪度摘要、权限建议、后台复制诊断和实现说明，也已统一改成“显式发送模式（不替换原键盘）”口径。当前仍暂时复用历史常量值 `ime` 做兼容存量配置，但界面和行为都已不再要求用户切换默认输入法
- 本轮也同步确认了一条重要边界：仓库里的 `ClipboardInputMethodService` 继续保留为历史探索证据和兼容排查素材，但后续正式路线不再围绕它打磨。后续 Android 正式主路径继续收敛为 `foreground / floating / 不替换原键盘的新方案`；其中当前已真正可落地、且已接入主发送链路的新方案入口，就是“系统分享 / 选中文本处理 / 主界面手动发送”
- 本轮本地验证已重新闭环，而且这次把“代码正确”和“Windows 构建缓存噪音”分开压实了：`android-sync-client` 下先执行 `./gradlew.bat clean` 清理增量目录，再串行执行 `./gradlew.bat testDebugUnitTest --no-daemon` 与 `./gradlew.bat assembleDebug --no-daemon`，两者均已重新通过。过程中先暴露出 `ShareReceiveActivity` 里 `ACTION_PROCESS_TEXT` 的 Kotlin 空安全写法错误，已当场修正；随后 `assembleDebug` 首次失败则是 Windows 本地 `app/build/intermediates` 资源增量目录异常，清理 `android-sync-client/app/build` 与 `android-sync-client/build` 后重跑即恢复通过，说明这轮剩余问题属于本机构建缓存，不是业务代码阻塞
- 这轮自动验证之后，Android “不替换原键盘”的三条显式发送入口已经全部补成真机闭环：系统分享文本已在真实服务端 `recentMessages` 中命中 `codex-share-route-20260612-7`，主界面/手动发送主链路已命中 `codex-debug-manual-send-20260612-3`，`PROCESS_TEXT` 入口也已在单独 `force-stop -> am start` 场景下命中 `codex-process-text-20260612-8`。因此当前 Android 下一步重点，不再是证明这三条入口能不能通，而是继续把旧探索态 `ClipboardInputMethodService` 从正式产品叙事、模式入口和迁移策略里彻底淡出
- 本轮又继续往前收了一层 Android 正式入口，而不是只停在文案层：主界面单选模式现在已经不再保留单独的“显式发送模式”入口，正式模式入口重新收敛为 `foreground / floating` 两项；显式发送则改为所有正式模式下都可直接使用的通用文本兜底能力。历史 `clipboard_mode=ime` 现在也会和 `accessibility / shizuku` 一样，在配置读取时自动迁回 `foreground`，避免用户升级后还停在旧探索路线
- 本轮继续把旧专用输入助手从正式 UI 存在感上再压了一层：`MainActivity` 里那些只在 `clipboard_mode=ime` 下才会走到的运行页说明、后台复制就绪度、诊断下一步、权限建议和实现摘要分支已进一步移除，正式 UI 不再主动把它当成一个当前可选模式来讲。现在保留的只有“历史输入助手状态”这种兼容诊断信息，以及独立 `ClipboardInputMethodService` 自身那套探索残留实现，方便后续排查历史现场
- 这轮还补了一次新的真机 UI 回归，把“入口真的收干净了”从代码判断压成了现场证据：新版 debug 包覆盖安装后，在 Redmi K50 Ultra 真机上先把 `clipboard_mode=ime` 人工写入配置，再通过 `force-stop -> 冷启动 MainActivity` 触发迁移，最终 `shared_prefs` 已确认自动写回 `clipboard_mode=foreground`；随后切到“运行”页截图，现场只剩 `前台服务模式` 和 `悬浮窗模式` 两个正式单选项，同时仍保留“发送当前剪贴板文本”按钮，说明现在已达到“旧 `ime` 不再作为正式模式入口出现，但显式发送兜底能力仍保留”的目标
- 本轮继续把这条“不替换原键盘”的路线从“功能能用”收成“用户能看懂的正式入口”：`activity_main.xml` 的运行页已经新增独立“原键盘发送兜底”卡片，不再只留一个孤立按钮；卡片会直接展示当前剪贴板预览、当前模式下为何仍建议保留这条兜底、以及三条正式入口说明，也就是“本页发送当前剪贴板文本 / 系统分享到云剪同步 / 选中文本后走系统处理菜单”
- 对应主界面逻辑也已同步重构，而不是只改 XML：`MainActivity` 已移除旧 `runtimeImeSendButton` 这类历史命名，改为显式发送卡片专用的预览、摘要、主按钮和说明按钮；按钮状态现在会跟随当前剪贴板是否为空自动刷新，点击说明时也不再把用户往输入法方向带，而是只提示这三条“不替换原键盘”的正式发送路径
- 这轮代码收口后已再次完成 Android 本地构建与真机覆盖安装验证：`android-sync-client` 下 `./gradlew.bat testDebugUnitTest --no-daemon` 与 `./gradlew.bat assembleDebug --no-daemon` 已通过，随后新版 `app-debug.apk` 已成功 `adb install -r` 覆盖安装到在线真机 `4e9e24e7 / 22081212C`，并重新拉起 `MainActivity`。当前还没补完的是这张新卡片在真机运行页里的最终人工视觉复看证据，因为本轮最后一次系统截图抓到的是桌面而不是应用前台，所以后续继续联调时要优先补这一步，而不是再回退去改旧输入法路线
- 上面这条真机视觉尾巴本轮也已经补完：经过再次真机切到“运行”页并抓取前台截图，当前首屏已能直接看到完整的“原键盘发送兜底”卡片、剪贴板预览、摘要说明、“发送当前剪贴板文本”主按钮和“查看分享 / 选中文本发送说明”次按钮，不再像上一版那样被两大块模式说明压到首屏下方。也就是说，这条新入口现在不仅代码和构建闭环了，真机首屏可见性也已压实
- 本轮还顺手把正式运行时里残留的 `ime` 专门分支再收了一层，而不是只停在 UI 命名替换：`MainActivity.handleRuntimeModeQuickAction()` 里原先仍保留的 `CLIPBOARD_MODE_IME` 特判，以及 `RuntimeModeValidator` 中原先仍把 `ime` 当作一条独立运行时校验路径的分支都已移除。现在历史 `clipboard_mode=ime` 仍会在 `SettingsStore` 中被兼容识别并迁回 `foreground`，`ClipboardInputMethodService` 也仍保留供历史排查，但正式运行时主路径已经不再为这条废弃路线继续分叉
- 本轮又继续把正式显式发送和历史输入助手在资源层彻底拆开，而不是只靠注释提醒：运行页说明 Toast 与 `ManualClipboardSender` / `FloatingClipboardOverlayService` 当前使用的“空剪贴板 / 已请求发送 / 预览占位”文案，已经全部改到新的正式资源名上；剩余 `clipboard_ime_*` 资源现在只继续服务于 `ClipboardInputMethodService` 和 `view_clipboard_ime.xml` 这套历史探索实现。后续如果再看到 `ime` 命名残留，应默认先判断它是不是历史输入助手本体，而不是把它误记成当前正式显式发送链路
- 本轮又顺手把“历史模式迁移提示”也继续收口成用户能直接理解的口径，而不是继续暴露底层枚举值：`MainActivity` 里迁移提示现在会把 `accessibility / shizuku / ime` 分别翻译成“无障碍辅助（历史兼容）/ Shizuku 诊断（历史兼容）/ 旧专用输入助手（探索残留）”，同时权限页摘要标题也改成“无障碍辅助 / 历史输入助手 / Shizuku 诊断”。这样后续即使用户手机里命中过去的历史配置，看到的也会是当前产品语义，而不是旧实现内部值
- 本轮继续把 Android 正式路线的用户可见口径再统一了一层：运行页卡片标题已经是“原键盘发送兜底”，但此前不少提示和建议里还混用“显式发送”这个旧阶段说法。现在运行页 Toast、迁移提示、权限建议、就绪度摘要、监听策略说明和无障碍 / Shizuku 兼容提示都统一改成了“原键盘发送”口径，避免正式界面一会儿说“原键盘发送”，一会儿又说“显式发送”，让后续产品表达更稳定
- Windows 端这轮也把当前隔离实例的真实落库证据补齐了：隔离面板 `http://127.0.0.1:19568` 当前状态已刷新为 `connected=true / trusted=true / lastActionType=file-send / lastActionDetail=codex-win-smoke-20260612.txt`；同轮真实 `9501` 的 `recentMessages` 已命中 `codex-win-send-text-20260612-2`，`recentPayloads` 也已新增标题为 `codex-win-smoke-20260612.txt` 的 payload 记录。也就是说，当前 Windows 隔离桌面端“连接、发送文本、发送文件、状态回写”这几条主链路在真实服务上都已再次压实

- 本轮继续把 Android 13 新真机 `4e9e24e7 / Redmi K50 Ultra / deviceId=c1c65d39-d744-455f-91e4-af08251148bc` 的二期联调从“能配上、能 trusted”推进到了“输入法/悬浮发送助手自动化入口可回归”：手机私有配置仍保持 `server_base=http://192.168.31.236:9501`、`room=default`，前台 `DebugPublishActivity` 已再次验证可把测试文本 `codex-debug-direct-publish-20260611-2` 真实写入 `http://127.0.0.1:9501/api/sync/bootstrap` 的 `recentMessages`，并让该设备恢复为 `online=true / trusted=true`
- 在上面这条主链路已确认可用的基础上，本轮还顺手抓到了一个会直接影响“专用输入助手”、悬浮发送助手和主界面“发送当前剪贴板文本”的真实缺口：`SyncService.sendManualText(...)` 原先每次都重新走 `startForegroundService`，在服务已在线时既容易被 ROM 干扰，也会让手动发送动作多绕一层；现已把它改成优先直达当前运行中的 `SyncService` 实例，仅在服务未运行时才回退到前台服务启动。修复后再用前台 `DebugPublishActivity` 触发 `manual-send`，测试文本 `codex-debug-manual-send-20260611-6` 已成功进入真实服务端 `recentMessages`，说明“专用输入助手面板 / 悬浮发送助手 / 运行页手动发送”共用的手动发送主链路终于在真机上被压实
- Android 二期的调试入口本轮也补成了后续可复用的正式联调工具，而不是一次性脚本：`DebugPublishActivity` 现在除了原来的 `extra_text -> debug publish`，还支持 `extra_action=manual-send` 和 `extra_action=show-floating`；同时补上了 `singleTop` 场景下的 `onNewIntent` 处理，避免 adb 连续 `am start` 时新 Intent 只是送到顶部实例、动作却完全没执行。当前用 `extra_action=show-floating` 拉起后，系统 `dumpsys notification` 已出现 `AlertWindowNotification - com.transparentlc.cloudclipboardsync`，可确认悬浮发送助手已经被系统实际显示，而不再只是“命令发了但是否弹出不明确”
- 广播型 `DebugClipboardInjectReceiver` 本轮也继续保留，但结合这台 Android 13 / MIUI 现场联调结果，后续应把它视为次级调试入口：系统层已经确认广播可送达，但在当前 ROM 下它不如前台 `DebugPublishActivity` 稳定；因此后续自动化联调应优先使用 `DebugPublishActivity` 的前台入口做“直接发布 / 手动发送 / 悬浮发送助手”回归，避免再把 ROM 对后台 receiver 拉前台服务的干扰误判成业务逻辑失败
- 本轮已继续把 Android 主路径收口从“只写在计划里”落实到代码层：`SettingsStore` 对历史 `clipboard_mode=accessibility/shizuku` 的自动迁移已落地；主界面模式选择当时先收口到了 `foreground / ime / floating` 三个入口；`RuntimeModeValidator`、`ClipboardModeSupport`、运行页/权限页状态摘要与快捷处理文案也开始同步改口径，把 `accessibility` 明确降为兼容旧配置时的辅助能力，把 `shizuku` 明确降为系统授权与 AppOps 诊断辅助。按最新决策，这里后续还要再从“旧三入口口径”继续收束到 `foreground / floating / 不替换原键盘的新方案`
- 本轮已完成 Android 主界面第一轮全面屏修复并做真机验证：`MainActivity` 接入 edge-to-edge 与 `WindowInsetsCompat`，`activity_main.xml` 增加了根布局与滚动区 id 配合 inset 分发；真机截图已确认顶部背景与头部区域能铺满状态栏，不再出现“上面露一截”的断层观感
- 在上面这轮基础上，本轮又继续做完了第二轮视觉收口，而不是停在“技术上铺满了就算结束”：状态栏现已真正透明，顶部沉浸背景直接延伸到系统图标后面，首页头图也从“整体圆角卡片”改成“顶部直切、仅底部圆角”的整块背景；通过 `adb exec-out screencap -p` 抓取真机首页复看后，已确认之前两侧露白、顶部条带分层和卡片悬浮感都明显收敛。后续这条 UI 只剩常规细节打磨，不再属于阻塞性缺口
- 本轮继续把 Android 13 新真机的两条 UI 发送路径都压到“真实按钮闭环”而不只是调试动作可用：其一，用前台 `DebugPublishActivity --es extra_action show-floating --es extra_text codex-floating-ui-20260611-3` 拉起正式悬浮发送卡片后，真机截图已确认卡片实际展示了 `codex-floating-ui-20260611-3` 预览文本；随后直接点击卡片自己的“发送文本”按钮，真实 `9501` 的 `recentMessages` 新增了同名文本，说明 `floating` 模式的正式按钮链路已经闭环。其二，在把云剪输入法重新切成当前默认输入法、确认 `mInputShown=true` 后，又真实点了一次输入法面板自己的“发送当前剪贴板文本”按钮；截图里状态徽章和 Toast 已明确出现“已请求发送当前剪贴板文本”，同时 `recentMessages` 新增了来源于当前 Android 13 真机的文本记录。这里现在只作为历史探索证据保留，用来证明当前仓库里的 `ime` 实现确实是一条独立输入法路线，而不是后续正式产品方向
- 本轮也顺手记录下 Android 13 / MIUI 现场联调里的一个很具体的 ROM 差异，后续不要丢：`adb shell am broadcast -a com.transparentlc.cloudclipboardsync.action.DEBUG_SHOW_FLOATING_CLIPBOARD --es extra_text codex-ime-ui-20260611-3` 这条广播在当前 ROM 上可以稳定送达，但它对系统当前剪贴板内容的覆盖并不总能立即反映到后续输入法面板读取结果里。本轮现场表现为：服务端 recent 中并没有出现 `codex-ime-ui-20260611-3`，说明广播没有偷跑正式发布；但随后从输入法面板真实点“发送当前剪贴板文本”时，服务端收到的仍是前一个剪贴板值 `codex-ime-ui-20260611-2`。因此当前可以确认的是“IME 正式发送按钮链路可用”，而“靠后台广播稳定改写当前系统剪贴板后再喂给 IME 自动化”在这台 MIUI 上仍不可靠，后续自动化应优先依赖前台入口、真实复制动作或直接检查发送结果，而不是把这条广播当成稳定的剪贴板注入器

- 本轮围绕 Android `ime` 做过一整轮真机恢复与验证探索，这些结果现在统一保留为历史证据，不再作为后续正式里程碑：其一，之前因重装 debug 包导致的 `device_id`、`server_base`、`room` 与 `clipboard_mode` 重置问题，已通过 `/api/sync/pair/request`、`/trust` 和 `adb push -> /data/local/tmp -> run-as cp shared_prefs` 的方式安全恢复，且没有删除手机端任何非调试数据或服务端真实历史；其二，`adb shell ime list -a`、`settings get secure default_input_method`、`dumpsys activity services`、`dumpsys input_method` 都已明确证明当前仓库里的 `ClipboardInputMethodService` 是一条真实的“替换系统默认输入法”的独立路线，而不是借权限留在原键盘里的方案；其三，前台 `DebugPublishActivity` 也已确认这台新安卓身份重新回到 `trusted/online`，Android 到真实 `9501` 的文本主发布链路仍然是通的
- 同一轮探索还补上了这条路线的交付外观与自动化难点证据：`ClipboardInputMethodService` 已改为 inflate 正式布局 `view_clipboard_ime.xml`，并补了剪贴板预览、空内容禁用发送、发送成功短暂切换状态徽章等细节；但 `uiautomator dump` 仍抓不到输入法宿主窗口内部按钮节点，说明这条路线天然受“系统输入法宿主自动化可见性受限”影响。结合用户已明确否定“替换原键盘”的方案，这一整段后续不再继续产品化打磨，只保留为后续设计“不替换原键盘的新方案”时的反向论据

- 本轮真机 `adb` 已重新回连，设备 `760435a8` 可正常安装最新版 debug 包；只读复查确认手机当前 `cloud_clipboard_sync.xml` 中仍是 `clipboard_mode=shizuku`、`server_base=http://192.168.31.236:9501`、`device_id=android-live-device`，同时服务端 `http://127.0.0.1:9501/api/sync/bootstrap?deviceId=android-live-device&room=default` 返回 `trusted=true`，说明 Android 设备身份与真实服务端配对状态仍然有效。本轮未删除手机端任何非调试数据或文件，也未删除服务端真实历史或真实上传
- 本轮真机进一步确认当前现场里云剪同步无障碍服务并没有真正启用：`settings get secure enabled_accessibility_services` 与 `dumpsys accessibility` 中都看不到 `com.transparentlc.cloudclipboardsync/.ClipboardAccessAccessibilityService`，因此“无障碍模式后台复制没推送”在当前这台手机上本来就无法闭环；后续继续测无障碍增强链路前，必须先确认系统无障碍列表里真的已经启用云剪同步
- 本轮继续验证 Shizuku 诊断模式时，发现原有 `adb am broadcast -a com.transparentlc.cloudclipboardsync.action.DEBUG_INJECT_CLIPBOARD ...` 在这台 MIUI / 澎湃 OS 真机上会被系统以“Background execution not allowed”直接拦截，`dumpsys activity broadcasts` 可明确看到调试广播被 `skipped by policy`；因此之前那条“广播打到了但服务端没收到”的现象，不应再归因为 Android 到服务端主发布链路故障，而是 ROM 对后台广播接收器的执行策略限制
- 针对上面的真机差异，本轮新增了只在 debug 包中使用的前台调试直推入口：`DebugClipboardInjectReceiver` 现在会把文本转交给 `SyncService.ACTION_DEBUG_PUBLISH_TEXT`，`SyncService` 新增了 `pendingDebugPublishText` / `flushPendingDebugPublishText()` 调试发布分支；另外新增 `DebugPublishActivity`，允许通过 `adb shell am start -n com.transparentlc.cloudclipboardsync/.DebugPublishActivity --es extra_text ...` 的前台方式绕开 MIUI 对后台广播的拦截。这条前台 debug 入口已经完成真机闭环：测试文本 `codex-debug-activity-20260610-1` 成功出现在真实 `9501` 的 `recentMessages` 中，`sourceDeviceId=android-live-device`，说明当前 Android 端到服务端的真实文本发布主链路本身是通的
- 本轮继续收口 Android Shizuku 文案：`MainActivity.kt` 中原先“服务仍按系统允许的剪贴板回调与轮询链路工作”的描述已改为更准确的诊断模式口径，明确写成“当前只作为系统授权和剪贴板 AppOps 诊断辅助，不再额外轮询系统剪贴板，也不承诺绕过后台剪贴板限制”，避免用户误以为只要 Shizuku 已授权就已经具备后台复制自动回传能力
- 当前 Android 真机剩余未自动闭环点已进一步缩小为两项：其一，当前手机现场尚未真正启用云剪同步无障碍服务，因此还没法对“无障碍增强后台复制”做新的自动真机回归；其二，这台手机的 `adb shell cmd clipboard set text ...` 会直接返回 `No shell command implementation.`，所以不能把该系统命令当作本机稳定的前后台复制自动注入方式。后续若继续测后台复制，应优先通过真实前台交互 / 真实 App 复制场景或继续走前台 debug 入口，而不是再依赖被 ROM 拦截的后台广播与缺失实现的 `cmd clipboard`
- 本轮还补齐了 Android 后台复制二期方向的产品收口：早期确实把 `ime / floating` 一起接进过主干，但结合后续真机结论和用户最终确认，`ime` 现在只保留为探索过的独立输入法路线，不再继续作为正式模式推进；真正要接着做的是 `foreground / floating / 不替换原键盘的新方案` 这三条主路径，并继续复用现有文本发布主链路
- 本轮也把这条新方向补成了可抗上下文压缩的计划细节：`floating` 继续走“复制后快速发送助手”，不冒充系统级全自动后台监听；“不替换原键盘的新方案”必须满足显式用户动作优先、不能误发普通打字、不能破坏现有文件接收悬浮卡片、并且 Windows / 服务端 `recent` 能稳定收到文本。也就是说，后续不能再把它混写回 `ime` 这条专用输入法实现里
- 本轮又继续补了一个真实可复现的 Android 诊断缺口：当手机当前模式已经切到 `accessibility`，但无障碍服务还没真正启用时，原先 `DebugPublishActivity -> SyncService.ACTION_DEBUG_PUBLISH_TEXT` 这条纯调试直推链路也会先被 `RuntimeModeValidator` 拦住，导致“只是想验证 Android 到服务端文本主发送链路”都做不了。现已把这条 debug 动作改成仅绕过“剪贴板模式是否就绪”的运行模式校验，但仍保留 `serverBase` 非空和非 loopback 校验，不会放松正式使用时的权限门槛。修复后再次真机回归，测试文本 `codex-debug-activity-20260610-2` 已在当前 `clipboard_mode=accessibility` 且无障碍未启用的现场成功出现在真实 `9501` 的 `recentMessages` 中，来源设备仍为 `android-live-device`
- 本轮继续针对 Android 无障碍现场状态做真机收口，确认当前这台澎湃 / MIUI 真机里 `settings get secure enabled_accessibility_services` 和 `dumpsys accessibility` 都没有真正启用 `com.transparentlc.cloudclipboardsync/.ClipboardAccessAccessibilityService`，但 `dumpsys activity services` 里仍残留一条 `ConnectionRecord ... DEAD`。为避免把这种“系统残留勾选 / 死连接”误判成无障碍已就绪，`PermissionStatusHelper` 已调整为只有 `AccessibilityManager` 真正枚举到服务时才算 `accessibilityEnabled=true`；仅设置字符串命中但服务没真正绑定时，会明确显示为“待系统重新绑定（设置已勾选）”。重新安装 debug 包后，真机运行页已明确显示“当前模式存在阻塞”，并给出“系统无障碍服务列表里还没启用云剪同步”的中文提示，不再把 `DEAD` 残留误报成已开启
- 本轮还修掉了 `DebugPublishActivity` 调试直推的一处时序问题：此前 `ACTION_DEBUG_PUBLISH_TEXT` 在已启动服务上会先尝试立刻 flush，再被后续 `manual-start` 重连打断，导致前台调试注入偶发“设备 online 了但文本没进 recent”。现已改为先缓存 `pendingDebugPublishText`，只有在首次连接成功、trusted 切换成功或“服务已启动且当前已 trusted”的情况下才真正 flush；不再在 `queueDebugPublish()` 里抢跑。修复后重新安装真机回归，测试文本 `codex-debug-activity-20260610-3b` 已成功出现在真实 `9501` 的 `recentMessages` 中，来源设备仍为 `android-live-device`，说明当前无障碍阻塞判断收紧后，Android 前台调试直推主链路仍保持可用
- 本轮继续收 Windows 端“控制面板里点发送文件没效果”这个真 bug，并已经把根因压实到面板接口和网页触发链路，而不是服务端主发送逻辑本身：原先 `desktop-client-go/internal/panel/static/index.html` 里的 `sendFile()` 只是空 `POST /api/send-file`，而 `desktop-client-go/internal/panel/server.go` 又只会把请求体当成 JSON `paths` 解析，导致浏览器页面既拿不到本地文件，也没有真正把文件内容传给桌面端后端。现已把这条链路补成双通道：控制面板网页新增隐藏文件选择器和拖拽投递区，按钮会真实走浏览器 `FormData` 上传；面板后端新增 `multipart/form-data` 解析，会把浏览器上传的文件暂存到临时目录，再复用现有 `backend.SendFiles(...)` 主链路发送，同时继续兼容提示窗拖拽 / 托盘 / 右键菜单仍在使用的 JSON `paths` 方式。并新增 `desktop-client-go/internal/panel/server_test.go` 锁定 JSON 路径和 multipart 双场景，避免后面再回退成“接口能返回 200，但网页其实没把文件传进去”
- 修复后已完成一轮新的 Windows 自动回归：`desktop-client-go` 下 `go test ./internal/app ./internal/tray ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-desktop ./cmd/cloud-clipboard-panel` 重新通过；随后使用隔离 headless 桌面实例、独立配置目录和本地端口 `127.0.0.1:19548` 直连真实 `http://127.0.0.1:9501` 做闭环验证，`POST /api/send-text` 成功写入测试文本 `codex-win-send-file-fix-text-20260610-1`，`curl -F files=@... http://127.0.0.1:19548/api/send-file` 成功返回 `codex-win-send-file-fix-20260610-1.txt`，并且真实 `9501` 的 `bootstrap.recentMessages` / `bootstrap.recentPayloads` 都确认新增对应记录；同轮 `POST /api/open-panel` 后本地 `api/status` 也刷新为 `lastActionType=open-panel`、`lastActionDetail=http://127.0.0.1:19548/`。这说明当前 Windows 端文件发送主链路已经从“接口可达”推进到“浏览器上传格式已对齐、桌面后端可接收、真实服务端 recent 可落库”的状态
- 本轮继续把 Windows 端 `open-panel` 的一个真实边角隐患收掉：此前 `panel_window_windows.go` 只按窗口标题“云剪同步桌面端”直接激活已有窗口，理论上可能把旧端口的 Chrome 面板窗口误当成当前实例。现在已改成先检测是否存在匹配当前 `panelURL` 的 `--app=...` Chrome 进程，只有命中同一面板地址时才复用激活，否则新开窗口；并补充 `panel_window_windows_test.go` 锁定“只激活匹配当前 URL 的现有窗口”这条行为。修复后 `desktop-client-go` 下 `go test ./internal/app ./internal/tray ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-desktop ./cmd/cloud-clipboard-panel` 继续通过；随后用隔离桌面实例 `127.0.0.1:19554` 做回归，同时预先保留一个旧的 Chrome app 窗口 `127.0.0.1:19553`，再触发当前实例 `POST /api/open-panel` 后，系统里成功新增匹配 `19554` 的 Chrome app 窗口，而旧 `19553` 窗口仍然存在，说明现在不会再误激活旧窗口
- 本轮还把 Windows 控制面板网页本身的文件发送脚本链路也压到了真实 Chrome 页面层，而不再只停留在桌面 API 层：使用隔离桌面实例 `127.0.0.1:19556`、真实 Chrome 远程调试端口 `9225` 和独立 Chrome profile，直接通过 Chrome DevTools 协议在控制面板页内执行真实 `File + FormData + fetch('/api/send-file')` 脚本，生成测试文件 `codex-chrome-cdp-file-send-20260610-1.txt` 并触发页面文件发送逻辑。执行后隔离面板 `api/status` 刷新为 `lastActionType=file-send`、`lastActionDetail=codex-chrome-cdp-file-send-20260610-1.txt`，同时真实 `9501` 的 `bootstrap.recentPayloads` 确认新增同名 payload 记录，`actionUrl` / `downloadUrl` 正常返回。到这里，Windows 端“桌面主进程、Chrome 宿主、控制面板网页脚本、后端 send-file、真实服务端落库”这整条可自动联调链路已经全部压实
- 本轮继续把 Android 后台复制二期从“计划口径”推进到“代码主干”：`SettingsStore` 已正式纳入 `ime / floating` 两个新 `clipboard_mode`；`PermissionStatusHelper` 已补齐输入法启用状态、当前选中状态和中文状态文案；`RuntimeModeValidator`、`ClipboardModeSupportHelper`、`MainActivity` 运行页 / 权限页 / 快捷动作 / 就绪度摘要 / 诊断摘要都已扩展成 `foreground / accessibility / shizuku / ime / floating` 五模式统一逻辑；`activity_main.xml` 与 `strings.xml` 也已经新增正式单选入口和对应中文文案
- 本轮 Android 二期还落了一个最小可用的真实文本发送入口，而不只是 UI 骨架：新增 `ClipboardInputMethodService`、`res/xml/clipboard_input_method.xml` 与 Manifest 注册，输入法面板里已经可以点“发送当前剪贴板文本 / 打开云剪同步主界面”；`SyncService` 同时新增正式 `ACTION_SEND_MANUAL_TEXT / EXTRA_MANUAL_TEXT / EXTRA_MANUAL_ROUTE`，沿用现有 trusted、去重、防回环和诊断广播主链路推送文本，不再复用 debug-only 注入动作。`floating` 模式本轮先停在“正式模式入口 + 悬浮窗权限校验 + 运行时文案骨架”，还没有单独引入新的悬浮发送服务，这点后续继续联调时不要误记成已经完成
- 本轮 Android 本地验证已闭环：`android-sync-client` 下 `./gradlew.bat testDebugUnitTest --no-daemon` 通过，新增 `PermissionStatusHelperTest` 已覆盖输入法启用识别与状态文案；`./gradlew.bat assembleDebug --no-daemon` 也通过。过程中出现过一次输入法 service 重复注册、一次 `ClipboardInputMethodService` 空安全编译错误和一次 Gradle `R.jar` classpath snapshot 变换失败，现都已收口。这里的验证结论也要按最新决策重写：已验证的是探索态 `ime` 路线本身能编、能装、能被系统识别；后续真机优先顺序则改为 1）继续压实 `floating` 的复制后快速发送体验；2）评估并落地“不替换原键盘”的新方案；3）把旧 `ime`、`accessibility`、`shizuku` 的迁移提示与兼容口径收干净；4）悬浮窗交互继续参考 `局域网同步-Android-0.2.46` 中不替换原键盘的思路，而不是继续做专用输入法产品化
- 本轮又把 Android `ime` 方案和悬浮发送助手往“可交付细节”推进了一步，而不只是停在最小可用：`ClipboardInputMethodService` 现在会在输入法面板里实时展示当前剪贴板预览，空剪贴板时直接禁用发送按钮；发送成功后状态徽章会短暂切到成功态，再自动复位到“等待发送”。不过这部分现在统一归档为探索性打磨结果，不再作为正式路线继续投入
- 同一轮还把悬浮发送助手的一个真实交互坑先收掉了：之前整张悬浮卡片都挂在拖动手势上，容易和“发送文本 / 打开主界面 / 关闭”这类点按动作互相抢事件。现在拖动只绑定在顶部拖拽区，正文和底部按钮保持普通点击行为；同时位置记忆逻辑继续保留，因此后续真机联调时既能拖动记住位置，也更不容易出现“悬浮卡片能出来但按钮不好点”的体验问题
- 本轮继续把这条悬浮层交互再往前收了一步，避免后续 `floating` 正式助手和文件/图片接收悬浮卡片互相打架：`FloatingClipboardOverlayService` 与 `FloatingConfirmService` 现在在显示前会先互相发送 dismiss 动作，确保同一时刻只保留一层悬浮内容；同时文件接收悬浮卡片也和发送助手一样，改成只有顶部拖拽区负责移动位置，底部确认/详情/稍后按钮不再和拖动手势抢事件。这样后续真机联调时，不会再出现两个悬浮层叠在一起、或者“卡片能拖动但按钮像点不动”的混合问题
- Windows 面板这轮也补了一个轻量但实用的交互收口：控制面板网页在通过文件选择器或拖拽发送文件后，如果后端没有回传文件名列表，不再把结果误提示成“已取消选择文件”，而是改成按实际选择数量反馈“已发送 N 个文件”，并立即刷新概览状态，减少用户看到“明明发了却像没发”的错觉
- Windows 控制面板“快捷键与右键”页本轮继续补齐保存前兜底：现在会实时检测 5 组热键是否重复，冲突字段会高亮并显示中文冲突提示，概览页“快捷键概览”也会附带“快捷键冲突：N 组”摘要；保存配置前会再次拦截重复热键，避免把明显冲突的组合直接写进配置后才在托盘或全局注册阶段踩坑
- 这条桌面端热键冲突收口已完成最小回归：`desktop-client-go` 下 `go test ./internal/app ./internal/tray ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-panel` 通过。后续若继续细抠，可再考虑补“与系统常见占用组合”的弱提示，但当前“同应用内重复绑定”这类最容易导致用户误判的冲突已先在前端保存前挡住
- 本轮安卓 13 真机 `4e9e24e7 / c1c65d39-d744-455f-91e4-af08251148bc` 已重新在线，且当前现场态要明确记住：`shared_prefs` 中实际是 `clipboard_mode=foreground`、`floating_enabled=true`，系统默认输入法仍是云剪输入法，但系统无障碍列表里并没有启用云剪同步无障碍服务。因此今天这轮自动真机 smoke 的重点不是“无障碍后台复制”，而是重新压实前台调试直推、手动发送主链路和悬浮发送助手可见性
- 对应真机 smoke 已补完三条：其一，`DebugPublishActivity --es extra_action manual-send --es extra_text codex-debug-manual-send-20260612-1/2` 已再次写入真实 `9501` 的 `recentMessages`；其二，`DebugPublishActivity --es extra_text codex-debug-direct-publish-20260612-2` 也已重新落到 `recentMessages`，说明前台调试直推链路当前仍可用；其三，`DebugPublishActivity --es extra_action show-floating --es extra_text codex-floating-smoke-20260612-2` 触发后，系统 `dumpsys notification --noredact` 继续可见 `AlertWindowNotification - com.transparentlc.cloudclipboardsync`，说明悬浮发送助手的 overlay 通道这轮仍然正常
- 本轮还补了一组只读现场态证据，后续判断 Shizuku / 无障碍时要以这组结果为准：当前手机里 `shizuku_server`、`moe.shizuku.privileged.api` 和 `com.transparentlc.cloudclipboardsync` 进程都在线，悬浮窗 `SYSTEM_ALERT_WINDOW` 为 `allow`；但 `READ_CLIPBOARD` 的 AppOps 仍然只是 `foreground`，说明即使当前 Shizuku 进程已经起来，也不代表应用已经获得“后台无限制读剪贴板”能力。同时 `dumpsys accessibility` 里 `Enabled services / Bound services` 依旧没有云剪同步无障碍服务，和 `dumpsys activity services` 中残留的多条 `ClipboardAccessAccessibilityService` live/dead connection record 形成了典型的系统残留现场，所以后续对无障碍是否真正可用，仍应优先以系统无障碍列表和 `AccessibilityManager` 判定为准，而不是只看 service 连接残留
- Windows 侧本轮也补了一次完整隔离 smoke，而且不碰真实配置：基于 `desktop-client-go/config.json` 克隆临时配置，仅把 `panelAddress` 切到 `127.0.0.1:19564`、通知改为 `off`、右键与热键关闭、下载目录切到临时目录，再用 `cloud-clipboard-desktop -headless` 启动隔离实例。验证结果里 `api/status` 启动后返回 `status=trusted / connected=true`，`/api/send-text` 写入测试文本 `codex-headless-smoke-20260612-4` 后真实 `9501` 的 `recentMessages` 已确认命中，`/api/send-file` 发送的 `codex-headless-smoke-20260612-4.txt` 也已在 `recentPayloads` 中命中，同时 `/api/open-panel` 触发后当前系统中确实存在 `--app=http://127.0.0.1:19564/` 的 Chrome 独立窗口。说明当前完整桌面客户端主进程在隔离运行态下仍能跑通“连接、文本、文件、打开 Chrome 面板”四条主链路
- 本轮又补了一个小但很实用的 Android 收口：`PermissionStatusHelper` 新增了剪贴板 AppOps 中文化标签和“读剪贴板仍只前台允许”的限制说明，`MainActivity` 运行页里与 Shizuku 相关的“模式说明 / 后台复制就绪度 / 诊断摘要”也同步改成直接展示“读取 仅前台允许 / 写入 允许”这类更直白的口径，避免现场把“Shizuku 已授权 + 进程在线”继续误读成“后台复制理应已经可用”。这一轮只改口径，不改同步主链路本身
- 上面这条 Shizuku 诊断提示收口已完成本地回归：`android-sync-client` 下 `./gradlew.bat testDebugUnitTest --no-daemon` 与 `./gradlew.bat assembleDebug --no-daemon` 通过，新增 `PermissionStatusHelperTest` 已锁定 `clipboardAppOpLabel("foreground") = 仅前台允许` 和对应限制说明；验证后已执行 `gradlew --stop` 与 `gradlew clean`，没有删除手机端任何非调试数据或文件
- 本轮最新自动验证结果也需要一起记住：`android-sync-client` 下 `./gradlew.bat testDebugUnitTest --no-daemon`、`./gradlew.bat assembleDebug --no-daemon` 再次通过；`desktop-client-go` 下 `go test ./internal/app ./internal/tray ./internal/panel ./internal/shellmenu` 与 `go build ./cmd/cloud-clipboard-desktop ./cmd/cloud-clipboard-panel` 通过；真机 `adb devices -l` 当前已重新枚举到 `4e9e24e7 / 22081212C`，新版 debug 包已成功 `adb install -r` 覆盖安装，随后只做了不破坏配置的 smoke 检查：`ime list -a` 仍可识别 `com.transparentlc.cloudclipboardsync/.ClipboardInputMethodService`，`MainActivity` 也可正常拉起为前台顶层界面。整个过程中未删除手机端任何非调试数据或服务端真实历史/真实上传
- 在上面这轮基础上，本轮又追加了一次只针对悬浮层的真机 smoke，而不是只停在本地编译：新版 debug 包再次成功 `adb install -r` 覆盖安装后，通过前台 `DebugPublishActivity --es extra_action show-floating --es extra_text codex-floating-mutex-20260612-1` 拉起正式悬浮发送助手；随后从系统 `dumpsys notification --noredact` 可明确看到 `AlertWindowNotification - com.transparentlc.cloudclipboardsync`，说明悬浮层通道确实被系统拉起，没有因为这次互斥/拖拽改动而直接失效或 crash。当前这轮 smoke 还没有把“互斥后一层关闭再弹另一层”的真机动作链路完全点透，但至少已确认新版 APK 可正常安装、调试入口可正常触发、系统 overlay 通知仍按预期出现
- 在上面这轮 5 模式代码骨架落地之后，产品口径又进一步收敛了一次，后续不要把“当前代码里暂时还兼容 5 模式”误写成“后续也会长期保留 5 模式并列”：兼容层面暂时还需要识别 `accessibility / shizuku / ime`，但计划层面已经改成逐步收敛到 `foreground / floating / 不替换原键盘的新方案` 三条正式主路径。后续代码清理时应按“先兼容迁移、再删除入口、最后删遗留实现”的顺序推进，而不是继续扩展 `accessibility / shizuku` 或继续把 `ime` 当作正式模式打磨

