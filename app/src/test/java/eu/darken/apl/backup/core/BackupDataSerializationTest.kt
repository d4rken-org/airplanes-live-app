package eu.darken.apl.backup.core

import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.feeder.core.config.FeederConfig
import eu.darken.apl.feeder.core.config.FeederPosition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelper.json.toComparableJson
import java.time.Duration
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
            feeders = FeederBackup(
                configs = listOf(
                    FeederConfig(
                        receiverId = "receiver-001",
                        addedAt = fixedInstant,
                        label = "Rooftop",
                        position = FeederPosition(latitude = 50.8006, longitude = 6.0619),
                        offlineCheckTimeout = Duration.ofHours(48),
                    ),
                ),
                beastStats = listOf(
                    BeastStatBackup(
                        receiverId = "receiver-001",
                        receivedAt = fixedInstant,
                        positionRate = 12.5,
                        positions = 1000,
                        messageRate = 450.0,
                        bandwidth = 1024.0,
                        connectionTime = 86400,
                        latency = 15,
                    ),
                ),
                mlatStats = listOf(
                    MlatStatBackup(
                        receiverId = "receiver-001",
                        receivedAt = fixedInstant,
                        messageRate = 100.0,
                        peerCount = 42,
                        badSyncTimeout = 5,
                        outlierPercent = 1.5f,
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
                "feeders": {
                    "configs": [
                        {
                            "receiverId": "receiver-001",
                            "addedAt": "2026-03-06T12:00:00Z",
                            "label": "Rooftop",
                            "position": {
                                "latitude": 50.8006,
                                "longitude": 6.0619
                            },
                            "offlineCheckTimeout": "PT48H"
                        }
                    ],
                    "beastStats": [
                        {
                            "receiverId": "receiver-001",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "positionRate": 12.5,
                            "positions": 1000,
                            "messageRate": 450.0,
                            "bandwidth": 1024.0,
                            "connectionTime": 86400,
                            "latency": 15
                        }
                    ],
                    "mlatStats": [
                        {
                            "receiverId": "receiver-001",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "messageRate": 100.0,
                            "peerCount": 42,
                            "badSyncTimeout": 5,
                            "outlierPercent": 1.5
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

        backup.feeders shouldBe null
    }

    @Test
    fun `deserialization of feeder backup from known JSON`() {
        val jsonString = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.0",
                "appVersionCode": 60000,
                "feeders": {
                    "configs": [
                        {
                            "receiverId": "abc-123",
                            "addedAt": "2026-01-15T08:30:00Z",
                            "label": "Garage",
                            "position": {
                                "latitude": 48.8566,
                                "longitude": 2.3522
                            },
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
                    "mlatStats": [
                        {
                            "receiverId": "abc-123",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "messageRate": 50.0,
                            "peerCount": 10,
                            "badSyncTimeout": 2,
                            "outlierPercent": 0.5
                        }
                    ]
                }
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(jsonString)

        backup.feeders shouldNotBe null
        val feeders = backup.feeders!!

        feeders.configs.size shouldBe 1
        val config = feeders.configs[0]
        config.receiverId shouldBe "abc-123"
        config.addedAt shouldBe Instant.parse("2026-01-15T08:30:00Z")
        config.label shouldBe "Garage"
        config.position shouldBe FeederPosition(latitude = 48.8566, longitude = 2.3522)
        config.offlineCheckTimeout shouldBe Duration.ofHours(24)
        config.address shouldBe "192.168.1.100"

        feeders.beastStats.size shouldBe 1
        val beast = feeders.beastStats[0]
        beast.receiverId shouldBe "abc-123"
        beast.positionRate shouldBe 5.5
        beast.positions shouldBe 500
        beast.messageRate shouldBe 200.0
        beast.bandwidth shouldBe 512.0
        beast.connectionTime shouldBe 3600L
        beast.latency shouldBe 25L

        feeders.mlatStats.size shouldBe 1
        val mlat = feeders.mlatStats[0]
        mlat.receiverId shouldBe "abc-123"
        mlat.messageRate shouldBe 50.0
        mlat.peerCount shouldBe 10
        mlat.badSyncTimeout shouldBe 2L
        mlat.outlierPercent shouldBe 0.5f
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
            feeders = FeederBackup(
                configs = listOf(
                    FeederConfig(
                        receiverId = "feeder-1",
                        addedAt = fixedInstant,
                        label = "Main",
                        offlineCheckTimeout = Duration.ofHours(24),
                        offlineCheckSnoozedAt = fixedInstant,
                    ),
                ),
                beastStats = emptyList(),
                mlatStats = emptyList(),
            ),
            apiKey = "key-12345",
        )

        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<BackupData>(jsonString)

        restored shouldBe original
    }

    @Test
    fun `all four watch types serialize with correct type-specific fields`() {
        val backup = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
            watches = WatchBackup(
                items = listOf(
                    WatchItemBackup(type = "aircraft", createdAt = fixedInstant, hexCode = "AABBCC"),
                    WatchItemBackup(type = "flight", createdAt = fixedInstant, callsign = "BAW123"),
                    WatchItemBackup(type = "squawk", createdAt = fixedInstant, squawkCode = "7600"),
                    WatchItemBackup(
                        type = "location",
                        createdAt = fixedInstant,
                        locationLabel = "Airport",
                        latitude = 51.4700,
                        longitude = -0.4543,
                        radiusInMeters = 10000.0f,
                    ),
                ),
                checks = emptyList(),
            ),
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
                            "notificationEnabled": false,
                            "userNote": "",
                            "hexCode": "AABBCC"
                        },
                        {
                            "type": "flight",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "callsign": "BAW123"
                        },
                        {
                            "type": "squawk",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "squawkCode": "7600"
                        },
                        {
                            "type": "location",
                            "createdAt": "2026-03-06T12:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "locationLabel": "Airport",
                            "latitude": 51.47,
                            "longitude": -0.4543,
                            "radiusInMeters": 10000.0
                        }
                    ],
                    "checks": []
                }
            }
        """.toComparableJson()
    }

    @Test
    fun `null optional fields are omitted from JSON`() {
        val backup = BackupData(
            version = 1,
            createdAt = fixedInstant,
            appVersion = "0.6.1",
            appVersionCode = 60100,
            watches = null,
            feeders = null,
            apiKey = null,
        )

        val jsonString = json.encodeToString(backup)

        // With explicitNulls = false, null fields should not appear
        jsonString.contains("watches") shouldBe false
        jsonString.contains("feeders") shouldBe false
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
    fun `watch check with null seenHexes omits the field`() {
        val check = WatchCheckBackup(
            watchIndex = 0,
            checkedAt = fixedInstant,
            aircraftCount = 0,
            seenHexes = null,
        )

        val jsonString = json.encodeToString(check)

        jsonString.contains("seenHexes") shouldBe false
        jsonString.toComparableJson() shouldBe """
            {
                "watchIndex": 0,
                "checkedAt": "2026-03-06T12:00:00Z",
                "aircraftCount": 0
            }
        """.toComparableJson()
    }

    @Test
    fun `feeder config with all optional fields serializes correctly`() {
        val config = FeederConfig(
            receiverId = "full-config",
            addedAt = fixedInstant,
            label = "Complete Setup",
            position = FeederPosition(latitude = 40.7128, longitude = -74.0060),
            offlineCheckTimeout = Duration.ofHours(24),
            offlineCheckSnoozedAt = fixedInstant,
            address = "10.0.0.1",
        )

        val backup = FeederBackup(
            configs = listOf(config),
            beastStats = emptyList(),
            mlatStats = emptyList(),
        )

        val jsonString = json.encodeToString(backup)

        jsonString.toComparableJson() shouldBe """
            {
                "configs": [
                    {
                        "receiverId": "full-config",
                        "addedAt": "2026-03-06T12:00:00Z",
                        "label": "Complete Setup",
                        "position": {
                            "latitude": 40.7128,
                            "longitude": -74.006
                        },
                        "offlineCheckTimeout": "PT24H",
                        "offlineCheckSnoozedAt": "2026-03-06T12:00:00Z",
                        "address": "10.0.0.1"
                    }
                ],
                "beastStats": [],
                "mlatStats": []
            }
        """.toComparableJson()
    }

    @Test
    fun `deserialization from V1 reference snapshot`() {
        // This is the canonical V1 format. If this test breaks, the backup format has regressed.
        val v1Snapshot = """
            {
                "version": 1,
                "createdAt": "2026-03-06T12:00:00Z",
                "appVersion": "0.6.1",
                "appVersionCode": 60100,
                "watches": {
                    "items": [
                        {
                            "type": "aircraft",
                            "createdAt": "2026-01-01T00:00:00Z",
                            "notificationEnabled": true,
                            "userNote": "Interesting plane",
                            "hexCode": "A00001"
                        },
                        {
                            "type": "flight",
                            "createdAt": "2026-01-02T00:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "callsign": "UAL100"
                        },
                        {
                            "type": "squawk",
                            "createdAt": "2026-01-03T00:00:00Z",
                            "notificationEnabled": true,
                            "userNote": "Emergency",
                            "squawkCode": "7700"
                        },
                        {
                            "type": "location",
                            "createdAt": "2026-01-04T00:00:00Z",
                            "notificationEnabled": false,
                            "userNote": "",
                            "locationLabel": "KJFK",
                            "latitude": 40.6413,
                            "longitude": -73.7781,
                            "radiusInMeters": 20000.0
                        }
                    ],
                    "checks": [
                        {
                            "watchIndex": 0,
                            "checkedAt": "2026-02-01T10:00:00Z",
                            "aircraftCount": 1,
                            "seenHexes": "A00001"
                        },
                        {
                            "watchIndex": 2,
                            "checkedAt": "2026-02-01T10:00:00Z",
                            "aircraftCount": 0
                        }
                    ]
                },
                "feeders": {
                    "configs": [
                        {
                            "receiverId": "feeder-uuid-001",
                            "addedAt": "2025-12-01T00:00:00Z",
                            "label": "Main Antenna",
                            "position": {
                                "latitude": 50.1109,
                                "longitude": 8.6821
                            },
                            "offlineCheckTimeout": "PT48H"
                        }
                    ],
                    "beastStats": [
                        {
                            "receiverId": "feeder-uuid-001",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "positionRate": 10.0,
                            "positions": 800,
                            "messageRate": 350.0,
                            "bandwidth": 768.0,
                            "connectionTime": 172800,
                            "latency": 12
                        }
                    ],
                    "mlatStats": [
                        {
                            "receiverId": "feeder-uuid-001",
                            "receivedAt": "2026-03-06T12:00:00Z",
                            "messageRate": 80.0,
                            "peerCount": 35,
                            "badSyncTimeout": 1,
                            "outlierPercent": 0.8
                        }
                    ]
                },
                "apiKey": "apl_key_abc123"
            }
        """.trimIndent()

        val backup = json.decodeFromString<BackupData>(v1Snapshot)

        // Top-level
        backup.version shouldBe 1
        backup.appVersion shouldBe "0.6.1"
        backup.appVersionCode shouldBe 60100L
        backup.apiKey shouldBe "apl_key_abc123"

        // Watches
        val watches = backup.watches!!
        watches.items.size shouldBe 4
        watches.items[0].type shouldBe "aircraft"
        watches.items[0].hexCode shouldBe "A00001"
        watches.items[0].notificationEnabled shouldBe true
        watches.items[1].type shouldBe "flight"
        watches.items[1].callsign shouldBe "UAL100"
        watches.items[2].type shouldBe "squawk"
        watches.items[2].squawkCode shouldBe "7700"
        watches.items[3].type shouldBe "location"
        watches.items[3].locationLabel shouldBe "KJFK"
        watches.items[3].latitude shouldBe 40.6413
        watches.items[3].longitude shouldBe -73.7781
        watches.items[3].radiusInMeters shouldBe 20000.0f

        // Checks
        watches.checks.size shouldBe 2
        watches.checks[0].watchIndex shouldBe 0
        watches.checks[0].aircraftCount shouldBe 1
        watches.checks[0].seenHexes shouldBe "A00001"
        watches.checks[1].watchIndex shouldBe 2
        watches.checks[1].aircraftCount shouldBe 0
        watches.checks[1].seenHexes shouldBe null

        // Feeders
        val feeders = backup.feeders!!
        feeders.configs.size shouldBe 1
        feeders.configs[0].receiverId shouldBe "feeder-uuid-001"
        feeders.configs[0].label shouldBe "Main Antenna"
        feeders.configs[0].position shouldBe FeederPosition(50.1109, 8.6821)
        feeders.configs[0].offlineCheckTimeout shouldBe Duration.ofHours(48)

        feeders.beastStats.size shouldBe 1
        feeders.beastStats[0].positionRate shouldBe 10.0
        feeders.beastStats[0].positions shouldBe 800
        feeders.beastStats[0].connectionTime shouldBe 172800L

        feeders.mlatStats.size shouldBe 1
        feeders.mlatStats[0].peerCount shouldBe 35
        feeders.mlatStats[0].outlierPercent shouldBe 0.8f

        // Round-trip: re-serialize and compare to original
        val reserialized = json.encodeToString(backup)
        reserialized.toComparableJson() shouldBe v1Snapshot.toComparableJson()
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
