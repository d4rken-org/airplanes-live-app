package eu.darken.apl.airport.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.airport.core.Airport
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.displayCountry
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import java.util.Locale

@Composable
fun AirportDetailsSheetHost(
    airport: Airport,
    vm: AirportDetailsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    ModalBottomSheet(onDismissRequest = { vm.navUp() }) {
        AirportDetailsSheetContent(
            airport = airport,
            onShowOnMap = { vm.showOnMap(it) },
        )
    }
}

@Composable
internal fun AirportDetailsSheetContent(
    airport: Airport,
    onShowOnMap: (Airport) -> Unit,
) {
    val country = remember(airport.country) { displayCountry(airport.country) }
    val type = when (airport.size) {
        Airport.Size.LARGE -> stringResource(R.string.airport_type_large)
        Airport.Size.MEDIUM -> stringResource(R.string.airport_type_medium)
        Airport.Size.SMALL -> stringResource(R.string.airport_type_small)
        Airport.Size.OTHER -> airport.type
    }
    val coordinates = "%.4f, %.4f".format(Locale.ROOT, airport.latitude, airport.longitude)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = airport.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        airport.iata?.let { DetailRow(stringResource(R.string.airport_details_iata_label), it, mono = true) }
        airport.icao?.let { DetailRow(stringResource(R.string.airport_details_icao_label), it, mono = true) }
        DetailRow(stringResource(R.string.airport_details_ident_label), airport.ident, mono = true)
        airport.local?.let { DetailRow(stringResource(R.string.airport_details_local_label), it, mono = true) }
        airport.municipality?.let { DetailRow(stringResource(R.string.airport_details_municipality_label), it) }
        airport.region?.let { DetailRow(stringResource(R.string.airport_details_region_label), it) }
        country?.let { DetailRow(stringResource(R.string.airport_details_country_label), it) }
        DetailRow(stringResource(R.string.airport_details_type_label), type)
        DetailRow(stringResource(R.string.airport_details_coordinates_label), coordinates)

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Button(
            onClick = { onShowOnMap(airport) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_show_on_map_action))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(0.35f)
                .padding(end = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (mono) FontFamily.Monospace else null,
            ),
            modifier = Modifier.weight(0.65f),
        )
    }
}

@Preview2
@Composable
private fun AirportDetailsSheetContentPreview() {
    PreviewWrapper {
        AirportDetailsSheetContent(
            airport = Airport(
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
            onShowOnMap = {},
        )
    }
}
