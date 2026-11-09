package com.livesub.translate

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile: toggles the accessibility-based translation.
 * Opens the accessibility settings if the service isn't enabled yet.
 */
class TranslateTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val svc = CaptionA11yService.instance
        when {
            CaptionA11yService.isActive() -> svc?.stopTranslating()
            svc == null -> launchActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            !Settings.canDrawOverlays(this) ->
                launchActivity(Intent(this, MainActivity::class.java))
            else -> svc.startTranslating()
        }
        refresh()
    }

    private fun launchActivity(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        qsTile?.apply {
            state = if (CaptionA11yService.isActive())
                Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
