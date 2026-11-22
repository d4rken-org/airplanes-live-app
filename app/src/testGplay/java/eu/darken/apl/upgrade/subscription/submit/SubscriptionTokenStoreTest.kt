package eu.darken.apl.upgrade.subscription.submit

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_A
import eu.darken.apl.upgrade.subscription.submit.SubmissionFixture.Companion.INSTALLATION_B
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.PlayState
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.Submission
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class SubscriptionTokenStoreTest : BaseTest() {

    private val fixture = SubmissionFixture()

    @AfterEach
    fun teardown() = fixture.close()

    private val record = SubscriptionTokenRecord(
        installationId = INSTALLATION_A,
        tokenHash = "0a1b2c3d",
        token = TOKEN,
        productId = "upgrade.pro",
        playState = PlayState.PURCHASED,
        submission = Submission.NOT_ATTACHED,
        serverState = "on_hold",
        requestedGen = 3,
        completedGen = 2,
        retryNotBefore = 1234L,
    )

    @Test
    fun `stored tokens survive a restart`() = runTest {
        val other = record.copy(installationId = INSTALLATION_B, submission = Submission.ATTACHED)
        fixture.store.update { it.upsert(record).upsert(other).copy(awaitingPolicyFor = INSTALLATION_A) }

        fixture.restart()

        fixture.store.current() shouldBe SubscriptionTokens(
            records = listOf(record, other),
            awaitingPolicyFor = INSTALLATION_A,
        )
    }

    @Test
    fun `records of another installation are not this installation's`() = runTest {
        fixture.store.update { it.upsert(record.copy(installationId = INSTALLATION_B)) }

        fixture.store.current().recordsOf(INSTALLATION_A) shouldBe emptyList()
        fixture.store.current().find(INSTALLATION_A, record.tokenHash) shouldBe null
    }

    @Test
    fun `an upsert replaces the record of the same installation and token`() = runTest {
        fixture.store.update { it.upsert(record) }
        fixture.store.update { it.upsert(record.copy(submission = Submission.ATTACHED)) }

        fixture.store.current().records shouldBe listOf(record.copy(submission = Submission.ATTACHED))
    }

    @Test
    fun `states this build does not know fall back instead of losing the records`() = runTest {
        fixture.dataStore.edit {
            it[stringPreferencesKey(SubscriptionTokenStore.KEY)] = """
                {
                  "records": [{
                    "installationId": "$INSTALLATION_A",
                    "tokenHash": "0a1b2c3d",
                    "token": "$TOKEN",
                    "productId": "upgrade.pro",
                    "playState": "SOMETHING_NEW",
                    "submission": "ALSO_NEW",
                    "serverState": "in_grace",
                    "requestedGen": 4,
                    "completedGen": 4,
                    "someFutureField": true
                  }],
                  "awaitingPolicyFor": "$INSTALLATION_A"
                }
            """.trimIndent()
        }

        val stored = fixture.store.current()

        stored.awaitingPolicyFor shouldBe INSTALLATION_A
        stored.records shouldBe listOf(
            SubscriptionTokenRecord(
                installationId = INSTALLATION_A,
                tokenHash = "0a1b2c3d",
                token = TOKEN,
                productId = "upgrade.pro",
                playState = PlayState.PENDING,
                submission = Submission.NONE,
                serverState = "in_grace",
                requestedGen = 4,
                completedGen = 4,
            )
        )
    }

    @Test
    fun `a corrupt value falls back to no tokens`() = runTest {
        fixture.dataStore.edit { it[stringPreferencesKey(SubscriptionTokenStore.KEY)] = "{\"records\": [{\"broken" }

        fixture.store.current() shouldBe SubscriptionTokens()
    }

    @Test
    fun `a record's text never contains the token`() {
        val text = record.toString()

        text shouldNotContain TOKEN
        text shouldContain "#0a1b2c3d"
        SubscriptionTokens(listOf(record)).toString() shouldNotContain TOKEN
    }

    companion object {
        private const val TOKEN = "PURCHASE-TOKEN-SENTINEL"
    }
}
