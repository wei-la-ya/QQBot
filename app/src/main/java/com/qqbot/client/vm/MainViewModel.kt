package com.qqbot.client.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qqbot.client.bot.ChatMessage
import com.qqbot.client.bot.ChatTarget
import com.qqbot.client.bot.ChatType
import com.qqbot.client.bot.GatewayClient
import com.qqbot.client.bot.GatewayState
import com.qqbot.client.bot.ImageHost
import com.qqbot.client.bot.MarkdownBuilder
import com.qqbot.client.bot.MessageSegment
import com.qqbot.client.bot.OpenApiClient
import com.qqbot.client.bot.QqBotConfig
import com.qqbot.client.bot.TokenManager
import com.qqbot.client.data.BotAccount
import com.qqbot.client.data.MediaCache
import com.qqbot.client.data.Conversation
import com.qqbot.client.data.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 登录/添加机器人结果 */
sealed class AddBotResult {
    data class Success(val account: BotAccount) : AddBotResult()
    data class Failure(val message: String) : AddBotResult()
}

/** 正在发送中的占位消息 */
data class PendingSend(
    val localId: String,
    val chatKey: String,
    val kind: Kind,
    val text: String = "",
    val localUri: String = "",
    val progress: String = "",
) {
    enum class Kind { TEXT, IMAGE, VIDEO }
}

data class UiState(
    val accounts: List<BotAccount> = emptyList(),
    val currentAccount: BotAccount? = null,
    val conversations: List<Conversation> = emptyList(),
    val activeChatKey: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val hasMoreOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    val gateway: GatewayState = GatewayState.Closed,
    val pendingSends: List<PendingSend> = emptyList(),
    val markdownMode: Boolean = false,
    val toast: String? = null,
    /** 定位到某条消息后高亮 */
    val highlightMsgId: String? = null,
    /** 当前会话的群成员（@ 映射用） */
    val members: List<com.qqbot.client.bot.GroupMember> = emptyList(),
    /** 机器人在当前群是否管理员（决定能否禁言） */
    val botIsAdmin: Boolean = false,
    /** 相册展示模式：half / full */
    val albumMode: String = "half",
) {
    val totalUnread: Int get() = conversations.sumOf { it.unread }
    val pinned: List<Conversation> get() = conversations.filter { it.pinned }
    val normal: List<Conversation> get() = conversations.filter { !it.pinned }
    val fullAccessGroups: List<Conversation> get() = conversations.filter { it.isGroup && it.fullAccess }
    val normalGroups: List<Conversation> get() = conversations.filter { it.isGroup && !it.fullAccess }
    val activeConversation: Conversation? get() = conversations.firstOrNull { it.chatKey == activeChatKey }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = StorageManager(app)
    private val mediaCache = com.qqbot.client.data.MediaCache(app)
    private val httpClient = TokenManager.defaultClient()

    private var apiClient: OpenApiClient? = null
    private var imageHost: ImageHost? = null
    private var gateway: GatewayClient? = null
    private var botOpenId: String = ""

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val accounts = storage.loadAccounts()
        val currentId = storage.currentAppId()
        val current = accounts.firstOrNull { it.appId == currentId } ?: accounts.firstOrNull()
        _state.update {
            it.copy(
                accounts = accounts,
                currentAccount = current,
                albumMode = storage.albumMode(),
                markdownMode = current?.let { a -> storage.markdownEnabled(a.appId) } ?: false,
            )
        }
        current?.let { startBot(it) }
        // 冷启动先清一次过期消息与图片缓存，避免要等下一条消息才清理
        viewModelScope.launch(Dispatchers.IO) {
            storage.trimOnStartup()
            mediaCache.trimExpired()
        }
    }

    // ==================== 账号登录 ====================

    fun addBot(appId: String, secret: String, version: QqBotConfig.ApiVersion, onDone: (AddBotResult) -> Unit) {
        viewModelScope.launch {
            try {
                val tm = TokenManager(appId.trim(), secret.trim(), version, httpClient)
                tm.getToken()
                val client = OpenApiClient(tm, httpClient)
                val gw = client.getGatewayUrl()

                // 上线即拉取机器人自身资料：openid、昵称、真实头像
                val self = client.getSelfInfo().takeIf { it.ok }?.data
                val account = BotAccount(
                    appId = appId.trim(),
                    appSecret = secret.trim(),
                    botName = self?.optString("username")?.ifEmpty { null }
                        ?: "QQ机器人 ${appId.trim().takeLast(4)}",
                    botOpenId = self?.optString("id").orEmpty(),
                    apiVersion = version,
                    avatar = self?.optString("avatar")?.replace("http://", "https://").orEmpty(),
                )
                withContext(Dispatchers.IO) {
                    val accounts = (_state.value.accounts.filter { it.appId != account.appId } + account)
                    storage.saveAccounts(accounts)
                    storage.setCurrentAppId(account.appId)
                }
                _state.update {
                    it.copy(
                        accounts = (it.accounts.filter { a -> a.appId != account.appId } + account),
                        currentAccount = account,
                        markdownMode = storage.markdownEnabled(account.appId),
                    )
                }
                startBot(account)

                if (!gw.ok) {
                    onDone(AddBotResult.Failure("登录成功，但网关获取失败：${gw.message()}"))
                } else {
                    onDone(AddBotResult.Success(account))
                }
            } catch (e: Exception) {
                onDone(AddBotResult.Failure(e.message ?: "登录失败"))
            }
        }
    }

    fun switchAccount(appId: String) {
        val account = _state.value.accounts.firstOrNull { it.appId == appId } ?: return
        storage.setCurrentAppId(appId)
        _state.update {
            it.copy(
                currentAccount = account,
                markdownMode = storage.markdownEnabled(appId),
                activeChatKey = null,
                messages = emptyList(),
            )
        }
        startBot(account)
    }

    fun logout(appId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = _state.value.accounts.filter { it.appId != appId }
            storage.saveAccounts(accounts)
            withContext(Dispatchers.Main) {
                gateway?.close()
                gateway = null
                apiClient = null
                imageHost = null
                if (_state.value.currentAccount?.appId == appId) {
                    storage.setCurrentAppId(null)
                    val next = accounts.firstOrNull()
                    _state.update {
                        it.copy(
                            accounts = accounts,
                            currentAccount = next,
                            conversations = emptyList(),
                            messages = emptyList(),
                            activeChatKey = null,
                        )
                    }
                    next?.let { startBot(it) }
                } else {
                    _state.update { it.copy(accounts = accounts) }
                }
            }
        }
    }

    // ==================== 启动机器人 ====================

    private fun startBot(account: BotAccount) {
        gateway?.close()
        // 优先用账号里的 openid，其次用之前从 mentions 学到的缓存
        botOpenId = account.botOpenId.ifEmpty { storage.selfOpenId(account.appId) }
        if (account.botOpenId.isEmpty() && botOpenId.isNotEmpty()) {
            val updated = account.copy(botOpenId = botOpenId)
            _state.update { st ->
                st.copy(
                    currentAccount = if (st.currentAccount?.appId == account.appId) updated else st.currentAccount,
                    accounts = st.accounts.map { if (it.appId == account.appId) updated else it },
                )
            }
        }

        val tm = TokenManager(account.appId, account.appSecret, account.apiVersion, httpClient)
        val client = OpenApiClient(tm, httpClient)
        apiClient = client
        imageHost = ImageHost(client)

        // 每次启动刷新一次自身资料：头像 URL 带签名有时效，缓存里的可能已失效
        viewModelScope.launch {
            val self = client.getSelfInfo()
            if (self.ok) {
                // 注意：/users/@me 的 id 是「机器人 ID」数字串，不是消息里用的 openid。
                // 之前这里会把它写进 botOpenId，导致学到的 32 位 hex openid 被冲掉、
                // 自己发的消息识别不出 isSelf。只有当前为空、或不是 32 位 hex 时才回退用它。
                val selfId = self.data.optString("id")
                if (botOpenId.isEmpty() || !isHexOpenId(botOpenId)) {
                    botOpenId = selfId.ifEmpty { botOpenId }
                }
                val latest = _state.value.currentAccount
                if (latest != null && latest.appId == account.appId) {
                    val updated = latest.copy(
                        botName = self.data.optString("username").ifEmpty { latest.botName },
                        botOpenId = botOpenId,
                        // 用 ifEmpty 兜底：接口没返回 avatar 时不要清掉现有头像
                        avatar = self.data.optString("avatar")
                            .replace("http://", "https://")
                            .ifEmpty { latest.avatarUrl },
                    )
                    _state.update {
                        it.copy(
                            currentAccount = updated,
                            accounts = it.accounts.map { a -> if (a.appId == account.appId) updated else a },
                        )
                    }
                    withContext(Dispatchers.IO) {
                        storage.saveAccounts(_state.value.accounts)
                        storage.setSelfOpenId(account.appId, botOpenId)
                    }
                }
            }
        }

        refreshConversations()

        // 冷启动时 botOpenId 可能还是 /users/@me 的数字 id（旧版本留下的缓存），
        // 用已存的群会话调一次 bot_state 把它换成真正的 32 位 hex openid。
        viewModelScope.launch {
            kotlinx.coroutines.delay(1200)
            if (isHexOpenId(botOpenId)) return@launch
            val groups = runCatching {
                storage.loadConversations(account.appId)
                    .filter { it.chatType == ChatType.GROUP }
                    .map { it.chatKey }
            }.getOrDefault(emptyList())
            for (gid in groups) {
                learnBotOpenIdFromGroup(account, gid)
                kotlinx.coroutines.delay(800)
                if (isHexOpenId(botOpenId)) break
            }
        }

        // 自身资料定时刷新：/users/@me 的 avatar 是带 kti/t 的签名 URL，会过期，
        // 只刷一次的话跑久了自己的头像就 403（之前注释写「每隔N分钟」但没有循环）。
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30 * 60 * 1000L)
                val self = client.getSelfInfo()
                if (!self.ok) continue
                val latest = _state.value.currentAccount ?: continue
                if (latest.appId != account.appId) continue
                val newAvatar = self.data.optString("avatar")
                    .replace("http://", "https://")
                    .ifEmpty { latest.avatarUrl }
                val updated = latest.copy(
                    botName = self.data.optString("username").ifEmpty { latest.botName },
                    avatar = newAvatar,
                )
                _state.update { st ->
                    st.copy(
                        currentAccount = updated,
                        accounts = st.accounts.map { a -> if (a.appId == account.appId) updated else a },
                    )
                }
                withContext(Dispatchers.IO) { storage.saveAccounts(_state.value.accounts) }
            }
        }

        val gw = GatewayClient(
            appId = account.appId,
            apiClient = client,
            scope = viewModelScope,
            onEvent = { type, d -> handleEvent(account, type, d) },
            onState = { st -> _state.update { it.copy(gateway = st) } },
            onSelf = { openId, name ->
                // 上线即拿到机器人自身 openid 与昵称，用于显示自己的头像
                botOpenId = openId
                val latest = _state.value.currentAccount
                if (latest != null && latest.appId == account.appId) {
                    val updated = latest.copy(
                        botOpenId = openId,
                        botName = name.ifEmpty { latest.botName },
                    )
                    _state.update { st ->
                        st.copy(
                            currentAccount = updated,
                            accounts = st.accounts.map { if (it.appId == account.appId) updated else it },
                        )
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        storage.saveAccounts(_state.value.accounts)
                        storage.setSelfOpenId(account.appId, openId)
                    }
                }
            },
        )
        gateway = gw
        gw.connect()
    }

    /** 32 位 hex 才算真正的 openid，数字串是机器人 ID */
    private fun isHexOpenId(s: String): Boolean =
        s.length == 32 && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    /** 已通过 bot_state 学过 openid 的群，避免重复请求 */
    private val botStateQueried = mutableSetOf<String>()

    /**
     * 用 GET /v2/groups/{group_openid}/bot_state 拿到机器人自身 openid。
     * 每个群只需成功一次；失败（如 11253 无权限）不重试，退回 mentions 学习。
     */
    private fun learnBotOpenIdFromGroup(account: BotAccount, groupOpenId: String) {
        if (groupOpenId.isEmpty()) return
        if (isHexOpenId(botOpenId)) return
        if (!botStateQueried.add("${account.appId}:$groupOpenId")) return
        val client = apiClient ?: return
        viewModelScope.launch {
            val res = client.getGroupBotState(groupOpenId)
            val oid = if (res.ok) res.data.optString("member_openid") else ""
            if (isHexOpenId(oid) && oid != botOpenId) {
                botOpenId = oid
                val latest = _state.value.currentAccount
                if (latest != null && latest.appId == account.appId) {
                    val updated = latest.copy(botOpenId = oid)
                    _state.update { st ->
                        st.copy(
                            currentAccount = updated,
                            accounts = st.accounts.map { a -> if (a.appId == account.appId) updated else a },
                        )
                    }
                }
                withContext(Dispatchers.IO) { saveSelfOpenId(account.appId, oid) }
            }
        }
    }

    private fun handleEvent(account: BotAccount, type: String, d: JSONObject) {
        when (type) {
            QqBotConfig.EV_GROUP_AT_MESSAGE_CREATE,
            QqBotConfig.EV_GROUP_MESSAGE_CREATE,
            QqBotConfig.EV_C2C_MESSAGE_CREATE -> {
                // 群消息顺带用 bot_state 学一次机器人自身 openid（拿不到就靠 mentions 兜底）
                if (type != QqBotConfig.EV_C2C_MESSAGE_CREATE) {
                    learnBotOpenIdFromGroup(account, d.optString("group_openid"))
                }
                val msg = ChatMessage.fromEvent(type, d, botOpenId.ifEmpty { account.botOpenId }) ?: return
                onIncoming(account, msg)
            }
            QqBotConfig.EV_GROUP_ADD_ROBOT -> {
                val groupOpenId = d.optString("group_openid")
                if (groupOpenId.isNotEmpty()) ensureGroupConversation(account, groupOpenId)
            }
            QqBotConfig.EV_INTERACTION_CREATE -> {
                val id = d.optString("id")
                if (id.isNotEmpty()) viewModelScope.launch { apiClient?.respondInteraction(id) }
            }
        }
    }

    /** 机器人自己的 openid 存到偏好里，重启后自己的头像仍然正确 */
    private fun saveSelfOpenId(appId: String, openId: String) {
        storage.setSelfOpenId(appId, openId)
        val account = _state.value.currentAccount
        if (account != null && account.appId == appId && account.botOpenId != openId) {
            val updated = account.copy(botOpenId = openId)
            _state.update { st ->
                st.copy(
                    currentAccount = updated,
                    accounts = st.accounts.map { if (it.appId == appId) updated else it },
                )
            }
        }
    }

    /**
     * 把消息里的图片下载到本地缓存，返回带 localPath 的消息。
     *
     * 只缓存图片（不缓存视频/语音），单张上限 20MB；
     * 下载失败不影响消息入库，仍保留远程 URL。
     */
    private fun cacheMedia(msg: ChatMessage): ChatMessage {
        if (msg.segments.none { it.kind == MessageSegment.Kind.IMAGE && it.url.isNotEmpty() }) return msg
        val newSegments = msg.segments.map { seg ->
            if (seg.kind != MessageSegment.Kind.IMAGE || seg.url.isEmpty()) return@map seg
            // 已有缓存就跳过
            if (mediaCache.existing(seg.url, MediaCache.extOf(seg.url)) != null) {
                return@map seg.copy(localPath = mediaCache.fileFor(seg.url, MediaCache.extOf(seg.url)).absolutePath)
            }
            val bytes = downloadBytes(seg.url) ?: return@map seg
            val ext = MediaCache.sniffExt(bytes)
            val path = mediaCache.put(seg.url, bytes, ext)
            if (path != null) seg.copy(localPath = path) else seg
        }
        if (newSegments == msg.segments) return msg

        // markdown 消息里的图片是内嵌 URL，把正文中的 URL 换成对应的本地路径标记，
        // 渲染时由 MarkdownText 优先读本地文件。
        var text = msg.text
        newSegments.forEach { seg ->
            if (seg.localPath.isNotEmpty() && seg.url.isNotEmpty()) {
                text = text.replace(seg.url, "file://${seg.localPath}")
            }
        }
        return msg.copy(segments = newSegments, text = text)
    }

    /** 下载网络资源，带大小上限与超时 */
    private fun downloadBytes(url: String, maxBytes: Long = 20L * 1024 * 1024): ByteArray? = try {
        val req = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else resp.body?.let { body ->
                if (body.contentLength() > maxBytes) null else body.bytes()
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun onIncoming(account: BotAccount, msg: ChatMessage) {
        val active = _state.value.activeChatKey
        // 官方没有获取机器人 openid 的接口，只能从 mentions 里 is_you=true 的那条学到
        if (botOpenId.isEmpty()) {
            com.qqbot.client.bot.MemberBook.learnSelfOpenId(msg)?.let { selfId ->
                botOpenId = selfId
                viewModelScope.launch(Dispatchers.IO) { saveSelfOpenId(account.appId, selfId) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // 先把消息里的图片下载到本地缓存，存库时一并带上 localPath，
            // 这样断网或 CDN 链接过期后，历史消息里的图仍然能显示。
            val cachedMsg = cacheMedia(msg)
            val wasActive = storage.recordIncoming(account.appId, cachedMsg, active)
            // 从消息里学习群成员，供 @ 映射与资料卡使用
            val learned = com.qqbot.client.bot.MemberBook.learnFrom(cachedMsg, botOpenId.ifEmpty { account.botOpenId })
            if (learned.isNotEmpty()) storage.upsertMembers(account.appId, cachedMsg.chatKey, learned)

            if (wasActive) storage.clearUnread(account.appId, cachedMsg.chatKey)
            val convs = storage.loadConversations(account.appId)
            val isActiveChat = active == cachedMsg.chatKey
            val members = if (isActiveChat) storage.loadMembers(account.appId, cachedMsg.chatKey) else emptyList()
            withContext(Dispatchers.Main) {
                _state.update { st ->
                    if (isActiveChat) {
                        // 同一 id 可能因分页边界重复，去重后再追加
                        val exists = st.messages.any { it.id == cachedMsg.id }
                        st.copy(
                            messages = if (exists) st.messages else st.messages + cachedMsg,
                            conversations = convs,
                            members = members,
                        )
                    } else {
                        st.copy(conversations = convs)
                    }
                }
            }
        }
        if (msg.chatType == ChatType.GROUP) ensureGroupConversation(account, msg.chatKey, onlyInfo = true)
    }

    private fun ensureGroupConversation(account: BotAccount, groupOpenId: String, onlyInfo: Boolean = false) {
        viewModelScope.launch {
            val existing = _state.value.conversations.firstOrNull { it.chatKey == groupOpenId }
            if (existing != null && existing.title.isNotEmpty() && existing.memberCount > 0 && existing.fullAccess) return@launch
            withContext(Dispatchers.IO) {
                storage.upsertConversation(account.appId, groupOpenId, ChatType.GROUP, "", "")
            }
            val info = apiClient?.getGroupInfo(groupOpenId)
            val state = apiClient?.getGroupBotState(groupOpenId)
            withContext(Dispatchers.IO) {
                if (info != null && info.ok) {
                    val name = info.data.optString("group_name")
                    val count = info.data.optInt("group_member_num", 0)
                    if (name.isNotEmpty()) storage.setConversationTitle(account.appId, groupOpenId, name)
                    if (count > 0) storage.setMemberCount(account.appId, groupOpenId, count)
                }
                if (state != null && state.ok) {
                    val full = state.data.optString("recv_msg_setting") == "all"
                    storage.setFullAccess(account.appId, groupOpenId, full)
                    storage.setBotRole(account.appId, groupOpenId, state.data.optString("member_role"))
                }
                val convs = storage.loadConversations(account.appId)
                withContext(Dispatchers.Main) { _state.update { it.copy(conversations = convs) } }
            }
        }
    }

    private fun refreshConversations() {
        val account = _state.value.currentAccount ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val convs = storage.loadConversations(account.appId)
            withContext(Dispatchers.Main) { _state.update { it.copy(conversations = convs) } }
        }
    }

    // ==================== 会话切换与分页 ====================

    fun openChat(chatKey: String) {
        val account = _state.value.currentAccount ?: return
        _state.update {
            it.copy(activeChatKey = chatKey, messages = emptyList(), hasMoreOlder = false, loadingOlder = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearUnread(account.appId, chatKey)
            val page = storage.loadLatest(account.appId, chatKey)
            val convs = storage.loadConversations(account.appId)
            val members = storage.loadMembers(account.appId, chatKey)
            val role = storage.botRole(account.appId, chatKey)
            val isAdmin = role == "admin" || role == "owner"
            withContext(Dispatchers.Main) {
                if (_state.value.activeChatKey == chatKey) {
                    _state.update {
                        it.copy(
                            messages = page.messages,
                            hasMoreOlder = page.hasMoreOlder,
                            conversations = convs,
                            members = members,
                            botIsAdmin = isAdmin,
                        )
                    }
                } else {
                    _state.update { it.copy(conversations = convs) }
                }
            }
        }
        if (_state.value.conversations.firstOrNull { it.chatKey == chatKey && it.isGroup } != null) {
            ensureGroupConversation(account, chatKey, onlyInfo = true)
        }
    }

    /** 向上翻页加载更早消息 */
    fun loadOlder() {
        val account = _state.value.currentAccount ?: return
        val s = _state.value
        val chatKey = s.activeChatKey ?: return
        if (s.loadingOlder || !s.hasMoreOlder) return
        val oldest = s.messages.firstOrNull() ?: return
        _state.update { it.copy(loadingOlder = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val page = storage.loadOlder(account.appId, chatKey, oldest)
            withContext(Dispatchers.Main) {
                if (_state.value.activeChatKey != chatKey) return@withContext
                _state.update {
                    it.copy(
                        messages = page.messages + it.messages,
                        hasMoreOlder = page.hasMoreOlder,
                        loadingOlder = false,
                    )
                }
            }
        }
    }

    fun closeChat() {
        _state.update {
            it.copy(activeChatKey = null, messages = emptyList(), hasMoreOlder = false, highlightMsgId = null)
        }
    }

    /** 手动添加群聊 */
    fun addGroup(groupOpenId: String, onDone: (Boolean, String) -> Unit) {
        val account = _state.value.currentAccount ?: return onDone(false, "未登录")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                storage.upsertConversation(account.appId, groupOpenId, ChatType.GROUP, "", "")
            }
            val res = apiClient?.getGroupInfo(groupOpenId)
            val state = apiClient?.getGroupBotState(groupOpenId)
            withContext(Dispatchers.IO) {
                if (res != null && res.ok) {
                    val name = res.data.optString("group_name")
                    if (name.isNotEmpty()) storage.setConversationTitle(account.appId, groupOpenId, name)
                    storage.setMemberCount(account.appId, groupOpenId, res.data.optInt("group_member_num", 0))
                }
                if (state != null && state.ok) {
                    storage.setFullAccess(account.appId, groupOpenId, state.data.optString("recv_msg_setting") == "all")
                    storage.setBotRole(account.appId, groupOpenId, state.data.optString("member_role"))
                }
                val convs = storage.loadConversations(account.appId)
                withContext(Dispatchers.Main) { _state.update { it.copy(conversations = convs) } }
            }
            onDone(true, res?.data?.optString("group_name").orEmpty().ifEmpty { "已添加" })
        }
    }

    fun deleteConversation(chatKey: String) {
        val account = _state.value.currentAccount ?: return
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteConversation(account.appId, chatKey)
            val convs = storage.loadConversations(account.appId)
            withContext(Dispatchers.Main) {
                if (_state.value.activeChatKey == chatKey) closeChat()
                _state.update { it.copy(conversations = convs) }
            }
        }
    }

    fun setPinned(chatKey: String, pinned: Boolean) {
        val account = _state.value.currentAccount ?: return
        viewModelScope.launch(Dispatchers.IO) {
            storage.setPinned(account.appId, chatKey, pinned)
            val convs = storage.loadConversations(account.appId)
            withContext(Dispatchers.Main) { _state.update { it.copy(conversations = convs) } }
        }
    }

    // ==================== 输入模式 ====================

    fun setMarkdownMode(enabled: Boolean) {
        val account = _state.value.currentAccount ?: return
        storage.setMarkdownEnabled(account.appId, enabled)
        _state.update { it.copy(markdownMode = enabled) }
    }

    /** 切换相册展示方式（半屏 / 全屏），持久化到偏好 */
    fun setAlbumMode(mode: String) {
        storage.setAlbumMode(mode)
        _state.update { it.copy(albumMode = mode) }
    }

    // ==================== 发送 ====================

    private fun targetOf(chatKey: String): ChatTarget {
        val conv = _state.value.conversations.firstOrNull { it.chatKey == chatKey }
        return if (conv?.isGroup != false) ChatTarget.Group(chatKey) else ChatTarget.User(chatKey)
    }

    /**
     * 发送文字。
     *
     * 规则（与 QQ 官方机器人一致）：
     *  - 普通模式：msg_type=0 纯文本
     *  - MD 模式：msg_type=2；用户输入没有 md 语法时自动按纯文本转义包装
     *  - 带 @ 或引用时：**强制 Markdown**
     *      @ 用户 → `<qqbot-at-user id="openid" />`，@ 全体 → `<qqbot-at-everyone />`
     *      引用   → `message_reference`
     *  - 输入框里的"@昵称"会在这里映射回 openid
     */
    fun sendText(
        chatKey: String,
        rawText: String,
        quoteMessageId: String? = null,
        atMembers: List<com.qqbot.client.bot.GroupMember> = emptyList(),
        atEveryone: Boolean = false,
        quoted: ChatMessage? = null,
    ) {
        val text = rawText.trim()
        // 输入框里的「@全体成员」文本同样视为全体艾特（与 QQ 一致：插入的是标签文本）
        val everyoneInText = text.contains("@${com.qqbot.client.bot.AtSyntax.EVERYONE_NAME}")
        val finalAtEveryone = atEveryone || everyoneInText
        if (text.isEmpty() && atMembers.isEmpty() && !finalAtEveryone) return
        val account = _state.value.currentAccount ?: return
        val target = targetOf(chatKey)
        val isGroup = target is ChatTarget.Group

        val needMd = _state.value.markdownMode || atMembers.isNotEmpty() || finalAtEveryone || !quoteMessageId.isNullOrEmpty()
        val body = if (needMd) {
            // 先把输入里的"@昵称"与"@全体成员"移除（避免重复），再统一加上官方提及语法
            var t = text
            atMembers.forEach { m -> t = t.replace("@${m.nickname}", "").trim() }
            t = t.replace("@${com.qqbot.client.bot.AtSyntax.EVERYONE_NAME}", "").trim()
            buildString {
                atMembers.forEach { m ->
                    append(com.qqbot.client.bot.AtSyntax.user(m.openId))
                }
                if (finalAtEveryone) append(com.qqbot.client.bot.AtSyntax.everyone())
                if (atMembers.isNotEmpty() || finalAtEveryone) append(' ')
                append(
                    when {
                        // 普通模式：一律按纯文本发送（严格转义，任何语法都不会生效）
                        // 但表情标签 <faceType=...> 必须原样保留，否则会变成一堆反斜杠
                        !_state.value.markdownMode -> protectFaces(t) { MarkdownBuilder.autoWrapPlain(it) }
                        // MD 模式：用户已经手写了 md 语法就原样发，否则按块级语法混排包装
                        looksLikeMarkdown(t) -> t
                        else -> protectFaces(t) { MarkdownBuilder.autoWrapMixed(it) }
                    }
                )
            }.trim()
        } else text

        if (MarkdownBuilder.isTooLong(body)) {
            toast("消息过长（超过 4000 字节），请分段发送")
            return
        }

        // 本地回显用可读形式（@昵称），与 QQ 聊天记录一致
        val display = if (needMd) {
            com.qqbot.client.bot.AtSyntax.toDisplay(body, atMembers.associate { it.openId to it.nickname })
        } else body

        viewModelScope.launch {
            val res = apiClient?.sendText(
                target = target,
                content = body,
                msgType = if (needMd) QqBotConfig.MSG_TYPE_MARKDOWN else QqBotConfig.MSG_TYPE_TEXT,
                referenceId = quoteMessageId,
            )
            if (res != null && res.ok) {
                val local = ChatMessage(
                    id = "local_" + System.nanoTime(),
                    chatKey = chatKey,
                    chatType = if (isGroup) ChatType.GROUP else ChatType.PRIVATE,
                    senderOpenId = account.botOpenId,
                    senderName = account.botName,
                    text = display,
                    // 发送引用消息时，本地也要记录被引用的内容，气泡才会显示引用样式
                    quote = quoted?.let {
                        com.qqbot.client.bot.QuoteRef(
                            messageId = it.messageId.ifEmpty { it.id },
                            authorName = if (it.isSelf) account.botName else it.senderName,
                            text = quoteSummary(it),
                        )
                    },
                    timestampMs = System.currentTimeMillis(),
                    isSelf = true,
                    // 只要这条消息是作为 Markdown 发出去的，本地就按 Markdown 渲染
                    markdown = needMd,
                )
                withContext(Dispatchers.IO) {
                    storage.saveMessage(account.appId, local)
                    val convs = storage.loadConversations(account.appId)
                    withContext(Dispatchers.Main) {
                        _state.update { it.copy(messages = it.messages + local, conversations = convs) }
                    }
                }
            } else {
                toast("发送失败：${res?.message() ?: "未知错误"}")
            }
        }
    }

    /**
     * 发送图片。
     * - 普通模式：走富媒体接口（msg_type=7），和 QQ 发图一样
     * - MD 模式：先通过图床上传到分片接口拿公网 URL，再把图片作为 md 插图发送
     */
    fun sendImage(chatKey: String, uri: android.net.Uri, caption: String = "") {
        sendMedia(chatKey, uri, isVideo = false, caption = caption)
    }

    /**
     * 相册里勾选多张图后直接发送（QQ 行为：立即发出，不等文字）。
     * 逐张排队发送，保证顺序。
     */
    fun sendImages(chatKey: String, uris: List<android.net.Uri>) {
        viewModelScope.launch {
            // 逐张发送并按顺序等待完成，避免并发上传导致顺序错乱
            uris.forEach { uri ->
                sendMedia(chatKey, uri, isVideo = false)?.join()
            }
        }
    }

    fun sendVideo(chatKey: String, uri: android.net.Uri) {
        sendMedia(chatKey, uri, isVideo = true)
    }

    private fun sendMedia(
        chatKey: String,
        uri: android.net.Uri,
        isVideo: Boolean,
        caption: String = "",
    ): kotlinx.coroutines.Job? {
        val account = _state.value.currentAccount ?: return null
        val target = targetOf(chatKey)
        val isGroup = target is ChatTarget.Group
        val mdMode = _state.value.markdownMode && !isVideo

        val localId = "pending_" + System.nanoTime()
        _state.update {
            it.copy(
                pendingSends = it.pendingSends + PendingSend(
                    localId = localId,
                    chatKey = chatKey,
                    kind = if (isVideo) PendingSend.Kind.VIDEO else PendingSend.Kind.IMAGE,
                    localUri = uri.toString(),
                    progress = if (mdMode) "上传到图床…" else "上传中…",
                )
            )
        }

        return viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("无法读取文件")
                // 防止超大文件把内存撑爆（普通视频一般远小于此）
                if (raw.size > MAX_UPLOAD_BYTES) {
                    finishPending(localId)
                    toast("文件过大（>${MAX_UPLOAD_BYTES / 1024 / 1024}MB），请选择更小的文件")
                    return@launch
                }

                if (mdMode) {
                    // === MD 模式：图床 → 拿 URL → md 照片 ===
                    val bytes = ImageHost.compressForUpload(raw)
                    val size = ImageHost.decodeSize(bytes)
                    val host = imageHost ?: throw IllegalStateException("图床未初始化")
                    val uploaded = host.upload(target, bytes, "image_${System.currentTimeMillis()}.jpg")
                        ?: throw IllegalStateException("图床上传失败")

                    val md = buildString {
                        append(MarkdownBuilder.image(uploaded.imageUrl, size?.first ?: 0, size?.second ?: 0))
                        if (caption.isNotBlank()) {
                            append('\n')
                            append(MarkdownBuilder.autoWrap(caption))
                        }
                    }
                    val res = apiClient?.sendText(
                        target = target,
                        content = md,
                        msgType = QqBotConfig.MSG_TYPE_MARKDOWN,
                    )
                    finishPending(localId)
                    if (res != null && res.ok) {
                        saveLocalMedia(account, chatKey, isGroup, md, uploaded.imageUrl, markdown = true)
                    } else {
                        toast("发送失败：${res?.message() ?: "未知错误"}")
                    }
                } else {
                    // === 普通模式：富媒体消息 ===
                    _state.update { st ->
                        st.copy(pendingSends = st.pendingSends.map {
                            if (it.localId == localId) it.copy(progress = "发送中…") else it
                        })
                    }
                    val res = apiClient?.sendMedia(
                        target = target,
                        bytes = raw,
                        fileType = if (isVideo) QqBotConfig.MEDIA_VIDEO else QqBotConfig.MEDIA_IMAGE,
                        fileName = "upload_${System.currentTimeMillis()}." + if (isVideo) "mp4" else "png",
                    )
                    finishPending(localId)
                    if (res != null && res.ok) {
                        saveLocalMedia(account, chatKey, isGroup, "", uri.toString(), markdown = false, isVideo = isVideo)
                    } else {
                        toast("发送失败：${res?.message() ?: "未知错误"}")
                    }
                }
            } catch (e: Exception) {
                finishPending(localId)
                toast("发送失败：${e.message}")
            }
        }
    }

    private fun finishPending(localId: String) {
        _state.update { st -> st.copy(pendingSends = st.pendingSends.filter { it.localId != localId }) }
    }

    /**
     * 保存一条本地（自己发的）媒体消息。
     *
     * MD 模式下图片是内嵌在 markdown 文本里的，所以必须保留 [text] 原文，
     * 由 MarkdownText 解析出 `![](url)` 再渲染；普通模式才用 [url] 作为图片片段。
     */
    /**
     * 把自己发送时选中的图片转存到应用私有缓存，返回本地路径。
     * 支持 content:// （相册/相机）与 http(s):// 两种来源。
     */
    private fun cacheLocalUri(appId: String, uriString: String): String {
        if (uriString.isEmpty()) return ""
        return try {
            when {
                uriString.startsWith("content://") -> {
                    val key = "self_${appId}_$uriString"
                    mediaCache.existing(key)?.absolutePath ?: run {
                        val ctx = getApplication<Application>()
                        val bytes = ctx.contentResolver.openInputStream(android.net.Uri.parse(uriString))
                            ?.use { it.readBytes() } ?: return ""
                        val ext = MediaCache.sniffExt(bytes)
                        mediaCache.put(key, bytes, ext) ?: ""
                    }
                }
                uriString.startsWith("http") -> {
                    mediaCache.existing(uriString, MediaCache.extOf(uriString))?.absolutePath
                        ?: downloadBytes(uriString)?.let { b ->
                            mediaCache.put(uriString, b, MediaCache.sniffExt(b))
                        } ?: ""
                }
                else -> uriString // 已经是本地路径
            }
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun saveLocalMedia(
        account: BotAccount,
        chatKey: String,
        isGroup: Boolean,
        text: String,
        url: String,
        markdown: Boolean,
        isVideo: Boolean = false,
    ) {
        val local = ChatMessage(
            id = "local_" + System.nanoTime(),
            chatKey = chatKey,
            chatType = if (isGroup) ChatType.GROUP else ChatType.PRIVATE,
            senderOpenId = account.botOpenId,
            senderName = account.botName,
            text = if (markdown) text else "",
            segments = if (markdown) emptyList() else {
                // 自己发的图同样落盘缓存：content:// 是可读的本地来源，转存为私有副本，
                // 这样系统相册清理临时文件后，聊天记录里的图仍能显示。
                val cachedPath = cacheLocalUri(account.appId, url)
                listOf(
                    MessageSegment(
                        kind = if (isVideo) MessageSegment.Kind.VIDEO else MessageSegment.Kind.IMAGE,
                        url = url,
                        localPath = cachedPath,
                    )
                )
            },
            timestampMs = System.currentTimeMillis(),
            isSelf = true,
            markdown = markdown,
        )
        withContext(Dispatchers.IO) {
            storage.saveMessage(account.appId, local)
            val convs = storage.loadConversations(account.appId)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(messages = it.messages + local, conversations = convs) }
            }
        }
    }

    /**
     * 转义时保护表情标签。
     * 用户选的表情会以 `<faceType=...>` 形式插入输入框，
     * 这些标签是发给服务端的指令，绝不能被当成普通文字转义。
     */
    private fun protectFaces(text: String, escape: (String) -> String): String {
        val faceRegex = Regex("<faceType=[^>]*>")
        val placeholders = mutableListOf<String>()
        val replaced = faceRegex.replace(text) { m ->
            placeholders.add(m.value)
            "\u0000F${placeholders.size - 1}\u0000"
        }
        var result = escape(replaced)
        placeholders.forEachIndexed { i, tag ->
            result = result.replace("\u0000F$i\u0000", tag)
        }
        return result
    }

    /** 被引用消息的摘要文字（图片/视频等显示为占位标签） */
    private fun quoteSummary(m: ChatMessage): String {
        val media = when {
            m.segments.any { it.kind == com.qqbot.client.bot.MessageSegment.Kind.IMAGE } -> "[图片]"
            m.segments.any { it.kind == com.qqbot.client.bot.MessageSegment.Kind.VIDEO } -> "[视频]"
            m.segments.any { it.kind == com.qqbot.client.bot.MessageSegment.Kind.VOICE } -> "[语音]"
            else -> ""
        }
        val text = m.text.replace(Regex("\\s+"), " ").take(40)
        return listOf(media, text).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "[消息]" }
    }

    /** 粗略判断用户输入是否已经包含 Markdown 语法 */
    private fun looksLikeMarkdown(s: String): Boolean {
        if (s.isBlank()) return false
        val patterns = listOf(
            Regex("^#{1,6}\\s", RegexOption.MULTILINE),
            Regex("\\*\\*[^*]+\\*\\*"),
            Regex("```"),
            Regex("!\\[[^]]*]\\([^)]+\\)"),
            Regex("\\[[^]]+]\\([^)]+\\)"),
            Regex("^>\\s", RegexOption.MULTILINE),
            Regex("^---\\s*$", RegexOption.MULTILINE),
            Regex("<@[^>]+>"),
        )
        return patterns.any { it.containsMatchIn(s) }
    }

    fun recallMessage(chatKey: String, messageId: String) {
        val account = _state.value.currentAccount ?: return
        val target = targetOf(chatKey)
        viewModelScope.launch {
            val res = apiClient?.recall(target, messageId)
            if (res != null && res.ok) {
                withContext(Dispatchers.IO) { storage.markRecalled(account.appId, messageId) }
                _state.update { st ->
                    st.copy(messages = st.messages.map { if (it.id == messageId) it.copy(recalled = true) else it })
                }
            } else {
                toast("撤回失败：${res?.message() ?: "该消息已过期"}")
            }
        }
    }

    // ==================== 查找聊天记录 / 跳转 ====================

    suspend fun searchInChat(chatKey: String, keyword: String): List<ChatMessage> {
        val account = _state.value.currentAccount ?: return emptyList()
        return withContext(Dispatchers.IO) { storage.searchMessages(account.appId, chatKey, keyword) }
    }

    /** 跳到某条历史消息：载入它前后的消息并高亮 */
    fun jumpToMessage(chatKey: String, msg: ChatMessage) {
        val account = _state.value.currentAccount ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val around = storage.loadAround(account.appId, chatKey, msg)
            val hasMore = storage.countMessages(account.appId, chatKey) > around.size
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        activeChatKey = chatKey,
                        messages = around,
                        hasMoreOlder = hasMore,
                        highlightMsgId = msg.id,
                    )
                }
            }
        }
    }

    fun clearHighlight() = _state.update { it.copy(highlightMsgId = null) }

    // ==================== 群成员 / 禁言 ====================

    /** 查询单个成员资料并写入成员表（用于资料卡补全昵称与角色） */
    /**
     * 清除昵称类缓存（成员表里的群昵称/QQ昵称/角色），**不动消息与图片**。
     * 用于修掉「别人改了昵称但本地还是旧的」这种情况，之后收到消息会重新学习。
     */
    fun clearNameCache() {
        val appId = _state.value.currentAccount?.appId ?: return
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { storage.clearMemberCache(appId) }
            refreshConversations()
            toast("已清除昵称缓存（消息与图片未动）")
            android.util.Log.i("MainViewModel", "clearNameCache rows=$n")
        }
    }

    fun refreshMember(chatKey: String, openId: String) {
        val account = _state.value.currentAccount ?: return
        viewModelScope.launch {
            val res = apiClient?.getGroupMember(chatKey, openId) ?: return@launch
            if (!res.ok) return@launch
            val nickname = res.data.optString("username")
            val member = com.qqbot.client.bot.MemberBook.fromRoleResponse(openId, nickname, res.data)
            withContext(Dispatchers.IO) {
                storage.upsertMember(account.appId, chatKey, member)
                val members = storage.loadMembers(account.appId, chatKey)
                withContext(Dispatchers.Main) {
                    if (_state.value.activeChatKey == chatKey) {
                        _state.update { it.copy(members = members) }
                    }
                }
            }
        }
    }

    /** 禁言成员（分钟，<=0 为解除）。需要机器人是群管理员 */
    fun muteMember(chatKey: String, openId: String, minutes: Int) {
        viewModelScope.launch {
            val res = apiClient?.muteMember(chatKey, openId, minutes)
            if (res != null && res.ok) {
                toast(if (minutes <= 0) "已解除禁言" else "已禁言 $minutes 分钟")
            } else {
                val msg = res?.message().orEmpty()
                toast(
                    when {
                        msg.contains("权限") || msg.contains("permission", true) -> "禁言失败：机器人不是群管理员"
                        else -> "禁言失败：${msg.ifEmpty { "未知错误" }}"
                    }
                )
            }
        }
    }

    /** 成员资料卡需要的本地数据：发言数、最近发言、最后活跃时间 */
    suspend fun memberProfile(
        chatKey: String,
        openId: String,
    ): Triple<Int, List<ChatMessage>, Long> {
        val account = _state.value.currentAccount ?: return Triple(0, emptyList(), 0L)
        return withContext(Dispatchers.IO) {
            Triple(
                storage.memberMessageCount(account.appId, chatKey, openId),
                storage.memberMessages(account.appId, chatKey, openId, limit = 50),
                storage.memberLastActive(account.appId, chatKey, openId),
            )
        }
    }

    // ==================== 群资料 ====================

    suspend fun fetchBotState(groupOpenId: String): Map<String, String>? {
        val res = apiClient?.getGroupBotState(groupOpenId) ?: return null
        if (!res.ok) return mapOf("err_msg" to res.message())
        val d = res.data
        return mapOf(
            "member_role" to d.optString("member_role"),
            "recv_msg_setting" to d.optString("recv_msg_setting"),
            "allow_proactive_msg" to d.optBoolean("allow_proactive_msg").toString(),
        )
    }

    /**
     * 图文混发：文字 + 若干图片一起发送。
     *
     * 发给 QQ 的仍是一条 md 消息：
     *  1. 每张图先按当前模式上传（MD 走图床拿 URL，普通走富媒体拿 file_info）
     *  2. 普通模式：先发一条富媒体消息（第一张图），再发文字——保证两端都能看到
     *  3. MD 模式：把所有图片作为 md 插图与文字拼成一条 markdown 发送
     */
    fun sendTextWithAttachments(
        chatKey: String,
        rawText: String,
        quoteMessageId: String? = null,
        atMembers: List<com.qqbot.client.bot.GroupMember> = emptyList(),
        atEveryone: Boolean = false,
        quoted: ChatMessage? = null,
        attachments: List<android.net.Uri>,
    ) {
        if (attachments.isEmpty()) {
            sendText(chatKey, rawText, quoteMessageId, atMembers, atEveryone, quoted)
            return
        }
        val account = _state.value.currentAccount ?: return
        val target = targetOf(chatKey)
        val isGroup = target is ChatTarget.Group
        val mdMode = _state.value.markdownMode
        val ctx = getApplication<Application>()

        viewModelScope.launch {
            try {
                if (mdMode) {
                    // ---- MD 模式：图片走图床，拼成一条 md ----
                    val urls = attachments.mapNotNull { uri ->
                        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
                        val bytes = ImageHost.compressForUpload(raw)
                        val size = ImageHost.decodeSize(bytes)
                        imageHost?.upload(target, bytes, "image_${System.nanoTime()}.jpg")?.let {
                            it.imageUrl to size
                        }
                    }
                    if (urls.isEmpty()) {
                        toast("图片上传失败")
                        return@launch
                    }
                    // 与 sendText 保持一致：文本里的 @全体成员 也视为艾特
                    val everyoneInText = rawText.contains("@${com.qqbot.client.bot.AtSyntax.EVERYONE_NAME}")
                    val ev = atEveryone || everyoneInText
                    var cleanText = rawText
                    atMembers.forEach { m -> cleanText = cleanText.replace("@${m.nickname}", "") }
                    cleanText = cleanText.replace("@${com.qqbot.client.bot.AtSyntax.EVERYONE_NAME}", "").trim()
                    val body = buildString {
                        atMembers.forEach { append(com.qqbot.client.bot.AtSyntax.user(it.openId)) }
                        if (ev) append(com.qqbot.client.bot.AtSyntax.everyone())
                        if (atMembers.isNotEmpty() || ev) append(' ')
                        urls.forEach { (u, s) ->
                            append(MarkdownBuilder.image(u, s?.first ?: 0, s?.second ?: 0))
                            append('\n')
                        }
                        if (cleanText.isNotBlank()) {
                            append(if (looksLikeMarkdown(cleanText)) cleanText else MarkdownBuilder.autoWrapMixed(cleanText))
                        }
                    }.trim()

                    val res = apiClient?.sendText(
                        target = target,
                        content = body,
                        msgType = QqBotConfig.MSG_TYPE_MARKDOWN,
                        referenceId = quoteMessageId,
                    )
                    if (res != null && res.ok) {
                        saveLocalMedia(account, chatKey, isGroup, body, urls.first().first, markdown = true)
                    } else {
                        toast("发送失败：${res?.message() ?: "未知错误"}")
                    }
                } else {
                    // ---- 普通模式：先发图片（富媒体），再发文字 ----
                    var first = true
                    var allOk = true
                    for (uri in attachments) {
                        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                        val res = apiClient?.sendMedia(
                            target = target,
                            bytes = raw,
                            fileType = QqBotConfig.MEDIA_IMAGE,
                            fileName = "image_${System.nanoTime()}.png",
                        )
                        if (res == null || !res.ok) {
                            allOk = false
                            toast("图片发送失败：${res?.message() ?: "未知错误"}")
                            break
                        }
                        if (first) {
                            saveLocalMedia(account, chatKey, isGroup, "", uri.toString(), markdown = false)
                            first = false
                        } else {
                            saveLocalMedia(account, chatKey, isGroup, "", uri.toString(), markdown = false)
                        }
                    }
                    if (allOk && rawText.isNotBlank()) {
                        sendText(chatKey, rawText, quoteMessageId, atMembers, atEveryone, quoted)
                    }
                }
            } catch (e: Exception) {
                toast("发送失败：${e.message}")
            }
        }
    }

    /** 把一张已有公网 URL 的图片分享到另一个群 */
    suspend fun shareImageTo(chatKey: String, imageUrl: String) {
        val account = _state.value.currentAccount ?: return
        val target = targetOf(chatKey)
        val isGroup = target is ChatTarget.Group
        val mdMode = _state.value.markdownMode

        withContext(Dispatchers.IO) {
            try {
                val res = if (mdMode) {
                    apiClient?.sendText(
                        target = target,
                        content = com.qqbot.client.bot.MarkdownBuilder.image(imageUrl),
                        msgType = QqBotConfig.MSG_TYPE_MARKDOWN,
                    )
                } else {
                    apiClient?.sendMediaByUrl(
                        target = target,
                        mediaUrl = imageUrl,
                        fileType = QqBotConfig.MEDIA_IMAGE,
                    )
                }
                withContext(Dispatchers.Main) {
                    if (res != null && res.ok) {
                        saveLocalMedia(
                            account = account,
                            chatKey = chatKey,
                            isGroup = isGroup,
                            text = "",
                            url = imageUrl,
                            markdown = mdMode,
                        )
                        toast("已分享")
                    } else {
                        toast("分享失败：${res?.message() ?: "未知错误"}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("分享失败：${e.message}") }
            }
        }
    }

    /** 当前图片缓存占用（字节），设置页展示用 */
    fun mediaCacheSize(): Long = mediaCache.sizeBytes()

    fun toast(msg: String) = _state.update { it.copy(toast = msg) }
    fun consumeToast() = _state.update { it.copy(toast = null) }

    override fun onCleared() {
        super.onCleared()
        gateway?.close()
    }

    companion object {
        /** 单文件上传上限：超过则提示用户换小文件，避免 OOM */
        const val MAX_UPLOAD_BYTES = 100L * 1024 * 1024
    }
}
