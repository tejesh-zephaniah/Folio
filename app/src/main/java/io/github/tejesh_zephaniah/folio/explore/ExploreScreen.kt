package io.github.tejesh_zephaniah.folio.explore

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.Executors

private val cookieWrites = Executors.newSingleThreadExecutor()

private val exploreSiteLayout = """
    (() => {
        if (!document.head || document.getElementById('folio-auth-layout')) return;
        const style = document.createElement('style');
        style.id = 'folio-auth-layout';
        style.textContent = `
            #zlibrary-modal-auth.modal {
                position: fixed !important;
                inset: 0 !important;
                width: min(100%, 430px) !important;
                height: 100% !important;
                max-height: none !important;
                margin: 0 auto !important;
                padding: 12px !important;
                box-sizing: border-box !important;
                overflow: auto !important;
            }
            #zlibrary-modal-auth .modal-dialog {
                display: block !important;
                margin: min(7vh, 40px) auto !important;
                max-width: 100% !important;
            }
            /* The site's dropdown backdrop otherwise has no tappable height in WebView. */
            .dark-overlay.show.dropdown {
                height: 100% !important;
            }
        `;
        document.head.appendChild(style);
    })();
""".trimIndent()

internal class ExploreNotice(val text: String)

/** A live browser stays in memory across Folio tabs. Only WebView's site data is persisted. */
internal class ExploreSession(
    context: Context,
    private val scope: CoroutineScope,
    private val onBookImported: (Book) -> Unit,
    private val onDownloadStarted: (id: String, title: String) -> Unit = { _, _ -> },
    private val onDownloadProgress: (id: String, progress: Float?) -> Unit = { _, _ -> },
    private val onDownloadReadyToJump: (id: String, originInWindow: Offset?) -> Unit = { _, _ -> },
    private val onDownloadFinished: (id: String, book: Book) -> Unit = { _, _ -> },
    private val onDownloadFailed: (id: String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private var browser: WebView? = null
    private val downloads = mutableSetOf<URI>()
    private var lastTouchInWindow: Offset? = null
    private var lastTouchAtMs: Long = 0L
    private var viewportCenterInWindow: Offset? = null

    fun recordTouch(offsetInWindow: Offset) {
        lastTouchInWindow = offsetInWindow
        lastTouchAtMs = SystemClock.uptimeMillis()
    }

    fun recordViewportCenter(centerInWindow: Offset) {
        viewportCenterInWindow = centerInWindow
    }

    var showHint by mutableStateOf(true)
    var notice by mutableStateOf<ExploreNotice?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var unavailable by mutableStateOf(false)
        private set
    var rendererLost by mutableStateOf(false)
        private set
    var externalHost by mutableStateOf<String?>(null)
        private set

    fun attach(context: Context): WebView {
        val view = browser ?: createBrowser(context).also {
            browser = it
            it.loadUrl(EXPLORE_HOME)
        }
        (view.parent as? ViewGroup)?.removeView(view)
        view.onResume()
        return view
    }

    fun detach(view: WebView) {
        if (view === browser) {
            view.clearFocus()
            view.onPause()
        }
    }

    fun flushSiteData() {
        if (browser != null) cookieWrites.execute { CookieManager.getInstance().flush() }
    }

    fun close() {
        flushSiteData()
        browser?.let(::destroyBrowser)
    }

    fun dismissNotice(item: ExploreNotice) {
        if (notice === item) notice = null
    }

    fun back(onLeave: () -> Unit) {
        val view = browser
        if (view != null && view.canGoBack()) view.goBack() else onLeave()
    }

    fun returnToSite() {
        val view = browser ?: return
        val history = view.copyBackForwardList()
        val previous = (history.currentIndex - 1 downTo 0).firstOrNull { index ->
            history.getItemAtIndex(index).url?.let(::httpsExploreUri)?.let(::isExploreSite) == true
        }
        if (previous == null) view.loadUrl(EXPLORE_HOME)
        else view.goBackOrForward(previous - history.currentIndex)
    }

    fun retry() {
        unavailable = false
        loading = true
        if (rendererLost) rendererLost = false
        else browser?.let { view ->
            if (view.url?.let(::httpsExploreUri) != null) view.reload()
            else view.loadUrl(EXPLORE_HOME)
        }
    }

    private fun destroyBrowser(view: WebView) {
        if (browser === view) browser = null
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    private fun createBrowser(context: Context) = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!request.isForMainFrame || httpsExploreUri(request.url.toString()) != null) {
                    return null
                }
                return WebResourceResponse(
                    "text/plain", "UTF-8", 403, "Forbidden", emptyMap(),
                    ByteArrayInputStream("Explore requires a secure HTTPS address.".toByteArray())
                )
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = httpsExploreUri(request.url.toString()) ?: run {
                    if (request.hasGesture() || request.isRedirect) {
                        notice = ExploreNotice("Explore requires a secure HTTPS link.")
                        loading = false
                    }
                    return true
                }
                // Let Chromium resolve redirect chains and identify the attachment by its response.
                return !(isExploreSite(url) || request.hasGesture() || request.isRedirect)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                loading = true
                unavailable = false
                externalHost = url?.let(::httpsExploreUri)?.takeUnless(::isExploreSite)?.host
            }

            override fun onPageCommitVisible(view: WebView, url: String?) = applySiteLayout(view, url)

            override fun onPageFinished(view: WebView, url: String?) {
                loading = false
                applySiteLayout(view, url)
                flushSiteData()
            }

            private fun applySiteLayout(view: WebView, url: String?) {
                if (url?.let(::httpsExploreUri)?.let(::isExploreSite) == true) {
                    view.evaluateJavascript(exploreSiteLayout, null)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) pageFailed()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, error: WebResourceResponse) {
                if (request.isForMainFrame && error.statusCode >= 500) pageFailed()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                destroyBrowser(view)
                rendererLost = true
                pageFailed()
                return true
            }
        }
        setDownloadListener { address, agent, disposition, mimeType, _ ->
            loading = false
            queueDownload(this, address, agent, disposition, mimeType)
        }
    }

    private fun pageFailed() {
        unavailable = true
        loading = false
    }

    private fun queueDownload(
        view: WebView,
        address: String?,
        agent: String?,
        disposition: String?,
        mimeType: String?
    ) {
        val url = address?.let(::httpsExploreUri)
        if (url == null) {
            notice = ExploreNotice("This download link isn’t supported. You can import the file locally.")
            return
        }
        if (url in downloads) return
        if (downloads.size >= 2) {
            notice = ExploreNotice("Wait for an active download to finish.")
            return
        }
        downloads.add(url)
        val referer = view.url?.let(::httpsExploreUri)
        val userAgent = agent ?: view.settings.userAgentString
        val name = URLUtil.guessFileName(address, disposition, mimeType)
        val downloadId = UUID.randomUUID().toString()
        val tapOffset = lastTouchInWindow ?: viewportCenterInWindow
        val tapTime = lastTouchAtMs
        onDownloadStarted(downloadId, name)

        var downloadFailedOrCancelled = false
        val elapsed = if (tapTime > 0L) SystemClock.uptimeMillis() - tapTime else Long.MAX_VALUE
        val jumpDelayMs = if (elapsed in 0L..1450L) (1150L - elapsed).coerceAtLeast(0L) else 1050L
        val jumpJob = scope.launch {
            delay(jumpDelayMs)
            if (!downloadFailedOrCancelled) {
                onDownloadReadyToJump(downloadId, tapOffset ?: viewportCenterInWindow)
            }
        }

        scope.launch {
            try {
                val book = downloadExploreBook(appContext, url, userAgent, referer, name) { progress ->
                    scope.launch { onDownloadProgress(downloadId, progress) }
                }
                onDownloadFinished(downloadId, book)
                onBookImported(book)
                notice = ExploreNotice("Added to Library · ${book.title}")
            } catch (e: CancellationException) {
                downloadFailedOrCancelled = true
                jumpJob.cancel()
                onDownloadFailed(downloadId)
                throw e
            } catch (e: ExploreDownloadException) {
                downloadFailedOrCancelled = true
                jumpJob.cancel()
                onDownloadFailed(downloadId)
                notice = ExploreNotice(e.message.orEmpty())
            } catch (e: Exception) {
                // Request URLs and cookie headers can carry site credentials.
                downloadFailedOrCancelled = true
                jumpJob.cancel()
                onDownloadFailed(downloadId)
                Log.w("FolioExplore", "Book download failed (${e.javaClass.simpleName})")
                notice = ExploreNotice("Couldn’t add the book. Try again or import it locally.")
            } finally {
                downloads.remove(url)
            }
        }
    }
}

@Composable
internal fun ExploreScreen(
    session: ExploreSession, onLeave: () -> Unit, modifier: Modifier = Modifier
) {
    BackHandler { session.back(onLeave) }
    Column(modifier.fillMaxSize()) {
        if (session.showHint) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Site availability varies. “Add to Library” saves to your website account. " +
                            "Download an EPUB or PDF to add it to Folio, or import from your phone.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { session.showHint = false },
                        modifier = Modifier.semantics { contentDescription = "Dismiss Explore note" }
                    ) {
                        FolioCloseIcon(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(14.dp))
                    }
                }
            }
        }
        session.externalHost?.let { host ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    host, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = session::returnToSite) { Text("Back to Explore") }
            }
        }
        var viewOriginInWindow by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    val origin = coords.positionInWindow()
                    viewOriginInWindow = origin
                    session.recordViewportCenter(
                        Offset(
                            origin.x + coords.size.width / 2f,
                            origin.y + coords.size.height / 2f
                        )
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        session.recordTouch(viewOriginInWindow + down.position)
                    }
                }
        ) {
            if (!session.rendererLost) {
                AndroidView(
                    factory = session::attach,
                    modifier = Modifier.fillMaxSize(),
                    onReset = null,
                    onRelease = session::detach
                )
            }
            if (session.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            if (session.unavailable) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Explore couldn’t load", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Try again or import books from your phone in Library.",
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = session::retry) { Text("Try again") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExploreMessage(notice: ExploreNotice, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp
    ) {
        Text(
            notice.text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
