package app.clodex.harness

import android.app.Application
import androidx.room.withTransaction
import androidx.work.await
import androidx.datastore.preferences.core.edit
import app.clodex.harness.data.*
import app.clodex.harness.data.mcp.DataStoreMcpConfigRepository
import app.clodex.harness.data.mcp.KtorMcpGateway
import app.clodex.harness.data.mcp.McpRepository
import app.clodex.harness.data.mcp.McpOAuth
import app.clodex.harness.data.mcp.connectorSettingsStore
import app.clodex.harness.data.code.DefaultCodeAgent
import app.clodex.harness.util.ToastBus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import app.clodex.harness.ui.vm.ChatViewModel
import app.clodex.harness.ui.vm.CodeViewModel
import androidx.core.content.ContextCompat
import android.content.Intent

class ClodexApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val chatVm by lazy { ChatViewModel(this) }
    val codeVm by lazy { CodeViewModel(sessionRepo, settings, toasts, codeAgent, onRunStart = ::keepGenerationAlive, openFiles = codeAgent::files) }

    fun keepGenerationAlive() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, GenerationService::class.java))
        } catch (_: IllegalStateException) {
            toasts.show("Android не разрешил фоновую работу")
        }
    }

    val db: HarnessDb by lazy { HarnessDb.create(app) }
    internal suspend fun enqueueSchedule(row: ScheduledMessage) {
        val dao = db.schedules()
        dao.insert(row)
        try { scheduleWork(app, row.id, row.due).await() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { dao.failWaiting(row.id, row.due, "Не удалось запланировать отправку") }
    }

    internal suspend fun cancelSchedule(id: String) {
        db.schedules().cancel(id)
        androidx.work.WorkManager.getInstance(app).cancelAllWorkByTag("schedule:$id").await()
    }
    val settings: SettingsRepository by lazy { SettingsRepository(app.settingsStore) }
    val toasts = ToastBus()
    val files: FileStore by lazy { FileStore(app) }

    private val http: HttpClient by lazy {
        HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 300_000; connectTimeoutMillis = 20_000; socketTimeoutMillis = 120_000 } }
    }
    private val mcpHttp by lazy { HttpClient(OkHttp) {
        followRedirects = false
        engine { config { followRedirects(false); followSslRedirects(false) } }
        install(HttpTimeout) { requestTimeoutMillis = 60_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 30_000 }
    } }
    val provider: RoutedProviderClient by lazy { RoutedProviderClient(settings, http) }
    internal val scheduledProvider by lazy { RoutedProviderClient(settings, newScheduledHttp()) }
    private val codeAgent by lazy {
        DefaultCodeAgent(app, http, settings, provider, db::memoryFor, {
            skillInstructions(skillRepo.skills.first(), Caveman.block(settings.flow.first()))
        })
    }
    val skillFetcher: SkillFetcher by lazy { GitHubSkillFetcher(http, settings) }
    val providerChecker: ProviderChecker by lazy { ProviderChecker(http) }

    val skillRepo: SkillRepository by lazy { RoomSkillRepository(db.skills()) }
    val caveman by lazy { CavemanRepository(skillRepo, settings) }
    val connectorRepo: ConnectorRepository by lazy { RoomConnectorRepository(db.connectors()) }
    val mcp: McpRepository by lazy { McpRepository(DataStoreMcpConfigRepository(app.connectorSettingsStore), KtorMcpGateway(mcpHttp), McpOAuth(mcpHttp)) }
    val sessionRepo: SessionRepository by lazy { RoomSessionRepository(db.sessions(), db.workspaces()) }

    /** One blocking read so the very first frame already has the right theme and tab. */
    val initialSettings: AppSettings by lazy { runBlocking { settings.flow.first() } }

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    init {
        scope.launch {
            Seed.seedCatalog(db, resetConnectors = false)
            // A fresh install (or a DB migrated after the demo data was removed) always lands on one empty chat.
            val id = Seed.ensureChat(db)
            val stored = settings.flow.first()
            if (stored.currentChatId == 0L || db.chats().get(stored.currentChatId) == null) settings.set(K.currentChat, id)
            if (!stored.seeded) settings.set(K.seeded, true)
            _ready.value = true
            db.schedules().observe().first().filter { it.state == "waiting" }.forEach { row ->
                try { scheduleWork(app, row.id, row.due).await() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { db.schedules().failWaiting(row.id, row.due, "Не удалось восстановить расписание") }
            }
        }
    }

    /** "Удалить все данные": tables + preferences, then the builtin skills and the connector catalog come back clean. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.schedules().observe().first().forEach { db.schedules().cancel(it.id) }
        androidx.work.WorkManager.getInstance(app).cancelAllWorkByTag("scheduled-messages").await()
        withContext(Dispatchers.Main) {
            chatVm.cancelAndJoinReply()
            codeVm.cancelAndJoinRuns()
        }
        db.clearAllTables()
        files.clearAll()
        settings.clearAll()
        app.connectorSettingsStore.edit { it.clear() }
        Seed.seedCatalog(db, resetConnectors = true)
        val id = Seed.ensureChat(db)
        settings.set(K.currentChat, id)
        settings.set(K.seeded, true)
    }
}
