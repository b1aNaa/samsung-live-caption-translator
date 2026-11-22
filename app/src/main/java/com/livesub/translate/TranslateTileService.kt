package com.livesub.translate

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that toggles translation. Sends the user to wherever
 * setup is incomplete (accessibility settings, or the app to enter a key).
 */
class TranslateTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val status = ServiceStatus.flow.value
        when {
            status.running -> CaptionA11yService.stop()
            !status.connected -> launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            else -> when (CaptionA11yService.start()) {
                StartResult.STARTED -> Unit
                StartResult.NO_SERVICE -> launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                StartResult.NO_CREDENTIALS -> launch(
                    Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                )
            }
        }
        refresh()
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (ServiceStatus.flow.value.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
