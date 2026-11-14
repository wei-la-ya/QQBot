package com.qqbot.client

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.qqbot.client.bot.GroupMember
import com.qqbot.client.data.Conversation
import com.qqbot.client.ui.ActivityScreen
import com.qqbot.client.ui.AddBotScreen
import com.qqbot.client.ui.ChatScreen
import com.qqbot.client.ui.CameraScreen
import com.qqbot.client.ui.ChatSearchDialog
import com.qqbot.client.ui.ContactsScreen
import com.qqbot.client.ui.GroupInfoScreen
import com.qqbot.client.ui.AlbumMode
import com.qqbot.client.ui.ImageViewerScreen
import com.qqbot.client.ui.ShareToChatDialog
import com.qqbot.client.ui.MeScreen
import com.qqbot.client.ui.MemberCardDialog
import com.qqbot.client.ui.MessageListScreen
import com.qqbot.client.ui.QQ
import com.qqbot.client.ui.QQTab
import com.qqbot.client.ui.QqBottomBar
import com.qqbot.client.vm.AddBotResult
import com.qqbot.client.vm.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QQApp() }
    }
}

@Composable
private fun QQApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(QQTab.MESSAGE) }
    var showAddBot by remember { mutableStateOf(state.currentAccount == null) }
    var addBusy by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var addGroupDialog by remember { mutableStateOf(false) }
    var showGroupInfo by remember { mutableStateOf(false) }
    var showAlbum by remember { mutableStateOf(false) }
    // 应用内相机
    var showCamera by remember { mutableStateOf(false) }
    var albumVideoMode by remember { mutableStateOf(false) }
    // 相册模式：由 VM 持久化，默认半屏
    val albumMode = if (state.albumMode == "full") AlbumMode.FULL else AlbumMode.HALF
    // 全屏看图
    var viewerImage by remember { mutableStateOf<String?>(null) }
    var shareImage by remember { mutableStateOf<String?>(null) }
    // 需要插入到输入框的文本（来自长按头像 / 资料卡「提醒TA」）
    var pendingInsert by remember { mutableStateOf<String?>(null) }
    // 相册里勾选中的图片（关闭相册后仍保留，用于图文混发）
    var pendingAttachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // 图片缓存占用（进设置页时刷新）
    var cacheBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(tab) {
        if (tab == QQTab.ME) {
            cacheBytes = withContext(kotlinx.coroutines.Dispatchers.IO) { vm.mediaCacheSize() }
        }
    }
    var memberCard by remember { mutableStateOf<GroupMember?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<com.qqbot.client.bot.ChatMessage>>(emptyList()) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.consumeToast()
        }
    }

    // 相册权限
    fun needPermission(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            if (albumVideoMode) arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            else arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { showAlbum = true }

    // 拍照：写到应用私有缓存，拍完直接发送（与 QQ 一致）
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraUri
        val chatKey = state.activeChatKey
        if (ok && uri != null && chatKey != null) {
            vm.sendImage(chatKey, uri)
        }
        cameraUri = null
    }

    fun makeCameraUri(): Uri {
        val f = java.io.File(
            context.cacheDir,
            "camera_${System.currentTimeMillis()}.jpg"
        )
        if (!f.exists()) f.createNewFile()
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            f
        )
    }

    fun openAlbum(video: Boolean) {
        albumVideoMode = video
        val perms = needPermission()
        val granted = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) showAlbum = true else permissionLauncher.launch(perms)
    }

    LaunchedEffect(state.activeChatKey) {
        if (state.activeChatKey == null) {
            showGroupInfo = false
            showSearch = false
            memberCard = null
            showAlbum = false
        }
    }

    if (state.currentAccount == null || showAddBot) {
        BackHandler(enabled = state.currentAccount != null) { showAddBot = false }
        AddBotScreen(
            busy = addBusy,
            errorText = addError,
            onBack = { if (state.currentAccount != null) showAddBot = false },
            onSubmit = { appId, secret, version ->
                addBusy = true
                addError = null
                vm.addBot(appId, secret, version) { result ->
                    addBusy = false
                    when (result) {
                        is AddBotResult.Success -> {
                            showAddBot = false
                            addError = null
                            tab = QQTab.MESSAGE
                        }
                        is AddBotResult.Failure -> addError = result.message
                    }
                }
            },
        )
        return
    }

    val activeConv = state.conversations.firstOrNull { it.chatKey == state.activeChatKey }

    // ===== 群资料页 =====
    if (activeConv != null && showGroupInfo) {
        BackHandler { showGroupInfo = false }
        GroupInfoScreen(
            conversation = activeConv,
            onBack = { showGroupInfo = false },
            onFetchBotState = { vm.fetchBotState(activeConv.chatKey) },
        )
        return
    }

    // ===== 聊天页 =====
    if (activeConv != null) {
        BackHandler { vm.closeChat() }
        Box(Modifier.fillMaxSize()) {
            ChatScreen(
                conversation = activeConv,
                messages = state.messages,
                account = state.currentAccount,
                members = state.members,
                pendingSends = state.pendingSends.filter { it.chatKey == activeConv.chatKey },
                hasMoreOlder = state.hasMoreOlder,
                loadingOlder = state.loadingOlder,
                markdownMode = state.markdownMode,
                highlightMsgId = state.highlightMsgId,
                onToggleMarkdown = { vm.setMarkdownMode(it) },
                onBack = { vm.closeChat() },
                onLoadOlder = { vm.loadOlder() },
                onSend = { text, quoted, atMembers, atEveryone ->
                    vm.sendText(
                        chatKey = activeConv.chatKey,
                        rawText = text,
                        quoteMessageId = quoted?.messageId?.ifEmpty { quoted.id },
                        atMembers = atMembers,
                        atEveryone = atEveryone,
                        quoted = quoted,
                    )
                },
                onPickImage = { openAlbum(false) },
                onPickVideo = { openAlbum(true) },
                // 之前漏了这个回调，ChatScreen 里点「图片」走的是 onOpenAlbum 的默认空实现
                onOpenAlbum = { video -> openAlbum(video) },
                // 应用内相机（不再跳系统相机）
                onPickCamera = { showCamera = true },
                onRecall = { vm.recallMessage(activeConv.chatKey, it) },
                onShowInfo = { showGroupInfo = true },
                onShowMemberCard = {
                    memberCard = it
                    if (it.openId.isNotEmpty()) vm.refreshMember(activeConv.chatKey, it.openId)
                },
                onSearch = { showSearch = true },
                onSeenHighlight = { vm.clearHighlight() },
                onImageClick = { url -> viewerImage = url },
                insertText = pendingInsert,
                onInsertConsumed = { pendingInsert = null },
                // 相册：由 ChatScreen 内部按半屏/全屏参与布局
                albumVisible = showAlbum,
                albumMode = albumMode,
                albumVideoMode = albumVideoMode,
                onAlbumDismiss = { showAlbum = false },
                // 相册里勾选后点「发送」：直接发送（QQ PhotoPanelVB 发送按钮行为）
                onAlbumConfirm = { uris, _ ->
                    showAlbum = false
                    pendingAttachments = emptyList()
                    if (uris.isNotEmpty()) {
                        if (albumVideoMode) {
                            uris.forEach { vm.sendVideo(activeConv.chatKey, it) }
                        } else {
                            vm.sendImages(activeConv.chatKey, uris)
                        }
                    }
                },
                // 底栏「相册」→ 跳系统相册（QQ: PhotoPanelVM.jumpToAlbum）
                onAlbumPickFromSystem = {
                    showAlbum = false
                    runCatching {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_PICK,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        )
                        context.startActivity(intent)
                    }.onFailure { vm.toast("没有可用的相册应用") }
                },
                // 点击缩略图 → 全屏预览（QQ: PhotoPanelAdapter.b0 → PhotoPanelVM.h0）
                onAlbumPreview = { uri -> viewerImage = uri.toString() },
                onAlbumPermission = { openAlbum(albumVideoMode) },
                onAlbumToast = { vm.toast(it) },
                onToast = { vm.toast(it) },
                // 从相册里滑出去 → 立即发送这一张（QQ PhotoPanelVM 的 i.h → sendSingleMediaInfo）
                // 注意：QQ 滑出发送**不关闭面板**，只发这一张并刷新列表（h.m 里仅 notifyDataSetChanged）
                onAlbumDragToChat = { uri ->
                    if (albumVideoMode) vm.sendVideo(activeConv.chatKey, uri)
                    else vm.sendImage(activeConv.chatKey, uri)
                },
                // 相册内勾选：记到待发送，关闭相册后仍可在输入框上方看到
                attachments = pendingAttachments,
                onAttachmentsChange = { pendingAttachments = it },
                // 图文混发：相册里勾图 + 输入框有文字时点发送，一起发
                onSendWithAttachments = { text, quoted, atMembers, atEveryone, atts ->
                    if (atts.isNotEmpty()) {
                        vm.sendTextWithAttachments(
                            chatKey = activeConv.chatKey,
                            rawText = text,
                            quoteMessageId = quoted?.messageId?.ifEmpty { quoted.id },
                            atMembers = atMembers,
                            atEveryone = atEveryone,
                            quoted = quoted,
                            attachments = atts,
                        )
                    } else {
                        vm.sendText(
                            chatKey = activeConv.chatKey,
                            rawText = text,
                            quoteMessageId = quoted?.messageId?.ifEmpty { quoted.id },
                            atMembers = atMembers,
                            atEveryone = atEveryone,
                            quoted = quoted,
                        )
                    }
                },
            )
        }

        memberCard?.let { m ->
            // 打开资料卡时加载本地统计（发言数 / 最近发言）
            var profile by remember(m.openId, activeConv.chatKey) {
                mutableStateOf<Triple<Int, List<com.qqbot.client.bot.ChatMessage>, Long>>(Triple(0, emptyList(), 0L))
            }
            LaunchedEffect(m.openId, activeConv.chatKey) {
                profile = vm.memberProfile(activeConv.chatKey, m.openId)
            }
            MemberCardDialog(
                member = m,
                appId = state.currentAccount?.appId ?: "",
                botIsAdmin = state.botIsAdmin,
                messageCount = profile.first,
                recentMessages = profile.second,
                lastActive = profile.third,
                onDismiss = { memberCard = null },
                onMute = { minutes -> vm.muteMember(activeConv.chatKey, m.openId, minutes) },
                onUnmute = { vm.muteMember(activeConv.chatKey, m.openId, 0) },
                onMention = {
                    // 真正把 @昵称 插入输入框（而不是只弹提示）
                    pendingInsert = "@${m.nickname} "
                    memberCard = null
                },
                onJumpToMessage = { msg -> vm.jumpToMessage(activeConv.chatKey, msg) },
            )
        }

        if (showSearch) {
            ChatSearchDialog(
                results = searchResults,
                onQuery = { q ->
                    scope.launch {
                        searchResults = vm.searchInChat(activeConv.chatKey, q)
                    }
                },
                onPick = { msg -> vm.jumpToMessage(activeConv.chatKey, msg) },
                onDismiss = { showSearch = false },
            )
        }

        // ===== 应用内相机 =====
        if (showCamera) {
            BackHandler { showCamera = false }
            CameraScreen(
                onClose = { showCamera = false },
                onCaptured = { file ->
                    showCamera = false
                    vm.sendImage(activeConv.chatKey, android.net.Uri.fromFile(file))
                },
                onError = { vm.toast(it) },
            )
        }

        // ===== 全屏图片查看 =====
        viewerImage?.let { url ->
            BackHandler { viewerImage = null }
            ImageViewerScreen(
                imageUrl = url,
                onClose = { viewerImage = null },
                onShare = { shareImage = it },
                onToast = { vm.toast(it) },
            )
        }

        // ===== 分享到别的群 =====
        shareImage?.let { url ->
            ShareToChatDialog(
                groups = state.conversations.filter { it.isGroup },
                onDismiss = { shareImage = null },
                onPick = { conv ->
                    shareImage = null
                    viewerImage = null
                    scope.launch {
                        vm.shareImageTo(conv.chatKey, url)
                    }
                },
            )
        }
        return
    }

    // ===== 主界面 =====
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            QqBottomBar(
                current = tab,
                unread = state.totalUnread,
                onSelect = { tab = it },
            )
        },
        containerColor = Color.White,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                QQTab.MESSAGE -> MessageListScreen(
                    conversations = state.conversations,
                    onOpenChat = { conv: Conversation -> vm.openChat(conv.chatKey) },
                    onAddGroup = { addGroupDialog = true },
                    onDelete = { conv: Conversation -> vm.deleteConversation(conv.chatKey) },
                    onPin = { conv, pinned -> vm.setPinned(conv.chatKey, pinned) },
                )
                QQTab.CONTACT -> ContactsScreen(
                    conversations = state.conversations,
                    onOpenChat = { conv: Conversation -> vm.openChat(conv.chatKey) },
                    onAddGroup = { addGroupDialog = true },
                )
                QQTab.ACTIVITY -> ActivityScreen()
                QQTab.ME -> MeScreen(
                    account = state.currentAccount,
                    gateway = state.gateway,
                    accounts = state.accounts,
                    albumMode = albumMode,
                    onAlbumModeChange = { vm.setAlbumMode(if (it == AlbumMode.FULL) "full" else "half") },
                    cacheBytes = cacheBytes,
                    onCopyOpenId = { id ->
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("openid", id))
                        vm.toast("已复制 OpenID")
                    },
                    onAddBot = { showAddBot = true; addError = null },
                    onSwitch = { vm.switchAccount(it) },
                    onLogout = { vm.logout(it) },
                    onClearNameCache = { vm.clearNameCache() },
                )
            }
        }
    }

    if (addGroupDialog) {
        AddGroupDialog(
            onDismiss = { addGroupDialog = false },
            onConfirm = { id ->
                addGroupDialog = false
                vm.addGroup(id) { _, msg -> vm.toast(msg) }
            },
        )
    }
}

@Composable
private fun AddGroupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加群聊") },
        text = {
            Column {
                Text("输入群的 group_openid", color = QQ.TextSecondary)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    singleLine = true,
                    placeholder = { Text("例如 8F3A...") },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotEmpty()) onConfirm(text) }) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
