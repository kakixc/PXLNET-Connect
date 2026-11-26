package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.PxlShapeLoadingIndicator
import io.nekohasekai.sfa.compose.component.pxlPressMotion
import io.nekohasekai.sfa.utils.PxlAuthClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PxlEmailLoginDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
    onSubscriptionHelp: (() -> Unit)? = null,
    focus: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<PxlAuthClient.EmailChallenge?>(null) }
    var code by remember { mutableStateOf(TextFieldValue("")) }
    var busy by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    val success = remember { Animatable(0f) }
    var request by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var sent by remember { mutableStateOf(false) }
    var resendAt by remember { mutableStateOf(0L) }
    var verifyAt by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var epochSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    val currentChallenge = challenge
    val resendSeconds = ((resendAt - now + 999) / 1000).coerceAtLeast(0)
    val verifySeconds = ((verifyAt - now + 999) / 1000).coerceAtLeast(0)
    val expired = currentChallenge != null && epochSeconds >= currentChallenge.expiresAt

    LaunchedEffect(verified) {
        if (verified) {
            success.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 480f))
            delay(300)
            onDismiss()
        }
    }

    LaunchedEffect(currentChallenge, resendAt) {
        while (currentChallenge != null || SystemClock.elapsedRealtime() < resendAt) {
            now = SystemClock.elapsedRealtime()
            epochSeconds = System.currentTimeMillis() / 1000
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }

    fun changeAddress() {
        request?.cancel()
        challenge = null
        code = TextFieldValue("")
        error = null
        sent = false
    }

    fun runRequest(verifying: Boolean = false, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        sent = false
        request = scope.launch {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: PxlAuthClient.ApiError) {
                error = when {
                    failure.status == 429 -> {
                        val seconds = failure.retryAfterSeconds?.coerceAtMost(Long.MAX_VALUE / 2000) ?: 60
                        val retryAt = SystemClock.elapsedRealtime() + seconds * 1000
                        if (verifying) verifyAt = retryAt else resendAt = maxOf(resendAt, retryAt)
                        R.string.pxl_email_rate_limited
                    }
                    failure.code == "invalid_email" -> R.string.pxl_email_invalid
                    failure.code == "invalid_or_expired_code" ->
                        if (System.currentTimeMillis() / 1000 >= (challenge?.expiresAt ?: Long.MAX_VALUE)) {
                            R.string.pxl_email_expired
                        } else R.string.pxl_email_wrong_code
                    failure.code == "email_delivery_unavailable" -> R.string.pxl_email_delivery_unavailable
                    else -> R.string.pxl_email_unavailable
                }
            } catch (_: Exception) {
                error = R.string.pxl_email_unavailable
            } finally {
                busy = false
            }
        }
    }

    fun send(address: String) {
        if (resendSeconds > 0) return
        runRequest {
            val result = viewModel.startEmailLogin(address)
            recipient = address
            challenge = result
            code = TextFieldValue("")
            sent = true
            resendAt = SystemClock.elapsedRealtime() + 60_000
        }
    }

    fun back() {
        if (verified) return
        if (challenge == null) onDismiss() else changeAddress()
    }

    Dialog(
        onDismissRequest = ::back,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (focus) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(if (focus) 24.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (focus) 12.dp else 6.dp),
                ) {
                    Text(
                        stringResource(if (currentChallenge == null) R.string.pxl_email_login else R.string.pxl_email_code),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (currentChallenge == null) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; error = null },
                            label = { Text(stringResource(R.string.pxl_email_address)) },
                            singleLine = true,
                            enabled = !busy,
                            isError = error == R.string.pxl_email_invalid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        val recipientText = if (sent) R.string.pxl_email_sent else R.string.pxl_email_recipient
                        Column {
                            Text(
                                stringResource(recipientText, recipient),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = ::changeAddress, enabled = !busy && !verified) {
                                Text(stringResource(R.string.pxl_email_change))
                            }
                        }
                        PxlOtpField(
                            code,
                            { code = it; error = null },
                            enabled = !busy && !expired && !verified,
                            verified = verified,
                            successProgress = success.value,
                        )
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                val candidate = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                                    ?.let(::clipboardCode)
                                if (candidate == null) {
                                    error = R.string.pxl_email_clipboard_empty
                                } else {
                                    code = TextFieldValue(candidate, TextRange(candidate.length))
                                    error = null
                                }
                            },
                            enabled = !busy && !expired && !verified,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(R.string.pxl_email_paste)) }
                    }

                    (error ?: R.string.pxl_email_expired.takeIf { expired && !verified })?.let {
                        Text(
                            stringResource(it),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val actionEnabled = !busy && !verified && if (currentChallenge == null) {
                        email.isNotBlank() && resendSeconds == 0L
                    } else code.text.length == 6 && !expired && verifySeconds == 0L
                    Button(
                        onClick = {
                            if (currentChallenge == null) send(email.trim())
                            else runRequest(verifying = true) {
                                viewModel.confirmEmailLogin(recipient, currentChallenge.id, code.text)
                                verified = true
                            }
                        },
                        enabled = actionEnabled,
                        modifier = Modifier.fillMaxWidth().pxlPressMotion(enabled = actionEnabled),
                    ) {
                        if (busy) PxlShapeLoadingIndicator(size = 20.dp)
                        else Text(
                            when {
                                currentChallenge == null -> stringResource(R.string.pxl_email_send)
                                verifySeconds > 0 -> stringResource(R.string.pxl_email_verify_wait, verifySeconds)
                                else -> stringResource(R.string.pxl_email_confirm)
                            },
                        )
                    }
                    if (currentChallenge != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { send(recipient) },
                                modifier = Modifier.weight(1f),
                                enabled = !busy && !verified && resendSeconds == 0L,
                            ) {
                                Text(if (resendSeconds == 0L) stringResource(R.string.pxl_email_resend)
                                    else stringResource(R.string.pxl_email_resend_wait, resendSeconds))
                            }
                            TextButton(onClick = onDismiss, enabled = !verified) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    } else {
                        if (resendSeconds > 0) Text(stringResource(R.string.pxl_email_resend_wait, resendSeconds))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (onSubscriptionHelp != null) {
                                TextButton(
                                    onClick = onSubscriptionHelp,
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.pxl_email_already_have_subscription))
                                }
                            }
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun clipboardCode(text: CharSequence): String? =
    Regex("(?<![0-9])[0-9]{6}(?![0-9])").findAll(text).singleOrNull()?.value

@Composable
private fun PxlOtpField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    verified: Boolean,
    successProgress: Float,
) {
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(R.string.pxl_email_code)
    BasicTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.text.filter { it in '0'..'9' }.take(6)
            val caret = input.text.take(input.selection.end).count { it in '0'..'9' }.coerceAtMost(digits.length)
            onChange(if (input.text == digits) input else TextFieldValue(digits, TextRange(caret)))
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { input ->
            val merge = successProgress.coerceIn(0f, 1f)
            val colors = MaterialTheme.colorScheme
            val pillColor = colors.primaryContainer.copy(alpha = merge)
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().background(pillColor, RoundedCornerShape(50)))
                Row(
                    modifier = Modifier.graphicsLayer { alpha = 1f - merge },
                    horizontalArrangement = Arrangement.spacedBy(6.dp * (1f - merge)),
                ) {
                    repeat(6) { index ->
                        val active = focused && enabled && index == value.selection.end.coerceAtMost(5)
                        val filled = index < value.text.length
                        val background by animateColorAsState(
                            if (active) colors.primaryContainer
                            else if (filled) colors.secondaryContainer else colors.surfaceContainerHigh,
                            label = "code cell color",
                        )
                        val radius by animateDpAsState(
                            if (active) 20.dp else 12.dp,
                            spring(dampingRatio = 0.8f, stiffness = 500f),
                            label = "code cell shape",
                        )
                        val shape = RoundedCornerShape(radius)
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .background(background, shape)
                                .border(1.dp, if (active) colors.primary else colors.outlineVariant, shape)
                                .clearAndSetSemantics {},
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = value.text.getOrNull(index),
                                modifier = Modifier.clipToBounds(),
                                transitionSpec = {
                                    if (targetState == null) fadeIn() togetherWith fadeOut()
                                    else (slideInVertically(
                                        animationSpec = spring(dampingRatio = 0.56f, stiffness = 550f),
                                        initialOffsetY = { -it },
                                    ) + fadeIn() + scaleIn(initialScale = 0.75f)) togetherWith fadeOut()
                                },
                                label = "OTP digit",
                            ) { digit ->
                                Text(
                                    digit?.toString() ?: " ",
                                    color = if (active) colors.onPrimaryContainer else colors.onSecondaryContainer,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // One real field owns focus, selection, IME and paste for all six visual cells.
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) { input() }
                AnimatedVisibility(
                    visible = verified && merge > 0.55f,
                    enter = scaleIn(initialScale = 0.78f) + fadeIn(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Check, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.pxl_email_verified_code),
                            color = colors.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        },
    )
}
