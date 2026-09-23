package eu.darken.apl.upgrade.ui

sealed class FeederRegisterEvents {
    data class FeedersFound(val count: Int, val host: String?) : FeederRegisterEvents()
}
