package dev.nocturn.app.ui.screens.player

/**
 * A vertical drag that moves the now-playing sheet between the mini player and full screen.
 * Deltas and velocities are in pixels (and pixels per second) on the y axis, downward positive,
 * exactly as Compose gesture detectors report them.
 */
interface PlayerSheetDrag {
    fun onDragStart()

    fun onDrag(deltaY: Float)

    fun onDragEnd(velocityY: Float)
}
