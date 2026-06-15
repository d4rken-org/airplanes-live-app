package eu.darken.apl.backup.core

import eu.darken.apl.common.serialization.SerializationModule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.json.toComparableJson
import java.time.Instant

class BackupDataSerializationTest : BaseTest() {

    private val json = SerializationModule().json()

    private val fixedInstant = Instant.parse("2026-03-06T12:00:00Z")

    @Test
    fun `full backup serialization produces expected JSON structure`() {
        val backup = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
            watches = WatchBackup(
                items = listOf(
                    WatchItemBackup(
                        type = "aircraft",
                        createdAt = fixedInstant,
                        notificationEnabled = true,
                        userNote = "My favorite plane",
                        hexCode = "ABC123",
                    ),
                    WatchItemBackup(
                        type = "flight",
                        createdAt = fixedInstant,
                        callsign = "DLH123",
                    ),
                    WatchItemBackup(
                        type = "squawk",
                        createdAt = fixedInstant,
                        squawkCode = "7700",
                    ),
                    WatchItemBackup(
                        type = "location",
                        createdAt = fixedInstant,
                        userNote = "Near home",
                        locationLabel = "Home",
                        latitude = 50.8006,
                        longitude = 6.0619,
                        radiusInMeters = 50000.0f,
                    ),
                ),
                checks = listOf(
                    WatchCheckBackup(
                        watchIndex = 0,
                        checkedAt = fixedInstant,
                        aircraftCount = 2,
                        seenHexes = "ABC123,DEF456",
                    ),
                ),
            ),
            apiKey = "my-secret-key",
        )

        val jsonString = json.encodeToString(backup)

        jsonString.toComparableJson() shouldBe """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.1",
                "appVersionCode": 60100,
                "watches": {
                    "items": [
                        {
                            "type": "aircraft",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": true,
                            "userNote": "My favorite plane",
                            "hexCode": "ABC123"
                        },
                        {
                            "type": "flight",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "callsign": "DLH123"
                        },
                        {
                            "type": "squawk",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "squawkCode": "7700"
                        },
                        {
                            "type": "location",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "Near home",
                            "locationLabel": "Home",
                            "latitude": 50.8006,
                            "longitude": 6.0619,
                            "radiusInMeters": 50000.0
                        }
                    ],
                    "checks": [
                        {
                            "watchIndex": 0,
                            "checkedAt": "2026-03-06T12:00:00Z",
                            "aircraftCount": 2,
                            "seenHexes": "ABC123,DEF456"
                        }
                    ]
                },
                "apiKey": "my-secret-key"
            }
        """.toComparableJson()
    }

    @Test
    fun `minimal backup without optional data serializes correctly`() {
        val backup = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
        )

        val jsonString = json.encodeToString(backup)

        jsonString.toComparableJson() shouldBe """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.1",
                "appVersionCode": 60100
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialization from known JSON reproduces correct data`() {
        val jsonString = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.1",
                "appVersionCode": 60100,
                "watches": {
                    "items": [
                        {
                            "type": "aircraft",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": true,
                            "userNote": "Test note",
                            "hexCode": "ABCDEF"
                        }
                    ],
                    "checks": [
                        {
                            "watchIndex": 0,
                            "checkedAt": "2026-03-06T12:00:00Z",
                            "aircraftCount": 3,
                            "seenHexes": "A1,B2,C3"
                        }
                    ]
                },
                "apiKey": "test-key"
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(jsonString)

        backup.version shouldBe 1
        backup.createdAt shouldBe fixedInstant
        backup.appVersion shouldBe "0.6.1"
        backup.appVersionCode shouldBe 60100L
        backup.apiKey shouldBe "test-key"

        backup.watches shouldNotBe null
        backup.watches!!.items.size shouldBe 1
        val watch = backup.watches!!.items[0]
        watch.type shouldBe "aircraft"
        watch.createdAt shouldBe fixedInstant
        watch.notificationEnabled shouldBe true
        watch.userNote shouldBe "Test note"
        watch.hexCode shouldBe "ABCDEF"
        watch.callsign shouldBe null
        watch.squawkCode shouldBe null
        watch.locationLabel shouldBe null

        backup.watches!!.checks.size shouldBe 1
        val check = backup.watches!!.checks[0]
        check.watchIndex shouldBe 0
        check.checkedAt shouldBe fixedInstant
        check.aircraftCount shouldBe 3
        check.seenHexes shouldBe "A1,B2,C3"
    }

    @Test
    fun `old backup with a feeders block decodes safely and ignores it`() {
        // Pre-account backups serialized a "feeders" block. It must be ignored, not crash.
        val jsonString = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.0",
                "appVersionCode": 60000,
                "watches": {
                    "items": [
                        {
                            "type": "aircraft",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "hexCode": "ABC123"
                        }
                    ],
                    "checks": []
                },
                "feeders": {
                    "configs": [
                        {
                            "receiverId": "abc-123",
                            "addedAt": "2026-01-15T08:30:00Z",
                            "label": "Garage",
                            "position": { "latitude": 48.8566, "longitude": 2.3522 },
                            "offlineCheckTimeout": "PT24H",
                            "address": "192.168.1.100"
                        }
                    ],
                    "beastStats": [
                        {
                            "receiverId": "abc-123",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "positionRate": 5.5,
                            "positions": 500,
                            "messageRate": 200.0,
                            "bandwidth": 512.0,
                            "connectionTime": 3600,
                            "latency": 25
                        }
                    ],
                    "mlatStats": []
                },
                "apiKey": "legacy-key"
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(jsonString)

        backup.version shouldBe 1
        backup.apiKey shouldBe "legacy-key"
        backup.watches!!.items.size shouldBe 1
        backup.watches!!.items[0].hexCode shouldBe "ABC123"
    }

    @Test
    fun `deserialization ignores unknown fields for forward compatibility`() {
        val jsonString = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.7.0",
                "appVersionCode": 70000,
                "unknownFutureField": "some value",
                "watches": {
                    "items": [
                        {
                            "type": "aircraft",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "hexCode": "ABC123",
                            "futureField": true
                        }
                    ],
                    "checks": []
                }
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(jsonString)

        backup.version shouldBe 1
        backup.appVersion shouldBe "0.7.0"
        backup.watches!!.items.size shouldBe 1
        backup.watches!!.items[0].hexCode shouldBe "ABC123"
    }

    @Test
    fun `deserialization of minimal watch item with only required fields`() {
        val jsonString = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.1",
                "appVersionCode": 60100,
                "watches": {
                    "items": [
                        {
                            "type": "squawk",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "squawkCode": "7500"
                        }
                    ],
                    "checks": []
                }
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(jsonString)

        val item = backup.watches!!.items[0]
        item.type shouldBe "squawk"
        item.notificationEnabled shouldBe false
        item.userNote shouldBe ""
        item.squawkCode shouldBe "7500"
        item.hexCode shouldBe null
        item.callsign shouldBe null
        item.locationLabel shouldBe null
        item.latitude shouldBe null
        item.longitude shouldBe null
        item.radiusInMeters shouldBe null
    }

    @Test
    fun `round trip serialization preserves all data`() {
        val original = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
            watches = WatchBackup(
                items = listOf(
                    WatchItemBackup(
                        type = "aircraft",
                        createdAt = fixedInstant,
                        notificationEnabled = true,
                        userNote = "Note with \"quotes\" and\nnewlines",
                        hexCode = "A1B2C3",
                    ),
                    WatchItemBackup(
                        type = "location",
                        createdAt = fixedInstant,
                        locationLabel = "Paris",
                        latitude = 48.8566,
                        longitude = 2.3522,
                        radiusInMeters = 25000.0f,
                    ),
                ),
                checks = listOf(
                    WatchCheckBackup(
                        watchIndex = 0,
                        checkedAt = fixedInstant,
                        aircraftCount = 0,
                    ),
                    WatchCheckBackup(
                        watchIndex = 1,
                        checkedAt = fixedInstant,
                        aircraftCount = 5,
                        seenHexes = "X1,X2,X3,X4,X5",
                    ),
                ),
            ),
            apiKey = "key-12345",
        )

        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<BackupData>(jsonString)

        restored shouldBe original
    }

    @Test
    fun `null optional fields are omitted from JSON`() {
        val backup = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
            watches = null,
            apiKey = null,
        )

        val jsonString = json.encodeToString(backup)

        // With explicitNulls = false, null fields should not appear
        jsonString.contains("watches") shouldBe false
        jsonString.contains("apiKey") shouldBe false

        jsonString.toComparableJson() shouldBe """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.1",
                "appVersionCode": 60100
            }
        """.toComparableJson()
    }

    @Test
    fun `aircraft cache round trip serialization`() {
        val original = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
            aircraftCache = AircraftCacheBackup(
                items = listOf(
                    AircraftCacheItemBackup(
                        hex = "ABC123",
                        messageType = "adsb_icao",
                        dbFlags = 1,
                        registration = "N12345",
                        callsign = "UAL100",
                        operator = "United Airlines",
                        airframe = "B738",
                        description = "Boeing 737-800",
                        squawk = "1200",
                        emergency = null,
                        outsideTemp = -40,
                        altitude = "35000",
                        altitudeRate = 0,
                        groundSpeed = 450.5f,
                        indicatedAirSpeed = 280,
                        trackheading = 90.5,
                        groundTrack = 91.2f,
                        latitude = 40.6413,
                        longitude = -73.7781,
                        messages = 1500,
                        seenAt = fixedInstant,
                        rssi = -3.5,
                    ),
                    AircraftCacheItemBackup(
                        hex = "DEF456",
                        messageType = "adsb_icao",
                        messages = 100,
                        seenAt = fixedInstant,
                        rssi = -10.0,
                    ),
                ),
            ),
        )

        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<BackupData>(jsonString)

        restored shouldBe original
        restored.aircraftCache shouldNotBe null
        restored.aircraftCache!!.items.size shouldBe 2

        val first = restored.aircraftCache!!.items[0]
        first.hex shouldBe "ABC123"
        first.registration shouldBe "N12345"
        first.callsign shouldBe "UAL100"
        first.latitude shouldBe 40.6413
        first.longitude shouldBe -73.7781
        first.groundSpeed shouldBe 450.5f
        first.groundTrack shouldBe 91.2f
        first.seenAt shouldBe fixedInstant

        val second = restored.aircraftCache!!.items[1]
        second.hex shouldBe "DEF456"
        second.registration shouldBe null
        second.groundTrack shouldBe null
        second.latitude shouldBe null
    }

    @Test
    fun `old backup without aircraftCache deserializes correctly`() {
        val jsonString = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.0",
                "appVersionCode": 60000,
                "watches": {
                    "items": [
                        {
                            "type": "aircraft",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "hexCode": "ABC123"
                        }
                    ],
                    "checks": []
                }
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(jsonString)

        backup.version shouldBe 1
        backup.aircraftCache shouldBe null
        backup.watches shouldNotBe null
        backup.watches!!.items.size shouldBe 1
    }
}
