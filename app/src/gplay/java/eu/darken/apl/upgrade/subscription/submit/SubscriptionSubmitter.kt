package eu.darken.apl.upgrade.subscription.submit

import eu.darken.apl.common.debug.logging.Logging.Priority.ERROR
import eu.darken.apl.common.debug.logging.Logging.Priority.INFO
import eu.darken.apl.common.debug.logging.Logging.Priority.WARN
import eu.darken.apl.common.debug.logging.asLog
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.server.PrivacyAcceptance
import eu.darken.apl.server.ServerClock
import eu.darken.apl.server.access.AccessRepo
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.server.api.ServerEndpoint
import eu.darken.apl.server.api.SubscriptionRequest
import eu.darken.apl.server.api.SubscriptionResponse
import eu.darken.apl.server.api.retryAfter
import eu.darken.apl.server.deletion.ServerRequestGate
import eu.darken.apl.server.session.SessionManager
import eu.darken.apl.server.session.SessionRevokedException
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.PlayState
import eu.darken.apl.upgrade.subscription.submit.SubscriptionTokenRecord.Submission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Submits one stored purchase token per call, one call at a time across all tokens. */
@Singleton
class SubscriptionSubmitter @Inject constructor(
    private val store: SubscriptionTokenStore,
    private val scheduler: SubscriptionSubmitScheduler,
    private val policyWait: SubscriptionPolicyWait,
    private val endpoint: ServerEndpoint,
    private val sessionManager: SessionManager,
    private val accessRepo: AccessRepo,
    private val privacyAcceptance: PrivacyAcceptance,
    private val requestGate: ServerRequestGate,
    private val serverClock: ServerClock,
) {

    enum class Outcome {
        /** Nothing left for this run; a follow-up, if any, is queued. */
        DONE,

        /** Run again with backoff. */
        RETRY,

        /** Nothing this run can do will succeed. */
        FAILED,
    }

    private class IdentityChangedException(expected: String, actual: String) :
        Exception("Submission for $expected, the session is now $actual")

    private val lock = Mutex()

    suspend fun submit(installationId: String, tokenHash: String): Outcome {
        var answered = false
        val outcome = lock.withLock {
            val record = store.current().find(installationId, tokenHash)
            if (record == null || record.playState != PlayState.PURCHASED || !record.hasOpenRequest) {
                log(TAG) { "submit(#$tokenHash): nothing to do for $record" }
                return@withLock Outcome.DONE
            }
            submitLocked(record) { answered = true }
        }
        if (answered) refreshAccess()
        return outcome
    }

    private suspend fun submitLocked(record: SubscriptionTokenRecord, onAnswered: () -> Unit): Outcome {
        val genAtStart = record.requestedGen
        val tokenHash = record.tokenHash

        val wait = scheduler.waitFor(record)
        if (!wait.isZero) {
            log(TAG) { "submit(#$tokenHash): the server asked to wait, $wait left" }
            scheduler.followUp(record.installationId, tokenHash, wait, "retry-wait")
            return Outcome.DONE
        }
        // Left requested: the coordinator asks again once the server may be reached
        if (!privacyAcceptance.isAccepted()) {
            log(TAG) { "submit(#$tokenHash): privacy screen not accepted" }
            return Outcome.DONE
        }
        if (!requestGate.isOpen()) {
            log(TAG) { "submit(#$tokenHash): server requests are held back" }
            return Outcome.DONE
        }

        log(TAG) { "submit(#$tokenHash): gen $genAtStart" }
        val response = try {
            sessionManager.authedAs { installationId, token ->
                // Re-enrolled meanwhile: the token must not reach the server under another installation
                if (installationId != record.installationId) {
                    throw IdentityChangedException(record.installationId, installationId)
                }
                endpoint.submitSubscription(token, SubscriptionRequest(record.productId, record.token))
            }
        } catch (e: IdentityChangedException) {
            log(TAG, WARN) { "submit(#$tokenHash): ${e.message}" }
            return Outcome.DONE
        } catch (e: SessionRevokedException) {
            log(TAG, WARN) { "submit(#$tokenHash): installation is revoked" }
            return Outcome.FAILED
        } catch (e: ServerApiException) {
            return onRefused(record, genAtStart, e)
        } catch (e: IOException) {
            log(TAG, WARN) { "submit(#$tokenHash): network unavailable" }
            return Outcome.RETRY
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "submit(#$tokenHash) failed: ${e.asLog().replace(record.token, "#$tokenHash")}" }
            return Outcome.FAILED
        }

        onAccepted(record, genAtStart, response)
        onAnswered()
        return Outcome.DONE
    }

    private suspend fun onAccepted(record: SubscriptionTokenRecord, genAtStart: Long, response: SubscriptionResponse) {
        log(TAG, INFO) { "submit(#${record.tokenHash}): state=${response.state}, attached=${response.attached}" }
        val submission = if (response.attached) Submission.ATTACHED else Submission.NOT_ATTACHED
        policyWait.afterSubmission(record.installationId) { tokens ->
            tokens.complete(record, genAtStart) { it.copy(submission = submission, serverState = response.state) }
        }
    }

    private suspend fun onRefused(record: SubscriptionTokenRecord, genAtStart: Long, e: ServerApiException): Outcome {
        val tokenHash = record.tokenHash
        return when {
            e.code == ServerCodes.INSTALLATION_REVOKED -> {
                log(TAG, WARN) { "submit(#$tokenHash): installation is revoked" }
                Outcome.FAILED
            }

            // A request after an app update submits again
            e.code == ServerCodes.APP_UPDATE_REQUIRED -> {
                log(TAG, WARN) { "submit(#$tokenHash): the server requires a newer app" }
                Outcome.DONE
            }

            e.code == ServerCodes.PURCHASE_INVALID -> {
                log(TAG, WARN) { "submit(#$tokenHash): rejected, ${e.code}" }
                store.update { tokens -> tokens.complete(record, genAtStart) { it.copy(submission = Submission.REJECTED) } }
                Outcome.DONE
            }

            e.code in RETRY_LATER_CODES || e.status >= 500 -> {
                val wait: Duration = e.retryAfter
                log(TAG, WARN) { "submit(#$tokenHash): ${e.code} (HTTP ${e.status}), retrying in $wait" }
                val notBefore = serverClock.now().toEpochMilli() + wait.toMillis()
                store.update { tokens ->
                    tokens.updateRecord(record.installationId, tokenHash) { it.copy(retryNotBefore = notBefore) }
                }
                scheduler.followUp(record.installationId, tokenHash, wait, "retry-after")
                Outcome.DONE
            }

            e.status in 400..499 -> {
                log(TAG, WARN) { "submit(#$tokenHash): dropped, ${e.code} (HTTP ${e.status})" }
                store.update { tokens -> tokens.complete(record, genAtStart) { it.copy(submission = Submission.NONE) } }
                Outcome.DONE
            }

            else -> {
                log(TAG, ERROR) { "submit(#$tokenHash): unexpected ${e.code} (HTTP ${e.status})" }
                Outcome.FAILED
            }
        }
    }

    /**
     * Marks the request [genAtStart] as answered. A request that arrived meanwhile stays open
     * ([SubscriptionTokenRecord.hasOpenRequest]) and gets its own submission.
     */
    private fun SubscriptionTokens.complete(
        record: SubscriptionTokenRecord,
        genAtStart: Long,
        block: (SubscriptionTokenRecord) -> SubscriptionTokenRecord,
    ): SubscriptionTokens = updateRecord(record.installationId, record.tokenHash) { current ->
        block(current).copy(
            completedGen = maxOf(current.completedGen, genAtStart),
            retryNotBefore = null,
        )
    }

    /** The submission is stored when this runs, nothing here may fail the job. */
    private suspend fun refreshAccess() {
        try {
            accessRepo.refresh("subscription-submitted")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, WARN) { "The submission is stored, its access refresh is not: ${e.asLog()}" }
        }
    }

    companion object {
        private val RETRY_LATER_CODES = setOf(
            ServerCodes.INSTALLATION_RATE_EXCEEDED,
            ServerCodes.QUOTA_EXCEEDED,
            ServerCodes.BILLING_UNAVAILABLE,
            ServerCodes.DATABASE_UNAVAILABLE,
        )
        private val TAG = logTag("Upgrade", "Subscription", "Submitter")
    }
}
