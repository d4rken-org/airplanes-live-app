package eu.darken.apl.upgrade.subscription.submit

import kotlinx.serialization.Serializable

/** Everything this installation knows about the Play purchases it handed, or wants to hand, to the server. */
@Serializable
data class SubscriptionTokens(
    val records: List<SubscriptionTokenRecord> = emptyList(),
    /** Set after the server answered a submission, until a policy fetched afterwards arrived for this installation. */
    val awaitingPolicyFor: String? = null,
) {

    fun find(installationId: String, tokenHash: String): SubscriptionTokenRecord? =
        records.firstOrNull { it.installationId == installationId && it.tokenHash == tokenHash }

    /** A record of another installation says nothing about this one. */
    fun recordsOf(installationId: String): List<SubscriptionTokenRecord> =
        records.filter { it.installationId == installationId }

    fun upsert(record: SubscriptionTokenRecord): SubscriptionTokens {
        val existing = find(record.installationId, record.tokenHash)
        return copy(
            records = if (existing == null) {
                records + record
            } else {
                records.map { if (it === existing) record else it }
            }
        )
    }

    /** Leaves the tokens as they are when there is no such record. */
    fun updateRecord(
        installationId: String,
        tokenHash: String,
        block: (SubscriptionTokenRecord) -> SubscriptionTokenRecord,
    ): SubscriptionTokens = find(installationId, tokenHash)?.let { upsert(block(it)) } ?: this
}

/**
 * One purchase token for one installation. Enum values this build does not know decode to the
 * property's default, see [SubscriptionTokenStore].
 */
@Serializable
data class SubscriptionTokenRecord(
    val installationId: String,
    val tokenHash: String,
    val token: String,
    val productId: String,
    val playState: PlayState = PlayState.PENDING,
    val submission: Submission = Submission.NONE,
    /** The `state` of the last successful submission, an open set of values. */
    val serverState: String? = null,
    val requestedGen: Long = 0,
    val completedGen: Long = 0,
    /** Epoch milliseconds before which the server asked not to be asked again. */
    val retryNotBefore: Long? = null,
) {

    enum class PlayState {
        PENDING,
        PURCHASED,
        SUSPENDED,
    }

    enum class Submission {
        NONE,
        REQUESTED,
        ATTACHED,
        NOT_ATTACHED,
        REJECTED,
    }

    /** A request no finished submission has answered yet. */
    val hasOpenRequest: Boolean
        get() = requestedGen > completedGen

    // The purchase token is a credential, logging a record must not reveal it
    override fun toString(): String =
        "SubscriptionTokenRecord(installation=$installationId, token=#$tokenHash, product=$productId, " +
            "play=$playState, submission=$submission, serverState=$serverState, " +
            "gen=$completedGen/$requestedGen, retryNotBefore=$retryNotBefore)"
}
