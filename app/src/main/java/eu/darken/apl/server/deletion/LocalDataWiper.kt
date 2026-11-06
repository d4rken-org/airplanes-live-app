package eu.darken.apl.server.deletion

import android.app.ActivityManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

interface LocalDataWiper {
    /** Returns false when the wipe could not start. On success the process is killed. */
    fun wipe(): Boolean
}

class ActivityManagerDataWiper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocalDataWiper {
    override fun wipe(): Boolean = context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
}

@InstallIn(SingletonComponent::class)
@Module
abstract class DeletionModule {
    @Binds
    abstract fun localDataWiper(wiper: ActivityManagerDataWiper): LocalDataWiper
}
