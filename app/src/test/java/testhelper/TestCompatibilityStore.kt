package testhelper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import eu.darken.apl.common.BuildConfigWrap
import eu.darken.apl.common.serialization.SerializationModule
import eu.darken.apl.server.compat.AppCompatibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

/**
 * A file backed store for [AppCompatibility]. Every [holder] reads the same file, so a second one
 * starts from what the first one stored, like the app after a restart.
 */
class TestCompatibilityStore : AutoCloseable {

    // A DataStore keeps its file claimed until its scope is done
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val file = File(BaseTest.IO_TEST_BASEDIR, "compat-${UUID.randomUUID()}.preferences_pb").apply {
        parentFile?.mkdirs()
        delete()
    }

    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    fun holder(versionCode: Long = BuildConfigWrap.VERSION_CODE) = AppCompatibility(
        appScope = scope,
        dataStore = dataStore,
        json = SerializationModule().json(),
        versionCode = versionCode,
    )

    override fun close() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        file.delete()
    }
}
