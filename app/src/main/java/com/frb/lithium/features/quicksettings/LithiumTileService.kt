package com.rinn.lithium.features.quicksettings

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class LithiumTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile.apply {
            label = "AxTest"
            icon = Icon.createWithResource(this@LithiumTileService, com.rinn.engine.R.drawable.ic_lithium)
            state = Tile.STATE_INACTIVE
            updateTile()
        }


    }

    override fun onClick() {
        super.onClick()
        Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show()
    }


}