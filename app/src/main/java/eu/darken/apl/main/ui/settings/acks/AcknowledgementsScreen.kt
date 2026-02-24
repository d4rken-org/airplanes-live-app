package eu.darken.apl.main.ui.settings.acks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.apl.R
import eu.darken.apl.common.compose.aplContentWindowInsets
import eu.darken.apl.common.error.ErrorEventHandler
import eu.darken.apl.common.navigation.NavigationEventHandler
import eu.darken.apl.common.settings.SettingsCategoryHeader
import eu.darken.apl.common.settings.SettingsPreferenceItem

@Composable
fun AcknowledgementsScreenHost(
    vm: AcknowledgementsViewModel = hiltViewModel(),
) {
    NavigationEventHandler(vm)
    ErrorEventHandler(vm)

    AcknowledgementsScreen(
        onBack = { vm.navUp() },
        onOpenUrl = { vm.openUrl(it) },
    )
}

@Composable
fun AcknowledgementsScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Scaffold(
        contentWindowInsets = aplContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_acknowledgements_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            item {
                SettingsPreferenceItem(
                    title = "airplanes.live",
                    summary = stringResource(R.string.acks_airplanes_live_desc),
                    icon = Icons.TwoTone.Public,
                    onClick = { onOpenUrl("https://airplanes.live/") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "planespotters.net",
                    summary = stringResource(R.string.acks_planespotters_desc),
                    icon = Icons.TwoTone.Public,
                    onClick = { onOpenUrl("https://www.planespotters.net/") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "adsbdb.com",
                    summary = stringResource(R.string.acks_route_data_desc),
                    icon = Icons.TwoTone.Public,
                    onClick = { onOpenUrl("https://www.adsbdb.com/") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "hexdb.io",
                    summary = stringResource(R.string.acks_route_data_desc),
                    icon = Icons.TwoTone.Public,
                    onClick = { onOpenUrl("https://hexdb.io/") },
                )
            }

            item { SettingsCategoryHeader(title = stringResource(R.string.settings_licenses_label)) }

            item {
                SettingsPreferenceItem(
                    title = "SemVer",
                    summary = "Kotlin data class for Semantic Versioning 2.0.0 specification (SemVer) (MIT)",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://github.com/swiftzer/semver") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "Material Design Icons",
                    summary = "materialdesignicons.com (SIL Open Font License 1.1 / Attribution 4.0 International)",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://github.com/Templarian/MaterialDesign") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "Lottie",
                    summary = "Airbnb's Lottie for Android. (APACHE 2.0)",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://github.com/airbnb/lottie-android") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "Kotlin",
                    summary = "The Kotlin Programming Language. (APACHE 2.0)",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://github.com/JetBrains/kotlin") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "Dagger",
                    summary = "A fast dependency injector for Android and Java. (APACHE 2.0)",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://github.com/google/dagger") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "Android",
                    summary = "Android Open Source Project (APACHE 2.0)",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://source.android.com/source/licenses.html") },
                )
            }
            item {
                SettingsPreferenceItem(
                    title = "Android",
                    summary = "The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.",
                    icon = Icons.AutoMirrored.TwoTone.Article,
                    onClick = { onOpenUrl("https://developer.android.com/distribute/tools/promote/brand.html") },
                )
            }
        }
    }
}
