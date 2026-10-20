package eu.darken.apl.watch.core.server

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.apl.watch.core.alerts.WatchAlertNotifications

@InstallIn(SingletonComponent::class)
@Module
abstract class WatchServerModule {

    @Binds
    abstract fun serverAlerts(notifications: WatchAlertNotifications): WatchServerAlerts
}
