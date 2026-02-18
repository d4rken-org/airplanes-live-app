package eu.darken.apl.common.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable

@Composable
fun aplContentWindowInsets(hasBottomNav: Boolean = false): WindowInsets =
    if (hasBottomNav) ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars)
    else ScaffoldDefaults.contentWindowInsets
