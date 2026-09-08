package eu.darken.apl.main.core

import eu.darken.apl.common.coroutine.AppScope
import eu.darken.apl.common.debug.logging.log
import eu.darken.apl.common.debug.logging.logTag
import eu.darken.apl.common.flow.replayingShare
import eu.darken.apl.main.core.aircraft.Aircraft
import eu.darken.apl.main.core.aircraft.AircraftHex
import eu.darken.apl.main.core.db.AircraftDatabase
import eu.darken.apl.main.core.db.toAircraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AircraftRepo @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val aircraftDatabase: AircraftDatabase,
) {

    val aircraft: Flow<Map<AircraftHex, Aircraft>> = aircraftDatabase.current()
        .map { acs -> acs.map { it.toAircraft() }.associateBy { it.hex } }
        .replayingShare(appScope)

    suspend fun update(toUpdate: Collection<Aircraft>) {
        log(TAG) { "update(aircraft=${toUpdate.size})" }
        val before = aircraft.first().size
        aircraftDatabase.update(toUpdate)
        val after = aircraft.first().size
        log(TAG) { "Aircraft cache updated (before=${before}, after=${after})" }
    }

    companion object {
        private val TAG = logTag("Aircraft", "Repo")
    }
}