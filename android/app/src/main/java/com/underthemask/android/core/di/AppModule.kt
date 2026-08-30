package com.underthemask.android.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.underthemask.android.BuildConfig
import com.underthemask.android.core.config.BackendConfig
import com.underthemask.android.core.datastore.DataStoreSessionManager
import com.underthemask.android.core.datastore.SessionManager
import com.underthemask.android.core.network.AuthInterceptor
import com.underthemask.android.core.network.ErrorMapper
import com.underthemask.android.core.network.LobbyApiService
import com.underthemask.android.core.repository.DefaultGameRepository
import com.underthemask.android.core.repository.DefaultLobbyRepository
import com.underthemask.android.core.repository.GameRepository
import com.underthemask.android.core.repository.LobbyRepository
import com.underthemask.android.core.websocket.LobbyRealtimeClient
import com.underthemask.android.core.websocket.RealtimeConnectionFactory
import com.underthemask.android.core.websocket.StompLobbyClient
import com.underthemask.android.core.websocket.StompConnectionFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun sessionDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("player_session.preferences_pb") },
    )

    @Provides
    @Singleton
    fun sessionManager(implementation: DataStoreSessionManager): SessionManager = implementation

    @Provides
    @Singleton
    fun errorMapper(json: Json): ErrorMapper = ErrorMapper(json)

    @Provides
    @Singleton
    @Named("websocket")
    fun webSocketOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Provides
    @Singleton
    @Named("rest")
    fun restOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logger)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun apiService(
        @Named("rest") okHttpClient: OkHttpClient,
        json: Json,
    ): LobbyApiService = Retrofit.Builder()
        .baseUrl(BackendConfig.apiBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LobbyApiService::class.java)

    @Provides
    @Singleton
    fun lobbyRepository(implementation: DefaultLobbyRepository): LobbyRepository = implementation

    @Provides
    @Singleton
    fun gameRepository(implementation: DefaultGameRepository): GameRepository = implementation

    @Provides
    @Singleton
    fun realtimeConnectionFactory(
        implementation: StompConnectionFactory,
    ): RealtimeConnectionFactory = implementation

    @Provides
    @Singleton
    fun realtimeClient(implementation: StompLobbyClient): LobbyRealtimeClient = implementation
}
