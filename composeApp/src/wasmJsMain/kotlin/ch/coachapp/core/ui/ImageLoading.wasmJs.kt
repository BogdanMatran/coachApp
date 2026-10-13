package ch.coachapp.core.ui

import coil3.ComponentRegistry

/** Coil has no animated decoder for the Skia targets; [AnimatedImage] covers exercise demos. */
actual fun ComponentRegistry.Builder.addPlatformDecoders() = Unit
