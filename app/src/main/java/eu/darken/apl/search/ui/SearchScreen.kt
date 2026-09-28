package eu.darken.apl.search.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Clear
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Place
import androidx.compose.material.icons.twotone.Map
import androidx.compose.material.icons.twotone.MyLocation
import androidx.compose.material.icons.twotone.NotificationsActive
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.LoadingBox
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.search.core.SearchRepo
import eu.darken.apl.server.api.ServerApiException
import eu.darken.apl.server.api.ServerCodes
import eu.darken.apl.upgrade.ui.UpgradeBanner
import eu.darken.apl.common.planespotters.PlanespottersThumbnail
import eu.darken.apl.common.planespotters.coil.AircraftThumbnailQuery
import eu.darken.apl.common.compose.Preview2
import eu.darken.apl.common.compose.PreviewWrapper
import eu.darken.apl.common.compose.preview.FakeAircraft
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.isEmergencySquawk
import eu.darken.apl.main.core.aircraft.messageTypeLabel
import eu.darken.apl.main.ui.settings.DestinationGeneralSettings
import eu.darken.apl.watch.ui.preview.mockAircraftWatch

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
    ) { granted -> vm.onLocationPermissionResult(granted) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                SearchEvents.RequestLocationPermission -> {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }

                SearchEvents.LocationUnavailable -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.search_nearby_location_unavailable))
                }

                SearchEvents.PlaceSearchUnavailable -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.search_nearby_geocoder_unavailable))
                }

                is SearchEvents.PlaceNotFound -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.search_nearby_place_not_found, event.place))
                }
            }
        }
    }

    val state by vm.state.collectAsState(initial = null)

    // The result keeps its error across tab switches, claiming it shows the snackbar only once
    val searchError = state?.error
    LaunchedEffect(searchError) {
        if (searchError == null || !vm.claimError(searchError.error)) return@LaunchedEffect
        val apiError = searchError.error as? ServerApiException
        // The nearby lookup reports a used up allowance as a 429 too
        val isExhausted = apiError?.code == ServerCodes.DAILY_ALLOWANCE_EXHAUSTED
        val isRateLimited = !isExhausted && apiError?.status == 429
        val errorDetail = when (val error = searchError.error) {
            is ServerApiException -> error.code
            else -> error.message?.take(80) ?: error::class.simpleName ?: "Unknown"
        }
        val message = when {
            isExhausted -> context.getString(
                when (searchError.charged) {
                    SearchRepo.Charged.VIEWING -> R.string.search_banner_exhausted_title_viewing
                    SearchRepo.Charged.SEARCH -> R.string.search_banner_exhausted_title
                }
            )

            isRateLimited -> context.getString(R.string.search_error_rate_limited)
            else -> context.getString(R.string.search_error_generic, errorDetail)
        }
        snackbarHostState.showSnackbar(
            message = message,
            duration = if (isRateLimited || isExhausted) SnackbarDuration.Long else SnackbarDuration.Short,
        )
    }

    state?.let {
        SearchScreen(
            state = it,
            snackbarHostState = snackbarHostState,
            onClear = vm::clearSearch,
            onSubmit = vm::submitCurrent,
            draft = vm.draft,
            onDraftChange = vm::updateDraft,
            screenState = vm::screenState,
            onSelectionChange = vm::saveSelection,
            onGridPositionChange = vm::saveGridPosition,
            onToggleCategoryChip = vm::toggleCategoryChip,
            onToggleNearby = vm::toggleNearby,
            onNearbyPlace = vm::setNearbyPlace,
            onAircraftClick = { ac -> vm.openAircraftAction(ac.hex) },
            onThumbnailClick = { meta -> vm.openThumbnail(meta.link) },
            onWatchClick = { watch -> vm.openWatch(watch) },
            onShowOnMap = { aircraft -> vm.showOnMap(aircraft) },
            onGrantLocation = vm::requestLocationPermission,
            onDismissLocation = vm::dismissLocationPrompt,
            onStartFeeding = vm::startFeeding,
            onUpgrade = vm::goUpgrade,
        )
    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBox()
    }
}

@Composable
fun SearchScreen(
    state: SearchViewModel.State,
    snackbarHostState: SnackbarHostState,
    onClear: () -> Unit,
    onSubmit: (String) -> Unit,
    onToggleCategoryChip: (SearchViewModel.CategoryChip) -> Unit,
    onToggleNearby: () -> Unit,
    onNearbyPlace: (String?) -> Unit,
    onAircraftClick: (Aircraft) -> Unit,
    onThumbnailClick: (eu.darken.apl.common.planespotters.PlanespottersMeta) -> Unit,
    onWatchClick: (eu.darken.apl.watch.core.types.Watch) -> Unit,
    onShowOnMap: (Collection<Aircraft>) -> Unit,
    onGrantLocation: () -> Unit,
    onDismissLocation: () -> Unit,
    onStartFeeding: () -> Unit,
    onUpgrade: () -> Unit = {},
    draft: SearchSession.Draft? = null,
    onDraftChange: (base: String, text: String) -> Unit = { _, _ -> },
    screenState: (revision: Int) -> SearchSession.ScreenState = { SearchSession.ScreenState(it) },
    onSelectionChange: (revision: Int, Set<String>) -> Unit = { _, _ -> },
    onGridPositionChange: (revision: Int, SearchSession.GridPosition) -> Unit = { _, _ -> },
) {
    // Scroll and selection belong to one result, a new result starts at the top with nothing selected
    val revision = state.resultRevision
    var selectedHexes by remember(revision) { mutableStateOf(screenState(revision).selection) }
    val isSelectionMode = selectedHexes.isNotEmpty()
    LaunchedEffect(revision, selectedHexes) { onSelectionChange(revision, selectedHexes) }

    val savedGridPosition = remember { screenState(revision).gridPosition }
    val gridState = rememberLazyStaggeredGridState(
        initialFirstVisibleItemIndex = savedGridPosition.index,
        initialFirstVisibleItemScrollOffset = savedGridPosition.offset,
    )
    var gridRevision by remember { mutableStateOf(revision) }
    LaunchedEffect(revision) {
        if (revision == gridRevision) return@LaunchedEffect
        gridState.scrollToItem(0)
        gridRevision = revision
    }
    val currentGridRevision by rememberUpdatedState(gridRevision)
    DisposableEffect(gridState) {
        onDispose {
            onGridPositionChange(
                currentGridRevision,
                SearchSession.GridPosition(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset),
            )
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    var searchText by remember(state.input.text) {
        mutableStateOf(draft?.takeIf { it.base == state.input.text }?.text ?: state.input.text)
    }
    // What the input holds once a pending clear lands, text typed meanwhile is a draft against that
    var draftBase by remember(state.input.text) { mutableStateOf(state.input.text) }
    val clear = {
        searchText = ""
        draftBase = ""
        selectedHexes = emptySet()
        onDraftChange("", "")
        onClear()
    }
    var showPlaceDialog by remember { mutableStateOf(false) }

    if (showPlaceDialog) {
        NearbyPlaceDialog(
            place = state.input.place,
            onPlace = { place ->
                onNearbyPlace(place)
                showPlaceDialog = false
            },
            onDismiss = { showPlaceDialog = false },
        )
    }

    val panelScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    // The panel changes height with Nearby, bring it fully back so the change is visible
    LaunchedEffect(state.input.nearby) { panelScrollBehavior.scrollOffset = 0f }

    Scaffold(
        contentWindowInsets = aplContentWindowInsets(hasBottomNav = true),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedHexes.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedHexes = emptySet() }) {
                            Icon(Icons.TwoTone.Close, contentDescription = null)
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
                            Icon(Icons.TwoTone.Map, contentDescription = stringResource(R.string.common_show_on_map_action))
                        }
                    },
                )
            } else {
                SearchPanel(
                    state = state,
                    searchText = searchText,
                    onSearchTextChange = {
                        // A category or nearby search has no text of its own, blanking the field keeps its results
                        if (it.isBlank() && state.input.text.isNotBlank()) {
                            clear()
                        } else {
                            searchText = it
                            onDraftChange(draftBase, it)
                        }
                    },
                    onClear = clear,
                    onSubmit = {
                        onSubmit(searchText)
                        keyboardController?.hide()
                    },
                    onToggleCategoryChip = onToggleCategoryChip,
                    onToggleNearby = onToggleNearby,
                    onChangePlace = { showPlaceDialog = true },
                    modifier = with(panelScrollBehavior) { Modifier.searchBarScrollBehavior() },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val gridColumns = (maxWidth / 350.dp).toInt().coerceIn(1, 3)
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(gridColumns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    // Selection mode swaps the panel for a toolbar, the hidden panel must not eat the scroll
                    .then(if (isSelectionMode) Modifier else Modifier.nestedScroll(panelScrollBehavior.nestedScrollConnection)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            items(
                items = state.items,
                key = { item ->
                    when (item) {
                        is SearchViewModel.SearchItem.LocationPrompt -> "location_prompt"
                        is SearchViewModel.SearchItem.Hint -> "hint"
                        is SearchViewModel.SearchItem.Searching -> "searching"
                        is SearchViewModel.SearchItem.NoResults -> "no_results"
                        is SearchViewModel.SearchItem.Summary -> "summary"
                        is SearchViewModel.SearchItem.TermStatus -> "term_status_${item.term}"
                        is SearchViewModel.SearchItem.UpgradeBanner -> "upgrade_banner"
                        is SearchViewModel.SearchItem.AircraftResult -> item.aircraft.hex
                    }
                },
                span = { item ->
                    if (item is SearchViewModel.SearchItem.AircraftResult) {
                        StaggeredGridItemSpan.SingleLane
                    } else {
                        StaggeredGridItemSpan.FullLine
                    }
                },
            ) { item ->
                when (item) {
                    is SearchViewModel.SearchItem.LocationPrompt -> LocationPromptItem(
                        onGrant = onGrantLocation,
                        onDismiss = onDismissLocation,
                    )

                    is SearchViewModel.SearchItem.Hint -> HintItem(onExample = { example ->
                        searchText = example
                        onSubmit(example)
                    })

                    is SearchViewModel.SearchItem.Searching -> SearchingItem(
                        aircraftCount = item.aircraftCount,
                    )

                    is SearchViewModel.SearchItem.NoResults -> NoResultsItem(
                        onStartFeeding = onStartFeeding,
                    )

                    is SearchViewModel.SearchItem.Summary -> SummaryItem(
                        aircraftCount = item.aircraftCount,
                        cacheOnlyCount = item.cacheOnlyCount,
                        totalMatching = item.totalMatching,
                    )

                    is SearchViewModel.SearchItem.TermStatus -> TermStatusItem(item = item)

                    is SearchViewModel.SearchItem.UpgradeBanner -> SearchUpgradeBanner(
                        state = item.state,
                        charged = item.charged,
                        nowMillis = state.nowMillis,
                        onUpgrade = onUpgrade,
                    )

                    is SearchViewModel.SearchItem.AircraftResult -> AircraftResultItem(
                        item = item,
                        nowMillis = state.nowMillis,
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
            .padding(vertical = 8.dp),
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
private fun SearchUpgradeBanner(
    state: SearchViewModel.BannerState,
    charged: SearchRepo.Charged,
    nowMillis: Long,
    onUpgrade: () -> Unit,
) {
    // A nearby query spends the viewing allowance, so its numbers are lookups, not searches
    val viewing = charged == SearchRepo.Charged.VIEWING
    val title = when (state) {
        is SearchViewModel.BannerState.Capped -> stringResource(
            R.string.search_banner_capped_title,
            state.shown,
            state.total,
        )

        SearchViewModel.BannerState.CappedUnknown ->
            stringResource(R.string.search_banner_capped_title_unknown)

        is SearchViewModel.BannerState.Exhausted -> stringResource(
            if (viewing) R.string.search_banner_exhausted_title_viewing
            else R.string.search_banner_exhausted_title
        )

        is SearchViewModel.BannerState.Remaining -> stringResource(
            if (viewing) R.string.search_banner_remaining_title_viewing
            else R.string.search_banner_remaining_title,
            state.remaining,
            state.limit,
        )
    }
    val body = when (state) {
        is SearchViewModel.BannerState.Capped,
        SearchViewModel.BannerState.CappedUnknown -> stringResource(
            if (viewing) R.string.search_banner_capped_msg_viewing
            else R.string.search_banner_capped_msg
        )

        is SearchViewModel.BannerState.Exhausted -> when (val resetsAt = state.resetsAt) {
            null -> stringResource(R.string.search_banner_exhausted_msg)
            else -> stringResource(
                R.string.search_banner_exhausted_msg_x,
                // Server time: the reset instant is the server's, the device clock may be off
                DateUtils.getRelativeTimeSpanString(
                    resetsAt.toEpochMilli(),
                    nowMillis,
                    DateUtils.MINUTE_IN_MILLIS,
                ),
            )
        }

        is SearchViewModel.BannerState.Remaining -> stringResource(
            if (viewing) R.string.search_banner_remaining_msg_viewing
            else R.string.search_banner_remaining_msg
        )
    }
    UpgradeBanner(title = title, body = body, onClick = onUpgrade)
}

@Composable
private fun TermStatusItem(item: SearchViewModel.SearchItem.TermStatus) {
    val text = when (val state = item.state) {
        is SearchViewModel.TermState.Capped -> state.totalMatching
            ?.let { stringResource(R.string.search_term_capped_x, item.term, it) }
            ?: stringResource(R.string.search_term_capped, item.term)

        is SearchViewModel.TermState.Exhausted -> stringResource(R.string.search_term_exhausted_x, item.term)
        SearchViewModel.TermState.Invalid -> stringResource(R.string.search_term_invalid_x, item.term)
        SearchViewModel.TermState.Restricted -> stringResource(R.string.search_term_restricted_x, item.term)
        SearchViewModel.TermState.Expired -> stringResource(R.string.search_term_expired_x, item.term)
        SearchViewModel.TermState.Incomplete -> stringResource(R.string.search_term_incomplete_x, item.term)
        SearchViewModel.TermState.Stale -> stringResource(R.string.search_term_stale_x, item.term)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun NearbyPlaceRow(place: String?, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.TwoTone.Place,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.search_nearby_near_label,
                place ?: stringResource(R.string.search_nearby_my_location_label),
            ),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        TextButton(onClick = onChange) {
            Text(stringResource(R.string.search_nearby_change_action))
        }
    }
}

@Composable
private fun SearchPanel(
    state: SearchViewModel.State,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onToggleCategoryChip: (SearchViewModel.CategoryChip) -> Unit,
    onToggleNearby: () -> Unit,
    onChangePlace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                aplContentWindowInsets(hasBottomNav = true).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            ),
    ) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    TextField(
                        value = searchText,
                        onValueChange = onSearchTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_input_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            IconButton(onClick = onSubmit) {
                                Icon(
                                    Icons.TwoTone.Search,
                                    contentDescription = stringResource(R.string.search_submit_action),
                                )
                            }
                        },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = onClear) {
                                    Icon(Icons.TwoTone.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        colors = TextFieldDefaults.colors(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                        ),
                    )
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SearchViewModel.CategoryChip.entries.forEach { chip ->
                                FilterChip(
                                    selected = state.input.categories.any { it in chip.categories },
                                    onClick = { onToggleCategoryChip(chip) },
                                    label = {
                                        Text(
                                            text = when (chip) {
                                                SearchViewModel.CategoryChip.MILITARY -> stringResource(R.string.search_category_military_label)
                                                SearchViewModel.CategoryChip.PRIVACY -> stringResource(R.string.search_category_privacy_label)
                                            },
                                        )
                                    },
                                )
                            }
                            FilterChip(
                                selected = state.input.nearby,
                                onClick = onToggleNearby,
                                leadingIcon = {
                                    Icon(Icons.TwoTone.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                label = { Text(stringResource(R.string.search_nearby_label)) },
                            )
                        }
                    }
                    AnimatedVisibility(visible = state.input.nearby) {
                        Box(modifier = Modifier.padding(start = 12.dp, end = 4.dp)) {
                            NearbyPlaceRow(
                                place = state.input.place,
                                onChange = onChangePlace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintItem(onExample: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.TwoTone.TipsAndUpdates,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.search_hint_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = stringResource(R.string.search_hint_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.search_hint_examples_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SEARCH_EXAMPLES.forEach { example ->
                        SuggestionChip(
                            onClick = { onExample(example) },
                            label = { Text(example) },
                        )
                    }
                }
            }
        }
    }
}

private val RESULT_PHOTO_WIDTH = 132.dp

private val SEARCH_EXAMPLES = listOf("DLH A320", "D-AIBL", "3C6589", "A320", "7700")

@Composable
private fun NearbyPlaceDialog(
    place: String?,
    onPlace: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(place ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_nearby_place_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.search_mode_location_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onPlace(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onPlace(null) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.TwoTone.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.search_nearby_use_my_location_action))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPlace(text) }) {
                Text(stringResource(R.string.common_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel_action))
            }
        },
    )
}

@Composable
private fun SearchingItem(aircraftCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = pluralStringResource(R.plurals.search_progress_body, aircraftCount, aircraftCount),
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
private fun SummaryItem(aircraftCount: Int, cacheOnlyCount: Int = 0, totalMatching: Int? = null) {
    Text(
        // A capped answer's headline is how many matched, not how many rows came back, so the
        // cached-extras note would be counting a different set
        text = if (totalMatching != null) {
            pluralStringResource(R.plurals.search_summary_x_aircraft, totalMatching, totalMatching)
        } else if (cacheOnlyCount > 0) {
            pluralStringResource(R.plurals.search_summary_x_aircraft_y_cached, aircraftCount, aircraftCount, cacheOnlyCount)
        } else {
            pluralStringResource(R.plurals.search_summary_x_aircraft, aircraftCount, aircraftCount)
        },
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun AircraftResultItem(
    item: SearchViewModel.SearchItem.AircraftResult,
    nowMillis: Long,
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
            .padding(vertical = 4.dp)
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
        // The fixed grey is made for the plain card, on the selected tint it would all but vanish
        val secondaryColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        // At least the photo's 3:2 height, taller when the text needs it; the photo then crops to fill
        Row(
            modifier = Modifier
                .heightIn(min = RESULT_PHOTO_WIDTH / 1.5f)
                .height(IntrinsicSize.Min),
        ) {
            // The card's corners clip the photo, it runs edge to edge
            PlanespottersThumbnail(
                query = AircraftThumbnailQuery(hex = aircraft.hex, registration = aircraft.registration),
                modifier = Modifier
                    .width(RESULT_PHOTO_WIDTH)
                    .fillMaxHeight(),
                shape = RectangleShape,
                aspectRatio = null,
                onImageClick = onThumbnailClick,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = aircraft.registration ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = aircraft.messageTypeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    if (item.watch != null) {
                        Icon(
                            imageVector = Icons.TwoTone.NotificationsActive,
                            contentDescription = stringResource(R.string.watch_list_watch_edit_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onWatchClick)
                                .padding(4.dp)
                                .size(16.dp),
                        )
                    }
                }

                Text(
                    text = listOfNotNull(
                        aircraft.callsign?.takeIf { it.isNotBlank() },
                        "#${aircraft.hex}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                aircraft.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val distance = item.distanceInMeter?.let { "${(it / 1000).toInt()} km" }
                val squawk = aircraft.squawk
                if (distance != null || squawk != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val style = MaterialTheme.typography.bodySmall
                        val color = secondaryColor
                        if (distance != null) Text(distance, style = style, color = color)
                        if (distance != null && squawk != null) Text(" · ", style = style, color = color)
                        if (squawk != null) {
                            Text(
                                text = stringResource(R.string.common_squawk_label) + " ",
                                style = style,
                                color = color,
                            )
                            if (aircraft.isEmergencySquawk) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ) {
                                    Text(
                                        text = squawk,
                                        style = style,
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            } else {
                                Text(squawk, style = style, color = color)
                            }
                        }
                    }
                }

                val messageSeenAt = aircraft.messageSeenAt
                val showFreshness = item.freshness != SearchViewModel.Freshness.LIVE && messageSeenAt != null
                if (showFreshness || item.cacheOnly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showFreshness) {
                            val relativeTime = DateUtils.getRelativeTimeSpanString(
                                messageSeenAt!!.toEpochMilli(),
                                nowMillis,
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString()
                            val freshnessColor = when {
                                item.freshness == SearchViewModel.Freshness.OLD -> MaterialTheme.colorScheme.error
                                isSelected -> secondaryColor
                                item.freshness == SearchViewModel.Freshness.STALE -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            }
                            val lastSeenDescription =
                                stringResource(R.string.search_aircraft_last_seen_description, relativeTime)
                            Text(
                                text = relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = freshnessColor,
                                modifier = Modifier.semantics { contentDescription = lastSeenDescription },
                            )
                        }
                        if (showFreshness && item.cacheOnly) {
                            Text(
                                text = " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryColor,
                            )
                        }
                        if (item.cacheOnly) {
                            Text(
                                text = stringResource(R.string.search_result_cached_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview2
@Composable
private fun LocationPromptItemPreview() {
    PreviewWrapper { LocationPromptItem(onGrant = {}, onDismiss = {}) }
}

@Preview2
@Composable
private fun NearbyPlaceRowPreview() {
    PreviewWrapper { NearbyPlaceRow(place = "Frankfurt am Main", onChange = {}) }
}

@Preview2
@Composable
private fun HintItemPreview() {
    PreviewWrapper { HintItem(onExample = {}) }
}

@Preview2
@Composable
private fun NearbyPlaceDialogPreview() {
    PreviewWrapper { NearbyPlaceDialog(place = "Frankfurt am Main", onPlace = {}, onDismiss = {}) }
}

@Preview2
@Composable
private fun SearchingItemPreview() {
    PreviewWrapper { SearchingItem(aircraftCount = 42) }
}

@Preview2
@Composable
private fun NoResultsItemPreview() {
    PreviewWrapper { NoResultsItem(onStartFeeding = {}) }
}

@Preview2
@Composable
private fun SummaryItemPreview() {
    PreviewWrapper { SummaryItem(aircraftCount = 15) }
}

@Preview2
@Composable
private fun AircraftResultItemPreview() {
    PreviewWrapper {
        AircraftResultItem(
            item = SearchViewModel.SearchItem.AircraftResult(
                aircraft = FakeAircraft(),
                watch = null,
                distanceInMeter = 52_000f,
            ),
            nowMillis = System.currentTimeMillis(),
            isSelected = false,
            onClick = {},
            onLongClick = {},
            onThumbnailClick = {},
            onWatchClick = {},
        )
    }
}

@Preview2
@Composable
private fun AircraftResultItemSelectedPreview() {
    PreviewWrapper {
        AircraftResultItem(
            item = SearchViewModel.SearchItem.AircraftResult(
                aircraft = FakeAircraft(),
                watch = mockAircraftWatch(),
                distanceInMeter = null,
            ),
            nowMillis = System.currentTimeMillis(),
            isSelected = true,
            onClick = {},
            onLongClick = {},
            onThumbnailClick = {},
            onWatchClick = {},
        )
    }
}
