package eu.darken.apl.server.api

import eu.darken.apl.common.BuildConfigWrap
import okhttp3.Request

/** The build identification the server expects on every request sent to it. */
fun Request.Builder.appHeaders(): Request.Builder = this
    .header("X-App-Version", BuildConfigWrap.VERSION_CODE.toString())
    .header("X-App-Variant", ServerVariant.CURRENT)
