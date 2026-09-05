package com.example.chatapp.features.chat.presentation.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertHeaderItem
import com.example.chatapp.R
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
import java.util.UUID
import javax.inject.Inject
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

    init {
        getCurrentUser()
        observeLiveMessages()
        observeInternetConnectivity()
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
        if (text.isEmpty()) return
        _uiState.update { it.copy(inputText = "") }

        if (currentUser == null) {
            _uiState.update { it.copy(errorMessage = application.getString(R.string.error_user_not_loaded)) }
            return
        }
        val message = Message(
            id = UUID.randomUUID().toString(),
            sender = currentUser!!,
            content = MessageContent.text(text),
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
                    status == SendStatus.SENDING
                }
                .catch { upsertTail(message.copy(status = SendStatus.FAILED)) }
                .collect { status -> upsertTail(message.copy(status = status)) }
        }
    }
}
