package com.qqbot.client.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
@OptIn(ExperimentalLayoutApi::class)
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
    // 合并转发明细弹层
    var forwardView by remember { mutableStateOf<List<MsgNode>?>(null) }
    var menuFor by remember { mutableStateOf<ChatMessage?>(null) }
    var showMemberPicker by remember { mutableStateOf(false) }
    // 待发送的附件：由外部（相册）持有，这里只读
    val pendingAttachments = attachments
    val scope = rememberCoroutineScope()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    // 「再点一次面板按钮 = 回到键盘」时用来把焦点要回输入框
    val inputFocus = remember { FocusRequester() }

    val listState = rememberLazyListState()

    // ===== 底部面板槽位：键盘 / 表情 / 加号 / 相册 共用，同一时刻只有一个（QQ HideKeyBoardAndPanel）=====
    var panel by remember(conversation.chatKey) { mutableStateOf(ChatPanelKind.NONE) }
    // 相册由外部打开（权限流程在 MainActivity），一旦可见就占用槽位
    val shownPanel = if (albumVisible) ChatPanelKind.ALBUM else panel

    // 槽位高度 = max(实测键盘高度, 280dp)（panelcontainer.j#w()），
    // 这样从键盘切面板时高度完全一致，聊天区不会跳。
    // 屏幕很矮（分屏 / 小屏）时再按 55% 屏高兜一刀，避免把聊天区挤成 0。
    val imePx = WindowInsets.ime.getBottom(density)
    var keyboardPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(imePx) { if (imePx > keyboardPx) keyboardPx = imePx }
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxPanelHeight = screenHeightDp * 0.55f
    val panelHeight = minOf(
        maxOf(with(density) { keyboardPx.toDp() }, ChatPanelSpec.MinHeight),
        maxPanelHeight,
    )

    // 倒序视图：LazyColumn(reverseLayout = true) 里 index 0 在屏幕最底部 = 最新一条
    val viewMessages = remember(messages) { messages.asReversed() }
    val viewPendingSends = remember(pendingSends) { pendingSends.asReversed() }

    // 倒序布局里「贴底」= 可见的最小 index 是 0（允许 1 个的余量）
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf true
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf true
            first.index <= 1
        }
    }

    /**
     * 面板 / 键盘开合会改变聊天区高度。QQ 里聊天列表是「贴底」的：
     * 输入区变高时最新的消息仍然露在输入栏上方，而不是被面板盖掉。
     * 所以只要打开或关闭时本来就贴着底部，就在新高度生效后重新贴底（index 0 = 最新）。
     */
    val stickScrollIfAtBottom: () -> Unit = {
        if (atBottom) {
            scope.launch {
                withFrameNanos { }   // 让面板进入组合
                withFrameNanos { }   // 让新高度完成测量
                listState.scrollToItem(0)
            }
        }
    }

    /**
     * 收起键盘 + 关掉所有面板。QQ 里「点聊天区空白处」和「按系统返回键」都是这个动作
     * （`AIOMsgListEvent$HideKeyBoardAndPanel`）。
     */
    val closeAllPanels: () -> Unit = {
        stickScrollIfAtBottom()
        panel = ChatPanelKind.NONE
        if (albumVisible) onCloseAlbum()
        keyboard?.hide()
        focusManager.clearFocus()
    }

    /**
     * 打开 / 切换面板。**互斥**：先把键盘和其它面板全部收掉，槽位里永远只有一个面板。
     * 再点同一个按钮 = 收起面板并回到键盘输入（QQ 里此时按钮图标已经切成键盘图标）。
     */
    val togglePanel: (ChatPanelKind) -> Unit = { kind ->
        stickScrollIfAtBottom()
        if (shownPanel == kind) {
            panel = ChatPanelKind.NONE
            if (albumVisible) onCloseAlbum()
            keyboard?.hide()
            runCatching { inputFocus.requestFocus() }
        } else {
            panel = kind
            if (albumVisible) onCloseAlbum()
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // 相册（由 MainActivity 走权限流程后打开）一来就抢槽位：关掉表情/加号面板并收键盘
    LaunchedEffect(albumVisible) {
        if (albumVisible) {
            stickScrollIfAtBottom()
            panel = ChatPanelKind.NONE
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    // 系统返回键：有面板时先关面板，不要直接退出群聊（QQ 同行为）；
    // 只有键盘时也是先收键盘，第二次才是退出聊天。
    val imeVisible = WindowInsets.isImeVisible
    BackHandler(enabled = shownPanel != ChatPanelKind.NONE || imeVisible) {
        if (shownPanel != ChatPanelKind.NONE) {
            closeAllPanels()
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }

    // 与 QQ 一致：@全体成员 直接以标签插入输入框
    val insertAtEveryone: () -> Unit = {
        draft = if (draft.isEmpty() || draft.endsWith(" ") || draft.endsWith("\n")) {
            "$draft@全体成员 "
        } else {
            "$draft @全体成员 "
        }
    }
    // 表情面板：点小黄脸插入 QQ 表情标记，普通 emoji 直接插字符
    val insertFace: (String, Int) -> Unit = { c, code ->
        draft = if (code > 0) draft + QqFaces.insertTag(code, c) else draft + c
    }
    // 面板删除键：整体删掉一个表情标签，否则回退一个字符（与 QQ 面板删除键一致）
    val deleteFace: () -> Unit = {
        val tagEnd = draft.lastIndexOf('>')
        val tagStart = draft.lastIndexOf("<faceType=")
        draft = when {
            tagStart >= 0 && tagEnd > tagStart && tagEnd == draft.length - 1 ->
                draft.substring(0, tagStart)
            draft.isNotEmpty() -> draft.dropLast(1)
            else -> draft
        }
    }

    // 进会话**不需要**任何「先跳一次」的补偿动作：
    // 倒序列表首帧就落在 index 0（最新一条）上，跟 QQ 的 stackFromEnd 一样是原生的末尾锚定。

    // 后续新消息：只有「最新的那条变了」且本来就贴着底部时才自动跟随（倒序 → 目标是 index 0）。
    // 注意不能用 messages.size 判断：上滑加载更早消息也会让 size 变大，
    // 那样会把正在看历史的人强行拽回底部。
    val newestId = messages.lastOrNull()?.id
    var lastNewestId by remember(conversation.chatKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(newestId, pendingSends.size) {
        val changed = newestId != null && newestId != lastNewestId
        lastNewestId = newestId
        if (changed && atBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    // 倒序布局里「更早的消息」在 index 大的那一端：可见的最大 index 接近总数时就加载
    LaunchedEffect(conversation.chatKey, hasMoreOlder) {
        if (!hasMoreOlder) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.distinctUntilChanged().collect { (lastIndex, total) ->
            if (total > 0 && lastIndex >= total - 3) onLoadOlder()
        }
    }
    LaunchedEffect(highlightMsgId) {
        if (highlightMsgId != null) {
            val idx = viewMessages.indexOfFirst { it.id == highlightMsgId }
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

        // ===== 消息列表（占满输入区以外的空间；面板打开时它变矮，但最新消息仍贴着输入栏）=====
        //
        // 末尾锚定：QQ 的消息列表是 AIOLayoutManager.setStackFromEnd(true)
        // （AbsMsgListVB$recyclerView$2 → 锚到 itemCount-1），所以进群第一帧就停在最新一条，
        // 不会「先看到旧消息、再被强行跳到最新」那种闪屏。
        // Compose 没有 stackFromEnd，等价做法是 reverseLayout = true + 数据倒序：
        // 最新一条是 index 0，也就是屏幕最底部，首帧天然居中在末尾。
        Box(
            Modifier
                .weight(1f)
                // 与 QQ 一致：点击聊天区域收起键盘、关闭表情/加号面板与相册
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { closeAllPanels() })
                }
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                // 顺序 = 屏幕从下往上：待发送 → 最新消息 → … → 更早消息 → 加载更多
                items(viewPendingSends, key = { "pending_${it.localId}" }) { p ->
                    PendingRow(p, selfAvatar = account?.avatarUrl ?: "", selfName = account?.botName ?: "")
                }

                items(viewMessages, key = { "msg_${it.id}" }) { msg ->
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
                            onClick = { closeAllPanels() },
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

                // 列表顶端（倒序里是最后一个 item）：加载更早消息 / 没有更多
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
            }

            // 与 QQ 一致：用户上滑离开底部时不自动跟随新消息，而是在右下角显示「回到底部」箭头
            // （QQ 的 UnreadBubbleVB：有未读显示数字气泡，无未读显示 drawable/aio_back_bottom 箭头）
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.skin_aio_arrowdown_nor),
                        contentDescription = "回到底部",
                        modifier = Modifier.size(20.dp),
                    )
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
            // 当前占了槽位的面板（用来把「表情/加号」按钮切成键盘图标）
            activePanel = shownPanel,
            onTogglePanel = togglePanel,
            focusRequester = inputFocus,
            // 输入框拿到焦点 = 键盘要上来了：把相册/表情/加号面板全收掉（QQ 的互斥核心）
            onInputFocused = {
                if (panel != ChatPanelKind.NONE || albumVisible) {
                    stickScrollIfAtBottom()
                    panel = ChatPanelKind.NONE
                    if (albumVisible) onCloseAlbum()
                }
            },
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
            // 相册：先收掉其它面板与键盘（互斥），再交给外部（外部还要过存储权限）
            onPickImage = {
                stickScrollIfAtBottom()
                panel = ChatPanelKind.NONE
                keyboard?.hide()
                focusManager.clearFocus()
                onOpenAlbum(false)
            },
            onPickVideo = {
                stickScrollIfAtBottom()
                panel = ChatPanelKind.NONE
                keyboard?.hide()
                focusManager.clearFocus()
                onOpenAlbum(true)
            },
            // 相机不涉及面板，直接收起键盘
            onPickCamera = {
                panel = ChatPanelKind.NONE
                keyboard?.hide()
                focusManager.clearFocus()
                onPickCamera()
            },
            onAtEveryone = insertAtEveryone,
            attachments = pendingAttachments,
            onRemoveAttachment = { uri -> onAttachmentsChange(pendingAttachments.filter { it != uri }) },
        )

        // ===== 底部面板槽位：表情 / 加号 / 相册（半屏）共用同一块空间，同一时刻只有一个 =====
        // 高度 = max(键盘高度, 280dp)，与 QQ 的 panelcontainer 一致；
        // 它占真实布局空间（不是盖在聊天区上），所以聊天区会自动变矮而不是被遮挡。
        when (shownPanel) {
            ChatPanelKind.EMOJI -> EmojiPanel(
                visible = true,
                modifier = Modifier.fillMaxWidth().height(panelHeight),
                onPick = insertFace,
                onToast = onToast,
                onDelete = deleteFace,
            )

            ChatPanelKind.PLUS -> ChatPlusPanel(
                modifier = Modifier.fillMaxWidth().height(panelHeight),
                onAlbum = {
                    stickScrollIfAtBottom()
                    panel = ChatPanelKind.NONE
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onOpenAlbum(false)
                },
                onCamera = {
                    panel = ChatPanelKind.NONE
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onPickCamera()
                },
                onVideo = {
                    stickScrollIfAtBottom()
                    panel = ChatPanelKind.NONE
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onOpenAlbum(true)
                },
                onAtEveryone = {
                    insertAtEveryone()
                    panel = ChatPanelKind.NONE
                    runCatching { inputFocus.requestFocus() }
                },
            )

            // 半屏相册：占槽位；全屏相册走下面的覆盖层
            ChatPanelKind.ALBUM -> if (albumMode == AlbumMode.HALF) {
                Box(Modifier.fillMaxWidth().height(panelHeight)) {
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
            }

            ChatPanelKind.NONE -> Unit
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
                    // QQ: RoundBubbleImageView.setRadiusDP(10f) → 图片圆角 10dp
                    .clip(RoundedCornerShape(ChatImageSpec.CornerRadius))
                    .clickable(enabled = seg.url.isNotEmpty() || seg.localPath.isNotEmpty()) {
                        onImageClick(seg.localPath.ifEmpty { seg.url })
                    }
            ) {
                ChatImage(seg)
            }
            Spacer(Modifier.height(4.dp))
        }
        // 语音：QQ 风格语音条（可播放/暂停，进度随时长前移）
        msg.segments.filter { it.kind == MessageSegment.Kind.VOICE }.forEach { seg ->
            VoiceBubble(seg, self)
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

/**
 * 聊天里的图片气泡。
 *
 * 以前是 `widthIn(max = 200.dp)` 且**不限高**：1080×1920 的竖图会显示成 200×355dp，
 * 一条图片消息就吃掉大半屏，把最新消息挤出可视区 —— 这就是「有人发图片后页面显示不完整、
 * 看着像是按头像和昵称定位」的原因。
 *
 * 现在完全按 QQ 的规则算尺寸（见 [ChatImageSpec]）：
 *  - 可用宽 = 屏宽 − 108dp − 28dp
 *  - 长边上限 = 0.85 × 可用宽（原图长边 ≤200dp 的小图固定 135dp）
 *  - 短边下限 = 0.28 × 可用宽（小图 45dp）
 *  - 等比缩放后再把宽高各自夹一次上限 → 极端比例（长截图）会被裁切，与 QQ 一致
 *  - 圆角 10dp（QQ `RoundBubbleImageView.setRadiusDP(10f)`）
 */
@Composable
private fun ChatImage(seg: MessageSegment) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    // 原始像素宽高：优先用服务端下发的（稳定、不跳动），没有再退到解码后的位图尺寸
    var pxW by remember(seg.url, seg.localPath) { mutableIntStateOf(seg.width) }
    var pxH by remember(seg.url, seg.localPath) { mutableIntStateOf(seg.height) }
    val painter = coil.compose.rememberAsyncImagePainter(
        model = coil.request.ImageRequest.Builder(context)
            .data(seg.displayUri)
            .listener(
                onSuccess = { _, result ->
                    val d = result.drawable
                    if ((pxW <= 0 || pxH <= 0) && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                        pxW = d.intrinsicWidth
                        pxH = d.intrinsicHeight
                    }
                }
            )
            .build()
    )
    val (w, h) = ChatImageSpec.size(
        origWidthPx = pxW,
        origHeightPx = pxH,
        density = density.density,
        // 手机上就是屏宽；平板上要夹到气泡可用宽（气泡上限 270dp − 左右各 12dp 内边距 = 246dp，
        // 对应 availableWidthDp 上限 246/0.85 + 136 ≈ 425dp），否则图片会被气泡裁掉
        availableWidthDp = minOf(screenWidthDp, 425.dp),
    )
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.width(w).height(h),
    )
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
    /** 当前占用底部槽位的面板；键盘态 = NONE。只用来把按钮切成键盘图标。 */
    activePanel: ChatPanelKind,
    /** 点表情 / 加号按钮：由上层统一做「关键盘 + 关其它面板」的互斥 */
    onTogglePanel: (ChatPanelKind) -> Unit,
    focusRequester: FocusRequester,
    /** 输入框拿到焦点（键盘要出来了）—— 上层据此关掉相册/表情/加号面板 */
    onInputFocused: () -> Unit = {},
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickCamera: () -> Unit,
    onAtEveryone: () -> Unit,
    attachments: List<android.net.Uri> = emptyList(),
    onRemoveAttachment: (android.net.Uri) -> Unit = {},
) {
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
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                MentionTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    members = members,
                    appId = appId,
                    placeholder = "输入消息",
                    modifier = Modifier.fillMaxWidth(),
                    // 焦点判断加在真正的输入控件上：拿到焦点 = 键盘要出来了。
                    // 上层据此把相册/表情/加号面板全收掉 —— 这是 QQ 的互斥核心，
                    // 也是以前「键盘起来了面板却没关」的根因（旧代码在父 Box 上用 isFocused，
                    // 焦点其实在内部的 BasicTextField 上，父节点永远收不到 isFocused=true）。
                    fieldModifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { st -> if (st.isFocused) onInputFocused() },
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
                // 与 QQ 一致：自己这块槽位被占用时图标切成键盘图标（skin_aio_keyboard_nor）
                icon = if (activePanel == ChatPanelKind.EMOJI) R.drawable.skin_aio_keyboard_nor
                else R.drawable.qui_emoticon_aio_oversized,
                label = if (activePanel == ChatPanelKind.EMOJI) "键盘" else "表情",
                active = activePanel == ChatPanelKind.EMOJI,
            ) { onTogglePanel(ChatPanelKind.EMOJI) }
            Spacer(Modifier.weight(1f))
            // 加号：更多发送方式（同样是双态图标）
            Box(
                Modifier.size(36.dp).clickable { onTogglePanel(ChatPanelKind.PLUS) },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(
                        if (activePanel == ChatPanelKind.PLUS) R.drawable.skin_aio_keyboard_nor
                        else R.drawable.skin_aio_more_nor
                    ),
                    contentDescription = if (activePanel == ChatPanelKind.PLUS) "键盘" else "更多",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 加号（更多）面板。
 *
 * QQ 里它和表情/相册面板**共用同一个槽位**，高度也是 max(键盘高度, 280dp)，
 * 所以这里只负责在给定高度里排内容，不再自己定高。
 */
@Composable
private fun ChatPlusPanel(
    modifier: Modifier = Modifier,
    onAlbum: () -> Unit,
    onCamera: () -> Unit,
    onVideo: () -> Unit,
    onAtEveryone: () -> Unit,
) {
    Column(modifier.background(Color.White).padding(top = 24.dp, start = 16.dp, end = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PlusItem(R.drawable.qui_image_aio_oversized, "相册", onAlbum)
            PlusItem(R.drawable.qui_camera_aio_oversized, "拍摄", onCamera)
            PlusItem(R.drawable.qui_gallery_aio_oversized, "视频", onVideo)
            PlusItem(null, "@全体", onAtEveryone)
        }
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
private fun PlusItem(icon: Int?, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(10.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2F3F5)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
            } else {
                Text("@", fontSize = 20.sp, color = QQ.TextSecondary)
            }
        }
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
            // 宽度取自 QQ：合并转发气泡是 ArkAppLoadLayout（layout/cgh），固定 263dp × 294dp。
            // 内层由 ARK 应用运行时渲染（APK 里没有该应用包），我们只能用文本预览填充，
            // 所以高度随内容、宽度对齐 263dp。
            .width(263.dp)
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
        nodes.take(6).forEach { n ->
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
