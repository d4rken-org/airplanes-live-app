package eu.darken.apl.feeder.core.server

import eu.darken.apl.feeder.core.config.FeederConfig
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest

class FeederProblemCategoryTest : BaseTest() {

    private val allOn = FeederConfig(
        receiverId = ID,
        notifyMessageRateProblems = true,
        notifyMlatProblems = true,
        notifyConnectionProblems = true,
    )

    @Test
    fun `every known problem code has its category`() {
        mapOf(
            "deaf" to FeederProblemCategory.MESSAGE_RATE,
            "mlat_missing" to FeederProblemCategory.MLAT,
            "mlat_no_peers" to FeederProblemCategory.MLAT,
            "mlat_bad_sync" to FeederProblemCategory.MLAT,
            "mlat_outliers" to FeederProblemCategory.MLAT,
            "flapping" to FeederProblemCategory.CONNECTION,
            "duplicate_uuid" to FeederProblemCategory.CONNECTION,
        ).forEach { (code, category) ->
            FeederProblemCategory.of(code) shouldBe category
        }
    }

    @Test
    fun `an unknown code has no category and is eligible while problem alerts are on`() {
        FeederProblemCategory.of("something_new") shouldBe null

        val allOff = allOn.copy(
            notifyMessageRateProblems = false,
            notifyMlatProblems = false,
            notifyConnectionProblems = false,
        )
        isProblemAlertEligible(allOff, problemAlertsEnabled = true, code = "something_new") shouldBe true
    }

    @Test
    fun `nothing is eligible while problem alerts are off`() {
        CODES.forEach { code ->
            isProblemAlertEligible(allOn, problemAlertsEnabled = false, code = code) shouldBe false
        }
    }

    @Test
    fun `a disabled category makes only its own codes ineligible`() {
        val mlatOff = allOn.copy(notifyMlatProblems = false)

        CODES.associateWith { isProblemAlertEligible(mlatOff, problemAlertsEnabled = true, code = it) } shouldBe mapOf(
            "deaf" to true,
            "mlat_missing" to false,
            "mlat_no_peers" to false,
            "mlat_bad_sync" to false,
            "mlat_outliers" to false,
            "flapping" to true,
            "duplicate_uuid" to true,
            "something_new" to true,
        )
    }

    @Test
    fun `the defaults notify message rate and connection problems but not mlat`() {
        val config = FeederConfig.newFeeder(ID)

        isProblemAlertEligible(config, problemAlertsEnabled = true, code = "deaf") shouldBe true
        isProblemAlertEligible(config, problemAlertsEnabled = true, code = "mlat_missing") shouldBe false
        isProblemAlertEligible(config, problemAlertsEnabled = true, code = "flapping") shouldBe true
        config.problemCategories shouldBe setOf(FeederProblemCategory.MESSAGE_RATE, FeederProblemCategory.CONNECTION)
    }

    @Test
    fun `setting a category changes only that category`() {
        FeederProblemCategory.entries.forEach { category ->
            val off = allOn.withProblemCategory(category, enabled = false)

            off.problemCategories shouldBe FeederProblemCategory.entries.toSet() - category
            off.withProblemCategory(category, enabled = true) shouldBe allOn
        }
    }

    companion object {
        private const val ID = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d"
        private val CODES = listOf(
            "deaf",
            "mlat_missing",
            "mlat_no_peers",
            "mlat_bad_sync",
            "mlat_outliers",
            "flapping",
            "duplicate_uuid",
            "something_new",
        )
    }
}
