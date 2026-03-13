package eu.darken.apl.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@PlayStoreLocales
@Composable
fun Map() = MapContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Search() = SearchContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Watch() = WatchContent()

@PreviewTest
@PlayStoreLocales
@Composable
fun Feeders() = FeederContent()
