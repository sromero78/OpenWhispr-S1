package com.edib.openwhispr

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class OpenWhisprTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (WhisperAccessibilityService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "OpenWispr"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val service = WhisperAccessibilityService.instance
        if (service != null) {
            service.restoreOverlay()
            qsTile?.apply {
                state = Tile.STATE_ACTIVE
                updateTile()
            }
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
