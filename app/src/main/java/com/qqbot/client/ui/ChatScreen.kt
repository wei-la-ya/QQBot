package com.qqbot.client.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qqbot.client.R
import com.qqbot.client.bot.ChatMessage
import com.qqbot.client.bot.MsgNode
import com.qqbot.client.bot.GroupMember
import com.qqbot.client.bot.MessageSegment
import com.qqbot.client.data.BotAccount
import com.qqbot.client.data.Conversation
import com.qqbot.client.vm.PendingSend
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 聊天页（QQ AIO 界面）。
 *
 * 已对齐的 QQ 交互：
 *  - 长按消息 → 浮动菜单（引用 / 撤回 / 复制 / 查看资料）
 *  - 按住消息向左滑动 → 松手引用（带回弹动画与图标反馈）
 *  - 输入框输入 @ → 向上弹出成员候选列表（左头像右昵称），选中后蓝色高亮
 *  - 上滑自动加载更早消息，下滑回到底部
 */
@Composable
fun ChatScreen(
    conversation: Conversation,
    messages: List<ChatMessage>,
    account: BotAccount?,
    members: List<GroupMember>,
    pendingSends: List<PendingSend>,
    hasMoreOlder: Boolean,
    loadingOlder: Boolean,
    markdownMode: Boolean,
    highlightMsgId: String?,
    onToggleMarkdown: (Boolean) -> Unit,
    onBack: () -> Unit,
    onLoadOlder: () -> Unit,
    onSend: (text: String, quoted: ChatMessage?, atMembers: List<GroupMember>, atEveryone: Boolean) -> Unit,
    /** 图文混发：附件与文字一起发送 */
    onSendWithAttachments: (
        text: String,
        quoted: ChatMessage?,
        atMembers: List<GroupMember>,
        atEveryone: Boolean,
        attachments: List<android.net.Uri>,
    ) -> Unit = { t, q, a, e, _ -> onSend(t, q, a, e) },
    /** 选图后挂起的待发送附件 */
    onAttachmentsPicked: (List<android.net.Uri>) -> Unit = {},
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickCamera: () -> Unit,
    onRecall: (String) -> Unit,
    onShowInfo: () -> Unit,
    onShowMemberCard: (GroupMember) -> Unit,
    onSearch: () -> Unit,
    onSeenHighlight: () -> Unit,
    onImageClick: (String) -> Unit = {},
    /** 外部要求插入到输入框的文本（长按头像 / 资料卡「提醒TA」） */
    insertText: String? = null,
    onInsertConsumed: () -> Unit = {},
    /** 由外部持有的待发送附件（相册勾选后带出，可继续输入文字一起发） */
    attachments: List<android.net.Uri> = emptyList(),
    onAttachmentsChange: (List<android.net.Uri>) -> Unit = {},
    /** 打开相册：由外部统一处理「先收键盘再开面板」的互斥 */
    onOpenAlbum: (video: Boolean) -> Unit = {},
    /** 关闭相册 */
    onCloseAlbum: () -> Unit = {},
    albumVisible: Boolean = false,
    albumMode: AlbumMode = AlbumMode.HALF,
    albumVideoMode: Boolean = false,
    onAlbumDismiss: () -> Unit = {},
    onAlbumConfirm: (List<android.net.Uri>, Boolean) -> Unit = { _, _ -> },
    onAlbumDragToChat: (android.net.Uri) -> Unit = {},
    onAlbumPickFromSystem: () -> Unit = {},
    onAlbumPreview: (android.net.Uri) -> Unit = {},
    onAlbumPermission: () -> Unit = {},
    onAlbumToast: (String) -> Unit = {},
    /** 通用轻提示（表情面板里点「超级表情」时用） */
    onToast: (String) -> Unit = {},
) {
    var draft by remember(conversation.chatKey) { mutableStateOf("") }

    // 外部插入 @昵称：追加到草稿末尾并自动补空格（与 QQ 长按头像行为一致）
    LaunchedEffect(insertText) {
        val ins = insertText
        if (!ins.isNullOrEmpty()) {
            draft = if (draft.isEmpty() || draft.endsWith(" ") || draft.endsWith("\n")) {
                draft + ins
            } else {
                "$draft $ins"
            }
            onInsertConsumed()
        }
    }
    var quote by remember(conversation.chatKey) { mutableStateOf<ChatMessage?>(null) }
    var showPlus by remember { mutableStateOf(false) }
    // 表情面板状态提升到这里，才能和键盘/加号面板统一做互斥
    var showEmoji by remember { mutableStateOf(false) }
    // 合并转发明细弹层
    var forwardView by remember { mutableStateOf<List<MsgNode>?>(null) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // 统一收起：键盘 + 加号面板 + 表情面板 + 相册。QQ 里点聊天区就是全收
    val dismissPanels: () -> Unit = {
        keyboard?.hide()
        showPlus = false
        showEmoji = false
        if (albumVisible) onCloseAlbum()
    }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var showMemberPicker by remember { mutableStateOf(false) }
    // 待发送的附件：由外部（相册）持有，这里只读
    val pendingAttachments = attachments
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    val listState = rememberLazyListState()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 2
        }
    }

    // 进入会话：等消息真正加载完成（非空）后，无动画直接落到底部最新消息
    var initialScrollDone by remember(conversation.chatKey) { mutableStateOf(false) }
    LaunchedEffect(conversation.chatKey, messages.isNotEmpty()) {
        if (!initialScrollDone && messages.isNotEmpty()) {
            // 等下一帧，让 LazyColumn 完成首次测量，再滚到最后一条
            kotlinx.coroutines.delay(16)
            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.scrollToItem(lastIndex)
            initialScrollDone = true
        }
    }

    // 后续新消息：只有本来就贴着底部时才自动跟随
    LaunchedEffect(messages.size) {
        if (initialScrollDone && atBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(conversation.chatKey, hasMoreOlder) {
        if (!hasMoreOlder) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        }.distinctUntilChanged().collect { firstIndex ->
            if (firstIndex <= 3 && hasMoreOlder) onLoadOlder()
        }
    }
    LaunchedEffect(highlightMsgId) {
        if (highlightMsgId != null) {
            val idx = messages.indexOfFirst { it.id == highlightMsgId }
            if (idx >= 0) listState.animateScrollToItem(idx)
            kotlinx.coroutines.delay(1200)
            onSeenHighlight()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().background(QQ.PageBg).imePadding()
    ) {
        // ===== 顶部标题栏 =====
        Row(
            Modifier.fillMaxWidth().height(QQ.TitleBarHeight).background(QQ.TitleBarBg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clickable { onShowInfo() },
            ) {
                Text(
                    text = if (conversation.isGroup && conversation.memberCount > 0)
                        "${conversation.title}(${conversation.memberCount})" else conversation.title,
                    fontSize = QQ.TitleSize, color = QQ.TextPrimary, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 190.dp),
                )
                Text(" ›", fontSize = 17.sp, color = QQ.TextSecondary)
            }
            Text(
                "查找", fontSize = 14.sp, color = QQ.TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp).clickable { onSearch() },
            )
            Text(
                "···", fontSize = 18.sp, color = QQ.TextPrimary,
                modifier = Modifier.padding(horizontal = 10.dp).clickable { showMemberPicker = true },
            )
        }

        // ===== 消息列表（相册打开且为半屏时，聊天区与相册各占一半：weight 1:1）=====
        Box(
            Modifier
                .weight(1f)
                // 与 QQ 一致：点击聊天区域收起键盘、关闭表情/加号面板与相册
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { dismissPanels() })
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                if (hasMoreOlder) {
                    item(key = "__loading__") {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (loadingOlder) {
                                CircularProgressIndicator(
                                    color = QQ.Blue, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("加载中…", fontSize = 12.sp, color = QQ.TextSecondary)
                            } else {
                                Text("上滑加载更多", fontSize = 12.sp, color = QQ.TextHint)
                            }
                        }
                    }
                } else if (messages.isNotEmpty()) {
                    item(key = "__no_more__") {
                        Text(
                            "没有更多消息了", fontSize = 11.sp, color = QQ.TextHint,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    SwipeQuoteRow(
                        msg = msg,
                        onQuote = { quote = msg },
                    ) {
                        MessageRow(
                            msg = msg,
                            isGroup = conversation.isGroup,
                            appId = account?.appId ?: "",
                            selfAvatar = account?.avatarUrl ?: "",
                            selfName = account?.botName ?: "",
                            highlighted = msg.id == highlightMsgId,
                            onLongPress = { if (!msg.recalled) menuFor = msg },
                            onClick = { dismissPanels() },
                            onOpenForward = { nodes -> forwardView = nodes },
                            onAvatarClick = {
                                if (!msg.isSelf && conversation.isGroup) {
                                    val m = members.firstOrNull { it.openId == msg.senderOpenId }
                                        ?: GroupMember(msg.senderOpenId, msg.senderName)
                                    onShowMemberCard(m)
                                }
                            },
                            onAvatarLongPress = {
                                // 长按头像 = @ 该用户：把 @昵称 + 空格 插入输入框
                                if (!msg.isSelf && msg.senderName.isNotEmpty()) {
                                    val insert = "@${msg.senderName} "
                                    draft = if (draft.isEmpty() || draft.endsWith(" ") || draft.endsWith("\n")) {
                                        draft + insert
                                    } else {
                                        "$draft $insert"
                                    }
                                }
                            },
                            onImageClick = onImageClick,
                        )
                    }
                    MessageMenu(
                        expanded = menuFor?.id == msg.id,
                        msg = msg,
                        onDismiss = { menuFor = null },
                        onQuote = { quote = msg; menuFor = null },
                        onRecall = { onRecall(msg.messageId); menuFor = null },
                        onCopy = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(msg.text))
                            menuFor = null
                        },
                        onProfile = {
                            onShowMemberCard(GroupMember(msg.senderOpenId, msg.senderName))
                            menuFor = null
                        },
                    )
                }

                items(pendingSends, key = { it.localId }) { p ->
                    PendingRow(p, selfAvatar = account?.avatarUrl ?: "", selfName = account?.botName ?: "")
                }
            }
        }

        // ===== 引用条（QQ 样式：浅底 + 0.5dp 描边 + 左侧蓝条）=====
        quote?.let { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(Color(0xFFF5F6FA), RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(2.dp).height(30.dp).background(QQ.Blue))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        q.senderName,
                        fontSize = 11.sp, color = QQ.TextSecondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        q.text.take(40).ifEmpty { "[消息]" },
                        fontSize = 12.sp, color = QQ.TextHint,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "×", fontSize = 18.sp, color = QQ.TextSecondary,
                    modifier = Modifier.clickable { quote = null }.padding(horizontal = 8.dp),
                )
            }
        }

        // 与 QQ 一致：@全体成员不再单独占一行状态条，
        // 而是和其它 @ 一样以文本插入输入框（发送时统一解析）

        // ===== 普通 / MD 切换 =====
        ModeSwitchBar(markdownMode, onToggleMarkdown)

        // ===== 输入栏 =====
        ChatInputBar(
            draft = draft,
            onDraftChange = { draft = it },
            members = members,
            appId = account?.appId ?: "",
            showPlus = showPlus,
            showEmoji = showEmoji,
            onShowEmojiChange = { showEmoji = it },
            onToast = onToast,
            // 与 QQ 一致：表情/加号面板和键盘互斥，开面板先收键盘
            onTogglePlus = {
                val next = !showPlus
                showPlus = next
                if (next) {
                    keyboard?.hide()
                    if (albumVisible) onCloseAlbum()
                }
            },
            // 点击输入框：收起面板与相册，让键盘上来
            onInputFocused = {
                if (showPlus) showPlus = false
                if (showEmoji) showEmoji = false
                if (albumVisible) onCloseAlbum()
            },
            onRequestClosePlus = { showPlus = false },
            onSend = {
                // 输入框里只要还有内容（文字 / @ / 附件）就可以发
                if (draft.isNotBlank() || pendingAttachments.isNotEmpty()) {
                    val picked = parseAtChips(draft, members)
                        // "ALL" 是 @全体成员的占位，不能当成真实成员
                        .filter { it.openId != "ALL" }
                        .map { chip ->
                            members.firstOrNull { it.openId == chip.openId }
                                ?: GroupMember(chip.openId, chip.nickname)
                        }.distinctBy { it.openId }
                    // @全体成员 与 @用户 一样从文本里识别
                    val everyOne = draft.contains("@${com.qqbot.client.bot.AtSyntax.EVERYONE_NAME}")
                    // 图文混发：附件与文字一起交给上层发送
                    onSendWithAttachments(draft.trim(), quote, picked, everyOne, pendingAttachments)
                    // 发送后由外部清空附件，这里只清本地输入状态
                    draft = ""
                    quote = null
                }
            },
            onPickImage = {
                showPlus = false
                keyboard?.hide()
                onOpenAlbum(false)
            },
            onPickVideo = {
                showPlus = false
                keyboard?.hide()
                onOpenAlbum(true)
            },
            // 相机不涉及面板，直接收起键盘
            onPickCamera = { showPlus = false; keyboard?.hide(); onPickCamera() },
            // 与 QQ 一致：@全体成员 直接以标签插入输入框
            onAtEveryone = {
                draft = if (draft.isEmpty() || draft.endsWith(" ") || draft.endsWith("\n")) {
                    "$draft@全体成员 "
                } else {
                    "$draft @全体成员 "
                }
            },
            attachments = pendingAttachments,
            onRemoveAttachment = { uri -> onAttachmentsChange(pendingAttachments.filter { it != uri }) },
        )

        // ===== 相册面板（半屏时与聊天区各占一半高度，全屏时铺满）=====
        if (albumVisible) {
            if (albumMode == AlbumMode.HALF) {
                // 同列兄弟节点：聊天区 weight=1，相册 weight=1 → 上半聊天、下半相册
                Box(Modifier.weight(1f)) {
                    AlbumPanel(
                        visible = true,
                        mode = AlbumMode.HALF,
                        videoMode = albumVideoMode,
                        onDismiss = onAlbumDismiss,
                        onConfirm = onAlbumConfirm,
                        onDragToChat = onAlbumDragToChat,
                        initialSelected = attachments,
                        onSelectionChange = onAttachmentsChange,
                        onPickFromSystemAlbum = onAlbumPickFromSystem,
                        onPreview = onAlbumPreview,
                        onPermission = onAlbumPermission,
                        onToast = onAlbumToast,
                    )
                }
            } else {
                // 全屏相册不参与列布局，改为覆盖在聊天页之上（见下方 Box）
            }
        }
    }

    // 全屏相册：覆盖整页
    if (albumVisible && albumMode == AlbumMode.FULL) {
        Box(Modifier.fillMaxSize()) {
            AlbumPanel(
                visible = true,
                mode = AlbumMode.FULL,
                videoMode = albumVideoMode,
                onDismiss = onAlbumDismiss,
                onConfirm = onAlbumConfirm,
                onDragToChat = onAlbumDragToChat,
                initialSelected = attachments,
                onSelectionChange = onAttachmentsChange,
                onPickFromSystemAlbum = onAlbumPickFromSystem,
                onPreview = onAlbumPreview,
                onPermission = onAlbumPermission,
                onToast = onAlbumToast,
            )
        }
    }
    }

    // 合并转发明细
    forwardView?.let { nodes ->
        ForwardDetailDialog(nodes = nodes, onDismiss = { forwardView = null })
    }

    // 群成员选择（右上 ···）
    if (showMemberPicker) {
        MemberListSheet(
            members = members,
            appId = account?.appId ?: "",
            onDismiss = { showMemberPicker = false },
            onPick = {
                showMemberPicker = false
                onShowMemberCard(it)
            },
        )
    }
}

@Composable
private fun ModeSwitchBar(markdown: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF7F7F9)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentChip("普通", selected = !markdown) { onToggle(false) }
        Spacer(Modifier.width(6.dp))
        SegmentChip("MD", selected = markdown) { onToggle(true) }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (markdown) "Markdown：支持语法与插图" else "普通文本",
            fontSize = 11.sp, color = QQ.TextHint,
        )
    }
}

@Composable
private fun SegmentChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) QQ.Blue else Color(0xFFE8E9EC))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Text(
            text = label, fontSize = 12.sp,
            color = if (selected) Color.White else QQ.TextSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 长按消息后的浮动菜单 */
@Composable
private fun MessageMenu(
    expanded: Boolean,
    msg: ChatMessage,
    onDismiss: () -> Unit,
    onQuote: () -> Unit,
    onRecall: () -> Unit,
    onCopy: () -> Unit,
    onProfile: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("引用") }, onClick = onQuote)
        if (msg.isSelf && msg.messageId.isNotEmpty()) {
            DropdownMenuItem(text = { Text("撤回") }, onClick = onRecall)
        }
        if (msg.text.isNotEmpty()) {
            DropdownMenuItem(text = { Text("复制") }, onClick = onCopy)
        }
        if (!msg.isSelf && msg.senderOpenId.isNotEmpty()) {
            DropdownMenuItem(text = { Text("查看资料") }, onClick = onProfile)
        }
    }
}

/** 访问剪贴板 */

@Composable
private fun PendingRow(p: PendingSend, selfAvatar: String, selfName: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            when (p.kind) {
                PendingSend.Kind.IMAGE, PendingSend.Kind.VIDEO -> AsyncImage(
                    model = p.localUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.widthIn(max = 140.dp).clip(RoundedCornerShape(6.dp)),
                )
                PendingSend.Kind.TEXT -> Text(p.text, fontSize = QQ.BodySize)
            }
            Spacer(Modifier.height(3.dp))
            Text(p.progress, fontSize = 11.sp, color = QQ.TextSecondary)
        }
        Spacer(Modifier.width(8.dp))
        QqAvatar(url = selfAvatar.ifEmpty { null }, fallbackText = selfName.ifEmpty { "我" }, size = QQ.AvatarSizeSmall)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    msg: ChatMessage,
    isGroup: Boolean,
    appId: String,
    selfAvatar: String,
    selfName: String,
    highlighted: Boolean,
    onLongPress: () -> Unit,
    /** 点消息本体 = 收起键盘与表情/加号面板（与 QQ 一致） */
    onClick: () -> Unit = {},
    onOpenForward: (List<MsgNode>) -> Unit = {},
    onAvatarClick: () -> Unit,
    onAvatarLongPress: () -> Unit,
    onImageClick: (String) -> Unit,
) {
    val self = msg.isSelf
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(Color(0x22FFD700)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        horizontalArrangement = if (self) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!self) {
            Box(Modifier.combinedClickable(onClick = onAvatarClick, onLongClick = onAvatarLongPress)) {
                QqAvatar(
                    // 优先用事件里下发的 author.avatar；没有才用拼接接口兜底
                    url = msg.senderAvatar.ifEmpty {
                        com.qqbot.client.bot.QqBotConfig.memberAvatar(appId, msg.senderOpenId)
                    }.ifEmpty { null },
                    fallbackText = msg.senderName,
                    size = QQ.AvatarSizeSmall,
                    // 签名 URL 过期时用 openid 拼接兜底
                    fallbackUrl = com.qqbot.client.bot.QqBotConfig.memberAvatar(appId, msg.senderOpenId),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (self) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 270.dp),
        ) {
            if (!self && isGroup) {
                Text(
                    msg.senderName, fontSize = 11.sp, color = QQ.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            if (msg.recalled) {
                Text(
                    if (self) "你撤回了一条消息" else "${msg.senderName} 撤回了一条消息",
                    fontSize = 12.sp, color = QQ.TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                if (msg.isForward) {
                    ForwardCard(msg, self) { onOpenForward(msg.forwardNodes) }
                } else {
                    Bubble(msg, self, onImageClick)
                }
            }
        }

        if (self) {
            Spacer(Modifier.width(8.dp))
            QqAvatar(
                url = selfAvatar.ifEmpty { null },
                fallbackText = selfName.ifEmpty { "我" },
                size = QQ.AvatarSizeSmall,
                // 自身消息的 senderOpenId 就是机器人 openid，可直接拼接兜底
                fallbackUrl = com.qqbot.client.bot.QqBotConfig.memberAvatar(appId, msg.senderOpenId),
            )
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage, self: Boolean, onImageClick: (String) -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(QQ.BubbleCorner))
            .background(if (self) QQ.SelfBubble else QQ.OtherBubble)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        msg.quote?.let { q ->
            // QQ 的引用样式：左侧竖条 + 作者名 + 引用内容，整体比正文小一号
            Row(
                Modifier
                    .widthIn(max = 200.dp)
                    .background(
                        if (self) Color(0x22FFFFFF) else Color(0xFFF5F6FA),
                        RoundedCornerShape(4.dp),
                    )
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .heightIn(min = 28.dp)
                        .background(if (self) Color(0xCCFFFFFF) else QQ.Blue)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.padding(end = 8.dp)) {
                    Text(
                        q.authorName,
                        fontSize = 11.sp,
                        color = if (self) Color(0xE6FFFFFF) else QQ.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    q.imageUrls.firstOrNull()?.let { u ->
                        coil.compose.AsyncImage(
                            model = u,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(top = 2.dp).size(56.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                    if (q.text.isNotEmpty()) {
                        Text(
                            q.text,
                            fontSize = 12.sp,
                            color = if (self) Color(0xCCFFFFFF) else QQ.TextHint,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        msg.segments.filter { it.kind == MessageSegment.Kind.IMAGE }.forEach { seg ->
            // 点击图片全屏查看（长按仍交给外层的消息菜单/滑动引用）
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = seg.url.isNotEmpty() || seg.localPath.isNotEmpty()) {
                        onImageClick(seg.localPath.ifEmpty { seg.url })
                    }
            ) {
                AsyncImage(
                    model = seg.displayUri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.widthIn(max = 200.dp).heightIn(min = 80.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        msg.segments.filter { it.kind == MessageSegment.Kind.VIDEO }.forEach {
            Box(
                Modifier.width(180.dp).aspectRatio(16f / 9f)
                    .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", color = Color.White, fontSize = 28.sp) }
            Spacer(Modifier.height(4.dp))
        }
        if (msg.text.isNotEmpty()) {
            if (msg.markdown) {
                MarkdownText(normalizeAt(msg.text), self, onImageClick = onImageClick)
            } else {
                // 普通消息：把 <faceType> 表情标签渲染成 QQ 表情图
                TextWithFaces(normalizeAt(msg.text), self)
            }
        }
    }
}

/** 把 md 里的 <qqbot-at-user> / <qqbot-at-everyone> 还原成可读的 @昵称（服务端一般已转好，这里兜底） */
private fun normalizeAt(text: String): String {
    if (!text.contains("<qqbot-at")) return text
    var s = text
    s = Regex("""<qqbot-at-user\s+id="([^"]*)"\s*/>""").replace(s) { m ->
        val id = m.groupValues[1]
        "@" + if (id.length > 8) id.takeLast(8) else id
    }
    s = s.replace("<qqbot-at-everyone />", "@全体成员")
    return s
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    members: List<GroupMember>,
    appId: String,
    showPlus: Boolean,
    onTogglePlus: () -> Unit,
    /** 表情面板由父级持有，保证与键盘/加号面板互斥 */
    showEmoji: Boolean = false,
    onShowEmojiChange: (Boolean) -> Unit = {},
    onToast: (String) -> Unit = {},
    onInputFocused: () -> Unit = {},
    /** 请求上层关闭加号面板（表情面板打开时互斥） */
    onRequestClosePlus: () -> Unit = {},
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickCamera: () -> Unit,
    onAtEveryone: () -> Unit,
    attachments: List<android.net.Uri> = emptyList(),
    onRemoveAttachment: (android.net.Uri) -> Unit = {},
) {
    // 表情面板开关（与键盘、加号面板互斥）
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().background(Color(0xFFF7F7F9))) {
        // 待发送图片预览（图文混发）
        if (attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.take(9).forEach { uri ->
                    Box(Modifier.size(60.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).size(18.dp)
                                .background(Color(0x99000000), CircleShape)
                                .clickable { onRemoveAttachment(uri) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        // 输入框
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier.weight(1f).heightIn(min = 38.dp, max = 140.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color.White)
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    // 输入框获得焦点（键盘弹出）时，关闭表情/加号面板与相册
                    .onFocusChanged { st ->
                        if (st.isFocused) {
                            onInputFocused()
                            // 键盘与表情面板同一槽位：聚焦即收面板
                            onShowEmojiChange(false)
                        }
                    },
            ) {
                MentionTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    members = members,
                    appId = appId,
                    placeholder = "输入消息",
                    modifier = Modifier.fillMaxWidth(),
                    onPickEveryone = onAtEveryone,
                )
            }
            Spacer(Modifier.width(4.dp))

            if (draft.isNotEmpty()) {
                Box(
                    Modifier.height(38.dp).background(QQ.Blue, RoundedCornerShape(6.dp))
                        .clickable { onSend() }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("发送", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 输入框下方的功能栏（顺序与手机 QQ 一致：语音 / 图片 / 相机 / 表情 / 加号）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InputIcon(R.drawable.skin_aio_voice_nor, "语音") { /* 语音待接入 SILK */ }
            InputIcon(R.drawable.qui_image_aio_oversized, "图片") { onPickImage() }
            InputIcon(R.drawable.qui_camera_aio_oversized, "相机") { onPickCamera() }
            InputIcon(
                // 与 QQ 一致：面板展开时图标切换为键盘图标（skin_aio_keyboard_nor），
                // 收起时恢复表情图标。QQ 输入栏是程序化构建，两个图标资源同时存在于 classes4.dex。
                icon = if (showEmoji) R.drawable.skin_aio_keyboard_nor else R.drawable.qui_emoticon_aio_oversized,
                label = if (showEmoji) "键盘" else "表情",
                active = showEmoji,
            ) {
                val next = !showEmoji
                onShowEmojiChange(next)
                // 与 QQ 一致：表情面板与键盘/加号面板互斥
                if (next) {
                    keyboard?.hide()
                    onRequestClosePlus()
                }
            }
            Spacer(Modifier.weight(1f))
            // 加号：更多发送方式
            Box(
                Modifier.size(36.dp).clickable { onTogglePlus() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(
                        // 与 QQ 一致：加号面板展开时也切成键盘图标
                        if (showPlus) R.drawable.skin_aio_keyboard_nor else R.drawable.skin_aio_more_nor
                    ),
                    contentDescription = if (showPlus) "键盘" else "更多",
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (showPlus) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PlusItem("相册") { onPickImage() }
                PlusItem("拍摄") { onPickCamera() }
                PlusItem("视频") { onPickVideo() }
                PlusItem("@全体") { onAtEveryone(); onTogglePlus() }
            }
        }

        // 表情面板：点表情插入到输入框
        EmojiPanel(
            visible = showEmoji,
            onPick = { c, code ->
                if (code > 0) {
                    // QQ 小黄脸：插入对应的 QQ 表情标记，发送时转成官方语法
                    onDraftChange(draft + QqFaces.insertTag(code, c))
                } else {
                    onDraftChange(draft + c)
                }
            },
            onToast = onToast,
            onDelete = {
                // 与 QQ 面板的删除键一致：整体删除一个表情标签，否则回退一个字符
                val tagEnd = draft.lastIndexOf('>')
                val tagStart = draft.lastIndexOf("<faceType=")
                if (tagStart >= 0 && tagEnd > tagStart && tagEnd == draft.length - 1) {
                    onDraftChange(draft.substring(0, tagStart))
                } else if (draft.isNotEmpty()) {
                    onDraftChange(draft.dropLast(1))
                }
            },
        )
    }
}

/** 输入栏下方的一个功能图标 */
@Composable
private fun InputIcon(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Color(0x1412B7F5) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun PlusItem(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(12.dp),
    ) {
        Box(Modifier.size(52.dp).background(Color(0xFFF2F3F5), RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = QQ.TextSecondary)
    }
}


/**
 * 合并转发卡片（对应官方 message_type=102「聊天记录」）。
 *
 * QQ 的形态：一张卡片，标题 + 若干条预览 + 「查看N条消息」，点进去才看内联消息。
 */
@Composable
private fun ForwardCard(msg: ChatMessage, self: Boolean, onOpen: () -> Unit) {
    val nodes = msg.forwardNodes
    val total = nodes.sumOf { it.totalCount }
    Column(
        Modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(QQ.BubbleCorner))
            .background(if (self) QQ.SelfBubble else QQ.OtherBubble)
            .clickable { onOpen() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = "聊天记录",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (self) Color.White else QQ.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        nodes.take(4).forEach { n ->
            val line = if (n.children.isNotEmpty() && n.content.isEmpty()) {
                "聊天记录"
            } else if (n.authorName.isNotEmpty()) {
                "${n.authorName}: ${n.summary}"
            } else {
                n.summary
            }
            Text(
                text = line,
                fontSize = 12.sp,
                color = if (self) Color(0xE6FFFFFF) else QQ.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
        }
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.fillMaxWidth().height(0.5.dp)
                .background(if (self) Color(0x33FFFFFF) else QQ.Divider)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "查看${total}条消息",
            fontSize = 12.sp,
            color = if (self) Color.White else QQ.Blue,
        )
    }
}

/**
 * 合并转发明细。支持**嵌套合并转发**：点嵌套卡片进入下一层，可逐层返回。
 * 结构依据官方 `MsgElement.msg_elements` 递归定义。
 */
@Composable
private fun ForwardDetailDialog(nodes: List<MsgNode>, onDismiss: () -> Unit) {
    // 用栈表示层级：栈顶就是当前展示的这层
    val stack = remember { mutableStateListOf(nodes) }
    val current = stack.lastOrNull() ?: nodes
    val depth = stack.size

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (depth > 1) {
                    Text(
                        "返回",
                        fontSize = 14.sp,
                        color = QQ.Blue,
                        modifier = Modifier.clickable { stack.removeAt(stack.lastIndex) },
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (depth > 1) "聊天记录（第${depth}层）" else "聊天记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = QQ.TextPrimary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "关闭",
                    fontSize = 14.sp,
                    color = QQ.TextSecondary,
                    modifier = Modifier.clickable { onDismiss() },
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 460.dp),
            ) {
                items(current.size) { i ->
                    ForwardNodeRow(
                        node = current[i],
                        depth = 0,
                        onEnterNested = { stack.add(it) },
                    )
                    if (i < current.size - 1) {
                        Box(Modifier.fillMaxWidth().height(0.5.dp).background(QQ.Divider))
                    }
                }
            }
        }
    }
}

/** 明细里的一行；嵌套转发渲染成可点击的子卡片 */
@Composable
private fun ForwardNodeRow(
    node: MsgNode,
    depth: Int,
    onEnterNested: (List<MsgNode>) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (depth * 12).dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (node.authorName.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QqAvatar(
                    url = node.authorAvatar.ifEmpty { null },
                    fallbackText = node.authorName,
                    size = 18.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(node.authorName, fontSize = 11.sp, color = QQ.TextSecondary)
            }
            Spacer(Modifier.height(3.dp))
        }

        // 嵌套合并转发：再画一张小卡片
        if (node.children.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFF7F7F9))
                    .clickable { onEnterNested(node.children) }
                    .padding(10.dp),
            ) {
                Text("聊天记录", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = QQ.TextPrimary)
                Spacer(Modifier.height(4.dp))
                node.children.take(3).forEach { c ->
                    Text(
                        text = if (c.authorName.isNotEmpty()) "${c.authorName}: ${c.summary}" else c.summary,
                        fontSize = 11.sp,
                        color = QQ.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "查看${node.totalCount}条消息",
                    fontSize = 11.sp,
                    color = QQ.Blue,
                )
            }
        }

        if (node.content.isNotEmpty()) {
            if (node.children.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(node.content, fontSize = 14.sp, color = QQ.TextPrimary)
        }
        if (node.imageUrl.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            coil.compose.AsyncImage(
                model = node.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .heightIn(min = 60.dp, max = 240.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}
