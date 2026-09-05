package com.example.chatapp.features.chat.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.example.chatapp.R
import com.example.chatapp.core.Constants.DateTime.DATE_TIME_FORMAT
import com.example.chatapp.core.Constants.DateTime.DATE_TIME_YEAR_FORMAT
import com.example.chatapp.core.Constants.DateTime.TIME_FORMAT
import com.example.chatapp.features.chat.domain.entity.MediaType
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.SendStatus
import com.example.chatapp.features.chat.presentation.viewModel.ChatIntent
import com.example.chatapp.features.chat.presentation.viewModel.ChatState
import com.example.chatapp.features.chat.presentation.viewModel.ChatViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ChatRoute(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.handleIntent(ChatIntent.RefreshMessages)
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.handleIntent(ChatIntent.ClearError)
        }
    }

    ChatScreen(
        uiState = uiState,
        messages = messages,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::handleIntent
    )
}

@Composable
private fun ChatScreen(
    uiState: ChatState,
    messages: LazyPagingItems<Message>,
    snackbarHostState: SnackbarHostState,
    onIntent: (ChatIntent) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = messages.itemCount,
                        key = messages.itemKey { it.id }
                    ) { index ->
                        messages[index]?.let { message ->
                            MessageRow(
                                message = message,
                                isOwn = message.sender.deviceId == uiState.currentDeviceId,
                                onRetry = { onIntent(ChatIntent.Retry(message)) },
                                onCancel = { onIntent(ChatIntent.Cancel(message)) }
                            )
                        }
                    }

                    if (messages.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                when (messages.loadState.refresh) {
                    is LoadState.Loading if messages.itemCount == 0 ->
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    is LoadState.Error if messages.itemCount == 0 ->
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.chat_load_error))
                            Text(
                                text = stringResource(R.string.chat_retry),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { messages.retry() }
                            )
                        }

                    is LoadState.NotLoading if messages.itemCount == 0 ->
                        Text(
                            text = stringResource(R.string.chat_empty),
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                    else -> Unit
                }
            }

            MessageInputBar(
                text = uiState.inputText,
                onTextChange = { onIntent(ChatIntent.InputChanged(it)) },
                onSend = { onIntent(ChatIntent.Send) }
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: Message,
    isOwn: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            MessageAvatar(
                imageUrl = message.sender.profileImageUrl,
                contentDescription = message.sender.username
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            ),
            color = if (isOwn) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(
                    when (message.status) {
                        SendStatus.FAILED -> Modifier.clickable(onClick = onRetry)
                        SendStatus.SENDING -> Modifier.clickable(onClick = onCancel)
                        else -> Modifier
                    }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.sender.username.isNotBlank()) {
                    Text(
                        text = message.sender.username,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                MessageContentView(message)

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isOwn) {
                        StatusIndicator(message.status)
                    }
                }
            }
        }
        if (isOwn) {
            Spacer(modifier = Modifier.width(8.dp))
            MessageAvatar(
                imageUrl = message.sender.profileImageUrl,
                contentDescription = message.sender.username
            )
        }
    }
}

@Composable
private fun MessageAvatar(imageUrl: String?, contentDescription: String?) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun MessageContentView(message: Message) {
    when (message.content.type) {
        MediaType.TEXT -> Text(
            text = message.content.text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium
        )

        MediaType.IMAGE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            message.content.mediaUrls.firstOrNull()?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.chat_image_message),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(200.dp)
                )
            }
            message.content.text?.let { caption ->
                Text(text = caption, style = MaterialTheme.typography.bodyMedium)
            }
        }

        MediaType.AUDIO -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.chat_audio_message)
            )
            Text(
                text = stringResource(R.string.chat_audio_message),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: SendStatus) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            SendStatus.SENDING -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp
            )

            SendStatus.SENT -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.chat_status_sent),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SendStatus.FAILED -> Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.chat_failed_tap_retry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatTime(instant: Instant): String {
    val timeZone = TimeZone.currentSystemDefault()
    val dateTime = instant.toLocalDateTime(timeZone)
    val today = Clock.System.now().toLocalDateTime(timeZone).date
    val time = TIME_FORMAT.format(dateTime.hour, dateTime.minute)
    return when {
        dateTime.date == today -> time
        dateTime.year == today.year ->
            DATE_TIME_FORMAT.format(dateTime.dayOfMonth, dateTime.monthNumber, time)

        else ->
            DATE_TIME_YEAR_FORMAT.format(dateTime.dayOfMonth, dateTime.monthNumber, dateTime.year, time)
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(R.string.chat_message_hint)) },
            maxLines = 4,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSend, enabled = text.isNotBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_send)
            )
        }
    }
}
