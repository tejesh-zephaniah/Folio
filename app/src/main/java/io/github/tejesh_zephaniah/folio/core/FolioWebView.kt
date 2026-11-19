package io.github.tejesh_zephaniah.folio.core

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

internal object FolioWebView {
    private val ready = CompletableDeferred<Boolean>()
    private var started = false

    @MainThread
    fun start(context: Context) {
        if (started) return
        started = true
        WebViewCompat.startUpWebView(
            context.applicationContext,
            WebViewStartUpConfig.Builder(Dispatchers.IO.asExecutor()).build(),
            object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                override fun onResult(result: WebViewStartUpResult) {
                    ready.complete(true)
                }

                override fun onError(error: WebViewStartupException) {
                    Log.e("FolioWebView", "Could not start WebView", error)
                    ready.complete(false)
                }
            }
        )
    }

    @MainThread
    suspend fun awaitReady(context: Context): Boolean {
        start(context)
        return ready.await()
    }
}
