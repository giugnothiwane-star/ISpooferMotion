package com.chessoverlay.app.overlay

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Activité transparente qui affiche la demande officielle d'Android pour autoriser la capture
 * d'écran, puis transmet la réponse au service du menu flottant.
 */
class CaptureConsentActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) OverlayService.onProjectionGranted(this, r.resultCode, data)
        else OverlayService.onProjectionDenied(this)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            launcher.launch(mpm.createScreenCaptureIntent())
        }
    }
}
