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

## 四、待做的事（逐层对比清单）

对每一层，打开对应 QQ 类 → 找到真实行为 → 打开本项目实现 → 比对差异 → 修正 → 记录结论。

### 第 1 层：相册面板（聊天页半屏）
> 面板布局 = `layout/ez5`（`xu1.an`），item 布局 = `layout/ez3`（`xu1.al`），全部已解出（3.1.2 / 3.1.6）

| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 滚动方向（横向 vs 网格） | `PhotoPanelVB.java:379` `new LinearLayoutManager(ctx, 0, false)` | `HalfScreenAlbum.kt` LazyRow | 已复核（依据：PhotoPanelVB.bindViewAndData 字节码确认 `setLayoutManager(new LinearLayoutManager(ctx,0,false))`） |
| 单张/多张宽度策略 | `PhotoPanelAdapter.f0()` | `HalfScreenAlbum.kt` | 已复核（依据：`f0()` `getItemCount()==1 ? -1 : d.a(158)`） |
| 方向间 - 间距 | `layout/ez3` `dq3 marginLeft/Right=2dp` | 原为 6dp + 8dp contentPadding | **已改**（依据：3.1.2） |
| item 圆角/角标/间距 | `layout/ez3` + `PhotoPanelAdapter.onBindViewHolder` | 原为圆角6dp+圆形序号角标 | **已改**，无圆角、方形20dp CheckBox 显序号（依据：3.1.2） |
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
| 底栏真实内边距 = 25dp（覆盖 XML 17dp） | `PhotoPanelVB.e()` `AIOUtil.d(j,25,25,25,25)` + `post` | — | **已改** |

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
| 表情/加号按钮双态图标 | `skin_aio_keyboard_nor` / `skin_aio_more_nor` | 未实现键盘图标态 | 待改 |

### 第 4 层：键盘 / 面板互斥
| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 键盘与面板同一槽位 | `HideKeyBoardAndPanel` | `ChatScreen.kt` | 已按此实现，**待复核细节**（如点空白区域、返回键行为） |

### 第 5 层：消息气泡 / 引用 / 拖放区
| 对比项 | QQ 出处 | 本项目 | 状态 |
|---|---|---|---|
| 拖放区文案与淡蓝 | string `0x7f121e8a`「拖到此区域发送」/ `0x7f121e8b`「松手发送」、`qui_common_brand_light`=#330099FF | `HalfScreenAlbum`（已改为横向，拖放区需重做） | **待复核** |
| 左滑引用 | `GestureEvent.LeftSwipeEvent`（c6）—— 已确认**只有 `handled` 布尔字段**，不含方向/阈值 | `SwipeQuoteRow.kt` | 已复核（依据：`AIOGestureEvent`/`GestureEvent` 全部字段只有一个 `boolean d`；`NormalMenuHelper` 只把它当作「关闭菜单」的触发源）→ 方向与手势参数 **QQ 侧无对应证据，属本项目自定义** |
| 引用条样式 | 颜色已确认 | `ChatScreen.kt` | 已对齐 |

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
