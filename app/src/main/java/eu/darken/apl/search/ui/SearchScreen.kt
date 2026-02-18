package eu.darken.apl.search.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.BottomNavBar
import eu.darken.apl.common.compose.InfoCell
import eu.darken.apl.common.compose.LoadingBox
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import retrofit2.HttpException

@Composable
fun SearchScreenHost(
    targetHexes: List<String>? = null,
    targetSquawks: List<String>? = null,
    targetCallsigns: List<String>? = null,
    vm: SearchViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    LaunchedEffect(targetHexes, targetSquawks, targetCallsigns) {
        vm.init(targetHexes, targetSquawks, targetCallsigns)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                SearchEvents.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }

                is SearchEvents.SearchError -> {
                    val message = when {
                        event.error is HttpException && (event.error as HttpException).code() == 429 ->
                            context.getString(R.string.search_error_rate_limited)

                        event.error.message?.contains("rate limit", ignoreCase = true) == true ->
                            context.getString(R.string.search_error_rate_limited)

                        else ->
                            context.getString(R.string.search_error_generic, event.error.message ?: event.error.toString())
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val state by vm.state.collectAsState(initial = null)

    state?.let {
        SearchScreen(
            state = it,
            snackbarHostState = snackbarHostState,
            onSearchText = vm::updateSearchText,
            onModeSelected = vm::updateMode,
            onPositionHome = vm::searchPositionHome,
            onSettings = { vm.navTo(eu.darken.apl.main.ui.settings.DestinationSettingsIndex) },
            onAircraftClick = { ac -> vm.openAircraftAction(ac.hex) },
            onThumbnailClick = { meta -> vm.openThumbnail(meta.link) },
            onWatchClick = { watch -> vm.openWatch(watch) },
            onShowOnMap = { aircraft -> vm.showOnMap(aircraft) },
            onGrantLocation = vm::requestLocationPermission,
            onDismissLocation = vm::dismissLocationPrompt,
            onStartFeeding = vm::startFeeding,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: SearchViewModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchText: (String) -> Unit,
    onModeSelected: (SearchViewModel.State.Mode) -> Unit,
    onPositionHome: () -> Unit,
    onSettings: () -> Unit,
    onAircraftClick: (Aircraft) -> Unit,
    onThumbnailClick: (eu.darken.apl.common.planespotters.PlanespottersMeta) -> Unit,
    onWatchClick: (eu.darken.apl.watch.core.types.Watch) -> Unit,
    onShowOnMap: (Collection<Aircraft>) -> Unit,
    onGrantLocation: () -> Unit,
    onDismissLocation: () -> Unit,
    onStartFeeding: () -> Unit,
) {
    var selectedHexes by remember { mutableStateOf(emptySet<String>()) }
    val isSelectionMode = selectedHexes.isNotEmpty()

    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by remember(state.input.raw) { mutableStateOf(state.input.raw) }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(hasBottomNav = true),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedHexes.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedHexes = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val aircraft = state.items
                                .filterIsInstance<SearchViewModel.SearchItem.AircraftResult>()
                                .filter { it.aircraft.hex in selectedHexes }
                                .map { it.aircraft }
                            onShowOnMap(aircraft)
                            selectedHexes = emptySet()
                        }) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.common_show_on_map_action))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.search_page_label)) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomNavBar(selectedTab = 1) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            // Search input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = when (state.input.mode) {
                                SearchViewModel.State.Mode.ALL -> stringResource(R.string.search_mode_all_hint)
                                SearchViewModel.State.Mode.HEX -> stringResource(R.string.search_mode_hex_hint)
                                SearchViewModel.State.Mode.CALLSIGN -> stringResource(R.string.search_mode_callsign_hint)
                                SearchViewModel.State.Mode.REGISTRATION -> stringResource(R.string.search_mode_registration_hint)
                                SearchViewModel.State.Mode.SQUAWK -> stringResource(R.string.search_mode_squawk_hint)
                                SearchViewModel.State.Mode.AIRFRAME -> stringResource(R.string.search_mode_airframe_hint)
                                SearchViewModel.State.Mode.INTERESTING -> stringResource(R.string.search_mode_military_hint)
                                SearchViewModel.State.Mode.POSITION -> stringResource(R.string.search_mode_location_hint)
                            },
                        )
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = {
                                searchText = ""
                                onSearchText("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearchText(searchText)
                            keyboardController?.hide()
                        },
                    ),
                )
                if (state.input.mode == SearchViewModel.State.Mode.POSITION) {
                    IconButton(onClick = onPositionHome) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                    }
                }
            }

            // Mode chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchViewModel.State.Mode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.input.mode == mode,
                        onClick = { onModeSelected(mode) },
                        label = {
                            Text(
                                text = when (mode) {
                                    SearchViewModel.State.Mode.ALL -> "All"
                                    SearchViewModel.State.Mode.HEX -> "HEX"
                                    SearchViewModel.State.Mode.CALLSIGN -> "Callsign"
                                    SearchViewModel.State.Mode.REGISTRATION -> "Reg"
                                    SearchViewModel.State.Mode.SQUAWK -> "Squawk"
                                    SearchViewModel.State.Mode.AIRFRAME -> "Airframe"
                                    SearchViewModel.State.Mode.INTERESTING -> "Interesting"
                                    SearchViewModel.State.Mode.POSITION -> "Location"
                                },
                            )
                        },
                    )
                }
            }

            // Results list
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = state.items,
                    key = { item ->
                        when (item) {
                            is SearchViewModel.SearchItem.LocationPrompt -> "location_prompt"
                            is SearchViewModel.SearchItem.Searching -> "searching"
                            is SearchViewModel.SearchItem.NoResults -> "no_results"
                            is SearchViewModel.SearchItem.Summary -> "summary"
                            is SearchViewModel.SearchItem.AircraftResult -> item.aircraft.hex
                        }
                    },
                ) { item ->
                    when (item) {
                        is SearchViewModel.SearchItem.LocationPrompt -> LocationPromptItem(
                            onGrant = onGrantLocation,
                            onDismiss = onDismissLocation,
                        )

                        is SearchViewModel.SearchItem.Searching -> SearchingItem(
                            aircraftCount = item.aircraftCount,
                        )

                        is SearchViewModel.SearchItem.NoResults -> NoResultsItem(
                            onStartFeeding = onStartFeeding,
                        )

                        is SearchViewModel.SearchItem.Summary -> SummaryItem(
                            aircraftCount = item.aircraftCount,
                        )

                        is SearchViewModel.SearchItem.AircraftResult -> AircraftResultItem(
                            item = item,
                            isSelected = item.aircraft.hex in selectedHexes,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedHexes = if (item.aircraft.hex in selectedHexes) {
                                        selectedHexes - item.aircraft.hex
                                    } else {
                                        selectedHexes + item.aircraft.hex
                                    }
                                } else {
                                    onAircraftClick(item.aircraft)
                                }
                            },
                            onLongClick = {
                                selectedHexes = if (item.aircraft.hex in selectedHexes) {
                                    selectedHexes - item.aircraft.hex
                                } else {
                                    selectedHexes + item.aircraft.hex
                                }
                            },
                            onThumbnailClick = onThumbnailClick,
                            onWatchClick = { item.watch?.let(onWatchClick) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationPromptItem(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.search_location_prompt_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.search_location_prompt_body),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_dismiss_action))
                }
                TextButton(onClick = onGrant) {
                    Text(stringResource(R.string.common_grant_permission_action))
                }
            }
        }
    }
}

@Composable
private fun SearchingItem(aircraftCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.search_progress_body, aircraftCount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NoResultsItem(onStartFeeding: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.search_empty_body),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onStartFeeding, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.common_start_feeding_action))
        }
    }
}

@Composable
private fun SummaryItem(aircraftCount: Int) {
    Text(
        text = stringResource(R.string.search_summary_x_aircraft, aircraftCount),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AircraftResultItem(
    item: SearchViewModel.SearchItem.AircraftResult,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onThumbnailClick: (eu.darken.apl.common.planespotters.PlanespottersMeta) -> Unit,
    onWatchClick: () -> Unit,
) {
    val aircraft = item.aircraft

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = if (isSelected) {
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            androidx.compose.material3.CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = aircraft.registration ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "| #${aircraft.hex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = aircraft.messageTypeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Thumbnail + info grid
            Row(modifier = Modifier.fillMaxWidth()) {
                PlanespottersThumbnail(
                    query = AircraftThumbnailQuery(hex = aircraft.hex, registration = aircraft.registration),
                    modifier = Modifier.weight(1f),
                    onImageClick = onThumbnailClick,
                )

                Spacer(Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InfoCell(
                            value = aircraft.callsign?.takeIf { it.isNotBlank() } ?: "?",
                            label = stringResource(R.string.common_callsign_label),
                            modifier = Modifier.weight(1f),
                        )
                        InfoCell(
                            value = aircraft.squawk ?: "?",
                            label = stringResource(R.string.common_squawk_label),
                            modifier = Modifier.weight(1f),
                            isAlert = aircraft.squawk?.startsWith("7") == true,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        InfoCell(
                            value = item.distanceInMeter?.let { "${(it / 1000).toInt()} km" } ?: "?",
                            label = stringResource(R.string.common_distance_label),
                            modifier = Modifier.weight(1f),
                        )
                        InfoCell(
                            value = aircraft.description ?: "?",
                            label = stringResource(R.string.common_airframe_label),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Watch ribbon
            if (item.watch != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onWatchClick, modifier = Modifier.align(Alignment.End)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_alarm_bell_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.watch_list_watch_edit_label),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
