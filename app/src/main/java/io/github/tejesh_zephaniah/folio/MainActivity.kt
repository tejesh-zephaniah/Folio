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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFragment

class MainActivity : FragmentActivity() {

    private var incomingUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            // The library owns navigation after recreation, and its Compose reader
            // host and publication are not available to FragmentManager yet.
            supportFragmentManager.fragmentFactory = object : FragmentFactory() {
                override fun instantiate(classLoader: ClassLoader, className: String): Fragment =
                    when (className) {
                        EpubNavigatorFragment::class.java.name ->
                            EpubNavigatorFragment.createDummyFactory().instantiate(classLoader, className)
                        PdfNavigatorFragment::class.java.name ->
                            PdfNavigatorFragment.createDummyFactory(PdfiumEngineProvider())
                                .instantiate(classLoader, className)
                        else -> super.instantiate(classLoader, className)
                    }
            }
        }
        super.onCreate(savedInstanceState)
        val restoredReaders = supportFragmentManager.fragments.filter {
            it is EpubNavigatorFragment || it is PdfNavigatorFragment<*, *>
        }
        if (restoredReaders.isNotEmpty()) {
            supportFragmentManager.commitNow { restoredReaders.forEach { remove(it) } }
        }
        enableEdgeToEdge()

        if (savedInstanceState == null || savedInstanceState.getBoolean("incomingBookPending")) {
            handleIntent(intent)
        }

        setContent {
            FolioApp(
                incomingUri = incomingUri,
                onIncomingUriConsumed = { incomingUri = null }
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("incomingBookPending", incomingUri != null)
        super.onSaveInstanceState(outState)
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
