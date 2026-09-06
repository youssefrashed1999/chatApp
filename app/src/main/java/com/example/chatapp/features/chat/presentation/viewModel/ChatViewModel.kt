package com.example.chatapp.features.chat.presentation.viewModel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertHeaderItem
import com.example.chatapp.R
import com.example.chatapp.core.Constants
import com.example.chatapp.core.error.toUserMessage
import com.example.chatapp.core.network.ConnectivityObserver
import com.example.chatapp.core.session.CurrentUserProvider
import com.example.chatapp.features.chat.domain.entity.Message
import com.example.chatapp.features.chat.domain.entity.MessageContent
import com.example.chatapp.features.chat.domain.entity.SendStatus
import com.example.chatapp.features.chat.domain.usecase.CancelOngoingMessageUseCase
import com.example.chatapp.features.chat.domain.usecase.ObserveLiveMessagesUseCase
import com.example.chatapp.features.chat.domain.usecase.ObserveMessageStatusUseCase
import com.example.chatapp.features.chat.domain.usecase.ObserveMessagesUseCase
import com.example.chatapp.features.chat.domain.usecase.RefreshMessagesUseCase
import com.example.chatapp.features.chat.domain.usecase.SendMessageUseCase
import com.example.chatapp.features.users.domain.entity.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import androidx.core.net.toUri

private const val MIN_RECORDING_MS = 500L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    observeMessagesUseCase: ObserveMessagesUseCase,
    private val currentUserProvider: CurrentUserProvider,
    private val connectivityObserver: ConnectivityObserver,
    private val refreshMessagesUseCase: RefreshMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val cancelOngoingMessageUseCase: CancelOngoingMessageUseCase,
    private val observeMessageStatusUseCase: ObserveMessageStatusUseCase,
    private val observeLiveMessagesUseCase: ObserveLiveMessagesUseCase,
    application: Application,
) : AndroidViewModel(application) {

    private val _tail = MutableStateFlow<List<Message>>(emptyList())

    val messages: Flow<PagingData<Message>> =
        observeMessagesUseCase().cachedIn(viewModelScope)
            .combine(_tail) { pagingData, tail ->
                val filtered = pagingData.filter { message ->
                    updateNewestMessageCreatedAt(message)
                    tail.none { it.id == message.id }
                }
                tail.fold(filtered) { acc, message -> acc.insertHeaderItem(item = message) }
            }
            .cachedIn(viewModelScope)

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private var currentUser: UserProfile? = null

    private var newestMessageCreatedAt: Instant? = null

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartAt: Long = 0L
    private val preparingMessageIds = mutableSetOf<String>()

    private val _audioPlayers = MutableStateFlow<Map<String, MediaPlayer>>(emptyMap())
    val audioPlayers: StateFlow<Map<String, MediaPlayer>> = _audioPlayers.asStateFlow()

    init {
        getCurrentUser()
        observeLiveMessages()
        observeInternetConnectivity()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        releaseRecorder()
        recordingFile?.delete()
        recordingFile = null
        _audioPlayers.value.values.forEach {
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun observeInternetConnectivity() = viewModelScope.launch {
        connectivityObserver.isOnline
            .drop(1)
            .filter { it }
            .collect { onReconnected() }
    }

    private fun getCurrentUser() = viewModelScope.launch {
        runCatching { currentUserProvider.requireCurrentUser() }
            .onSuccess { user ->
                currentUser = user
                _uiState.update { it.copy(currentDeviceId = user.deviceId) }
            }
    }

    private var liveMessagesJob: Job? = null
    private var firstResume = true

    private fun observeLiveMessages() {
        liveMessagesJob?.cancel()
        liveMessagesJob = viewModelScope.launch {
            observeLiveMessagesUseCase().catch { error ->
                _uiState.update { it.copy(errorMessage = error.toUserMessage(application)) }
            }.collect { upsertTail(it) }
        }
    }

    private fun onForegrounded() {
        if (firstResume) {
            firstResume = false
            return
        }
        onReconnected()
    }

    private fun onReconnected() = newestMessageCreatedAt?.let {
        viewModelScope.launch {
            val newMessages = refreshMessagesUseCase.invoke(it)
            newMessages.forEach { newMessage -> upsertTail(newMessage) }
        }
    }


    fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.InputChanged ->
                _uiState.update { it.copy(inputText = intent.text, errorMessage = null) }

            ChatIntent.Send -> send()
            is ChatIntent.ImagesPicked -> _uiState.update {
                it.copy(
                    pendingImageUris = (it.pendingImageUris + intent.uris)
                        .distinct()
                        .take(Constants.MAX_IMAGES_PER_MESSAGE)
                )
            }

            is ChatIntent.RemovePendingImage -> _uiState.update {
                it.copy(pendingImageUris = it.pendingImageUris - intent.uri)
            }

            ChatIntent.StartRecording -> startRecording()
            ChatIntent.StopRecording -> stopRecording()
            is ChatIntent.ToggleAudioPlayback -> toggleAudioPlayback(intent.message)
            is ChatIntent.Retry -> retry(intent.message)
            is ChatIntent.Cancel -> cancel(intent.message)
            ChatIntent.ClearError -> _uiState.update { it.copy(errorMessage = null) }
            ChatIntent.RefreshMessages -> onForegrounded()
        }
    }

    private fun upsertTail(message: Message) {
        updateNewestMessageCreatedAt(message)
        _tail.update { list ->
            val index = list.indexOfFirst { it.id == message.id }
            (if (index >= 0) list.toMutableList().apply { set(index, message) } else list + message)
                .sortedBy { it.createdAt }
        }
    }

    private fun updateNewestMessageCreatedAt(message: Message) {
        if (message.status == SendStatus.SENT && (newestMessageCreatedAt == null || message.createdAt > newestMessageCreatedAt!!)) {
            newestMessageCreatedAt = message.createdAt
        }
    }

    private fun send() {
        val text = _uiState.value.inputText.trim()
        val images = _uiState.value.pendingImageUris
        if (text.isEmpty() && images.isEmpty()) return

        if (currentUser == null) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.error_user_not_loaded)) }
            return
        }
        _uiState.update { it.copy(inputText = "", pendingImageUris = emptyList()) }
        val content = if (images.isNotEmpty()) {
            MessageContent.images(images.map { it.toString() }, text.ifBlank { null })
        } else {
            MessageContent.text(text)
        }
        val message = Message(
            id = UUID.randomUUID().toString(),
            sender = currentUser!!,
            content = content,
            status = SendStatus.SENDING,
            createdAt = Clock.System.now()
        )
        sendMessage(message)
    }

    private fun retry(message: Message) {
        val retrying = message.copy(status = SendStatus.SENDING)
        sendMessage(retrying)
    }

    private fun sendMessage(message: Message) {
        upsertTail(message)
        sendMessageUseCase(message)
        collectStatus(message)
    }

    private fun cancel(message: Message) {
        cancelOngoingMessageUseCase(message.id)
        upsertTail(message.copy(status = SendStatus.FAILED))
    }

    private fun collectStatus(message: Message) {
        viewModelScope.launch {
            observeMessageStatusUseCase(message.id)
                .transformWhile { status ->
                    emit(status)
                    status != SendStatus.SENT
                }
                .catch { upsertTail(message.copy(status = SendStatus.FAILED)) }
                .collect { status ->
                    val current = _tail.value.find { it.id == message.id } ?: message
                    upsertTail(current.copy(status = status))
                }
        }
    }

    private fun startRecording() {
        if (mediaRecorder != null) return
        if (ContextCompat.checkSelfPermission(application, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.audio_permission_denied)) }
            return
        }
        pauseAllAudioPlayers()
        _uiState.update { it.copy(isRecording = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(application.cacheDir, "audio_messages").apply { mkdirs() }
                val file = File(dir, "audio_${UUID.randomUUID()}.m4a")
                recordingFile = file
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(application) else MediaRecorder()
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(file.absolutePath)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                recordingStartAt = SystemClock.elapsedRealtime()
            } catch (_: Exception) {
                recordingFile?.delete()
                recordingFile = null
                recordingStartAt = 0L
                releaseRecorder()
                _uiState.update { it.copy(isRecording = false, errorMessage = application.getString(R.string.audio_record_error)) }
            }
        }
    }

    private fun stopRecording() {
        if (mediaRecorder == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val recorder = mediaRecorder ?: return@launch
            try {
                recorder.stop()
            } catch (_: Exception) {
            } finally {
                releaseRecorder()
            }
            val file = recordingFile ?: return@launch
            val duration = SystemClock.elapsedRealtime() - recordingStartAt
            if (duration < MIN_RECORDING_MS) {
                file.delete()
                _uiState.update { it.copy(isRecording = false) }
                recordingFile = null
                recordingStartAt = 0L
                return@launch
            }
            val user = currentUser
            if (user == null) {
                file.delete()
                _uiState.update { it.copy(isRecording = false, errorMessage = application.getString(R.string.error_user_not_loaded)) }
                recordingFile = null
                recordingStartAt = 0L
                return@launch
            }
            try {
                val uri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
                _uiState.update { it.copy(isRecording = false) }
                sendAudio(uri, user)
            } catch (_: Exception) {
                file.delete()
                _uiState.update { it.copy(isRecording = false, errorMessage = application.getString(R.string.audio_record_error)) }
                recordingFile = null
                recordingStartAt = 0L
            }
        }
    }

    private fun sendAudio(uri: Uri, sender: UserProfile) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            sender = sender,
            content = MessageContent.audio(uri.toString()),
            status = SendStatus.SENDING,
            createdAt = Clock.System.now()
        )
        sendMessage(message)
        recordingFile = null
        recordingStartAt = 0L
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    private fun toggleAudioPlayback(message: Message) {
        if (message.id in preparingMessageIds) return
        val player = _audioPlayers.value[message.id]
        if (player == null) {
            prepareAudioPlayer(message)
            return
        }
        try {
            if (player.isPlaying) {
                player.pause()
            } else {
                pauseAllAudioPlayers()
                player.start()
            }
        } catch (_: Exception) {
            releasePlayer(message.id)
        }
    }

    private fun pauseAllAudioPlayers() {
        _audioPlayers.value.values.forEach { player ->
            try {
                if (player.isPlaying) player.pause()
            } catch (_: Exception) {
                // invalid player will be cleaned up when its message is played again
            }
        }
    }

    private fun prepareAudioPlayer(message: Message) {
        val url = message.content.mediaUrls.firstOrNull() ?: return
        preparingMessageIds.add(message.id)
        val player = MediaPlayer()
        player.setOnPreparedListener {
            preparingMessageIds.remove(message.id)
            _audioPlayers.update { it + (message.id to player) }
            pauseAllAudioPlayers()
            player.start()
        }
        player.setOnCompletionListener {
            player.seekTo(0)
        }
        player.setOnErrorListener { _, _, _ ->
            preparingMessageIds.remove(message.id)
            releasePlayer(message.id)
            _uiState.update { it.copy(errorMessage = application.getString(R.string.audio_play_error)) }
            true
        }
        try {
            player.setDataSource(application, url.toUri())
            player.prepareAsync()
        } catch (_: Exception) {
            preparingMessageIds.remove(message.id)
            releasePlayer(message.id)
            _uiState.update { it.copy(errorMessage = application.getString(R.string.audio_play_error)) }
        }
    }

    private fun releasePlayer(id: String) {
        val player = _audioPlayers.value[id]
        _audioPlayers.update { it - id }
        try {
            player?.release()
        } catch (_: Exception) {
        }
    }
}
