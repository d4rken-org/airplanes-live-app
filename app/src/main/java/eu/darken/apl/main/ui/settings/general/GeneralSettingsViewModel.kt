package eu.darken.apl.main.ui.settings.general

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.WebpageTool
import eu.darken.apl.common.coroutine.DispatcherProvider
import eu.darken.apl.common.datastore.value
import eu.darken.apl.common.datastore.valueBlocking
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.combine
import eu.darken.apl.common.theming.ThemeColor
import eu.darken.apl.common.theming.ThemeMode
import eu.darken.apl.common.theming.ThemeStyle
import eu.darken.apl.common.uix.ViewModel4
import eu.darken.apl.main.core.GeneralSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    @Suppress("unused") private val handle: SavedStateHandle,
    private val dispatcherProvider: DispatcherProvider,
    private val generalSettings: GeneralSettings,
    private val webpageTool: WebpageTool,
    private val baseHttpClient: OkHttpClient,
) : ViewModel4(
    dispatcherProvider = dispatcherProvider,
    tag = logTag("Settings", "General", "VM"),
) {

    init {
        if (!generalSettings.airplanesLiveApiKey.valueBlocking.isNullOrBlank()) {
            validateApiKey()
        }
    }

    val state = combine(
        generalSettings.themeMode.flow,
        generalSettings.themeStyle.flow,
        generalSettings.themeColor.flow,
        generalSettings.isUpdateCheckEnabled.flow,
        flowOf(BuildConfigWrap.FLAVOR == BuildConfigWrap.Flavor.FOSS),
        generalSettings.airplanesLiveApiKey.flow,
        generalSettings.apiKeyValid,
    ) { themeMode, themeStyle, themeColor, isUpdateCheckEnabled, isUpdateCheckSupported, airplanesLiveApiKey, apiKeyValid ->
        State(
            themeMode = themeMode,
            themeStyle = themeStyle,
            themeColor = themeColor,
            isUpdateCheckEnabled = isUpdateCheckEnabled,
            isUpdateCheckSupported = isUpdateCheckSupported,
            airplanesLiveApiKey = airplanesLiveApiKey,
            apiKeyValid = apiKeyValid,
        )
    }.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = launch {
        log(tag) { "setThemeMode($mode)" }
        generalSettings.themeMode.value(mode)
    }

    fun setThemeStyle(style: ThemeStyle) = launch {
        log(tag) { "setThemeStyle($style)" }
        generalSettings.themeStyle.value(style)
    }

    fun setThemeColor(color: ThemeColor) = launch {
        log(tag) { "setThemeColor($color)" }
        generalSettings.themeColor.value(color)
    }

    fun toggleUpdateCheck() {
        log(tag) { "toggleUpdateCheck()" }
        generalSettings.isUpdateCheckEnabled.valueBlocking = !generalSettings.isUpdateCheckEnabled.valueBlocking
    }

    fun setAirplanesLiveApiKey(key: String?) = launch {
        log(tag) { "setAirplanesLiveApiKey(${if (key != null) "***" else "null"})" }
        generalSettings.apiKeyValid.value = null
        generalSettings.airplanesLiveApiKey.value(key?.takeIf { it.isNotBlank() })
        if (!key.isNullOrBlank()) validateApiKey()
    }

    fun requestAirplanesLiveApiKey() {
        webpageTool.open("https://airplanes.live/mobileapp/")
    }

    private fun validateApiKey() = launch {
        val key = generalSettings.airplanesLiveApiKey.valueBlocking ?: return@launch
        try {
            val request = Request.Builder().apply {
                url("https://rest.api.airplanes.live/?all")
                header("auth", key)
            }.build()
            val response = withContext(dispatcherProvider.IO) {
                baseHttpClient.newCall(request).execute()
            }
            response.use {
                generalSettings.apiKeyValid.value = it.code != 403
            }
        } catch (e: Exception) {
            log(tag) { "API key validation failed: ${e.message}" }
        }
    }

    enum class ApiKeyState { CHECKING, VALID, INVALID }

    data class State(
        val themeMode: ThemeMode,
        val themeStyle: ThemeStyle,
        val themeColor: ThemeColor,
        val isUpdateCheckEnabled: Boolean,
        val isUpdateCheckSupported: Boolean,
        val airplanesLiveApiKey: String? = null,
        val apiKeyValid: Boolean? = null,
    ) {
        val apiKeyState: ApiKeyState?
            get() = when {
                airplanesLiveApiKey.isNullOrBlank() -> null
                apiKeyValid == null -> ApiKeyState.CHECKING
                apiKeyValid == true -> ApiKeyState.VALID
                else -> ApiKeyState.INVALID
            }
    }
}
