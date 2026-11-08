package eu.darken.apl.search.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.darken.apl.R
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.airport.core.AirportAggregation
import eu.darken.apl.airport.core.AirportRow
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.displayCountry

private const val COLLAPSED_AIRPORT_ROWS = 3

@Composable
internal fun AirportsSectionItem(
    section: SearchViewModel.AirportSection,
    onAirportClick: (Airport) -> Unit,
    onRetry: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.search_airports_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            when (val status = section.status) {
                SearchViewModel.AirportStatus.Loading -> Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.search_airports_loading),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is SearchViewModel.AirportStatus.Results -> AirportResults(
                    airports = status.airports,
                    expanded = section.expanded,
                    onAirportClick = onAirportClick,
                    onExpandedChange = onExpandedChange,
                )

                SearchViewModel.AirportStatus.Failed -> AirportStatusLine(
                    text = stringResource(R.string.search_airports_failed),
                    onRetry = onRetry,
                )

                SearchViewModel.AirportStatus.RateLimited -> AirportStatusLine(
                    text = stringResource(R.string.search_airports_rate_limited),
                    onRetry = onRetry,
                )

                SearchViewModel.AirportStatus.Unavailable -> AirportStatusLine(
                    text = stringResource(R.string.search_airports_unavailable),
                )

                SearchViewModel.AirportStatus.UpdateRequired -> AirportStatusLine(
                    text = stringResource(R.string.search_airports_update_required),
                )

                SearchViewModel.AirportStatus.Restricted -> AirportStatusLine(
                    text = stringResource(R.string.upgrade_restricted_msg),
                )

                SearchViewModel.AirportStatus.Revoked -> AirportStatusLine(
                    text = stringResource(R.string.server_error_revoked_msg),
                )
            }

            if (section.cutoff) {
                AirportNote(pluralStringResource(R.plurals.search_airports_cutoff, section.terms.size, section.terms.size))
            }
        }
    }
}

@Composable
private fun AirportResults(
    airports: AirportAggregation.Shown,
    expanded: Boolean,
    onAirportClick: (Airport) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    if (airports.noAirports) {
        AirportStatusLine(text = stringResource(R.string.search_airports_none))
    }

    val rows = airports.rows
    val shown = if (expanded) rows else rows.take(COLLAPSED_AIRPORT_ROWS)
    shown.forEach { row ->
        AirportRowItem(row = row, onClick = { onAirportClick(row.airport) })
    }
    if (rows.size > COLLAPSED_AIRPORT_ROWS) {
        TextButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                if (expanded) {
                    stringResource(R.string.search_airports_show_fewer_action)
                } else {
                    stringResource(R.string.search_airports_show_all_action, rows.size)
                }
            )
        }
    }

    if (airports.invalidTerms.isNotEmpty()) {
        AirportNote(
            stringResource(
                R.string.search_airports_invalid_terms_x,
                airports.invalidTerms.joinToString(stringResource(R.string.common_list_separator)),
            )
        )
    }
    if (airports.truncated) {
        AirportNote(stringResource(R.string.search_airports_truncated))
    }
}

@Composable
private fun AirportRowItem(row: AirportRow, onClick: () -> Unit) {
    val airport = row.airport
    val country = remember(airport.country) { displayCountry(airport.country) }
    val place = airportPlaceLine(row, country, stringResource(R.string.common_list_separator))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = airport.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = airportCodesLine(airport),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        place?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AirportStatusLine(text: String, onRetry: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.search_airports_retry_action))
            }
        }
    }
}

@Composable
private fun AirportNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * IATA and ICAO, or the ident when it has neither. A code the search matched that isn't among them leads:
 * Frankfurt found by name is "FRA · EDDF", Danville found by `CDN2` is "CDN2 · CA-0027".
 */
internal fun airportCodesLine(airport: Airport): String {
    val codes = listOfNotNull(airport.iata.nonBlank(), airport.icao.nonBlank())
        .ifEmpty { listOf(airport.ident) }
    val matched = airport.matchedCode.nonBlank()
        ?.takeIf { matched -> codes.none { it.equals(matched, ignoreCase = true) } }
    return (listOfNotNull(matched) + codes).joinToString(" · ")
}

/**
 * Municipality and [country], with the region between them only when another row shares the municipality:
 * "Frankfurt am Main, Germany", "Danville, US-IL, United States". Null when nothing is known.
 */
internal fun airportPlaceLine(row: AirportRow, country: String?, separator: String): String? {
    val airport = row.airport
    return listOfNotNull(
        airport.municipality.nonBlank(),
        airport.region.nonBlank()?.takeIf { row.municipalityAmbiguous },
        country.nonBlank(),
    ).joinToString(separator).ifEmpty { null }
}

private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }

private val previewAirports = listOf(
    Airport(
        id = "oa:2212",
        name = "Frankfurt am Main Airport",
        type = "large",
        ident = "EDDF",
        latitude = 50.033333,
        longitude = 8.570556,
        municipality = "Frankfurt am Main",
        country = "DE",
        region = "DE-HE",
        icao = "EDDF",
        iata = "FRA",
    ),
    Airport(
        id = "oa:39673",
        name = "Danville Airport",
        type = "small",
        ident = "CA-0027",
        latitude = 45.79425,
        longitude = -71.995339,
        municipality = "Val-des-Sources",
        country = "CA",
        region = "CA-QC",
        local = "CDN2",
        matchedCode = "CDN2",
    ),
)

@Preview2
@Composable
private fun AirportsSectionResultsPreview() {
    PreviewWrapper {
        AirportsSectionItem(
            section = SearchViewModel.AirportSection(
                terms = listOf("FRA", "CDN2", "X"),
                cutoff = false,
                status = SearchViewModel.AirportStatus.Results(
                    AirportAggregation.Shown(
                        rows = previewAirports.map { AirportRow(it, municipalityAmbiguous = false) },
                        truncated = true,
                        invalidTerms = listOf("X"),
                        searchedAny = true,
                    )
                ),
            ),
            onAirportClick = {},
            onRetry = {},
            onExpandedChange = {},
        )
    }
}

@Preview2
@Composable
private fun AirportsSectionFailedPreview() {
    PreviewWrapper {
        AirportsSectionItem(
            section = SearchViewModel.AirportSection(
                terms = listOf("FRA"),
                cutoff = true,
                status = SearchViewModel.AirportStatus.Failed,
            ),
            onAirportClick = {},
            onRetry = {},
            onExpandedChange = {},
        )
    }
}
