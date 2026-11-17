package io.github.tejesh_zephaniah.folio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            FolioApp(
                incomingUri = incomingUri,
                onIncomingUriConsumed = { incomingUri = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        android.util.Log.d("MainActivity", "handleIntent: action=${intent?.action}, data=${intent?.data}")
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                incomingUri = uri
            }
        }
    }
}
