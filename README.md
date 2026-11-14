# QQBot —— 基于 QQ 官方机器人的本地 QQ 客户端（Android）

外观与手机 QQ 一致、底层换成 **QQ 官方机器人 OpenAPI** 的安卓客户端。
输入机器人的 `AppID` 与 `AppSecret` 换取 `access_token` 后登录，即可查看群聊消息并发言。

> 协议实现参考 [ElainaCore/ElainaBot_v2](https://github.com/ElainaCore/ElainaBot_v2)（Python 版），
> 界面资源与尺寸取自手机 QQ **9.3.85**（`com.tencent.mobileqq`，versionCode 16920）的真实 APK。

---

## 一、构建与安装

### 本地构建

```bash
./gradlew assembleRelease      # 产物：app/build/outputs/apk/release/QQBot.apk
./gradlew testDebugUnitTest    # 运行单元测试
```

环境要求：JDK 17、Android SDK 34。

- 应用名：**QQBot**
- 包名：**`com.qqbot.client`**，minSdk 24，targetSdk 34
- 版本：`versionCode 8` / `versionName 1.7`
- `local.properties` 由本地生成（指向自己的 SDK 路径），**不要提交**

### 下载 CI 构建的 APK

仓库已配置 GitHub Actions，推送到 `main` 后自动跑测试 → 构建签名 APK → **发布到 Releases**。

**直接下载最新版（固定链接，永远是最新构建）：**

```
https://github.com/wei-la-ya/QQBot/releases/latest/download/QQBot.apk
```

也可以打开 [Releases](https://github.com/wei-la-ya/QQBot/releases) 页面手动下载：

| 标签 | 说明 |
|---|---|
| `latest` | 滚动 Release，每次推送 `main` 都更新，内含 `QQBot.apk` 与 `QQBot-v<版本>.apk` |
| `v*` | 手动打 tag（如 `git tag v1.5 && git push --tags`）发布正式版本 |

APK 已用仓库 secrets 里的密钥签名，可直接覆盖安装。CI 运行记录在 Actions → Build APK，同时保留 30 天 Artifact。

### 云端自动构建（GitHub Actions）

推送到 `main`/`master` 或手动触发 `workflow_dispatch` 会自动：
跑单元测试 → 构建 release APK → 上传到 Actions 的 Artifacts。

打 `v*` 标签（如 `v1.1`）时会额外创建 Release 并附上 APK。

**可选：自动签名。** 仓库未配置密钥时产出的是 `QQBot-unsigned-*.apk`（无法直接安装）。
在仓库 `Settings → Secrets and variables → Actions` 中添加下列 secrets 即可自动签名：

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | 密钥库文件的 base64：`base64 -w0 keystore.jks`（macOS 用 `base64 -i keystore.jks`） |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_PASSWORD` | 私钥密码 |
| `KEY_ALIAS` | 密钥别名 |

> 密钥库文件本身已被 `.gitignore` 排除，**永远不要提交进仓库**。

---

## 二、功能清单

### 消息收发
| 能力 | 实现方式 |
|---|---|
| 普通文字 | `msg_type=0` + `content` |
| Markdown | `msg_type=2` + `markdown.content` |
| **普通 / MD 切换** | 输入框上方切换条；MD 模式下纯文本自动转义包装，也可直接写语法 |
| 图片（普通） | 富媒体：`POST /files` 取 `file_info` → `msg_type=7` |
| **图片（MD）** | 先走**分片接口**拿公网 URL，再作为 MD 插图发送（见下节） |
| 视频 | 富媒体 `file_type=2` |
| 引用回复 | 长按菜单「引用」或**按住消息左滑**；引用消息强制走 MD + `message_reference` |
| 撤回 | 长按菜单「撤回」→ `DELETE /v2/groups/{id}/messages/{id}` |
| 复制 | 长按菜单「复制」 |

### @ 提及
官方语法（文档）：

```
@用户：<qqbot-at-user id="" />
@全部成员：<qqbot-at-everyone />
```

客户端交互与 QQ 一致：
- 输入框里输入 `@` → 向上弹出候选列表（左头像、右昵称），首项是「全体成员」
- 选中后 `@昵称` 变蓝色并自动补空格
- 直接输入「@昵称 + 空格」或「@openid + 空格」也会自动识别
- **同名成员**会全部列在候选列表里供挑选
- 发送时把 `@昵称` 映射回 openid 生成官方提及语法；带 @ 的消息自动以 MD 发送

**候选列表的数据来源**（官方没有「群成员列表」接口，只能靠本地积累）：

| 来源 | 记录内容 |
|---|---|
| 收到群消息时的发送者 | openid + QQ 昵称 |
| 消息里的 `mentions` 字段 | 被 @ 者的 openid + 昵称 |
| 点开资料卡时请求 `/v2/groups/{id}/members/{openid}` | 补全角色（群主/管理员） |

- **头像**：`https://q.qlogo.cn/qqapp/{appId}/{openId}/100`
- **筛选**：输入 `@` 后继续打字，按昵称模糊匹配或 openid 前缀匹配，**实时缩小范围**；无匹配时给出提示而不是空白
- **昵称更新**：openid 不变，QQ 昵称变了会在下次发言时**静默更新**，并同步刷新该成员历史消息里的显示名
- 拿不到群昵称（官方不提供），显示的一律是 QQ 昵称

### 群管理与资料
- 点头像 → 成员资料卡：头像、QQ 昵称、角色、OpenID、**发言数**、**最后发言时间**
- 资料卡内「查看发言记录」→ 该成员的发言列表，点击可**跳转到对应消息**
- 资料卡内可**禁言**（10 分钟 / 30 分钟 / 1 小时 / 3 小时 / 1 天）与**解除禁言**
  → `POST /v2/groups/{id}/mute`，`op=add|del`，`mute_expire_at` 为带时区的 ISO 8601
  → 仅当机器人在群内是**管理员/群主**时可用，否则按钮置灰并提示
- 右上 `···` → 群成员列表（可搜索昵称或 openid）
- 群资料页：群名、人数、机器人身份、消息权限（全量 / 仅 @）

### 会话与列表
- 会话列表显示**群名称 / 用户昵称** + 人数 + 摘要 + 时间 + 未读
- **置顶**：长按会话 → 置顶/取消置顶，置顶区在顶部单独分组
- 聊天页左上角显示同样的名称，点名称进入群资料
- 联系人页群聊分两组：**已开启全量消息** / **未开启全量消息**

### 体验
- 上滑自动加载更早消息；进入会话定位到最新
- 「查找」可按关键字搜索聊天记录并跳转到对应消息（高亮显示）
- 半屏相册选图（Q 的相册面板样式），支持多选最多 9 张
- 所有 SQLite 读写都在 IO 线程，列表用 LazyColumn key 复用，滑动不卡顿

### 存储
- 本地只保留**最近 3 天**消息（`RETENTION_DAYS = 3`），每次写入顺带清理
- 分页游标 `(ts, rowid)`，同一毫秒的多条消息也不会漏
- 三张表：`conversations` / `messages` / `members`，均有对应索引

---

## 三、MD 模式发图的图床原理

QQ 的分片上传接口在收尾时除了 `file_info` 还会返回**公网图片地址**
（`image_url` / `url` / `download_url` / `raw_url` 之一），这个地址可以直接当作 Markdown 图片链接。

流程（`bot/ImageHost.kt`，等价于 `QQBot图床.js` 的 qqbot backend）：

```
POST {scope}/upload_prepare   {file_type:1, file_name, file_size, md5, sha1}
PUT  {parts[0].presigned_url} 原图字节
POST {scope}/upload_part_finish {upload_id, part_index, block_size, md5}
POST {scope}/files {upload_id}  →  file_info + image_url
     ↓ 给 URL 追加 ?response-content-type=image/jpeg 让 CDN 按图片返回
发送 MD：![image #宽px #高px](该URL)
```

`scope` 取当前会话：`/v2/groups/{openid}` 或 `/v2/users/{openid}`。

---

## 四、协议对照（ElainaBot_v2）

| 环节 | 实现 | 参考 |
|---|---|---|
| 取 token | `POST {tokenBase}/app/getAppAccessToken`，提前 60s 刷新，重试 3 次 | `core/network/access.py` |
| 双域名 | 新版 `api.bot.qq.com` / 旧版 `api.sgroup.qq.com`+`bots.qq.com` | `access.py` |
| 网关 | `GET /gateway/bot` → HELLO(10) → 心跳 → IDENTIFY(2)，支持 RESUME(6) | `core/network/websocket.py` |
| intents | `(1<<0)|(1<<12)|(1<<24)|(1<<25)|(1<<26)|(1<<27)` | `websocket.py` |
| 发媒体 | `POST /files` → `file_info` → `msg_type=7` + `media.file_info` | `core/message/media.py` |
| 大文件 | >5MB 分片：`upload_prepare` → PUT → `upload_part_finish` → `files` | `media.py` |
| 引用 | `message_reference: {message_id, ignore_get_message_error:true}` | `web/tools/_message/media.py` |
| 禁言 | `POST /v2/groups/{id}/mute` `{members:[{op, member_openid, mute_expire_at}]}` | `core/message/sender.py` |
| 成员资料 | `GET /v2/groups/{id}/members/{openid}` | `sender.py` |
| token 失效 | 错误码 `11244` 自动刷新重试 | `core/message/_http.py` |
| 事件解析 | `attachments` / `msg_elements` / `mentions` / `<faceType>` / `message_scene.ext` | `core/message/parsers/base.py` |

---

## 五、已知限制

- QQ 机器人**无法读取入群前的聊天记录**，消息从登录后开始累积。
- 机器人只能看到**被 @ 的群消息或全量权限下的全部消息**，取决于群里给机器人开的权限。
- 官方没有「拉取全量群成员列表」的接口，成员表只能从消息与 mention 中逐步积累，因此刚进群时 @ 候选可能不全；输入 @ + openid 仍然可用。
- 官方不提供**群昵称**，显示的是 QQ 昵称；头像走 `q.qlogo.cn/qqapp/{appId}/{openId}/100`，部分成员可能取不到图，会退回昵称首字色块。
- 主动消息有平台频控（每月每用户/群 4 条）。
- 语音发送暂未接入 SILK 转码。

---

## 六、测试

```bash
./gradlew testDebugUnitTest
```

15 个单元测试（Robolectric）覆盖：

| 测试 | 覆盖点 |
|---|---|
| `分页加载不重复不遗漏 含同毫秒消息` | 向上翻 4 页，**同一毫秒 5 条消息**不重不漏、顺序正确 |
| `定位消息时前后各取一页` | 搜索结果跳转定位 |
| `只保留三天内的消息` | 过期清理 |
| `会话按置顶和时间排序` | 置顶分组 |
| `群成员昵称与openid互相匹配` | 重名成员、按 openid 匹配、成员为空不崩 |
| `搜索聊天记录` | 关键字检索 |
| `生成官方提及语法` / `解析文本中的提及` / `发送用id 本地回显用昵称` | @ 语法双向转换 |
| `MD 模式纯文本自动转义` / `MD 图片语法` / `超长消息判定` | Markdown 构建 |
| `解析群消息 附件与表情` / `私聊消息` / `非消息事件返回 null` | 事件解析 |

