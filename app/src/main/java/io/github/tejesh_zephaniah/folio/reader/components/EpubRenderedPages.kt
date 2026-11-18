package io.github.tejesh_zephaniah.folio.reader.components

import android.view.View
import android.widget.FrameLayout
import android.util.LruCache
import android.util.Log
import android.webkit.WebView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.withResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url
import java.io.File
import java.io.IOException
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
    layout: EpubLayoutKey
): EpubRenderedPages? {
    val links = publication.readingOrder
    if (links.isEmpty()) return null
    renderedPageCounts[layout]?.let { return EpubRenderedPages(links, it) }
    val cache = File(activity.cacheDir, "folio-pages-${File(layout.path).nameWithoutExtension}")
    val signature = "1:${layout.width}:${layout.height}:${layout.fontScale}:" +
        "${activity.resources.displayMetrics.densityDpi}:${WebView.getCurrentWebViewPackage()?.versionName}"
    val cached = withContext(Dispatchers.IO) {
        try {
            if (!cache.isFile) return@withContext null
            val lines = cache.readLines()
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

    val manager = activity.supportFragmentManager
    val container = FragmentContainerView(activity).apply {
        id = View.generateViewId()
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    val counts = IntArray(links.size)
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
            val href = link.url().removeFragment()
            if (latestCount.value?.first != href && !navigator.go(link, animated = false)) return null
            val measured = withTimeoutOrNull(6_000) { latestCount.first { it?.first == href } }
                ?: return null
            counts[index] = measured.second.coerceAtLeast(1)
        }
        renderedPageCounts.put(layout, counts)
        withContext(Dispatchers.IO) {
            try {
                cache.writeText("$signature\n${counts.joinToString(",")}")
            } catch (e: IOException) {
                Log.w("FolioReader", "Could not cache EPUB pages", e)
            }
        }
        return EpubRenderedPages(links, counts)
    } finally {
        if (navigator.isAdded && !manager.isDestroyed) {
            manager.commitNow(allowStateLoss = true) { remove(navigator) }
        }
        host.removeView(container)
    }
}
