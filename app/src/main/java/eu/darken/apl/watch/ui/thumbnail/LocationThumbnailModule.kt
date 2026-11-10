package eu.darken.apl.watch.ui.thumbnail

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
abstract class LocationThumbnailModule {

    @Binds
    abstract fun renderer(renderer: MapLibreLocationThumbnailRenderer): LocationThumbnailRenderer
}
