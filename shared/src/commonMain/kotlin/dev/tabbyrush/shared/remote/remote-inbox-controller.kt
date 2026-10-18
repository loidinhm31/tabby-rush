package dev.tabbyrush.shared.remote

import dev.tabbyrush.shared.auth.HubAuthSession
import dev.tabbyrush.shared.auth.HubHttpClient
import dev.tabbyrush.shared.contract.AccountIdentity
import dev.tabbyrush.shared.contract.ApiResult
import dev.tabbyrush.shared.contract.RemoteInboxAction
import dev.tabbyrush.shared.contract.RemoteInboxAvailability
import dev.tabbyrush.shared.contract.RemoteInboxController
import dev.tabbyrush.shared.contract.RemoteInboxState
import dev.tabbyrush.shared.contract.RemoteNotificationAdapter
import dev.tabbyrush.shared.contract.RemoteQuiesceResult
import dev.tabbyrush.shared.contract.SafeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

class DefaultRemoteInboxController(
    val client: RemoteInboxClient,
    val streamClient: RemoteStreamClient,
    val coroutineScope: CoroutineScope,
    val adapter: RemoteNotificationAdapter? = null,
    val clock: MonotonicClock = SystemMonotonicClock(),
    val isDesktop: Boolean = true
) : RemoteInboxController {

    constructor(
        httpClient: HubHttpClient,
        authSession: HubAuthSession,
        coroutineScope: CoroutineScope,
        adapter: RemoteNotificationAdapter? = null,
        clock: MonotonicClock = SystemMonotonicClock(),
        isDesktop: Boolean = true
    ) : this(
        client = RemoteInboxClient(httpClient, authSession),
        streamClient = RemoteStreamClient(httpClient, authSession),
        coroutineScope = coroutineScope,
        adapter = adapter,
        clock = clock,
        isDesktop = isDesktop
    )

    private val _state = MutableStateFlow(RemoteInboxState())
    override val state: StateFlow<RemoteInboxState> = _state.asStateFlow()

    private val cache = RemoteInboxCache(clock)
    private val mutex = Mutex()

    private var currentGeneration: Long = 0L
    private var currentAccount: AccountIdentity? = null

    private var streamJob: Job? = null
    private var periodicRefreshJob: Job? = null
    private val activeJobs = mutableListOf<Job>()

    private val connectionSeenIds = mutableSetOf<String>()
    private var currentStreamEpoch: String? = null
    private var baselineAcceptanceOrdinal: Long = 0L
    private var baselineComplete = false
    private val baselineBuffer = mutableListOf<SseAnnouncementData>()
    private var reconnectTrigger: CompletableDeferred<Unit>? = null

    override suspend fun start(account: AccountIdentity, generation: Long) = mutex.withLock {
        currentGeneration = generation
        currentAccount = account

        adapter?.setActivationCallback { messageId, gen ->
            onNativeOpen(messageId, gen)
        }

        _state.value = RemoteInboxState(
            availability = RemoteInboxAvailability.Loading,
            loading = true
        )

        cancelBackgroundJobs()

        periodicRefreshJob = coroutineScope.launch {
            runPeriodicRefreshLoop(generation)
        }
        trackJob(periodicRefreshJob)

        streamJob = coroutineScope.launch {
            runStreamLoop(generation)
        }
        trackJob(streamJob)
    }

    private fun trackJob(job: Job?) {
        if (job == null) return
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    private fun cancelBackgroundJobs() {
        streamJob?.cancel()
        streamJob = null
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }

    private suspend fun runPeriodicRefreshLoop(capturedGeneration: Long) {
        while (true) {
            delay(60_000L)
            if (capturedGeneration != currentGeneration) break
            refreshInboxAndUnread(capturedGeneration, silent = true)
        }
    }

    private suspend fun runStreamLoop(capturedGeneration: Long) {
        var backoffMs = 1_000L
        while (capturedGeneration == currentGeneration) {
            val trigger = CompletableDeferred<Unit>()
            reconnectTrigger = trigger
            baselineComplete = false
            baselineBuffer.clear()
            connectionSeenIds.clear()

            var streamResult: ApiResult<Unit>? = null
            var isSilentReconnect = false

            kotlinx.coroutines.coroutineScope {
                val hydrationJob = launch {
                    performBaselineHydration(capturedGeneration)
                }
                trackJob(hydrationJob)

                val streamConnJob = launch {
                    streamResult = streamClient.openAndConsumeStream(
                        onRevoked = {
                            handleRevoked(capturedGeneration)
                        },
                        onEvent = { event ->
                            handleStreamEvent(event, capturedGeneration)
                        }
                    )
                }

                val watcherJob = launch {
                    try {
                        trigger.await()
                        isSilentReconnect = true
                        hydrationJob.cancel()
                        streamConnJob.cancel()
                    } catch (_: CancellationException) {
                        // Watcher cancelled on scope teardown or normal stream completion
                    }
                }

                streamConnJob.join()
                watcherJob.cancel()
                hydrationJob.cancel()
            }

            if (capturedGeneration != currentGeneration) break

            val silentReconnectTriggered = isSilentReconnect || (trigger.isCompleted && !trigger.isCancelled)
            if (silentReconnectTriggered) {
                // Silent reconnect triggered (hint buffer overflow, dedup cap, or lagged reconcile)
                // Immediately establish a new connection and reset baseline without backoff delay
                backoffMs = 1_000L
                continue
            }

            when (val res = streamResult) {
                is ApiResult.Failure -> {
                    if (res.error.code == "appAccessRevoked") {
                        handleRevoked(capturedGeneration)
                        break
                    }
                    _state.update { it.copy(availability = RemoteInboxAvailability.Offline, loading = false) }
                }
                is ApiResult.Success -> {
                    backoffMs = 1_000L
                }
                null -> {
                    // Stream connection ended or was cancelled externally
                }
            }

            val jitter = Random.nextLong(0, 1_000L)
            delay(backoffMs + jitter)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun performBaselineHydration(capturedGeneration: Long) {
        val inboxResult = client.fetchInbox(limit = 25, cursor = null)
        val unreadResult = client.fetchUnread()
        mutex.withLock {
            if (capturedGeneration != currentGeneration) return

            if (inboxResult is ApiResult.Failure) {
                if (inboxResult.error.code == "appAccessRevoked") {
                    handleRevokedLocked(capturedGeneration)
                    return
                }
                _state.update {
                    it.copy(
                        availability = RemoteInboxAvailability.Offline,
                        loading = false,
                        error = inboxResult.error.message
                    )
                }
                return
            }

            if (inboxResult is ApiResult.Success) {
                cache.replaceFirstPage(inboxResult.value)
            }

            val unreadCount = if (unreadResult is ApiResult.Success) {
                unreadResult.value.unreadCount
            } else {
                _state.value.unreadCount
            }

            _state.update {
                it.copy(
                    availability = RemoteInboxAvailability.Online,
                    items = cache.getItems(),
                    unreadCount = unreadCount,
                    nextCursor = cache.nextCursor,
                    loading = false,
                    error = null
                )
            }

            baselineComplete = true

            // Drain buffered hints
            val buffered = baselineBuffer.toList()
            baselineBuffer.clear()
            for (hint in buffered) {
                processAnnouncementHintLocked(hint, capturedGeneration)
            }
        }
    }

    private suspend fun handleStreamEvent(event: RemoteStreamEvent, capturedGeneration: Long) {
        if (capturedGeneration != currentGeneration) return

        when (event) {
            is RemoteStreamEvent.Ready -> {
                mutex.withLock {
                    if (capturedGeneration != currentGeneration) return
                    currentStreamEpoch = event.data.streamEpoch
                    baselineAcceptanceOrdinal = event.data.baselineAcceptanceOrdinal.toLongOrNull() ?: 0L
                    cache.updateServerNow(event.data.serverNow)
                }
            }

            is RemoteStreamEvent.Announcement -> {
                mutex.withLock {
                    if (capturedGeneration != currentGeneration) return
                    if (!baselineComplete) {
                        if (baselineBuffer.size >= 256) {
                            // Buffer overflow -> drop buffer and silent reconnect
                            baselineBuffer.clear()
                            reconnectTrigger?.complete(Unit)
                            return
                        }
                        baselineBuffer.add(event.data)
                    } else {
                        processAnnouncementHintLocked(event.data, capturedGeneration)
                    }
                }
            }

            is RemoteStreamEvent.ReadState -> {
                mutex.withLock {
                    if (capturedGeneration != currentGeneration) return
                    // Monotonic read state merge
                    val nowIso = cache.currentServerTimeMs()?.let {
                        kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString()
                    } ?: ""
                    cache.markItemRead(event.data.messageId, nowIso)
                    _state.update { it.copy(items = cache.getItems()) }
                }
                // Refresh unread count
                val unreadRes = client.fetchUnread()
                if (unreadRes is ApiResult.Success && capturedGeneration == currentGeneration) {
                    _state.update { it.copy(unreadCount = unreadRes.value.unreadCount) }
                }
            }

            is RemoteStreamEvent.Reconcile -> {
                if (event.data.reason == "lagged") {
                    // Silent reconnect
                    reconnectTrigger?.complete(Unit)
                } else {
                    refreshInboxAndUnread(capturedGeneration, silent = true)
                }
            }

            is RemoteStreamEvent.Keepalive -> {
                // Heartbeat keeps connection alive
            }
        }
    }

    private suspend fun processAnnouncementHintLocked(
        data: SseAnnouncementData,
        capturedGeneration: Long
    ) {
        if (connectionSeenIds.size >= 2048) {
            // Dedup cap 2048 reached -> trigger silent reconnect
            reconnectTrigger?.complete(Unit)
            return
        }
        val isFirstSeen = connectionSeenIds.add(data.messageId)
        val ord = data.acceptanceOrdinal.toLongOrNull() ?: 0L
        val isDesktopAlertEligible = isDesktop &&
            data.liveEligible &&
            data.acceptanceEpoch == currentStreamEpoch &&
            ord > baselineAcceptanceOrdinal &&
            isFirstSeen

        if (isDesktopAlertEligible) {
            // Check active detail from server before posting OS toast
            coroutineScope.launch {
                val detailRes = client.fetchDetail(data.messageId)
                if (detailRes is ApiResult.Success && capturedGeneration == currentGeneration) {
                    val item = detailRes.value
                    if (!cache.isExpired(item.expiresAt)) {
                        adapter?.show(data.messageId, capturedGeneration)
                    }
                }
            }
        }

        // Silent refresh of inbox and unread
        coroutineScope.launch {
            refreshInboxAndUnread(capturedGeneration, silent = true)
        }
    }

    private suspend fun refreshInboxAndUnread(capturedGeneration: Long, silent: Boolean = false) {
        if (capturedGeneration != currentGeneration) return
        val inboxResult = client.fetchInbox(limit = 25, cursor = null)
        val unreadResult = client.fetchUnread()

        mutex.withLock {
            if (capturedGeneration != currentGeneration) return

            if (inboxResult is ApiResult.Success) {
                cache.replaceFirstPage(inboxResult.value)
                val unreadCount = if (unreadResult is ApiResult.Success) {
                    unreadResult.value.unreadCount
                } else {
                    _state.value.unreadCount
                }
                _state.update {
                    it.copy(
                        availability = RemoteInboxAvailability.Online,
                        items = cache.getItems(),
                        unreadCount = unreadCount,
                        nextCursor = cache.nextCursor,
                        loading = false,
                        error = null
                    )
                }
            } else if (inboxResult is ApiResult.Failure) {
                if (inboxResult.error.code == "appAccessRevoked") {
                    handleRevokedLocked(capturedGeneration)
                } else if (!silent) {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = inboxResult.error.message
                        )
                    }
                }
            }
        }
    }

    private fun handleRevoked(capturedGeneration: Long) {
        coroutineScope.launch {
            mutex.withLock {
                handleRevokedLocked(capturedGeneration)
            }
        }
    }

    private fun handleRevokedLocked(capturedGeneration: Long) {
        if (capturedGeneration != currentGeneration) return
        cache.clear()
        connectionSeenIds.clear()
        cancelBackgroundJobs()
        _state.value = RemoteInboxState(
            availability = RemoteInboxAvailability.Revoked,
            loading = false,
            error = "Application access revoked"
        )
    }

    override fun dispatch(action: RemoteInboxAction) {
        val capturedGen = currentGeneration
        coroutineScope.launch {
            when (action) {
                is RemoteInboxAction.Refresh -> {
                    _state.update { it.copy(loading = true, error = null) }
                    refreshInboxAndUnread(capturedGen, silent = false)
                }

                is RemoteInboxAction.LoadNext -> {
                    val cursor = _state.value.nextCursor
                    if (cursor != null && !_state.value.loading) {
                        _state.update { it.copy(loading = true) }
                        val nextRes = client.fetchInbox(limit = 25, cursor = cursor)
                        mutex.withLock {
                            if (capturedGen != currentGeneration) return@withLock
                            if (nextRes is ApiResult.Success) {
                                cache.appendPage(nextRes.value)
                                _state.update {
                                    it.copy(
                                        items = cache.getItems(),
                                        nextCursor = cache.nextCursor,
                                        loading = false
                                    )
                                }
                            } else if (nextRes is ApiResult.Failure) {
                                _state.update {
                                    it.copy(
                                        loading = false,
                                        error = nextRes.error.message
                                    )
                                }
                            }
                        }
                    }
                }

                is RemoteInboxAction.Open -> {
                    if (_state.value.availability == RemoteInboxAvailability.Offline) {
                        _state.update { it.copy(error = "Unavailable offline. Connect to network to view details.") }
                        return@launch
                    }
                    _state.update { it.copy(loading = true, error = null) }
                    val detailRes = client.fetchDetail(action.messageId)
                    mutex.withLock {
                        if (capturedGen != currentGeneration) return@withLock
                        if (detailRes is ApiResult.Success) {
                            val detail = detailRes.value
                            cache.upsertItem(detail)
                            _state.update {
                                it.copy(
                                    detail = detail,
                                    items = cache.getItems(),
                                    loading = false
                                )
                            }
                            // PUT read
                            coroutineScope.launch {
                                val readRes = client.markRead(action.messageId)
                                mutex.withLock {
                                    if (capturedGen != currentGeneration) return@withLock
                                    if (readRes is ApiResult.Success) {
                                        val updatedItem = cache.markItemRead(action.messageId, readRes.value.readAt)
                                        _state.update {
                                            it.copy(
                                                items = cache.getItems(),
                                                unreadCount = readRes.value.unreadCount,
                                                detail = if (it.detail?.id == action.messageId) updatedItem ?: it.detail else it.detail
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (detailRes is ApiResult.Failure) {
                            if (detailRes.error.code == "appAccessRevoked") {
                                handleRevokedLocked(capturedGen)
                            } else {
                                _state.update {
                                    it.copy(
                                        loading = false,
                                        error = detailRes.error.message
                                    )
                                }
                            }
                        }
                    }
                }

                is RemoteInboxAction.MarkRead -> {
                    val readRes = client.markRead(action.messageId)
                    mutex.withLock {
                        if (capturedGen != currentGeneration) return@withLock
                        if (readRes is ApiResult.Success) {
                            val updatedItem = cache.markItemRead(action.messageId, readRes.value.readAt)
                            _state.update {
                                it.copy(
                                    items = cache.getItems(),
                                    unreadCount = readRes.value.unreadCount,
                                    detail = if (it.detail?.id == action.messageId) updatedItem ?: it.detail else it.detail
                                )
                            }
                        }
                    }
                }

                is RemoteInboxAction.Back -> {
                    _state.update { it.copy(detail = null) }
                }

                is RemoteInboxAction.DismissError -> {
                    _state.update { it.copy(error = null) }
                }
            }
        }
    }

    override fun onForeground() {
        val capturedGen = currentGeneration
        coroutineScope.launch {
            mutex.withLock {
                if (capturedGen != currentGeneration) return@withLock
                // Filter expired items
                _state.update { it.copy(items = cache.getItems()) }
            }
            refreshInboxAndUnread(capturedGen, silent = true)
        }
    }

    override fun onNativeOpen(messageId: String, capturedGeneration: Long) {
        if (capturedGeneration != currentGeneration) return

        coroutineScope.launch {
            if (_state.value.availability == RemoteInboxAvailability.Offline) {
                _state.update { it.copy(error = "Unavailable offline. Connect to network to view details.") }
                return@launch
            }

            val detailRes = client.fetchDetail(messageId)
            mutex.withLock {
                if (capturedGeneration != currentGeneration) return@withLock
                if (detailRes is ApiResult.Success) {
                    val detail = detailRes.value
                    cache.upsertItem(detail)
                    _state.update {
                        it.copy(
                            detail = detail,
                            items = cache.getItems()
                        )
                    }
                    // Mark read
                    coroutineScope.launch {
                        val readRes = client.markRead(messageId)
                        mutex.withLock {
                            if (capturedGeneration != currentGeneration) return@withLock
                            if (readRes is ApiResult.Success) {
                                val updatedItem = cache.markItemRead(messageId, readRes.value.readAt)
                                _state.update {
                                    it.copy(
                                        items = cache.getItems(),
                                        unreadCount = readRes.value.unreadCount,
                                        detail = if (it.detail?.id == messageId) updatedItem ?: it.detail else it.detail
                                    )
                                }
                            }
                        }
                    }
                } else if (detailRes is ApiResult.Failure) {
                    if (detailRes.error.code == "appAccessRevoked") {
                        handleRevokedLocked(capturedGeneration)
                    } else {
                        _state.update {
                            it.copy(error = detailRes.error.message)
                        }
                    }
                }
            }
        }
    }

    override suspend fun quiesce(): RemoteQuiesceResult = mutex.withLock {
        val quiescedGen = ++currentGeneration
        currentAccount = null

        cancelBackgroundJobs()
        reconnectTrigger?.cancel()

        cache.clear()
        connectionSeenIds.clear()
        baselineBuffer.clear()

        adapter?.clearRemote(quiescedGen)

        _state.value = RemoteInboxState(
            availability = RemoteInboxAvailability.SignedOut
        )

        // Bounded drain of active child jobs
        val runningJobs = activeJobs.toList()
        runningJobs.forEach { it.cancel() }

        val drainSuccess = withTimeoutOrNull(2_000L) {
            runningJobs.forEach { it.join() }
            true
        }

        if (drainSuccess == true) {
            RemoteQuiesceResult.Quiesced
        } else {
            val remaining = runningJobs.count { it.isActive }
            RemoteQuiesceResult.TimedOut(remaining)
        }
    }

    override suspend fun close() {
        quiesce()
        adapter?.close()
    }
}
