package eu.darken.apl.upgrade.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.feeder.core.link.FeederLinkRepo.FeederLinkState
import eu.darken.apl.upgrade.UpgradeRepo
import eu.darken.apl.upgrade.subscription.SubscriptionState
import eu.darken.apl.upgrade.subscription.SubscriptionState.Offer
import eu.darken.apl.upgrade.subscription.SubscriptionState.Ownership
import eu.darken.apl.upgrade.subscription.SubscriptionState.Submission
import eu.darken.apl.upgrade.ui.UpgradeOfferTextTest.Case.MONTHLY
import eu.darken.apl.upgrade.ui.UpgradeOfferTextTest.Case.MONTHLY_7_DAY_TRIAL
import eu.darken.apl.upgrade.ui.UpgradeOfferTextTest.Case.QUARTERLY_1_DAY_TRIAL
import eu.darken.apl.upgrade.ui.UpgradeOfferTextTest.Case.YEARLY
import eu.darken.apl.upgrade.ui.UpgradeOfferTextTest.Case.YEARLY_1_MONTH_TRIAL
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.Test
import org.robolectric.annotation.Config
import testhelper.BaseComposeTest
import java.time.Period

/** The offer's headline and terms with the billing and trial periods inserted, per language. */
class UpgradeOfferTextTest : BaseComposeTest() {

    enum class Case(val billingPeriod: Period, val trialPeriod: Period?) {
        MONTHLY_7_DAY_TRIAL(Period.ofMonths(1), Period.ofDays(7)),
        MONTHLY(Period.ofMonths(1), null),
        QUARTERLY_1_DAY_TRIAL(Period.ofMonths(3), Period.ofDays(1)),
        YEARLY_1_MONTH_TRIAL(Period.ofYears(1), Period.ofMonths(1)),
        YEARLY(Period.ofYears(1), null),
    }

    private class Expected(val case: Case, val headline: String, val terms: String)

    private fun stateFor(case: Case) = UpgradeViewModel.State(
        type = UpgradeRepo.Type.GPLAY,
        isSettled = true,
        link = FeederLinkState.Unlinked(tier = "free"),
        subscription = SubscriptionState(
            supported = true,
            offer = Offer.Available(
                formattedPrice = "€2.99",
                billingPeriod = case.billingPeriod,
                trialPeriod = case.trialPeriod,
            ),
            ownership = Ownership.None,
            submission = Submission.Idle,
        ),
    )

    /** ICU may separate number and unit with U+00A0 or U+202F, e.g. "3 mois". */
    private fun String.plainSpaces() = replace(' ', ' ').replace(' ', ' ')

    private fun hasTextIgnoringNoBreakSpaces(expected: String) =
        SemanticsMatcher("Text = \"$expected\" (no-break spaces as plain spaces)") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.plainSpaces() == expected }
        }

    private fun shownTexts(): List<String> = composeRule
        .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text.plainSpaces() } }

    private fun assertShown(text: String) {
        val nodes = composeRule
            .onAllNodes(hasTextIgnoringNoBreakSpaces(text), useUnmergedTree = true)
            .fetchSemanticsNodes()
        withClue("\"$text\" among ${shownTexts()}") { nodes shouldHaveSize 1 }
    }

    private fun assertOfferTexts(vararg expected: Expected) {
        expected.map { it.case } shouldContainExactly Case.entries

        val state = mutableStateOf(stateFor(expected.first().case))
        composeRule.setContent {
            PreviewWrapper {
                UpgradeScreen(
                    state = state.value,
                    onNavigateUp = {},
                    onRefresh = {},
                    onRegister = {},
                    onUnlink = {},
                )
            }
        }

        expected.forEach { item ->
            composeRule.runOnIdle { state.value = stateFor(item.case) }
            composeRule.waitForIdle()

            withClue(item.case.name) {
                assertShown(item.headline)
                assertShown(item.terms)
            }
        }
    }

    @Test
    @Config(qualifiers = "en")
    fun `English offer texts`() = assertOfferTexts(
        Expected(
            MONTHLY_7_DAY_TRIAL,
            headline = "Monthly subscription · €2.99",
            terms = "Free for 7 days, then billed monthly until you cancel in Google Play.",
        ),
        Expected(
            MONTHLY,
            headline = "Monthly subscription · €2.99",
            terms = "Billed monthly until you cancel in Google Play.",
        ),
        Expected(
            QUARTERLY_1_DAY_TRIAL,
            headline = "Subscription · €2.99 per 3 months",
            terms = "Free for 1 day, then billed every 3 months until you cancel in Google Play.",
        ),
        Expected(
            YEARLY_1_MONTH_TRIAL,
            headline = "Subscription · €2.99 per 1 year",
            terms = "Free for 1 month, then billed every 1 year until you cancel in Google Play.",
        ),
        Expected(
            YEARLY,
            headline = "Subscription · €2.99 per 1 year",
            terms = "Billed every 1 year until you cancel in Google Play.",
        ),
    )

    @Test
    @Config(qualifiers = "de")
    fun `German offer texts`() = assertOfferTexts(
        Expected(
            MONTHLY_7_DAY_TRIAL,
            headline = "Monatsabo · €2.99",
            terms = "7 Tage kostenlos, danach monatliche Abrechnung, bis du in Google Play kündigst.",
        ),
        Expected(
            MONTHLY,
            headline = "Monatsabo · €2.99",
            terms = "Monatliche Abrechnung, bis du in Google Play kündigst.",
        ),
        Expected(
            QUARTERLY_1_DAY_TRIAL,
            headline = "Abo · €2.99 für jeweils 3 Monate",
            terms = "1 Tag kostenlos, danach Abrechnung für jeweils 3 Monate, bis du in Google Play kündigst.",
        ),
        Expected(
            YEARLY_1_MONTH_TRIAL,
            headline = "Abo · €2.99 für jeweils 1 Jahr",
            terms = "1 Monat kostenlos, danach Abrechnung für jeweils 1 Jahr, bis du in Google Play kündigst.",
        ),
        Expected(
            YEARLY,
            headline = "Abo · €2.99 für jeweils 1 Jahr",
            terms = "Abrechnung für jeweils 1 Jahr, bis du in Google Play kündigst.",
        ),
    )

    @Test
    @Config(qualifiers = "es")
    fun `Spanish offer texts`() = assertOfferTexts(
        Expected(
            MONTHLY_7_DAY_TRIAL,
            headline = "Suscripción mensual · €2.99",
            terms = "Gratis durante 7 días; después, se cobra cada mes hasta que canceles en Google Play.",
        ),
        Expected(
            MONTHLY,
            headline = "Suscripción mensual · €2.99",
            terms = "Se cobra cada mes hasta que canceles en Google Play.",
        ),
        Expected(
            QUARTERLY_1_DAY_TRIAL,
            headline = "Suscripción · €2.99 por 3 meses",
            terms = "Gratis durante 1 día; después, se cobra por periodos de 3 meses hasta que canceles en Google Play.",
        ),
        Expected(
            YEARLY_1_MONTH_TRIAL,
            headline = "Suscripción · €2.99 por 1 año",
            terms = "Gratis durante 1 mes; después, se cobra por periodos de 1 año hasta que canceles en Google Play.",
        ),
        Expected(
            YEARLY,
            headline = "Suscripción · €2.99 por 1 año",
            terms = "Se cobra por periodos de 1 año hasta que canceles en Google Play.",
        ),
    )

    @Test
    @Config(qualifiers = "fr")
    fun `French offer texts`() = assertOfferTexts(
        Expected(
            MONTHLY_7_DAY_TRIAL,
            headline = "Abonnement mensuel · €2.99",
            terms = "Gratuit pendant 7 jours, puis facturé chaque mois jusqu’à ce que tu résilies dans Google Play.",
        ),
        Expected(
            MONTHLY,
            headline = "Abonnement mensuel · €2.99",
            terms = "Facturé chaque mois jusqu’à ce que tu résilies dans Google Play.",
        ),
        Expected(
            QUARTERLY_1_DAY_TRIAL,
            headline = "Abonnement · €2.99 pour 3 mois",
            terms = "Gratuit pendant 1 jour, puis facturé par période de 3 mois jusqu’à ce que tu résilies dans Google Play.",
        ),
        Expected(
            YEARLY_1_MONTH_TRIAL,
            headline = "Abonnement · €2.99 pour 1 an",
            terms = "Gratuit pendant 1 mois, puis facturé par période de 1 an jusqu’à ce que tu résilies dans Google Play.",
        ),
        Expected(
            YEARLY,
            headline = "Abonnement · €2.99 pour 1 an",
            terms = "Facturé par période de 1 an jusqu’à ce que tu résilies dans Google Play.",
        ),
    )

    @Test
    @Config(qualifiers = "it")
    fun `Italian offer texts`() = assertOfferTexts(
        Expected(
            MONTHLY_7_DAY_TRIAL,
            headline = "Abbonamento mensile · €2.99",
            terms = "Gratis per 7 giorni, poi addebito mensile finché non annulli l’abbonamento in Google Play.",
        ),
        Expected(
            MONTHLY,
            headline = "Abbonamento mensile · €2.99",
            terms = "Addebito mensile finché non annulli l’abbonamento in Google Play.",
        ),
        Expected(
            QUARTERLY_1_DAY_TRIAL,
            headline = "Abbonamento · €2.99 per 3 mesi",
            terms = "Gratis per 1 giorno, poi addebito per periodi di 3 mesi finché non annulli l’abbonamento in Google Play.",
        ),
        Expected(
            YEARLY_1_MONTH_TRIAL,
            headline = "Abbonamento · €2.99 per 1 anno",
            terms = "Gratis per 1 mese, poi addebito per periodi di 1 anno finché non annulli l’abbonamento in Google Play.",
        ),
        Expected(
            YEARLY,
            headline = "Abbonamento · €2.99 per 1 anno",
            terms = "Addebito per periodi di 1 anno finché non annulli l’abbonamento in Google Play.",
        ),
    )

    @Test
    @Config(qualifiers = "ru")
    fun `Russian offer texts`() = assertOfferTexts(
        Expected(
            MONTHLY_7_DAY_TRIAL,
            headline = "Ежемесячная подписка · €2.99",
            terms = "Бесплатный период: 7 дней, затем ежемесячная оплата до отмены подписки в Google Play.",
        ),
        Expected(
            MONTHLY,
            headline = "Ежемесячная подписка · €2.99",
            terms = "Ежемесячная оплата до отмены подписки в Google Play.",
        ),
        Expected(
            QUARTERLY_1_DAY_TRIAL,
            headline = "Подписка · €2.99 за 3 месяца",
            terms = "Бесплатный период: 1 день, затем регулярная оплата до отмены подписки в Google Play. " +
                "Период оплаты: 3 месяца.",
        ),
        Expected(
            YEARLY_1_MONTH_TRIAL,
            headline = "Подписка · €2.99 за 1 год",
            terms = "Бесплатный период: 1 месяц, затем регулярная оплата до отмены подписки в Google Play. " +
                "Период оплаты: 1 год.",
        ),
        Expected(
            YEARLY,
            headline = "Подписка · €2.99 за 1 год",
            terms = "Регулярная оплата до отмены подписки в Google Play. Период оплаты: 1 год.",
        ),
    )
}
