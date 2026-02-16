package eu.darken.apl.feeder.ui.actions

import eu.darken.apl.feeder.core.Feeder
import eu.darken.apl.feeder.core.ReceiverId
import eu.darken.apl.feeder.ui.add.NewFeederQR

sealed class FeederActionEvents {
    data class Rename(val feeder: Feeder) : FeederActionEvents()
    data class ChangeIpAddress(val feeder: Feeder) : FeederActionEvents()
    data class RemovalConfirmation(val id: ReceiverId) : FeederActionEvents()
    data class ShowQrCode(val qr: NewFeederQR) : FeederActionEvents()
}
