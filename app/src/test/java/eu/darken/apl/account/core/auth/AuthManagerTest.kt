package eu.darken.apl.account.core.auth

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import eu.darken.apl.account.core.AccountSettings
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.datastore.DataStoreValue
import eu.darken.apl.common.datastore.createValue
import eu.darken.apl.common.datastore.value
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.openid.appauth.AuthState
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.coroutine.TestDispatcherProvider

class AuthManagerTest : BaseTest() {

    // datastore-preferences-core is pure JVM, so the real DataStoreValue read/update path runs
    // against this instead of a mock — the signature guard and clear logic are exercised for real.
    private class FakeDataStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> get() = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            state.value = transform(state.value)
            return state.value
        }
    }

    private val signature = "${BuildConfigWrap.OAUTH_HOST}|${BuildConfigWrap.OAUTH_CLIENT_ID}"

    private lateinit var rawValue: DataStoreValue<String?>
    private lateinit var signatureValue: DataStoreValue<String?>
    private lateinit var manager: AuthManager

    @BeforeEach
    fun setup() {
        // AuthManager builds its AuthorizationServiceConfiguration at construction; Uri.parse is
        // an unstubbed android.jar method on the JVM.
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
        mockkStatic(AuthState::class)

        val dataStore = FakeDataStore()
        rawValue = dataStore.createValue<String?>("account.oauth.authstate", null)
        signatureValue = dataStore.createValue<String?>("account.oauth.authstate.signature", null)
        val settings = mockk<AccountSettings> {
            every { authStateRaw } returns rawValue
            every { authStateSignature } returns signatureValue
        }
        manager = AuthManager(
            context = mockk<Context>(),
            settings = settings,
            baseClient = mockk<OkHttpClient>(),
            dispatcherProvider = TestDispatcherProvider(),
        )
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(Uri::class)
        unmockkStatic(AuthState::class)
    }

    private suspend fun seedSession(accessToken: String, storedSignature: String = signature) {
        rawValue.value("stored-state")
        signatureValue.value(storedSignature)
        val state = mockk<AuthState> {
            every { this@mockk.accessToken } returns accessToken
        }
        every { AuthState.jsonDeserialize("stored-state") } returns state
    }

    @Test
    fun `invalidate clears the session when the rejected token is current`() = runTest {
        seedSession(accessToken = "tok-a")

        manager.invalidateSessionIfCurrent("tok-a") shouldBe true

        rawValue.value() shouldBe null
        signatureValue.value() shouldBe null
        manager.isLoggedIn.first() shouldBe false
    }

    @Test
    fun `invalidate keeps the session when a newer token has replaced the rejected one`() = runTest {
        seedSession(accessToken = "tok-b")

        manager.invalidateSessionIfCurrent("tok-a") shouldBe false

        rawValue.value() shouldBe "stored-state"
        manager.isLoggedIn.first() shouldBe true
    }

    @Test
    fun `invalidate with no stored session counts as already expired`() = runTest {
        manager.invalidateSessionIfCurrent("tok-a") shouldBe true
    }

    @Test
    fun `invalidate treats a foreign-signature session as gone and clears it`() = runTest {
        seedSession(accessToken = "tok-a", storedSignature = "other-host|other-client")

        manager.invalidateSessionIfCurrent("tok-a") shouldBe true

        rawValue.value() shouldBe null
    }

    @Test
    fun `session epoch bumps on clear but not on read`() = runTest {
        seedSession(accessToken = "tok-a")
        val before = manager.sessionEpoch

        manager.invalidateSessionIfCurrent("tok-other") shouldBe false
        manager.sessionEpoch shouldBe before

        manager.invalidateSessionIfCurrent("tok-a") shouldBe true
        manager.sessionEpoch shouldBe before + 1
    }
}
