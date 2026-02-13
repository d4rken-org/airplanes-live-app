package eu.darken.apl.map.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import coil.imageLoader
import coil.request.ImageRequest
import eu.darken.apl.common.flight.FlightRoute
import eu.darken.apl.common.planespotters.PlanespottersMeta
import eu.darken.apl.common.planespotters.coil.PlanespottersImage
import eu.darken.apl.databinding.MapAircraftDetailsSheetBinding
import eu.darken.apl.map.core.MapAircraftDetails

class MapAircraftDetailsSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val ui = MapAircraftDetailsSheetBinding.inflate(LayoutInflater.from(context), this)

    var onCloseClicked: (() -> Unit)? = null
    var onCopyLinkClicked: ((String) -> Unit)? = null
    var onShowInSearchClicked: ((String) -> Unit)? = null
    var onAddWatchClicked: ((String) -> Unit)? = null
    var onThumbnailClicked: ((String) -> Unit)? = null

    var currentHex: String? = null
        private set

    init {
        ui.closeButton.setOnClickListener { onCloseClicked?.invoke() }
        ui.copyLinkButton.setOnClickListener { currentHex?.let { hex -> onCopyLinkClicked?.invoke(hex) } }
        ui.showInSearchAction.setOnClickListener { currentHex?.let { hex -> onShowInSearchClicked?.invoke(hex) } }
        ui.addWatchAction.setOnClickListener { currentHex?.let { hex -> onAddWatchClicked?.invoke(hex) } }
        ui.aircraftThumbnail.onViewImageListener = { meta -> onThumbnailClicked?.invoke(meta.link) }
    }

    fun setAircraftDetails(details: MapAircraftDetails?) {
        if (details == null) return
        currentHex = details.hex

        // Peek area
        ui.callsignValue.text = details.callsign ?: details.hex
        ui.hexValue.text = "#${details.hex}"

        val subtitleParts = listOfNotNull(details.registration, details.icaoType, details.country)
        ui.subtitleValue.setTextOrHide(subtitleParts.joinToString(" \u00b7 ").ifBlank { null })

        ui.typeValue.setTextOrHide(
            listOfNotNull(details.typeLong, details.typeDesc?.let { "($it)" }).joinToString(" ").ifBlank { null }
        )
        ui.operatorValue.setTextOrHide(details.operator)

        // Key info section
        setCellOrHide(ui.cellAltitude, ui.altitudeValue, details.altitude)
        setCellOrHide(ui.cellSpeed, ui.speedValue, details.speed)
        setRowVisibility(ui.rowAltitudeSpeed, ui.cellAltitude, ui.cellSpeed)

        setCellOrHide(ui.cellSquawk, ui.squawkValue, details.squawk)
        setCellOrHide(ui.cellTrack, ui.trackValue, details.track)
        setRowVisibility(ui.rowSquawkTrack, ui.cellSquawk, ui.cellTrack)

        setCellOrHide(ui.cellVertRate, ui.vertRateValue, details.vertRate)
        setCellOrHide(ui.cellPosition, ui.positionValue, details.position)
        setRowVisibility(ui.rowVratePos, ui.cellVertRate, ui.cellPosition)

        ui.dividerKey.setVisibleIf(ui.rowAltitudeSpeed, ui.rowSquawkTrack, ui.rowVratePos)

        // Navigation section
        setCellOrHide(ui.cellNavAltitude, ui.navAltitudeValue, details.navAltitude)
        setCellOrHide(ui.cellNavHeading, ui.navHeadingValue, details.navHeading)
        setRowVisibility(ui.rowNavAltHdg, ui.cellNavAltitude, ui.cellNavHeading)

        setCellOrHide(ui.cellNavModes, ui.navModesValue, details.navModes)
        setCellOrHide(ui.cellNavQnh, ui.navQnhValue, details.navQnh)
        setRowVisibility(ui.rowNavModesQnh, ui.cellNavModes, ui.cellNavQnh)

        ui.dividerNav.setVisibleIf(ui.rowNavAltHdg, ui.rowNavModesQnh)

        // Speed / Rates section
        setCellOrHide(ui.cellTas, ui.tasValue, details.tas)
        setCellOrHide(ui.cellIas, ui.iasValue, details.ias)
        setRowVisibility(ui.rowTasIas, ui.cellTas, ui.cellIas)

        setCellOrHide(ui.cellMach, ui.machValue, details.mach)
        setCellOrHide(ui.cellBaroRate, ui.baroRateValue, details.baroRate)
        setRowVisibility(ui.rowMachBaro, ui.cellMach, ui.cellBaroRate)

        setCellOrHide(ui.cellGeomRate, ui.geomRateValue, details.geomRate)
        setCellOrHide(ui.cellWindSpeed, ui.windSpeedValue, details.windSpeed?.let { ws ->
            details.windDir?.let { wd -> "$ws / $wd" } ?: ws
        })
        setRowVisibility(ui.rowGeomWind, ui.cellGeomRate, ui.cellWindSpeed)

        setCellOrHide(ui.cellTemp, ui.tempValue, details.temp)
        setRowVisibility(ui.rowTemp, ui.cellTemp)

        ui.dividerSpeed.setVisibleIf(ui.rowTasIas, ui.rowMachBaro, ui.rowGeomWind, ui.rowTemp)

        // Signal section
        setCellOrHide(ui.cellSource, ui.sourceValue, details.source)
        setCellOrHide(ui.cellRssi, ui.rssiValue, details.rssi)
        setRowVisibility(ui.rowSourceRssi, ui.cellSource, ui.cellRssi)

        setCellOrHide(ui.cellMsgRate, ui.messageRateValue, details.messageRate)
        setCellOrHide(ui.cellMessages, ui.messageCountValue, details.messageCount)
        setRowVisibility(ui.rowMsgrateMsgs, ui.cellMsgRate, ui.cellMessages)

        setCellOrHide(ui.cellSeen, ui.seenValue, details.seen)
        setCellOrHide(ui.cellAdsbVersion, ui.adsVersionValue, details.adsVersion)
        setRowVisibility(ui.rowSeenAdsb, ui.cellSeen, ui.cellAdsbVersion)

        setCellOrHide(ui.cellCategory, ui.categoryValue, details.category)
        setCellOrHide(ui.cellFlags, ui.dbFlagsValue, details.dbFlags)
        setRowVisibility(ui.rowCategoryFlags, ui.cellCategory, ui.cellFlags)

        ui.dividerSignal.setVisibleIf(ui.rowSourceRssi, ui.rowMsgrateMsgs, ui.rowSeenAdsb, ui.rowCategoryFlags)

        loadPhoto(details)
    }

    private fun loadPhoto(details: MapAircraftDetails) {
        val photoUrl = details.photoUrl
        if (photoUrl.isNullOrBlank()) {
            ui.aircraftThumbnail.setImage(null)
            ui.aircraftPhotoRow.visibility = View.GONE
            return
        }

        ui.photoAltitudeValue.setTextOrHide(details.altitude)
        ui.photoAltitudeLabel.visibility = ui.photoAltitudeValue.visibility
        ui.photoSpeedValue.setTextOrHide(details.speed)
        ui.photoSpeedLabel.visibility = ui.photoSpeedValue.visibility
        ui.photoSquawkValue.setTextOrHide(details.squawk)
        ui.photoSquawkLabel.visibility = ui.photoSquawkValue.visibility

        // Skip redundant loads from polling updates
        val thumbnailTag = ui.aircraftThumbnail.tag
        if (thumbnailTag == photoUrl) return
        ui.aircraftThumbnail.tag = photoUrl

        // Clear previous image
        ui.aircraftThumbnail.setImage(null)
        ui.aircraftPhotoRow.visibility = View.VISIBLE

        val hex = details.hex
        val meta = PlanespottersMeta(
            hex = hex,
            author = details.photoCredit,
            link = "https://www.planespotters.net/hex/$hex",
        )

        val request = ImageRequest.Builder(context)
            .data(photoUrl)
            .target(
                onSuccess = { drawable ->
                    if (currentHex == hex) {
                        ui.aircraftThumbnail.setImage(PlanespottersImage(drawable, meta))
                    }
                },
                onError = {
                    if (currentHex == hex) {
                        ui.aircraftThumbnail.setImage(null)
                        ui.aircraftPhotoRow.visibility = View.GONE
                    }
                }
            )
            .build()

        context.imageLoader.enqueue(request)
    }

    fun setRoute(route: FlightRoute?) {
        ui.routeDisplay.setRoute(route)
    }

    private fun setCellOrHide(cell: View, valueView: TextView, value: String?) {
        if (value.isNullOrBlank()) {
            cell.visibility = View.GONE
        } else {
            cell.visibility = View.VISIBLE
            valueView.text = value
        }
    }

    private fun setRowVisibility(row: LinearLayout, vararg cells: View) {
        row.visibility = if (cells.any { it.visibility == View.VISIBLE }) View.VISIBLE else View.GONE
    }

    private fun View.setVisibleIf(vararg rows: View) {
        visibility = if (rows.any { it.visibility == View.VISIBLE }) View.VISIBLE else View.GONE
    }

    private fun TextView.setTextOrHide(value: String?) {
        if (value.isNullOrBlank()) {
            visibility = View.GONE
        } else {
            visibility = View.VISIBLE
            text = value
        }
    }
}
