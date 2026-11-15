# QQBot 与手机 QQ 实现对比 —— 事实清单与待办

> 目标：让后续会话**独立对比本项目与手机 QQ 的实现**，只追求**行为 / UI / 元素 / 布局 / 手势**一致，不要求代码相同。
> 本文档只记录「已验证的事实」与「未验证的猜测」，**不代替结论**，接手方需自行复核。

---

## 一、资源位置

| 内容 | 绝对路径 |
|---|---|
| 手机 QQ 安装包（9.3.85, versionCode 16920, `com.tencent.mobileqq`） | `D:\ntqq\ntqq\base.apk (5).1`（380.9 MB，42 个 dex） |
| jadx 反编译工具 | `D:\code\tools\jadx\bin\jadx.bat`（v1.5.1） |
| 已解出的 dex 原始文件 | `D:\code\qq_decompile\dex\classes*.dex`（42 个，共 415 MB） |
| 资源表 dump（279,585 行） | `D:\code\qq_apk_probe\resources_dump.txt` |
| 从 APK 提取的图片资源 | `D:\code\qq_apk_probe\ui_assets\` |
| QQ 小黄脸表情配置 | `D:\code\qq_apk_probe\face_config.json`（188 个 sysface + 165 个 emoji） |
| 本项目源码 | `D:\code\QQBotQQ\`（包名 `com.qqbot.client`） |

### 反编译产物目录 → 内容对应

每个目录是用 jadx 对**单个 dex** 全量反编译的结果，路径均为 `<目录>\sources\...`。

| 目录 | 源 dex | 关键内容 |
|---|---|---|
| `D:\code\qq_decompile\c18` | classes18.dex | **聊天页相册面板**：`com\tencent\mobileqq\aio\panel\photo\` 下 `PhotoPanelVB / PhotoPanelVM / PhotoPanelAdapter / PhotoPanelUIState` |
| `D:\code\qq_decompile\c39` | classes39.dex | **滑出发送**：`com\tencent\mobileqq\aio\panel\photo\widget\ItemSwipeSendDecoration.java` |
| `D:\code\qq_decompile\c33` | classes33.dex | **相册主类 + 表情面板**：`com\tencent\mobileqq\activity\aio\photo\PhotoListPanel.java`、`com\tencent\mobileqq\emoticonview\SystemEmoticonPanel*.java`、`EmoticonPagerRadioGroup.java`、`EmoticonLinearLayout.java` |
| `D:\code\qq_decompile\c33b` | classes33.dex（`--show-bad-code`） | `PhotoListPanel.java` 含 `onSendBtnClicked` 的可读片段 |
| `D:\code\qq_decompile\c8` | classes8.dex | **相册基础设施**：`com\tencent\qqnt\qbasealbum\album\` 下 `PickerMediaListPart / PickerTitleBarPart / PickerBottomBarPart / MediaDragSelectHelper` |
| `D:\code\qq_decompile\c25` | classes25.dex | `PhotoListPanel$QueryMediaTask.java`（相册列表查询） |
| `D:\code\qq_decompile\c20` | classes20.dex | `QQEmoticonPanelLinearLayoutHelper` 相关 |
| `D:\code\qq_decompile\c6` | classes6.dex | **AIO MVI 事件**：`com\tencent\mobileqq\aio\event\PhotoPanelEvent*.java`、`AIOMsgListEvent$HideKeyBoardAndPanel.java` |
### 常用命令

```powershell
# 反编译某个 dex
D:\code\tools\jadx\bin\jadx.bat --no-res --no-imports -j 4 -d D:\code\qq_decompile\cXX D:\code\qq_decompile\dex\classesXX.dex

# 方法体被跳过时加 --show-bad-code 拿字节码
D:\code\tools\jadx\bin\jadx.bat --no-res --no-imports --show-bad-code -d <out> <dex>

# dump 布局 XML 树
C:\android-sdk\build-tools\34.0.0\aapt2.exe dump xmltree --file r/i/xxx.xml "D:\ntqq\ntqq\base.apk (5).1"

# 在全部 dex 里搜类名（PowerShell 直接读 415MB 会 OOM，用 python 逐文件搜字节）
```

---

## 二、已验证事实（有代码 / 资源出处，可直接采信）

### 2.1 颜色 / 尺寸 / 字符串（取自 resources.arsc）

| 项目 | 值 | 出处 |
|---|---|---|
| QQ 主色 | `#FF12B7F5` | resources.arsc |
| 底部导航底色 | `#CCF3F3F7` | `r/l/qui_common_bg_nav_bottom.xml` |
| 分隔线 | `#FFEDEDED` | resources.arsc |
| 引用条底色 | `#FFF5F6FA` | `qui_common_bg_middle_standard` |
| 引用条描边 | `#1A000000`，0.5dp | `qui_common_border_standard` |
| 拖放区淡蓝 | `#330099FF` | `qui_common_brand_light` |
| 拖放提示文案 | 「拖到此区域发送」/「松手发送」 | string `0x7f121e8a` / `0x7f121e8b` |
| 保存提示 | 「已保存到系统相册」/「图片保存失败」 | string `0x7f121e8c`~`0x7f121e8d` |
| 表情面板底部栏高 | **45dp** | `layout/emoticon_aio_panel` → `dimen/nw`(0x7f0707b1) |
| 表情面板分隔线 | 0.5dp | 同上布局内 View |
| 表情面板左侧分类图标 | 51dp 宽 | 同上布局内 ImageView |

### 2.2 表情面板

**`SystemEmoticonPanelViewBinder.java`**（c33）：
```java
private static final int NUM_COLUMN = 7;
private static final int NUM_ROW = 3;
adapter.setRowColumnNum(3, 7);
// 最后一格为删除键
EmoticonInfo e = new EmoticonInfo(); e.action = "delete";
adapter.setSpecEmoticon(e);
```

**`layout/emoticon_aio_panel.xml`** 结构：
```
EmoticonMainPanel
└ RelativeLayout (layout_above=底部栏)
  └ EmoticonPanelViewPager (match_parent)
└ 底部栏 LinearLayout (height=45dp, alignParentBottom)
  ├ View (0.5dp 分隔线)
  └ LinearLayout(horizontal)
    ├ ImageView (51dp, 分类图标)
    └ HorizontalListViewEx (weight=1, 横向滚动页签)
```

**`SystemEmoticonPanel.java`**：`RelativeLayout` + `ViewPager` + `EmoticonPagerRadioGroup` 分页控制。

### 2.3 相册面板（聊天页）

**`PhotoPanelVB.java:379`**（c18）—— 决定性证据：
```java
recyclerView.setLayoutManager(new LinearLayoutManager(context, 0, false));
//                                                       ↑ 0 = HORIZONTAL
```
→ 聊天页相册是**横向单行滚动的缩略图条**。

**`PhotoPanelAdapter.f0()`**（c18）：
```java
layoutParams.width = getItemCount() == 1 ? MATCH_PARENT : d.a(158);
```
→ 只有一张时铺满宽度；多张时每张 **158dp**（数值借用了 `TPVideoCodecType.TP_VIDEO_CODEC_TYPE_XBM = 158`）。

**`ItemSwipeSendDecoration.java`**（c39）：继承 `ItemDecoration` + 内部 `ItemTouchHelper` 式手势，
用 `viewHolder.itemView.setTranslationX/Y(0f)` 复位，拖动中通过 `getTranslationX/Y` 取值 → **滑出去即发送**。

**`PhotoPanelVM`**（c18）发送后：
```java
e().h(new PhotoPanelEvent.PhotoPanelActive(false));   // 关闭面板
```

**`PhotoListPanel.java`**（c33b）`onSendBtnClicked` 片段：
```java
QLog.d("PhotoListPanel", 2, "onSendBtnClicked");
LinkedList<String> paths = this.d;          // 已选路径列表
... sendMedias(paths, ...)
```
→ 发送直接取选中列表，**不存在「待发送」中间层**。

### 2.4 互斥

**`AIOMsgListEvent$HideKeyBoardAndPanel.java`**（c6）：事件名本身表明键盘与面板是**共用同一个显示槽位**。

### 2.5 其他已确认

- `q.qlogo.cn/qqapp/{appid}/{openid}/100` 对机器人 openid **恒返回同一张默认图**（实测 4208 字节）。
- 官方 `/users/@me` 存在，返回机器人自身 openid、昵称、真实头像：实测
  `{"id":"9084617195156459794","username":"小岸","avatar":"http://thirdqq.qlogo.cn/g?b=oidb&k=...&kti=..."}`
  （`avatar` 的 `k=` 每次不同，**带签名有时效**，需刷新）
- 消息里的 `faceId` 是 **QSid**，表情图床用 **EMCode**（呲牙 QSid=13 / EMCode=113；`e13.png` 404，`e113.png` 有效）。映射表在 `face_config.json`。

---

## 三、未验证 / 存疑（接手方需自行确认）

> **2026-11-14 复核已全部解出**，结论见 3.1，本节原始疑点保留做对照。

1. ~~**`PhotoListPanel.onClick` 未解出**~~ → **已解出**（见 3.1.1）
2. ~~**相册 item 的视觉细节未解出**~~ → **已解出**（见 3.1.2）
3. ~~**输入栏本体布局未解出**~~ → **已解出**（见 3.1.3）
4. ~~**`ItemSwipeSendDecoration` 的触发阈值**~~ → **已解出**（见 3.1.4）
5. ~~**表情面板页签图标**是什么资源~~ → **已解出**（见 3.1.5）
6. 所有结论仍**没有一处在真机上验证过**（本机无模拟器/真机）——只能保证与反编译代码/资源一致。

### 3.1 复核结论（含依据）

#### 3.1.1 PhotoListPanel.onClick —— 已解出，且**它不是聊天页相册**

复跑方法：`jadx --show-bad-code -d <out> classes33.dex` 后读 `PhotoListPanel.java`（c33b 版本已有 onClick 主体，只是局部 `throw null`）。
结论：

- `PhotoListPanel` 是**旧版全屏选图面板**（`android.support.v7` RecyclerView、`MediaFileFilter`、
  `ShortVideoPreviewActivity`、`QzoneConfig:PhotoUpload`），与聊天页相册**不是同一套实现**。
- 点击分支按 id 分派：`2131363108`→onAlbumBtnClicked、`2131380299`→onEditBtnClicked、
  `2131387806`/`2131387802`→权限/原图、`2131393055`→**onSendBtnClicked**、
  `2131373122`→同步到空间。
- `onSendBtnClicked` 直接取 `this.d`（`LinkedList<String>` 已选路径）→ `richmedia.q.h(list)`
  → `sendMedias(list)`，**没有「待发送」中间层**，发送后 `d.clear() / f.clear()`。
  → 原文档二.3 的判断成立，**且已复核**。

**但真正决定聊天页相册行为的是 `aio.panel.photo` 这一套**（PhotoPanelVB/VM/Adapter），
本轮把它的完整行为解出来了，见 3.1.6。两套不要混用。

#### 3.1.2 相册 item 布局/视觉 —— 已解出（layout `r/i/ez3.xml` = `layout/ez3`）

定位方法：ViewBinding `xu1.al`（相册 item，`xu1.am` 是权限占位 item）在 classes18 **没有**；
用 python 扫 APK 内全部 `r/i/*.xml` 二进制，对比 `xu1.al` 引用的 5 个 id
（`0x7f0a3d88/0x7f0a3d8a/0x7f0a3d1d/0x7f0a3d35/0x7f0a3e24`），命中 4 个候选，其中只有
`r/i/ez3.xml` 同时含 `0x7f0a3d35`（编辑角标）→ **`xu1.al` = `layout/ez3`**。

```
RelativeLayout  id/cg7           width=wrap_content  height=match_parent
├ URLImageView  id/dq3          width=match height=match
│                               marginLeft/Right = 2dp
│                               scaleType=centerCrop(6) adjustViewBounds=true
│                               maxWidth=300dp minWidth=50dp
│                               src=@drawable/hap(占位图)
├ ImageView     id/dq4          — 选中遮罩 —
│                               width=match height=match  scaleType=fitXY(1)
│                               background=#33000000
│                               与 dq3 四边对齐；代码里选中时 setVisibility(0) 并
│                               setBackgroundColor(0x33000000=855638016)
├ FrameLayout   id/udh          右上角 CheckBox 容器
│                               宽高 wrap，paddingTop=4dp paddingBottom=27dp
│                               paddingStart=27dp paddingEnd=4dp
│                               与 dq3 top/end 对齐
│  └ QUICheckBox id/dpo        style=a5w（parent=Widget.AppCompat.CompoundButton.CheckBox）
│                               textSize=20dp  textColor=qui_common_on_brand_primary
│                               gravity=center
└ TextView      id/drh          — 视频时长 —
│                               textSize=12sp textColor=#FFFFFFFF
│                               height=17dp  margin=4dp
│                               paddingStart/End=5dp
│                               背景=@drawable/mgq（solid=qui_common_overlay_dark, radius=18dp）
│                               与 dq3 bottom/end 对齐；visibility=gone；仅视频可见
└ ImageView     id/udx          — “已编辑”角标 —
                                width=height=17dp  padding=3dp  marginBottom=5dp marginStart=5dp
                                背景=@drawable/mgp（oval, solid=qui_common_overlay_dark）
                                src=@drawable/qui_edit_icon_white
                                alignParentBottom+Start；默认 gone
```

**图片本身没有任何圆角**（item 无 background、无 clipToOutline；代码里
`view.setOutlineProvider(null)`）。原文档「圆角 6dp」是自造的，**与 QQ 不符**。

选中态由 `PhotoPanelAdapter.onBindViewHolder` 的代码补全：

```java
m.setChecked(e1.contains(localMediaInfo));           // e1 = 已选列表
str = m.isChecked() ? String.valueOf(e1.indexOf(localMediaInfo) + 1) : "";
m.setText(str);                                      // 选中显示序号，未选中空
n.setVisibility(localMediaInfo.getExtIsEdit() ? 0 : 8);
p.setBackgroundColor(855638016 /*0x33000000*/);
p.setVisibility(m.isChecked() ? 0 : 8);              // p = dq4 遮罩
view.setTranslationX/Y(0); setAlpha(1f); setScaleX/Y(1f); setOutlineProvider(null);
```

即：**勾选 = 右上角 20dp 方形 CheckBox 显示序号 + 整图 20% 黑遮罩**，不是圆形角标。

#### 3.1.3 输入栏 —— 没有独立 layout，是**运行时构建**

- 代码定位：`classes4.dex` 含 `skin_aio_voice_nor(2131252456)`、`qui_image_aio_oversized(2131247318)`、
  `qui_camera_aio_oversized(2131246530)`、`qui_emoticon_aio_oversized(2131247083)`、
  `skin_aio_more_nor(2131252346)`、`skin_aio_keyboard_nor(2131252340)` 全部 6 个 AIO 输入栏图标，
  是唯一同时引用它们的 dex。
- `resources.arsc` 里 `layout/` 下**没有** aio input 布局（只有 `chat_input_bar_bg_big` 这类
  drawable/color），所以输入栏确实是**程序化组装**（`aio.input` 包：
  `AIOInputBarCompatUIState` / `AIOInputBarCompatMsgIntent` / `com.tencent.mobileqq.aio.input.n` 等）。
- **因此本项目输入栏的元素顺序/尺寸无法“从 layout XML 抄”**，只能按 drawable 资源名与
  QQ 已知交互约定实现，并在文档里标注为「按资源名推断」。仍可靠的间接依据：
  `skin_aio_keyboard_nor` 存在 → **表情/加号按钮按下时图标切换为键盘图标**（同一按钮双态），
  `skin_aio_voice_nor/pressed` → 语音按钮是**独立开关**在输入框左侧。

#### 3.1.4 ItemSwipeSendDecoration 触发阈值 —— 已解出（字节码）

jadx 反编译 `select()` 失败，改用 `dexdump -d classes39.dex` 读 `ItemSwipeSendDecoration.select`
（方法起始 `63bbc4`，562 个 16-bit code units）。关键指令：

```
004b: and-int/lit8 v3, v0, #int 3        // v0 是否允许上下拖
004f: iget v3, ...;.n:F                   // n = dy（当前位移）
0051: cmpl-float v3, v3, 0
0053: if-lez v3 ->0057 ; 否则 v3=2(下) 否则 v3=1(上)
0058: getHeight(recyclerView) -> v5 (float)      // 条高
005f: iget v7, ...;.h:F                          // h = **按下时的 touchX**
0063: iget v8, ...;.i:F                          // i = **按下时的 touchY**
0065: invoke-virtual {c.f:(FFLVH;)F}              // h.f() = f2 / itemView.getHeight()
0069: mul-float v5, v6                            // 阈值 = 条高 × (按下Y / 条目高)
006a: and-int/2addr v0, v3
006b: if-eqz v0 -> 判定为 0                       // 该方向不被允许
006d: iget v0, ...;.n:F  006f: abs(v0)           // |dy|
0073: cmpl-float v0, |dy|, v5
0075: if-lez v0 -> 判定为 0                       // |dy| 必须 **严格大于** 阈值
```

**阈值 = `recyclerView.getHeight() × 按下时的 TouchY / itemView.getHeight()`，
判定条件 `abs(dy) 严格大于` 该阈值。**

⚠️ 注意分子是**按下点的 Y（`this.i`）**，不是当前位移 `dy`。
由于水平条里 item 高度 == RecyclerView 高度，两者相除为 1，**阈值数值上就等于「按下点在条目内的 Y」**：
- 在条目顶部按下 → 阈值很小，稍微上滑就发送
- 在条目底部按下 → 阈值接近条目高，要滑很远
（视觉进度也按同一基准归一：`h.k()` 里 `progress = abs(dy) / initialTouchY`，到 1.0 正好等于阈值）

**只认竖向（dy），横向位移 dx 不参与判定** —— 原实现按 `hypot(dx,dy)>120px` 判断是**错的**。

其它已解出的手势参数（`h.java` = `ItemSwipeSendDecoration$c` 实现）：

| 项 | 值 | 出处 |
|---|---|---|
| 支持方向 | `e()` 返回 **257** = `(1<<8)|1` → 上、下 | `h.e()` |
| 动画时长 | 固定 **300ms** | `h.c()` |
| 飞出目标位移 | `h()` = `signum(dy) * (recyclerView.height + dimen/bpk(48dp) + inputBarHeight)` | `h.h()` |
| 拖动中视觉 | `alpha = 1 - 0.5*frac`、`scale = 1 - 0.3*frac`、`clipToOutline(true)` + 圆角 Outline | `h.l()` |
| 复位 | `itemView.setTranslationX/Y(0)`，`alpha/scale` 由 `l()` 每帧重算 | `$c.a()`、`$c.j()` |
| 松手未过阈值 | 弹回原位（`select(null,0)`） | 字节码 |

#### 3.1.5 表情面板的两个“页签”元素 —— 已解出，且**不再臆造**

`layout/emoticon_aio_panel.xml` 是 `EmoticonMainPanel`（**完整表情面板**）的布局，不是
`SystemEmoticonPanel`（经典表情页）。实测 `SystemEmoticonPanel.getLayoutId()` 返回 `2131567917`
= `layout/h80`，内容只有 `merge → ViewPager(id/0x7f0a93c1) + EmoticonPagerRadioGroup(id/hv2)`。
`EmoticonMainPanel` 通过 `BasePanelView` 用 id `2131394969` = `0x7f0a8199`
`id/tab_sencondary_emoticon` 找 `HorizontalListViewEx` —— 与布局里的 id 完全吻合。

`0x7f0a0dfa` = `id/btn_more_emoticon`，`contentDescription` 字符串
`0x7f1211d5` = **「表情商城入口」**，51dp 宽、`scaleType=center(6)`。
→ **它是「表情商城 / 更多表情」入口按钮，不是分类图标**，本项目的「😊 占位」是错的，
应改为隐藏（本项目无表情商城）或换成真实入口图标；**不应**用它当“回到第一页”。

**分页指示器真实形态**（`EmoticonPagerRadioGroup.createButton(1)`）：
40dp 宽 × 10dp 高（dp，`applyDimension(COMPLEX_UNIT_DIP, ...)`），
`gravity=17(center)`，`leftMargin=rightMargin=3dp`，`buttonDrawable = @drawable/qui_systememojipanel_radio_btn`：

```xml
<!-- qui_systememojipanel_radio_btn.xml -->
<selector>
  <item android:state_checked="false" android:drawable="@drawable/qui_systememojipanel_face_indicator"/>
  <item android:state_checked="true"  android:drawable="@drawable/qui_systememojipanel_face_indicator_current"/>
</selector>
<!-- face_indicator:         bitmap nol.png，tint=@color/qui_common_icon_tertiary  = #FFCCCCCE -->
<!-- face_indicator_current: bitmap nom.png，tint=@color/qui_common_icon_secondary = #FF929296 -->
```

**点状指示器：非选中 #CCCCCE / 选中 #929296**，不是蓝色。
`RadioGroup` 在 `layout/h80` 里是 `wrap_content` + `alignParentBottom` + `centerHorizontal` +
`marginBottom=3dp`（即贴在面板底部居中）。

#### 3.1.6 聊天页相册（`aio.panel.photo`）完整行为 —— 已解出

**面板布局 `xu1.an` = `layout/ez5`（`0x7f0d11f3`），出自 `PhotoPanelVB.onCreateView`：**

```
InterceptParentTouchLayout (root, vertical, match, clipChildren=false)
├ FrameLayout id/f_y  background=qui_common_bg_nav_bottom_aio_bg(#FFF)
│                      alignParentTop，layout_above=id/jst（底栏）
│  └ TextView id/fbx  「暂无图片」17sp，color=qui_common_text_secondary(#86…)，居中
├ RecyclerView id/r3y  background 同，visibility=gone，clipChildren=false
│                      alignParentTop + layout_above=id/jst
└ LinearLayout id/jst 底栏（horizontal，alignParentBottom）
                       gravity=bottom|left(0x30)  minHeight=60dp
                       paddingTop=11dp paddingStart=17dp paddingEnd=17dp
   ├ TextView id/p2   「相册」16sp  color=qui_common_text_primary(#E6000000)
   │                   marginTop=2dp drawablePadding=1dp
   ├ TextView id/ekt  「编辑」16sp enabled=false marginTop=2dp marginStart=dimen/23dp
   ├ QUICheckBox id/h1y 16×16dp marginTop=3dp marginStart=23dp
   ├ TextView id/h24  「原图」16sp color=text_primary marginTop=2dp marginStart=3dp
   ├ TextView id/h1z  「(999K)」16sp visibility=gone marginTop=2dp
   ├ <Space>            width=0 weight=1
   └ QUIButton id/send_btn  「发送」14sp(style) gravity=center
                            layout_gravity=alignParentRight|centerVertical
                            height=dimen/32dp minWidth=dimen/60dp buttonSizeType=2
```

**底栏行为（`PhotoPanelVB`）**

| 行为 | 依据 |
|---|---|
| 发送按钮文字 = `"发送" + (size>0 ? "($size)" : "")` | `handleUIState` `str2 = "发送" + str` |
| 发送按钮 enable = `size > 0`；颜色 `getColorStateList(enabled ? qui_common_text_primary : qui_common_text_secondary_light)` | 同上 |
| **编辑按钮 enable = `size == 1`**（只有单选一张才能编辑） | `anVar13.e.setEnabled(size == 1)` |
| 编辑按钮点击 → `IPhotoPanelApi.onEditBtnClicked(ctx, 选中列表, 是否原图)` | `PhotoPanelVB.S1` |
| 「原图」CheckBox 点击 = 翻转，radio 语义（选中后**立即取消**以免变 radioGroup） | `T1` + `R1` 末尾 `setChecked(false)` |
| 勾选原图后 `id/h1z`（大小文本）可见，文本 = 总大小 `(1KB)/(nKB)/(nMB)/(n.nG)`，掩码 `TP_CH_SIDE_RIGHT` | `Y1()` |
| 「相册」TextView 点击 → `i.b` intent → `jumpToAlbum()` 跳系统相册 | `bindViewAndData` + `handleIntent` |
| 列表为空：`FrameLayout`(暂无图片) 可见、RecyclerView gone；有数据反之 | `handleUIState` |
| 列表有更新且 `submitData` 返回 true → `scrollToPosition(0)` | 同上 |
| 无存储权限时插入一条 `isPermissionView=true` 的占位 item 到列表首位（`xu1.am`，点击跳设置） | `handleUIState` + `PhotoPanelAdapter.onBindViewHolder` |
| 最大选择数：**50**（`a0()==8` 我的电脑）否则 **20** | `PhotoPanelVB.A0`；超限 Toast `2131897592`=「最多只能选择20张图片/视频」 |
| 数量以 `8 == a0() ? 50 : 20` 同时传给预览页 `Config.h(...)` | `PhotoPanelVM.h0` |

**item 点击 = 打开全屏预览，不是勾选**（`PhotoPanelAdapter.b0`）：
`onItemClick → PhotoPanelVM.h0(pos, globalVisibleRect, imageView)` → 起 Activity(forResult)
带 `PreviewInitBean(全部媒体, 位置, 已选, "PREVIEW")`。**勾选只能点右上角 CheckBox。**

**拖出发送（`i.h` intent，来自 `h.m()`）**

```java
// h.m (ItemSwipeSendDecoration$c.m)
sendIntent(new i.h(viewHolder.mediaInfo, /*原图*/ false));   // 参数2 见字节码: Boolean.FALSE
adapter.notifyDataSetChanged();
c = true;   // 重置 i() 返回的拖动开关
```
`handleIntent` 中 `i.h` → `sendSingleMediaInfo(info, b)` **只发这一张**，
不动其它已选项（对比：`i.g` = `sendSelectMediaInfo(原图)` 才会发送全部已选）。

**发送完 / 关闭**：`PhotoPanelVM.onDestroy` 派发 `PhotoPanelEvent.PhotoPanelActive(false)`；
`PhotoPanelVM.onCreate` 派发 `PhotoPanelActive(true)`。
`sendSelectMediaInfo` / `sendSingleMediaInfo` 之后由上层把面板状态置 false（见 `PhotoPanelVM$sendSelectMediaInfo$2`）。

**相册是“面板”不是“页面”**：`onCreate` 时若 `a0()==8` 会 `ShieldQuality` 把
“编辑/原图/大小”全部 `setVisibility(4)`（我的电脑不显示原图相关）。

**主题细节**：`e()` 里 `aIOUtil.d(anVar.j, 25dp, 25dp, 25dp, 25dp)` ——
把 **`id/jst`（底栏 LinearLayout）的 padding 改成 25dp 四边**（覆盖 XML 里的 17dp），
`j.post(...)` 延后执行。**所以底栏真实左右内边距是 25dp，上下也是 25dp。**

---


### 3.2 真实网关抓包结论（2026-11-14，43 条事件）

用真实 appId/secret 连 `wss://api.sgroup.qq.com/websocket`，Identify 后统计 payload：

| 结论 | 证据 |
|---|---|
| **`author.avatar` 基本每条消息都下发** | 24/24 条消息事件带 `avatar`，形如 `https://thirdqq.qlogo.cn/bqqthird?key=..&kti=..&s=640`（带签名，有时效） |
| 拼接接口仍需保留作兜底 | `q.qlogo.cn/qqapp/{appid}/{openid}/100` 对**任意不存在的 openid 都返回同一张 4208 字节默认图**（用 3 个随机 openid 实测 sha 相同），所以只能用事件里的 avatar 判断真实头像 |
| 群消息带群名片 | `author.member_nickname`（21/23），`username` 是 QQ 昵称；显示应 `member_nickname ?: username` |
| 私聊结构不同 | C2C 事件 `member_openid = null`，用 `user_openid`；昵称只在 `username` |
| **机器人自身 id 是纯数字** | READY 的 `user.id` 与 `/users/@me` 的 `id` 同为 `9084617195156459794`；成员 openid 才是 32 位 hex（如 `6700E368419E178356AB7E79970EBE34`） |
| 图片消息的 content | `<faceType=6,faceId="0",ext="eyJ0ZXh0IjoiIn0=">` + `attachments[].url`；`ext` 是 base64 的 `{"text":""}` |
| @ 提及 | 正文里是 `<@A2C65E5D597608FB027C84E2C661FFB1>`，与 `mentions[].id` 对应 |
| 事件里**没有** msg_type | 收到的消息事件不带消息类型，md 与普通文本只能靠语法特征区分 |

据此修正的实现（v1.8）：头像优先 `senderAvatar`、显示名优先 `member_nickname`、
`looksLikeMarkdown()` 语法判定、剥离 OneBot 的 `attachmentType` 残留标签。

> 抓包用的临时脚本与含用户昵称/openid 的日志已删除，未入库。


### 3.3 机器人自身 openid：用 bot_state 接口取（2026-11-14 实测）

原以为“机器人自身 id 只能是数字”，**是错的**。`GET /v2/groups/{group_openid}/bot_state`
返回的 `member_openid` 才是机器人真正的 openid：

```
两个不同群分别请求：
  群 68BF8620… → member_openid = 16A7816F489D11BAF3BFF6789C6CA16A
  群 88DFF813… → member_openid = 16A7816F489D11BAF3BFF6789C6CA16A   ← 完全相同
```

结论：
- 机器人 openid 是 **32 位 hex**，且**跨群一致**（不是每群不同）
- 与 `/users/@me` 的 `id`（数字型 `9084617195156459794`）、READY 的 `user.id` 是两回事
- 该 openid 与消息里 `author.member_openid`、`mentions[].id`（is_you=true）同源，
  所以**用它做 isSelf 判断才正确**
- 接口需要 `group_openid`；用假 openid 请求返回 `11255 资源不存在`，
  说明本应用**有权限**（若返回 `11253` 则代表仅白名单可用）

顺带验证头像拼接接口：用**真实** openid 请求
`q.qlogo.cn/qqapp/{appid}/{openid}/100` 返回 **19846 字节真实头像**，
而随机假 openid 返回 4208 字节默认图 —— 说明
**只要 openid 真实，拼接接口就能拿到头像**，此前“机器人拿不到成员头像”的结论作废。

### v1.9 修复（据 3.3）
- `/users/@me` 的刷新**不再覆盖** `botOpenId`（之前每次都把 hex openid 冲成数字 id，
  导致自己发的消息识别不出 isSelf）
- 群消息到达时用 `bot_state` 学一次机器人 openid 并持久化；
  `mentions.is_you` 与自身消息作为兜底
- 「我的」页标签改为「机器人 OpenID」


### 3.4 成员头像拼接实测（2026-11-14）

拿抓包得到的 4 个真实成员 openid，用 `q.qlogo.cn/qqapp/{appid}/{openid}/100` 拼接并下载：

| openid | 昵称 | 字节数 |
|---|---|---|
| 6700E368419E178356AB7E79970EBE34 | 库洛鸣潮最锋利的刃 | 3170 |
| EDF812007E2C11A8F3D2189DAD3387BC | •᷄ࡇ•᷅ | 4048 |
| DCC94F5DA13DF12C42E5AD9FD6070D17 | 你们就是菜 | 3886 |
| 60330E46D134B61730DC41CD98C9765F | 睦子米 | 3618 |

四张都是 100×100 的真实头像，互不相同（默认图固定 4208 字节）。
**结论：只要有真实 openid，拼接接口就能拿到成员头像**，不需要依赖事件里的 `author.avatar`。

### v1.10 修复的问题
1. `/users/@me` 返回值若没有 `avatar`，会**把已有头像清成空串** → 改用 `ifEmpty { latest.avatarUrl }`
2. 自身资料**只刷新一次**（注释写「每隔N分钟」但代码没有循环），
   签名 URL 过期后自己的头像就 403 → 补 30 分钟定时刷新
3. `QqAvatar` 只有「加载失败 → 首字」一档兜底，签名过期时直接退化 →
   改为两级：**主 URL 失败 → 用 openid 拼接地址重试 → 仍失败才显首字**
4. 消息里的成员/自身头像都补上 `fallbackUrl`


### 3.5 v1.11 修复（用户实测反馈）

| 问题 | 根因 | 处理 |
|---|---|---|
| md 图片下面多一个 `)` | `MarkdownText` 的图片正则 `!\[[^\]]*]\(([^)\s]+)` **没有匹配结尾的 `)`**，`replace(img.value,"")` 只吃掉到 URL 为止，右括号残留成正文 | 正则改为 `...([^)\s]+)\)?`，把 `)` 一起吃掉 |
| 相册面板下方大块留白 | 调用方用 `Box(Modifier.weight(1f))` 给了半屏高度，`AlbumPanel` 内部又 `.fillMaxHeight(0.5f)`，等于又乘了一半 | 面板内部改 `fillMaxSize()`，高度完全由调用方决定 |
| 「我的」openid 仍是旧的纯数字 | `botOpenId` 存在本地，旧版本把它写成了 `/users/@me` 的数字 id；只有收到群消息才会经 bot_state 纠正 | 冷启动 1.2s 后，若仍非 32 位 hex，遍历已存群会话逐个调 `bot_state` 纠正 |
| 别人改了昵称本地还是旧的 | 成员表长期缓存且没有清理入口 | 「我的」页新增**清除昵称缓存**（只删 members 表，**不动消息与图片**） |
| 资料卡只有群昵称 | 模型只存了一个 nickname | `GroupMember` 增 `qqNickname`；members 表加列（DB v6→v7 带 ALTER 迁移）；资料卡上面群昵称、下面「QQ 昵称」 |
| 相机提示「没有可用的相机应用」 | 用的是 `ACTION_IMAGE_CAPTURE` 跳系统相机 | 改为 **CameraX 应用内相机**（`ui/CameraScreen.kt`）：预览嵌在应用里，拍完直接拿到 File 发送 |

仍待解决：**群内引用消息不显示**——需要抓一条真实引用消息的 payload 才能确定
`message_scene.ext` / `msg_elements[].msg_idx` 的实际结构，不凭猜实现。


### 3.6 引用消息与合并转发（2026-11-14 抓包实测）

**引用消息的真实结构**（`message_type = 103`）：

```json
"message_scene": { "ext": [
    "ref_msg_idx=REFIDX_tSeXi/…",     ← 被引用那条
    "msg_idx=REFIDX_N9Bqb0…",         ← 本条自己
    "auth_token=aE787uS5…"
]},
"msg_elements": [ {                  ← 被引用消息的内容在这里
    "msg_idx": "REFIDX_tSeXi/…",     ← 与 ref_msg_idx 相同
    "content": "",
    "attachments": [ { "content_type": "image/jpeg", "url": "…", "width":864, "height":1920 } ]
}],
"parallel_message": { "msg_nodes": [ { "content": "[图片]", "message_type": 7 } ] }
```

**引用一直不显示的根因**：原正则 `[?&](?:ref_)?msg_idx=([^&]+)` 要求 id 前面有 `?` 或 `&`，
但实际是 `ext` 数组里的裸字符串 `ref_msg_idx=REFIDX_…`，**永远匹配不到**。

**踩坑记录（差点造成回归）**：修正则后我加了“找不到 `ref_` 就退回普通 `msg_idx`”的兜底，
验证时发现**每条普通消息的 ext 里都带自己的 `msg_idx`**，兜底会让所有消息都挂上引用条。
已删除该兜底：**只有出现 `ref_msg_idx=` 才认为是引用**。
用抓到的 21 条群消息回归验证：仅 2 条是真引用，且都成功匹配到 `msg_elements`。

**合并转发**：内联消息在 `parallel_message.msg_nodes`（每条含 `content` / `message_type`）。
UI 按 QQ 形态实现为**卡片**：标题「聊天记录」+ 最多 4 行预览 + 「查看N条消息」，
点开是内联消息列表。判定阈值 `msg_nodes >= 2` 才算转发（引用消息只带 1 个 node）。

> ⚠️ 截至本次提交**尚未抓到真实的合并转发样本**，卡片渲染基于上面的结构推断，
> 待拿到 `message_type` 与 node 结构的实例后复核。


### 3.7 合并转发（聊天记录）真实结构 —— 与官方文档不一致

抓包拿到 `message_type = 102` 的真实样本，**它没有 `msg_elements`**，
内容被预先格式化成**一整段纯文本**放在 `content` 里：

```
[群聊的聊天记录]
=== 消息 1 ===
[发送者] GrimPigeon
[附件1] 类型:图片 文件名:00FA….jpg 尺寸:1080x1920 大小:493.9KB URL:https://multimedia.nt.qq.com.cn/…
（空行）
=== 消息 2 ===
[发送者] 小明
这是一条文字
```

官方 schema 里 `MsgElement` 带有递归的 `msg_elements`（且 `message_type` 含 101 并行消息 / 102 聊天记录），
但**实际群里收到的是上面的文本形态**，两者必须都兼容。

所以实现分两条：
1. **文本形态**（实测）：`parseChatRecord()` 按 `=== 消息 N ===` 切块，
   逐块取 `[发送者]`、`[附件N] 类型:图片 … URL:…`（含宽高）、其余行作为正文；
   非图片附件（文件/视频/语音）转成 `[类型]` 占位。
2. **schema 形态**（官方定义，防御性保留）：递归解析 `msg_elements`，
   元素若再带 `msg_elements` 即为**嵌套合并转发**。

UI：卡片（标题「聊天记录」+ 最多 4 行预览 + 「查看N条消息」）；
点开是明细列表，逐条显示 发送者头像/昵称 + 正文 + 图片；
嵌套转发在明细里再画一张子卡片，点进去**逐层进入、可返回**（层级栈 + 「返回」）。

验证：用真实抓包的文本格式写成单测（`QuoteForwardParseTest`），
覆盖 102+文本解析、引用解析、嵌套结构、以及「普通消息不被误判为引用」，
36+2 项单测全部通过。

> 说明：真实样本里嵌套未出现，嵌套分支按官方 schema 的递归定义实现，
> 尚未用真实嵌套样本验证。


### 3.8 语音消息（2026-11-15 实测 + QQ 反编译）

**为什么收到的语音不显示、也放不了**

1. 官方 `content_type` 里语音是 **`voice`**，既不是 `audio/*` 也不含 `silk`，
   原解析把它归成了 FILE → 界面上什么都没画出来。已改为识别 `voice`。
2. 两个地址的实测结果：

| 字段 | 实测 |
|---|---|
| `attachments[].url` | HTTP 200，响应头写 `audio/mp3`，但字节头是 `02 23 21 53 49 4c 4b` = `\x02#!SILK` → **实际是 SILK**，Android MediaPlayer 放不了 |
| `attachments[].voice_wav_url` | HTTP 200，`RIFF….WAVE`，115278 字节 → **真 WAV，可直接播** |

结论：**必须下载 `voice_wav_url` 到缓存再播放**（不是直接喂 URL 给播放器）。

**语音条尺寸/资源全部取自 QQ 反编译**（不是自拟）：

| 项 | QQ 出处 | 值 |
|---|---|---|
| 播放/停止图标 | `PttAudioPlayView.java`：两张位图 `c2p`(播放中) / `c2q`(停止) | 已提取为 `qq_ptt_playing` / `qq_ptt_paused` |
| 播放控件 | `r/i/cyl.xml` `PttAudioPlayView` | **34dp × 34dp** |
| 波形区 | 同上 `PttAudioWaveView` | **39dp × 37dp**，marginStart **12dp**、marginEnd **18dp** |
| 行高 | 同上外层 RelativeLayout | **37dp** |
| 时长文字 | 同上 `pttDuration` | **14sp**，颜色 `qui_common_text_secondary` |
| 文字色 | `0x7f06133d` = `qui_common_text_secondary` | 非硬编码灰 |

> 一处自我纠正：我一开始把图标写成 15dp，那是从**另一个布局**（语音转文字弹层 `r/i/8_.xml`）
> 串过来的值；`c2q/c2p` 本身是 73×72px @xhdpi≈36.5dp，应填满 34dp 控件。已修正。
> 另：我曾从被截断的输出里"拼"出一个 `pow(1-s/60)*125+15` 的宽度公式，
> 复核后确认**无法证实**归属，已废弃；实际做法是
> `AIOPttContentComponent` 里「时长文字测量宽 + 播放区宽 + margin 累加」。

**尚未完成（且有客观障碍）**

- **录音 + 变声**：QQ 的变声实现在 `voicechange/impl/VoiceChangeHelperImpl` 里是**空壳**
  （只有构造器，没有算法），`VoiceChangeParams` 也不在已反编译的 dex 中
  → 「变声跟 QQ 一致」无法据实复刻，不能凭印象编造音效。
- **发送语音文件（silk/mp3/wav/ogg）**：官方文档明确支持（`file_type=3`，超限降级为 `file_type=4`），
  这部分有据可依，是实现录音/发送时的落地路径。


### 3.9 语音音质真相：不是我们压缩了（2026-11-15 实测同一条 2.40s 语音）

| 字段 | 实测格式 | 体积 | 码率 |
|---|---|---|---|
| `attachments[].url` | SILK_V3（`\x02#!SILK_V3`） | 3738 字节 | **≈12.5 kbps**（有损语音编码） |
| `attachments[].voice_wav_url` | PCM WAV，24kHz / 16bit / mono | 115200 字节 data | **≈384 kbps** |

结论：
- `voice_wav_url` 是**服务端把 SILK 解码出来的未压缩 PCM**，比 SILK 大 30 倍，
  **不存在二次压缩**；它听感与 SILK 一致，是因为上限就是 SILK 那 12.5kbps。
- QQ 客户端里放的也是这份 SILK，所以「QQ 里听着也糊」是原始音质问题，
  不是我们这边处理造成的。
- Android 平台**没有 SILK 解码器**，直接播 `url` 必然失败（响应头写 audio/mp3 是骗人的）。

因此播放策略改为「**收到什么容器就原样播放，不转码**」：
1. 优先 `voice_wav_url`（PCM，平台可解）；
2. 否则下载 `url` 原始字节，按文件头嗅探容器
   （`RIFF/WAVE`、`OGG`、`#!AMR`、`ID3|FFEx`→mp3、`fLaC`、`ftyp`→m4a、`#!SILK`），
   扩展名即交给播放器的解码器，**不重采样、不重新编码**；
3. 若是 SILK 且没有 wav 备用 → 明确提示「该语音格式系统无法解码」，而不是假装能放。

回归中发现并修复的 bug：`sniffExtension` 里判 AMR 时取了 4 个字符却与
5 字符的 `"#!AMR"` 比较，永远不会命中（真实头是 `#!AMR\n` 6 字节）。
由新增的单测 `VoiceFormatTest` 抓出。


### 3.10 字体大小：QQ 真实实现是 **4 档 17/18/20/22dp**（已反编译确认）

定位过程（这次先扫全量 dex 找定义，没有再猜）：
1. `Lcom/tencent/mobileqq/activity/ChatTextSizeSettingActivity;` 出现在
   classes.dex / 3 / 7 / 13 / 33 / 35；而 `c13`（classes13.dex）**已经反编译过**，
   直接在里面找到了该类（上一轮我只 grep 了 forward 关键字，漏了它）。
2. `ChatTextSizeSettingActivity` 里 onClick 按 view id 分派 4 个档位：
   ```
   2131395617 → L=1    2131395616 → L=2    2131395614 → L=3    其它 → L=0
   ```
   `E1(int)` 把档位写进 `SharedPreferences("setting_text_size")` 的
   `chat_text_size_type`，并切换每行右侧打勾图标（id 2131395615）的 visibility。
3. `getChatTextSizeWithDP()` 把 dimen 换算成 dp：
   | chat_text_size_type | dimen | 值 |
   |---|---|---|
   | 0（默认） | `dimen/c0` (0x7f070450) | **17dp** |
   | 1 | `dimen/bz` (0x7f07044f) | **18dp** |
   | 2 | `dimen/by` (0x7f07044e) | **20dp** |
   | 3 | `dimen/bx` (0x7f07044d) | **22dp** |
4. 设置页标题 string = **「字体大小」**（0x7f122d7b），布局 layout/g_（0x7f0d037c），
   每档是一行预览文字（12sp）+ 选中打勾图标。

**因此本项目按真实值改为 4 档**（原先按资源字符串推测的 7 档「小…最大」是另一处的文案，
不是聊天字号设置，已作废）：
- 档位：`17dp / 18dp / 20dp / 22dp`，默认 17dp（= QQ 的 type 0）
- 缩放系数：以 17dp 为基准 → 1.0 / 18/17 / 20/17 / 22/17
- 生效方式仍是覆盖 `LocalDensity`，让文字/气泡/图片一起缩放
- 单测 `FontLevelTest` 锁定：4 档、四个 dp 值、默认档、系数换算、越界回退

> 与 QQ 的差异说明：QQ 这个设置只改**聊天正文字号**；
> 本项目用整体缩放是为了让气泡/图片一并跟随，观感一致但实现路径不同。

### 3.11 合并转发卡片：QQ 是用 **ARK** 渲染的（已翻全 42 个 dex 确认）

翻遍全部 42 个 dex 后的定位路径（记录方法，方便后续复用）：

1. 扫全量 dex 找类名线索 → `ForwardMsgItem` 在 `classes13.dex`，
   `component/fold` 包在 **`classes12.dex`**；
2. 反编译 `classes12.dex` → 命中
   `com/tencent/mobileqq/aio/msglist/holder/component/fold/AIOFoldContentComponent.java`
   （30KB，`extends BaseContentComponent<FoldMsgItem>`）；
3. 该组件里 inflate 的是 `ArkAppLoadLayout`，日志原文写着
   **`"fold msg handled by ark"`** —— 即气泡内部由 ARK 应用渲染，不是静态 XML；
4. 它 inflate 的布局 id = `2131559071` = `layout/cgh`（`r/i/cgh.xml`），dump 出来：

```
ArkAppLoadLayout   id/0x7f0a46a2   layout_width=263dp  layout_height=294dp
└ LinearLayout（竖向, gravity=center, layout_centerInParent）
  ├ ImageView  id/0x7f0a46a1  28dp × 28dp      ← 加载/异常图标
  └ TextView   id/0x7f0a46b5  12sp, textColor=0x7f060383  ← 文案（"消息异常" 0x7f120729）
```

5. 再查 ARK 应用包是否随 APK 下发：扫 `assets/` 里含 `multiforward / foldmsg / fold_msg` 的条目
   → **0 命中**，说明该 ARK 应用是运行时下发的，APK 内没有其内容布局。

**结论（可据实复刻的部分 vs 不可能复刻的部分）**

| 项 | 结论 |
|---|---|
| 卡片外框尺寸 | **263dp × 294dp**（`layout/cgh` 实测，已照此改本项目的 `ForwardCard` 宽度） |
| 加载/异常占位 | 28dp 图标 + 12sp 文案，失败文案「消息异常」 |
| 卡片内部排版（标题、每行预览、圆角、配色） | ❌ 由运行时下发的 ARK 应用绘制，**APK 内不存在**，无法逐像素复刻 |

因此本项目现在的做法：宽度对齐 263dp、用文本预览填充内容（QQ 那 294dp 高度是给 ARK 内容的），
并在代码注释里写明「内层 ARK 无法复刻」，**不冒称已与 QQ 像素级一致**。


### 3.12 后台保活（v2.0，2026-11-15）

**QQ 的做法**（扫全量 dex 确认）：`Lcom/tencent/mobileqq/msf/service/MsfService;`
定义在 `classes2.dex`（已单类反编译确认），配合 `QQService` / `CoreService` 常驻，
再加上厂商推送通道。`startForeground` 在 classes.dex/11/17/19/28/33 等 10 个 dex 里出现。

**本项目的做法**（没有厂商推送，只能自维持连接）：

| 组件 | 作用 |
|---|---|
| `keepalive/KeepAliveService` | 前台服务，`START_STICKY` + `stopWithTask=false`，常驻通知（IMPORTANCE_MIN，不出声） |
| `keepalive/BootReceiver` | 开机自启（`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `QUICKBOOT_POWERON`），已登录则拉起服务 |
| `bot/BotRuntime` | 进程级运行时：持有后台连接；界面在时让位（`uiAttached`），界面销毁后 5s 内接管 |
| `bot/BackgroundSink` | 后台只落库：会话 + 未读 + 成员学习，不依赖任何界面对象 |

关键设计（为什么必须这样）：
- 连接若由 `MainViewModel` 独占，Activity 被划掉 → ViewModel 清理 → socket 关闭 → 收不到消息；
- 所以把连接所有权下放前台服务：服务自己在后台连、自己落库（`BackgroundSink`）；
- 界面回来后 `attachUi()`，看护循环断开后台连接，避免**双 WebSocket**；
- `onCleared()` 里让位并关闭界面自己的连接，数秒内由服务重连。

**未做（按你要求）**：@ 提醒 / 消息通知。常驻通知只是前台服务的系统硬性要求，
渠道重要性设为 `IMPORTANCE_MIN`，不出声、不弹窗。
如需真正的消息通知，再加 channel + `NotificationCompat` 即可。

### 3.13 底部「面板槽位」：键盘 / 表情 / 加号 / 相册 只允许一个（2026-11-15 反编译确认）

**结论：QQ 的输入区下方只有一块面板空间，任何面板都只是「占这块空间」，高度由容器统一给。**

| 事实 | 证据 |
|---|---|
| 键盘与面板共用同一槽位 | 事件 `AIOMsgListEvent$HideKeyBoardAndPanel`（`c6`，`aio/event/`），名字本身就是语义 |
| 相册面板**不设高度** | `PhotoPanelVB.java` 全文只有 `recyclerView.setLayoutManager(...)` / `addItemDecoration` 等，**没有任何设置高度的代码**（`c18 .../aio/panel/photo/PhotoPanelVB.java:379-386`） |
| 高度由容器统一给 | `com.tencent.input.base.panelcontainer.j#w(float density, Configuration cfg)`：<br>`keyboardH = keyboard.e.a.a(context, orientation)`（上次保存的键盘高度）<br>`minH = (int)(density * 280)`<br>`return coerceAtLeast(keyboardH, minH)` → **面板高 = max(键盘高, 280dp)**；多窗口时 `(int)(density*204)`（`j.java:387-400, 568-572`） |
| 保存的键盘高度也被抬到 ≥280dp | `j.java:434-443`：保存时 `max(keyboardH, (int)(280*density))` |
| 面板 View 被强制写成该高度 | `j#E()`：`layoutParams.height = 目标高度; view.setLayoutParams(...)`（`j.java:137-149, 516-518`），容器 FrameLayout 同样（`j.java:282-290`，动画 150ms） |
| 表情面板没有自己的高度，只有「大屏」例外 | `aio/panel/emoticon/d.java:174-184` 的 `r0(boolean)`：`aio.utils.f.d(ctx)` 为真时返回 `heightPixels * 0.455f`，否则返回 `null`（= 用容器默认高度） |

**本项目据此实现**（`ui/ChatPanelSpec.kt` + `ui/ChatScreen.kt`）：

- 一个状态 `panel: ChatPanelKind { NONE, EMOJI, PLUS, ALBUM }`，槽位里**永远只有一个**；
- 打开任何面板：`focusManager.clearFocus()` + `keyboard.hide()` + 关掉其它面板/相册；
- 输入框拿到焦点（`onFocusChanged.isFocused`，挂在**真正的 BasicTextField** 上）：立刻关掉相册/表情/加号面板；
- **返回键**：`BackHandler(enabled = 面板非空 || 键盘可见)`，先关面板、再收键盘，最后才退群聊；
- **点空白**：聊天区点击 + 相册内容区空白点击都是 `closeAllPanels()`；
- 槽位高度 = `max(实测键盘高度, 280dp)`，表情/加号/相册三者同高，从键盘切面板时聊天区高度不变。

> bug 根因记录：旧实现把 `Modifier.onFocusChanged` 的判断写成 `st.isFocused`，而且挂在包住
> `MentionTextField` 的 **Box** 上 —— 焦点其实在内部的 `BasicTextField`，父节点的
> `isFocused` 永远 false ⇒「点输入框后键盘起来了，但相册/表情面板没关」。
> 现在把 `focusRequester` + `onFocusChanged` 直接交给 `MentionTextField` 的 `fieldModifier`，
> 挂在 BasicTextField 自己身上。

**QQ 侧的补充证据（同一轮反编译）**

| 行为 | QQ 实现 |
|---|---|
| 返回键优先级 | `AIOActivity.onBackPressed` → `AIODelegate.c()` → `ChatFragmentProvider.onBackPressed` → `BackEventEmitter` 遍历所有消费者；**`PanelBackPressHelper`（`c3/.../aio/helper/bi.java:126-159`）**：语音录制 → 消费；`isShowPanel` → 发 `HideAllPanelMsgIntent` **只关面板并消费**；否则返回 false → 一路到 `AIOActivity.super.onBackPressed()` 才离开聊天 |
| 键盘态返回键 | QQ 代码里**没有**专门分支（无 `isShowKeyboard` 处理），推测交给系统 IME 自己消费 —— 本项目仍补了一个「先收键盘」的分支，行为一致 |
| 点消息列表 | `AIOMsgListVM.x0()`（`c3/.../aio/msglist/AIOMsgListVM.java:636-644`）：`isShowKeyboard ? HideAllPanelAndKeyboard : (isShowPanel ? HideAllPanel : 无)` |
| 相册面板**内部**空白点击 | **不关闭**。面板根布局 `InterceptParentTouchLayout.dispatchTouchEvent` 里 `getParent().requestDisallowInterceptTouchEvent(true)` 后 **`return true` 无条件吃掉所有触摸**（`c12/.../qqui/widget/InterceptParentTouchLayout.java:43-66`）<br>⚠ 本项目**按用户明确要求**改成「点相册空白 = 关闭」，与 QQ 有意不同 |
| 面板出现时的键盘处理 | 先 `imm.hideSoftInputFromWindow(...)`，再等键盘隐藏回调后才展开面板（`panelcontainer/j.java:523, 532-542`），动画 150ms |

**聊天区不被盖住（贴底）**：QQ 的做法是消息列表用 `AIOLayoutManager.setStackFromEnd(true)`
（`AbsMsgListVB$recyclerView$2.java:59-60`，`AIOLinearLayoutManager.java:317` 锚到 `itemCount-1`），
所以输入区高度变化时列表**自动保持末尾贴底**，全仓库确实找不到 `scrollToBottom` 调用；
高度变化只改列表底部 padding（`AIOMsgListVB.f3()`，`c5/.../AIOMsgListVB.java:754-773`）。

Compose 的 LazyColumn 没有 `stackFromEnd`，本项目用等价实现：
面板是**真实布局兄弟节点**（不是浮层，聊天区 `weight(1f)` 会自动变矮），
并在「原本贴着底部」时于两个 frame 后 `scrollToItem(最后一条)`，
保证最新的消息（包括自己刚发的）始终露在输入栏上方。


### 3.14 相册缩略图宽度真相：**不是固定 158dp**（纠正 3.1.2）

旧结论「多张时每张 158dp」**只适用于「无相册权限」占位项**，不是缩略图：

```java
// PhotoPanelAdapter.java:175-181
private final void f0(RecyclerView.ViewHolder holder) {
    if (holder instanceof com.tencent.mobileqq.aio.panel.photo.c) {   // ← 只有占位项 holder c（itemViewType==100）
        holder.itemView.getLayoutParams().width =
            getItemCount() == 1 ? MATCH_PARENT : d.a(158);
    }
}
```

缩略图项（holder `t`，布局 `layout/ez3`）**代码里从不设置宽度**，宽度是布局量出来的：

| 证据 | 说明 |
|---|---|
| `layout/ez3` 根 `RelativeLayout`：`layout_width=wrap_content`、`layout_height=match_parent`（aapt2 dump） | 宽度交给子 View 决定 |
| 内部 `URLImageView`：`match_parent × match_parent`、`scaleType=6`(CENTER_CROP)、`adjustViewBounds=true`、`maxWidth=300dp`、`minWidth=50dp` | 关键三件套 |
| RecyclerView 给 wrap_content 子项的是 `AT_MOST`（不是 EXACTLY）→ `MATCH_PARENT` 子 View 拿到的也是 `AT_MOST` → **`adjustViewBounds` 生效** | ImageView 按图片宽高比自适应宽度，被 50dp/300dp 夹住 |
| `PhotoPanelAdapter.java:440-446` 只是把 LP 复位成 `-1/-1`（滑出动效改过 LP 时） | 不覆盖宽度 |
| 图片加载链路只 `setImageDrawable`（`c5 .../libra/LibraPicLoader.java:570`），全程无 `setScaleType`/`setAdjustViewBounds` | 缩放方式就是 XML 的 CENTER_CROP |
| 间距：`ItemSwipeSendDecoration.getItemOffsets` 里 `rect.setEmpty()`（`itemswipe.java:614-621`）+ item 左右各 2dp margin | 相邻间距 = 4dp，无竖直间距 |

**所以 QQ 的缩略图 = 高度填满面板内容区、宽度按图片宽高比算（50~300dp），整张图完整显示**，
不是「固定 158dp 再居中裁一刀」。

本项目实现（`ui/HalfScreenAlbum.kt`）：
`MediaStore` 多查 `WIDTH`/`HEIGHT` → `w = 内容区高 × (imgW/imgH)`，`coerceIn(50dp, 300dp)`，
外加左右各 2dp margin；拿不到宽高时退回 158dp。

**其它一并纠正的细节**：

| 项 | 旧文档 | 真相（证据） |
|---|---|---|
| 相册底栏内边距 | 「真实 25dp 四边」 | **错**。`PhotoPanelVB.e()` 里的 `AIOUtil.d()` 是 **TouchDelegate 热区**（`aio/utils/AIOUtil.java:163-193`），不是 padding ⇒ 底栏就是 XML 的 `minHeight=60dp`、`paddingTop=11dp`、`paddingStart/End=17dp`（`r/i/ez5.xml` aapt2） |
| 选中角标 | 「20dp 方形 CheckBox」 | **错**。`QUICheckBox` style `a5w`：`24dp × 24dp`（dimen/d1_=24dp）、`textSize 14sp`、白色居中；未选中 = `drawable/qui_common_check_box_unchecked_white_border`（圆 r=23/48，填充 `#4D000000`，白描边）+ 选中 = `quicheckbox/d.f()`：**圆形 OVAL**，填充 `qui_common_brand_standard = #FF0099FF`，1dp `qui_common_bubble_guest(#FFFFFFFF)` 描边（`c8 .../quicheckbox/QUICheckBox.java:44-120`、`d.java:41-43`、`_qqres` style 0x7f130397） |
| 「暂无图片」颜色 | — | `qui_common_text_secondary = #8F3C3C43`（`r/l/qui_common_text_secondary.xml`） |
| 底栏文字色 | — | `qui_common_text_primary = #E6000000` |


### 3.15 聊天页图片气泡尺寸：QQ 的真实公式（2026-11-15 反编译）

**问题**：本项目原来是 `widthIn(max = 200.dp)` 且**不限高**，1080×1920 的竖图显示成 200×355dp，
一条图片消息吃掉大半屏，把最新消息挤出可视区（表现为「只看到头像和昵称、图片只露一部分」）。

**QQ 的真实规则**（调用链 `AIOPicContentComponent` → `PicContentViewUtil.h()/j()` → `PicUtil.l()/j()` → `NTAIOImgThumbHelper`）：

| 项 | 值 | 出处 |
|---|---|---|
| 可用宽 | `min(AIO视图宽, 屏宽) − 108dp − 28dp` | `NTAIOImgThumbHelper.java:145-167`；108dp = `ViewUtils.b(108)`；28dp = 8+10+10dp（`dimen/bnw`=8dp、`bnu`=10dp、`bnt`=10dp） |
| 长边上限 | `0.85 × 可用宽`；原图长边 ≤200dp 的小图固定 **135dp** | `AIOPicThumbSizeConfig` 默认 `maxRatio=0.85f`（`:271`）；`NTAIOImgThumbHelper.java:98-111` |
| 短边下限 | `0.28 × 可用宽`；小图 **45dp** | `minRatio=0.28f`；`NTAIOImgThumbHelper.java:130` |
| 缩放 | `k = max(上限/长边, 下限/短边)`，宽高同乘 `k`（等比） | `PicUtil.java:77-80` |
| 最终夹取 | 宽、高**各自** `coerceAtMost(上限)` → 极端比例变非等比，靠 `CENTER_CROP` 裁 | `PicUtil.java:82` |
| 圆角 | **10dp**（`RoundBubbleImageView.setRadiusDP(10f)`，`canvas.clipPath` 硬裁，不是背景 drawable） | `AIOPicContentComponent.java:117-122`、`RoundBubbleImageView.java:77-87` |
| 气泡内边距 | 图片 View 外面 8dp×4（气泡整体 = 图片 + 16dp） | `AIOPicContentComponent.java:177-180` |
| scaleType | `CENTER_CROP`，背景置空 | `PicContentViewUtil.java:390-391` |
| 超长图 | 超过 3:1 **只切换缩略图 cacheKey**，尺寸规则不变 | `PicContentViewUtil.i()` 字节码 0x00a5–0x00c4 |

**推算结果（360dp 宽屏）**：可用宽 224dp → 长边上限 **190.4dp**、短边下限 **62.72dp**；
1080×1920 竖图 → **107×190dp**；1920×1080 横图 → 190×107dp；1080×5400 长截图 → 62.7×190dp（裁切显示）。

本项目实现：`ui/ChatImageSpec.kt`（纯函数，可单测）+ `ui/ChatScreen.kt` 的 `ChatImage`；
单测 `ChatImageSpecTest` 锁定 9 条（竖图不撑满屏、横图、正方、长截图、小图 135dp 上限、
小图不被放大、尺寸未知占位、可用宽变大时同比放大）。

**列表行为（新消息 / 上滑）** —— QQ 的做法：

| 项 | 实现 | 出处 |
|---|---|---|
| 末尾锚定 | `AIOLayoutManager.setStackFromEnd(true)` | `AbsMsgListVB$recyclerView$2.java:59-60` |
| 收新消息 | `BaseMsgRepo.c0(true, true)`（checkFocus + **动画**） | `MsgNotificationAbility.v()` 字节码 0x0406-0x043c |
| 自己发消息 | `c0(false, true)`（不做 focus 检查，**带动画**） | `MsgNotificationAbility.java:90` |
| 是否贴底 | `last >= size - 2 && last < size` | `scrollers/a.java:112-121` |
| 贴底 → 平滑滚 | `LinearSmoothScroller.startSmoothScroll` | `scrollers/a.java:112-172` |
| 不贴底 → 不动 | 显示未读数气泡 / 「回到底部」箭头（`drawable/aio_back_bottom`，描述「回到底部.」） | `UnreadBubbleVB.java:85-99,149-167` |
| 点箭头回底 | `c0(false, false)` → **无动画**直接跳 | `AIOMsgRepo.java:1039-1052` |

本项目：`LazyColumn(reverseLayout = true)` + 数据倒序（= `stackFromEnd`），
所以进群**首帧就停在最新一条**，不再有「先显示旧消息再强跳最新」的闪屏；
新消息到达时贴底才 `animateScrollToItem(0)`；不贴底时右下角显示「回到底部」箭头。


### 3.16 轻提示（toast）重复弹出 —— 根因与修复（2026-11-15）

**现象**：点「清除昵称缓存」弹一次提示，进出群聊页面又弹一次，再进再退又弹；表情相关提示同理。

**根因**（三个问题叠加）：
1. 提示状态放在 `UiState.toast: String?` 里，消费方式是
   `LaunchedEffect(state.toast) { snackbar.showSnackbar(it); vm.consumeToast() }`，
   而 `showSnackbar` 是**挂起函数，要等提示自己消失才返回**；
2. `SnackbarHost` 只挂在「主界面」的 `Scaffold` 上。提示还没消失就进了聊天页 → 那个 `LaunchedEffect`
   随主界面一起被移出组合（协程被取消）→ `consumeToast()` **永远不执行** → `toast` 一直是那条文本；
3. 回到主界面后 `LaunchedEffect(state.toast)` 因为 key（文本）没变不会自动重跑 ——
   但只要有一次重组把它重新进入组合（进出群聊正是），就会再弹一次，且每次都消费不掉。

**修复**：
- `UiState` 增加 `toastSeq: Long`，每次 `toast()` 自增；
- 提示渲染提到**根组合**（`QQAppContent` 里的 `GlobalToast`），所有页面（含聊天页、相机页）共用同一个 host；
- `LaunchedEffect(toastSeq)` 里**先 `onShown()`（立刻 consume）再显示**，
  这样即使中途切页面、协程被取消，状态也已经被消费，不可能重复弹；
- 改用自绘的深色圆角提示条（QQ 风格），不再依赖只在主界面存在的 `SnackbarHost`。


## 四、待做的事（逐层对比清单）

对每一层，打开对应 QQ 类 → 找到真实行为 → 打开本项目实现 → 比对差异 → 修正 → 记录结论。

### 第 1 层：相册面板（聊天页半屏）
> 面板布局 = `layout/ez5`（`xu1.an`），item 布局 = `layout/ez3`（`xu1.al`），全部已解出（3.1.2 / 3.1.6）

| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 滚动方向（横向 vs 网格） | `PhotoPanelVB.java:379` `new LinearLayoutManager(ctx, 0, false)` | `HalfScreenAlbum.kt` LazyRow | 已复核（依据：PhotoPanelVB.bindViewAndData 字节码确认 `setLayoutManager(new LinearLayoutManager(ctx,0,false))`） |
| **缩略图宽度** | `layout/ez3` 根 wrap_content + 图 `adjustViewBounds` + `maxWidth=300dp/minWidth=50dp`（`PhotoPanelAdapter.f0()` 的 158dp **只用于权限占位项**） | 原固定 158dp + 全高 Crop（又长又宽还裁图） | **已改**为 `宽 = 内容区高 × 图片宽高比`，夹 50~300dp（依据：3.14） |
| 单张时的宽度 | 同上（占位项才有 MATCH_PARENT） | 原单张直接铺满屏宽 | **已改**：单张同样按宽高比、最多 300dp（依据：3.14） |
| 方向间 - 间距 | `layout/ez3` `dq3 marginLeft/Right=2dp`；`ItemSwipeSendDecoration.getItemOffsets` = `rect.setEmpty()` | 原为 6dp + 8dp contentPadding | **已改**（依据：3.1.2 / 3.14） |
| item 圆角/角标 | `layout/ez3`：无圆角；角标 `QUICheckBox(style a5w)` = **24dp 圆形**、14sp 白字居中、未选中 `#4D000000`+白圈、选中 `#FF0099FF`+白圈 | 原为圆角6dp + 20dp 方形角标 | **已改**（依据：3.14） |
| 选中遮罩 | `id/dq4` background `#33000000`，选中可见 | 无 | **已改**（依据：3.1.2） |
| 视频时长角标 | `id/drh` 12sp 白字、17dp 高、5dp 内边距、`mgq`(dark,radius18) 底、右下 4dp | 无 | **已改**（依据：3.1.2） |
| 已编辑角标 | `id/udx` 17dp、`mgp`(dark oval) 底、`qui_edit_icon_white`、左下 5dp | 无 | **已改**（依据：3.1.2） |
| 滑出发送手势阈值 | `ItemSwipeSendDecoration.select` 字节码 | 原 `hypot(dx,dy)>120px` | **已改**为 `abs(dy) > 条高 × 按下TouchY / 条目高`（依据：3.1.4） |
| 滑出发送方向 | `h.e()` = 257 → 仅上/下 | 原用 detectHorizontalDragGestures | **已改**为竖向（依据：3.1.4） |
| 拖动中视觉 | `h.l()` alpha `1-0.5*progress`、scale `1-0.3*progress`、progress = `abs(dy)/按下TouchY` | 无 | **已改**（依据：3.1.4） |
| 飞出动画 | `h.c()` = 300ms；`h.h()` 位移 = `rv.height + 48dp + 输入栏高` | 无 | **已改**（依据：3.1.4） |
| 滑出只发这一张且**面板不关** | `h.m()` → `i.h(mediaInfo,false)` → `sendSingleMediaInfo`，之后只 `notifyDataSetChanged()` | 原发送后直接关面板 | **已改**（依据：3.1.6） |
| 发送后关闭面板 | `PhotoPanelVM` `PhotoPanelActive(false)` | `MainActivity.kt` | 已复核（依据：`PhotoPanelVM.onDestroy` 派发 `PhotoPanelActive(false)`；`onCreate` 派发 `true`） |
| 底栏「相册 / 编辑 / 原图(大小) / 发送」 | `layout/ez5` 底栏 + `PhotoPanelVB` | 原为自造「取消 / 标题 / 发送」 | **已改**（依据：3.1.6） |
| 编辑按钮 enable = 单选一张 | `PhotoPanelVB.handleUIState` `size == 1` | 无编辑按钮 | **已改** |
| 发送按钮文案与 enable | `"发送" + (size>0?"($size)":"")` | 原「发送」/「发送(N)」且始终可点 | **已改** |
| 点击图片 = 打开全屏预览 | `PhotoPanelAdapter.b0` → `PhotoPanelVM.h0` | 原点击即勾选 | **已改** |
| 最大选择数 20（我的电脑 50） | `PhotoPanelVB.A0` | 原 9 | **已改** |
| 「暂无图片」空态 + 无权限跳设置位 | `layout/ez5` + `PhotoPanelVM.h0` | 原「相册里没有图片」 | **已改** |
| **底栏内边距** | `layout/ez5`：`minHeight=60dp`、`paddingTop=11dp`、`paddingStart/End=17dp`（`AIOUtil.d()` 是 TouchDelegate，不是 padding） | 原按旧结论写成 25dp 四边 | **已改**为 XML 原值（依据：3.14） |
| **面板高度** | 由容器给：`max(键盘高, 280dp)`（`panelcontainer.j#w()`），面板自己不设高度 | 原用 `weight(1f)` 抢半屏 | **已改**为槽位统一高度（依据：3.13） |

### 第 2 层：表情面板
| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 行列数 3×7 | `SystemEmoticonPanelViewBinder` `setRowColumnNum(3,7)` | `EmojiPanel.kt` | 已复核（依据：SystemEmoticonPanelViewBinder.setRowColumnNum 原文） |
| 删除键位置 | 同上 `hasDeleteBtn` + `action="delete"` | 同上 | 已复核（依据：`EmoticonAdapter.getRealIndex` `(i+1)%pageTotalNum==0 → -1`，即每页末格） |
| 删除键图标 | `imageView.setImageResource(2131233892)` = `@drawable/cjz`（xhdpi png），contentDescription `2131919530`=「删除」 | 原为 `⌫` 文字 | **已改**为图标资源 |
| 每格表情尺寸 | `EmoticonPanelViewBinder.DefaultEmoticonAdapter`：`classicEmoticonSize = density * 30f` → **30dp** | 原 38dp 格内 30dp 图 | **已改**为按 30dp 图 + 均分格（依据：`layoutParams.width/height = classicEmoticonSize`） |
| 每格布局 | `EmoticonLinearLayout.onStructChanged`：外层垂直 LinearLayout，每行 `weight=1`；每格 RelativeLayout `weight=1` 内一个 URLImageView `addRule(13)` 居中、`FIT_XY` | 原 SpaceEvenly | **已改**为 weight 均分 |
| 底部栏 45dp + 0.5dp 线 | `emoticon_aio_panel.xml` `dimen/nw` | 同上 | 已复核（依据：`layout/emoticon_aio_panel` 线性底部栏 height=@dimen/nw=45dp，内含 height=0.5dp 的 View） |
| 左侧 51dp 元素 | `id/btn_more_emoticon`，contentDescription「表情商城入口」 | 原为「😊 回第一页」 | **已改**为隐藏（本项目无表情商城）（依据：3.1.5） |
| 页签列表横向滚动 | `id/tab_sencondary_emoticon` = `HorizontalListViewEx`，由 `BasePanelView` 找 id `2131394969` 绑定 `EmoticonTabAdapter` | 原 LazyRow 数字 | 已复核（依据：BasePanelView 构造 + layout/emoticon_aio_panel id 完全吻合） |
| 分页指示器形态 | `EmoticonPagerRadioGroup.createButton`：40dp×10dp、gravity=center、左右 margin 3dp、`qui_systememojipanel_radio_btn` | 原「1 2 3」文字 | **已改**为小圆点 |
| 分页指示器颜色 | 非选中 `#FFCCCCCE`(icon_tertiary)、选中 `#FF929296`(icon_secondary) | 原蓝色 | **已改** |
| 指示器贴底居中 | `layout/h80` RadioGroup `wrap_content` + `alignParentBottom` + `centerHorizontal` + `marginBottom=3dp` | 原在底部栏右侧 | **已改** |

### 第 3 层：输入栏
| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 图标顺序（语音/图片/相机/表情/加号） | `classes4.dex` 引用 6 个图标；`aio.input` 无 layout，程序化组装（3.1.3） | `ChatScreen.kt` `ChatInputBar` | 已复核（依据：3.1.3，QQ 无 layout 可抄，按 drawable 资源名 + 交互约定实现） |
| 图标尺寸与间距 | **QQ 无 layout，不可考**（3.1.3） | 24dp/38dp | 已复核（依据：3.1.3）—— 无法从 APK 取到原始数值 |
| 表情/加号按钮双态图标 | `skin_aio_keyboard_nor` / `skin_aio_more_nor` | 槽位被占时切键盘图标，再点 = 收起面板回到键盘 | **已改**（依据：3.13） |

### 第 4 层：键盘 / 面板互斥
| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 键盘与面板同一槽位 | `HideKeyBoardAndPanel`（c6） | `ChatScreen.kt` 单一 `panel: ChatPanelKind` + 槽位高度统一 | **已改**（依据：3.13） |
| 打开面板先收键盘 | 面板容器统一管理 | `togglePanel()` 里 `clearFocus() + keyboard.hide()` | **已改** |
| 点输入框 → 关掉面板再来键盘 | — | `onFocusChanged.hasFocus` → `panel = NONE` + 关相册 | **已改**（旧 bug：用了 `isFocused`，父 Box 永远收不到） |
| 点聊天区空白 → 全收 | 同上 | 聊天区 `pointerInput` + 消息行 `onClick` → `closeAllPanels()` | **已改** |
| 点面板内空白 → 关闭 | — | 相册内容区背景 `clickable` → `onDismiss()` | **已改**（按用户要求） |
| **系统返回键先关面板** | `HideKeyBoardAndPanel` + AIO 返回优先级 | `BackHandler(enabled = 面板非空 || 键盘可见)`：面板 → 键盘 → 退群聊 | **已改** |
| **最新消息不被面板遮挡** | 聊天列表贴底 | 面板是布局兄弟节点（聊天区 `weight(1f)` 自动变矮）+ 开合时若原本贴底则两帧后 `scrollToItem(最后一条)` | **已改**（QQ 消息列表 part 未反编译，按观感实现，标注推断） |
| 面板高度 = max(键盘高, 280dp) | `panelcontainer.j#w()` | `ChatPanelSpec.MinHeight = 280.dp` + 实测 IME 高度取大 | **已改**（依据：3.13） |

### 第 5 层：消息气泡 / 引用 / 拖放区
| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 拖放区文案与淡蓝 | string `0x7f121e8a`「拖到此区域发送」/ `0x7f121e8b`「松手发送」、`qui_common_brand_light`=#330099FF | `HalfScreenAlbum`（已改为横向，拖放区需重做） | **待复核** |
| 左滑引用 | `GestureEvent.LeftSwipeEvent`（c6）—— 已确认**只有 `handled` 布尔字段**，不含方向/阈值 | `SwipeQuoteRow.kt` | 已复核（依据：`AIOGestureEvent`/`GestureEvent` 全部字段只有一个 `boolean d`；`NormalMenuHelper` 只把它当作「关闭菜单」的触发源）→ 方向与手势参数 **QQ 侧无对应证据，属本项目自定义** |
| 引用条样式 | 颜色已确认 | `ChatScreen.kt` | 已对齐 |
| **图片气泡尺寸** | `PicUtil.l()` + `NTAIOImgThumbHelper`：长边 `0.85×(屏宽−136dp)`、短边 `0.28×…`、小图 135/45dp、宽高各自夹上限 | 原 `widthIn(max=200.dp)` 且不限高（竖图 200×355dp，吃掉大半屏） | **已改**为 `ChatImageSpec`（依据：3.15），并加 9 条单测 |
| 图片圆角 | `RoundBubbleImageView.setRadiusDP(10f)` | 原 4dp | **已改**为 10dp |
| 图片 scaleType | `CENTER_CROP` | Crop | 已对齐 |
| 列表末尾锚定 | `AIOLayoutManager.setStackFromEnd(true)` | 原用「先滚到最底 + 延迟 16ms 再滚」的补偿，会闪一下 | **已改**为 `reverseLayout=true` + 数据倒序（依据：3.15） |
| 上滑后新消息 | 不跟随，显示未读数 / 「回到底部」箭头 | 无 | **已改**为右下角箭头 |

### 第 6 层：其他
- 消息列表项、群头像（QQ 有真实群头像，**机器人 API 拿不到**，见二.5）
- 图片全屏查看的缩放/长按菜单


---

## 五、方法建议

1. **先搜类再反编译**：用 python 逐 dex 搜 `Lcom/tencent/...;` 形式，比整包反编译快得多。
2. **方法体被跳过时**加 `--show-bad-code`，虽然不可读，但能拿到方法内调用的类名和字符串（常常足够推断行为）。
3. **布局优先查 resources.arsc**：`aapt2 dump xmltree --file r/i/xxx.xml`，比反编译更准。
4. **不要凭印象写**：本项目前几版就是因为「按理解实现」导致相册做成九宫格、@全体变成待发送等回归，返工多次。
5. 每改一处，在本文档第四节表格里把 **待复核** 改成 **已复核（依据：xxx）**。

---

## 六、本项目构建 / 发布

```powershell
cd D:\code\QQBotQQ
.\gradlew.bat testDebugUnitTest          # 32 个单元测试
.\gradlew.bat assembleRelease            # 产物 app/build/outputs/apk/release/QQBot.apk
```

- 签名密钥 `keystore.jks` 在项目根目录（已 gitignore，**不要提交**）
- 推送到 `main` 后 GitHub Actions 自动构建并发布到 Releases
- 固定下载链接：`https://github.com/wei-la-ya/QQBot/releases/latest/download/QQBot.apk`
- CI secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD` / `KEY_ALIAS`

### 6.1 签名规范（唯一可信来源）

发布密钥固定，**换密钥 = 所有已安装用户无法升级**，所以指纹写死在代码里并强校验：

```
CN=QQBotQQ, OU=Local, O=Private, L=Local, ST=Local, C=CN
SHA-256 = a695b1e8f15e40b6e5219d6dd9d41c68985b7907cfd669907cd878cc9685e005
```

| 环节 | 位置 | 说明 |
|---|---|---|
| 指纹常量 | `app/build.gradle.kts` `expectedCertSha256` | 公开信息，可入库 |
| 本地签名 | `local.properties` 的 `QQBOT_STORE_FILE/PASSWORD`、`QQBOT_KEY_ALIAS/PASSWORD` | 已 gitignore；配好后 `assembleRelease` 自动签名 |
| 本地校验 | `./gradlew verifyReleaseSignature` | 未签名 / 指纹不符 → 直接失败 |
| CI 签名 | workflow `Sign release APK`（secrets 解码成 release.jks 后 apksigner 签） | 密钥只在 secrets |
| CI 校验 | workflow `Verify APK signature` | 指纹不符 `exit 1`，坏包不会进 Releases |

已实测验证（本轮）：
- 用临时密钥构建 → 产物确实被自动签名（证明 `signingConfig` 接线生效）
- 用临时密钥跑 `verifyReleaseSignature` → **BUILD FAILED**，正确拦截指纹不符
- 无凭据构建 → 产物未签名，`verifyReleaseSignature` 报「未签名」并给出配置指引
- 清理临时密钥后重新构建，根目录 `QQBot.apk` 未被污染（仍为 `a695b1e8…`）

历史事实（已核对，供后续参考）：
- 用户手机上曾安装的 **v1.1（versionCode 2）** 证书 SHA-256 也是 `a695b1e8…5e005`，
  因此 v1.1 → v1.7 可**直接覆盖安装**，无需卸载
- 反例：`assembleDebug` 产出的是 Android 公共调试证书 `839b335af8c8…1c55`，**不可对外分发**

---

## 七、v1.7 变更记录（本轮复核后的落地）

> 本轮先反编译复核全部 5 个「未验证/存疑」项（结论见 3.1），再按结论改代码。
> 验证：`testDebugUnitTest` 32 个用例全通过，`assembleRelease` 成功。

### 相册（`HalfScreenAlbum.kt` 重写）
- 底栏改为 QQ `layout/ez5` 形态：**相册 / 编辑 / ☑原图 (999K) / 发送**，
  去掉自造的「取消 + 标题」；底栏内边距 25dp（`PhotoPanelVB.e()` 的运行时覆盖值）
- item 改为 `layout/ez3`：图片左右 2dp 外边距、**无圆角**、
  右上 20dp 圆形 CheckBox 显示选中序号、选中整图盖 `#33000000`、
  视频右下角时长胶囊（12sp/17dp/radius18）、空态「暂无图片」17sp
- 点击图片 → 全屏预览；勾选只走右上角 CheckBox（与 QQ 一致）
- 滑出发送改为**竖向 + 向上**，阈值 = `条高 × 按下TouchY / 条目高`，
  progress = `|dy| / 按下TouchY`，飞出 300ms（`rv.height + 48dp`），向下拖动被夹住不动
- 滑出发送**不再关闭面板**，且只发滑出的那一张
- 最大选择数 9 → **20**（QQ 默认，我的电脑场景才是 50）
- 无相册权限 → 显示「点击前往设置」并可重试申请

### 表情面板（`EmojiPanel.kt` 重写）
- 每格改为 `weight` 均分（对齐 `EmoticonLinearLayout.onStructChanged`），表情 **30dp**
- 删除键换成 QQ 真实图标：从 APK 提取 `r/k/cjz.png` →
  `res/drawable-xhdpi/qq_emoticon_delete_key.png`
- 分页指示器从「1 2 3 文字」改为 **圆点**：40dp×10dp 可点区域、左右 margin 3dp、
  非选中 `#FFCCCCCE` / 选中 `#FF929296`，并**贴底居中**（`layout/h80` 的 RadioGroup 形态）
- 左侧 51dp 的 `btn_more_emoticon`（「表情商城入口」）不再用「😊 回第一页」冒充 ——
  本项目无表情商城，改为留白不渲染

### 输入栏（`ChatScreen.kt`）
- 表情按钮 / 加号按钮在面板展开时切换为**键盘图标**（`skin_aio_keyboard_nor`），
  收起时恢复原图标 —— 对应 `classes4.dex` 同时引用键盘与表情/更多两组图标
- ⚠️ 输入栏的**顺序与尺寸仍无法从 APK 取证**（程序化构建，无 layout），见 3.1.3

### 仍未做 / 不可做
- **编辑图片**：QQ 走 `IPhotoPanelApi.onEditBtnClicked`（独立图片编辑模块），
  客户端未接入，点「编辑」只提示。按钮 enable 规则已对齐（仅单选一张可用）
- **原图发送**：QQ 官方机器人 API 无「原图」语义，勾选只影响底栏大小显示，不影响上传
- 输入栏图标顺序/尺寸、`ItemSwipeSendDecoration` 的 `scale/alpha` 精确 outline 半径：
  前者无取证来源，后者需要 `h.a`（OutlineProvider）反编译，本轮未做
- 全部结论仍**未经真机验证**（本机无设备/模拟器）
