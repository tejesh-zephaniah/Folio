package io.github.tejesh_zephaniah.folio.reader.components

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.AtomicFile
import android.util.LruCache
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.allViews
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.withResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

internal class EpubRenderedPages(links: List<Link>, counts: IntArray) {
    private data class Chapter(val link: Link, val firstPage: Int, val count: Int)

    private val chapters = buildList {
        var first = 1
        links.forEachIndexed { index, link ->
            val count = counts[index].coerceAtLeast(1)
            add(Chapter(link, first, count))
            first += count
        }
    }
    private val byHref = chapters.associateBy { it.link.url().removeFragment() }
    val total: Int = counts.sum().coerceAtLeast(1)

    fun pageOf(locator: Locator): Int? {
        val chapter = byHref[locator.href.removeFragment()] ?: return null
        val offset = ((locator.locations.progression ?: 0.0) * chapter.count).roundToInt()
        return chapter.firstPage + offset.coerceIn(0, chapter.count - 1)
    }

    fun locatorFor(page: Int, publication: Publication): Locator? {
        val target = page.coerceIn(1, total)
        val chapter = chapters.lastOrNull { it.firstPage <= target } ?: return null
        val locator = publication.locatorFromLink(chapter.link) ?: return null
        val progression = (target - chapter.firstPage).toDouble() / chapter.count
        return locator.copy(locations = locator.locations.copy(progression = progression))
    }
}

internal data class EpubLayoutKey(
    val path: String,
    val width: Int,
    val height: Int,
    val fontScale: Double
)

private val renderedPageCounts = LruCache<EpubLayoutKey, IntArray>(12)

internal fun folioEpubConfiguration() = EpubNavigatorFragment.Configuration {
    shouldApplyInsetsPadding = false
    useReadiumCssFontSize = false
}

/**
 * Measure in a private Readium navigator with the same viewport and preferences.
 * It leaves the visible navigator and its selection, curl, and reading location alone.
 */
internal suspend fun measureEpubPages(
    activity: FragmentActivity,
    host: FrameLayout,
    publication: Publication,
    preferences: EpubPreferences,
    layout: EpubLayoutKey,
    awaitPageTurn: suspend () -> Unit
): EpubRenderedPages? {
    val links = publication.readingOrder
    if (links.isEmpty()) return null
    renderedPageCounts[layout]?.let { return EpubRenderedPages(links, it) }
    val signature = "1:${layout.width}:${layout.height}:${layout.fontScale}:" +
        "${activity.resources.displayMetrics.densityDpi}:${WebView.getCurrentWebViewPackage()?.versionName}"
    val legacyCache = File(activity.cacheDir, "folio-pages-${File(layout.path).nameWithoutExtension}")
    val cache = AtomicFile(File(legacyCache.parentFile, "${legacyCache.name}-${signature.hashCode()}"))
    val cached = withContext(Dispatchers.IO) {
        try {
            val lines = when {
                cache.baseFile.isFile -> cache.openRead().bufferedReader().use { it.readLines() }
                legacyCache.isFile -> legacyCache.readLines()
                else -> return@withContext null
            }
            if (lines.size != 2 || lines[0] != signature) return@withContext null
            val pages = lines[1].split(",").mapNotNull(String::toIntOrNull)
            pages.takeIf { it.size == links.size && it.all { count -> count > 0 } }?.toIntArray()
        } catch (e: IOException) {
            Log.w("FolioReader", "Could not read cached EPUB pages", e)
            null
        }
    }
    if (cached != null) {
        renderedPageCounts.put(layout, cached)
        return EpubRenderedPages(links, cached)
    }

    // Let the visible reader settle and coalesce quick repeated text-size taps on cache misses.
    delay(120)
    awaitPageTurn()
    val manager = activity.supportFragmentManager
    val container = FragmentContainerView(activity).apply {
        id = View.generateViewId()
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    val counts = IntArray(links.size)
    // Follow Readium's local publication URL so chapters with the same filename stay distinct.
    val publicationBase = publication.baseUrl ?: requireNotNull(AbsoluteUrl("https://readium_package/"))
    val resourceUris = links.map { Uri.parse(publicationBase.resolve(it.url()).toString()) }
    var fastCounts = 0
    var reusedLayouts = 0
    var layoutProbe: EpubLayoutProbe? = null
    var secondProbe: EpubLayoutProbe? = null
    val parallelLayout = links.size >= 16 &&
        !(activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    var probeValidated = false
    var probeUnavailable = publication.baseUrl != null || links.size < 6
    var matchingLayouts = 0
    val started = SystemClock.elapsedRealtime()
    val latestCount = MutableStateFlow<Pair<Url, Int>?>(null)
    val factory = EpubNavigatorFactory(publication).createFragmentFactory(
        initialLocator = null,
        initialPreferences = preferences,
        configuration = folioEpubConfiguration(),
        paginationListener = object : EpubNavigatorFragment.PaginationListener {
            override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                latestCount.value = locator.href.removeFragment() to totalPages
            }
        }
    )
    val navigator = factory.instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
    try {
        activity.lifecycle.withResumed {
            if (!host.isAttachedToWindow || manager.isStateSaved) return@withResumed
            host.addView(container, 0, FrameLayout.LayoutParams(layout.width, layout.height))
            manager.commitNow { add(container, navigator, "folio_page_measurement_${container.id}") }
        }
        if (!navigator.isAdded) return null
        links.forEachIndexed { index, link ->
            if (counts[index] > 0) return@forEachIndexed
            awaitPageTurn()
            val href = link.url().removeFragment()
            val resource = resourceUris[index]
            if (probeValidated && link.mediaType?.isHtml == true) {
                val nextIndex = index + 1
                val neighbor = secondProbe?.takeIf {
                    links.getOrNull(nextIndex)?.mediaType?.isHtml == true && counts[nextIndex] == 0
                }
                val direct = coroutineScope {
                    val next = neighbor?.let { probe ->
                        async { probe.count(resourceUris[nextIndex], timeout = 900L) }
                    }
                    val current = layoutProbe?.count(resource, timeout = 900L)
                    val following = next?.await()
                    if (following != null) {
                        counts[nextIndex] = following
                        reusedLayouts++
                    }
                    current
                }
                if (direct != null) {
                    counts[index] = direct
                    reusedLayouts++
                    return@forEachIndexed
                }
            }
            if (latestCount.value?.first != href && !navigator.go(link, animated = false)) return null
            // Wait for Readium to initialize the first resource. Later HTML can be counted as
            // soon as its layout exists, without waiting for WebView to paint the hidden page.
            val measured = if (index == 0) null else withTimeoutOrNull(400) {
                var count: Int? = null
                while (count == null) {
                    count = latestCount.value?.takeIf { it.first == href }?.second
                        ?: navigator.webViewFor(resource)?.laidOutPageCount(resource)
                    if (count == null) delay(24)
                }
                count
            }
            val nativeCount = latestCount.value?.takeIf { it.first == href }?.second
            var count = nativeCount ?: measured?.also { fastCounts++ }
                ?: withTimeoutOrNull(500) { latestCount.first { it?.first == href } }?.second
            if (count == null && navigator.go(link, animated = false)) {
                count = withTimeoutOrNull(6_000) { latestCount.first { it?.first == href } }?.second
            }
            if (count == null) {
                Log.w("FolioReader", "EPUB page measurement timed out at resource ${index + 1}/${links.size}")
                return null
            }
            counts[index] = count.coerceAtLeast(1)
            val activeSource = navigator.webViewFor(resource)?.takeIf { it.width > 0 && it.height > 0 }
            if (activeSource != null) {
                layoutProbe?.useSource(activeSource)
                secondProbe?.useSource(activeSource)
            }

            if (!probeUnavailable && !probeValidated && link.mediaType?.isHtml == true) {
                if (activeSource != null) {
                    val probe = layoutProbe ?: EpubLayoutProbe(host, activeSource).also { layoutProbe = it }
                    val direct = probe.count(resource, timeout = 1_500L)
                    if (direct == count) {
                        matchingLayouts++
                        probeValidated = matchingLayouts >= 2 && counts.take(index + 1).any { it > 1 }
                        if (probeValidated && parallelLayout) secondProbe = EpubLayoutProbe(host, activeSource)
                    } else {
                        probeUnavailable = true
                        probe.close()
                        layoutProbe = null
                    }
                }
            }

            // The navigator preloads the neighboring resource. Reuse its layout if available.
            val next = resourceUris.getOrNull(index + 1)
            if (next != null && (probeUnavailable || probeValidated)) {
                val prefetched = withTimeoutOrNull(100) { navigator.webViewFor(next)?.laidOutPageCount(next) }
                if (prefetched != null && counts[index + 1] == 0) {
                    counts[index + 1] = prefetched
                    fastCounts++
                }
            }
        }
        Log.d("FolioReader", "Counted ${counts.sum()} EPUB pages in ${SystemClock.elapsedRealtime() - started} ms " +
            "($reusedLayouts reused a layout WebView, $fastCounts measured before the location callback)")
        renderedPageCounts.put(layout, counts)
        withContext(Dispatchers.IO) {
            try {
                val output = cache.startWrite()
                try {
                    output.write("$signature\n${counts.joinToString(",")}".toByteArray())
                    cache.finishWrite(output)
                } catch (e: IOException) {
                    cache.failWrite(output)
                    throw e
                }
            } catch (e: IOException) {
                Log.w("FolioReader", "Could not cache EPUB pages", e)
            }
        }
        return EpubRenderedPages(links, counts)
    } finally {
        secondProbe?.close()
        layoutProbe?.close()
        if (navigator.isAdded && !manager.isDestroyed) {
            manager.commitNow(allowStateLoss = true) { remove(navigator) }
        }
        host.removeView(container)
    }
}

/** Reuse Readium's transformed HTML and CSS without creating a reader fragment per chapter. */
private class EpubLayoutProbe(host: FrameLayout, source: WebView) {
    private var finished: Uri? = null
    private val browser = WebView(host.context)
    @Volatile private var readiumClient = source.webViewClient

    init {
        browser.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = source.settings.useWideViewPort
            loadWithOverviewMode = source.settings.loadWithOverviewMode
            textZoom = source.settings.textZoom
            allowFileAccess = false
            allowContentAccess = false
        }
        browser.isVerticalScrollBarEnabled = false
        browser.isHorizontalScrollBarEnabled = false
        browser.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        browser.alpha = 0f
        browser.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val uri = request.url
                if (uri.scheme == "https" && (uri.host == "readium_package" || uri.host == "readium_assets")) {
                    readiumClient.shouldInterceptRequest(view, request)?.let { return it }
                }
                return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)
            }

            override fun onPageFinished(view: WebView, url: String) {
                finished = Uri.parse(url)
            }
        }
        host.addView(browser, 0, FrameLayout.LayoutParams(source.width, source.height))
    }

    fun useSource(source: WebView) {
        readiumClient = source.webViewClient
    }

    suspend fun count(resource: Uri, timeout: Long): Int? = withTimeoutOrNull(timeout) {
        finished = null
        browser.loadUrl(resource.toString())
        var pages: Int? = null
        while (pages == null) {
            if (finished?.matchesSpineResource(resource) == true) {
                pages = browser.laidOutPageCount(resource)
            }
            if (pages == null) delay(20)
        }
        pages
    }

    fun close() {
        (browser.parent as? FrameLayout)?.removeView(browser)
        browser.destroy()
    }
}

private fun EpubNavigatorFragment.webViewFor(resource: Uri): WebView? {
    return view?.allViews?.filterIsInstance<WebView>()?.firstOrNull { webView ->
        val url = webView.url ?: return@firstOrNull false
        Uri.parse(url).matchesSpineResource(resource)
    }
}

private val pageMetricsScript = """
        (() => {
            const root = document.scrollingElement;
            if (!root || !window.readium || document.readyState !== 'complete' || window.innerWidth <= 0 ||
                (document.fonts && document.fonts.status !== 'loaded')) return null;
            return [location.href, root.scrollWidth / window.innerWidth];
        })()
    """.trimIndent()

private suspend fun WebView.laidOutPageCount(resource: Uri): Int? {
    val result = suspendCancellableCoroutine<String?> { continuation ->
        evaluateJavascript(pageMetricsScript) { continuation.resume(it) }
    }?.takeIf { it.startsWith('[') } ?: return null
    val metrics = try {
        JSONArray(result)
    } catch (_: JSONException) {
        return null
    }
    if (!Uri.parse(metrics.optString(0)).matchesSpineResource(resource)) return null
    val screens = metrics.optDouble(1)
    if (!screens.isFinite() || screens < 1) return null
    val wholeScreens = screens.roundToInt()
    return wholeScreens.takeIf { abs(screens - it) < 0.1 }
}

private fun Uri.matchesSpineResource(expected: Uri): Boolean =
    scheme.equals(expected.scheme, ignoreCase = true) &&
        authority.equals(expected.authority, ignoreCase = true) &&
        path == expected.path && query.orEmpty() == expected.query.orEmpty()
