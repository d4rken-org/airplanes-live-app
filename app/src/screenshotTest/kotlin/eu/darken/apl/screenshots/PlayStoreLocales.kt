package eu.darken.apl.screenshots

import androidx.compose.ui.tooling.preview.Preview

// @formatter:off

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "en-US", locale = "en", device = DS)
@Preview(name = "de-DE", locale = "de", device = DS)
@Preview(name = "es-ES", locale = "es", device = DS)
@Preview(name = "fr-FR", locale = "fr", device = DS)
@Preview(name = "it-IT", locale = "it", device = DS)
@Preview(name = "ru-RU", locale = "ru", device = DS)
annotation class PlayStoreLocales

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "en-US", locale = "en", device = DS)
@Preview(name = "de-DE", locale = "de", device = DS)
annotation class PlayStoreLocalesSmoke

// @formatter:on
