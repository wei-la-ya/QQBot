package com.qqbot.client.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqbot.client.R
import com.qqbot.client.bot.QqBotConfig

/**
 * 添加机器人（登录）页。
 * 输入 QQ 开放平台的 AppID 与 AppSecret，获取 access_token 后进入主界面。
 */
@Composable
fun AddBotScreen(
    busy: Boolean,
    errorText: String?,
    onBack: () -> Unit,
    onSubmit: (appId: String, secret: String, version: QqBotConfig.ApiVersion) -> Unit,
) {
    var appId by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(QqBotConfig.ApiVersion.NEW) }
    var showSecret by remember { mutableStateOf(false) }

    val canSubmit = appId.isNotBlank() && secret.isNotBlank() && !busy

    Column(Modifier.fillMaxSize().background(QQ.TitleBarBg)) {
        QqTitleBar(title = "添加机器人", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))

            // 头部说明
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .background(QQ.Blue, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("QQ", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text("登录 QQBot", fontSize = 18.sp, color = QQ.TextPrimary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "填入 QQ 开放平台的 AppID 与 AppSecret",
                    fontSize = 13.sp,
                    color = QQ.TextSecondary,
                )
            }

            Spacer(Modifier.height(24.dp))

            // 表单
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
            ) {
                QQTextField(
                    value = appId,
                    onValueChange = { appId = it.trim() },
                    label = "AppID",
                    hint = "机器人 appId，例如 102056888",
                    keyboardType = KeyboardType.Number,
                )
                Divider(color = QQ.Divider, modifier = Modifier.padding(start = 16.dp))
                QQTextField(
                    value = secret,
                    onValueChange = { secret = it.trim() },
                    label = "AppSecret",
                    hint = "机器人密钥",
                    password = !showSecret,
                    trailing = {
                        Text(
                            text = if (showSecret) "隐藏" else "显示",
                            color = QQ.Blue,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { showSecret = !showSecret }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            // API 版本
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(Color.White, RoundedCornerShape(10.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("接口版本", fontSize = 15.sp, color = QQ.TextPrimary)
                    Spacer(Modifier.weight(1f))
                    QqBotConfig.ApiVersion.entries.forEach { v ->
                        val selected = v == version
                        Text(
                            text = if (v == QqBotConfig.ApiVersion.NEW) "新版" else "旧版",
                            fontSize = 13.sp,
                            color = if (selected) Color.White else QQ.TextSecondary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(
                                    if (selected) QQ.Blue else Color(0xFFF2F3F5),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { version = v }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (errorText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorText,
                    color = Color(0xFFFF3B30),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            Spacer(Modifier.height(28.dp))

            // 登录按钮
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(46.dp)
                    .background(
                        if (canSubmit) QQ.Blue else Color(0xFFC8CDD4),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable(enabled = canSubmit) { onSubmit(appId, secret, version) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在获取 token…", color = Color.White, fontSize = 16.sp)
                    }
                } else {
                    Text("登 录", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "登录后可与机器人所在的群聊收发消息。\nAppID/AppSecret 仅保存在本机。",
                fontSize = 12.sp,
                color = QQ.TextHint,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun QQTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 15.sp, color = QQ.TextPrimary, modifier = Modifier.width(88.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                color = QQ.TextPrimary,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(QQ.Blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 15.sp, color = QQ.TextHint)
                    }
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}
