package io.github.tejesh_zephaniah.folio.reader.components

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.withResumed
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.ReadiumManager
import io.github.tejesh_zephaniah.folio.data.model.bookPageFromLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.json.JSONArray
import java.io.File

class ReadiumController(
    val publication: Publication,
    val isPdf: Boolean
) {
    var navigator: OverflowableNavigator? = null
    var epubFragment: EpubNavigatorFragment? = null
    var positions: List<Locator> = emptyList()
    internal var epubPageCurl: EpubPageCurl? = null
    internal val renderedPages = MutableStateFlow<EpubRenderedPages?>(null)
    internal val paginationFailed = MutableStateFlow(false)
    val reflowsText: Boolean
        get() = !isPdf && publication.metadata.layout != Layout.FIXED

    val totalPages: Int
        get() = renderedPages.value?.total
            ?: if (positions.isNotEmpty()) positions.size else publication.readingOrder.size.coerceAtLeast(1)

    val currentLocator: Locator?
        get() = epubPageCurl?.previewOrigin ?: navigator?.currentLocator?.value

    private val availableNavigator: OverflowableNavigator?
        get() = navigator.takeUnless { epubPageCurl?.isTurning == true }

    fun pageOf(locator: Locator): Int? = if (reflowsText) {
        renderedPages.value?.pageOf(locator)
    } else {
        bookPageFromLocator(locator.locations.position, locator.locations.totalProgression, totalPages)
            ?: positions.indexOfFirst { it.href == locator.href }.takeIf { it >= 0 }?.plus(1)
    }

    fun goForward(animated: Boolean = true): Boolean {
        return availableNavigator?.goForward(animated) ?: false
    }

    fun goBackward(animated: Boolean = true): Boolean {
        return availableNavigator?.goBackward(animated) ?: false
    }

    fun go(locator: Locator, animated: Boolean = true): Boolean {
        return availableNavigator?.go(locator, animated) ?: false
    }

    fun go(link: Link, animated: Boolean = true): Boolean {
        return availableNavigator?.go(link, animated) ?: false
    }

    fun goToPage(page: Int): Boolean {
        if (reflowsText) return renderedPages.value?.locatorFor(page, publication)?.let { go(it) } ?: false
        if (positions.isNotEmpty()) {
            val loc = positions.getOrNull((page - 1).coerceIn(0, positions.size - 1))
            if (loc != null) return go(loc)
        } else {
            val rOrder = publication.readingOrder
            if (rOrder.isEmpty()) return false
            val index = ((page.toFloat() / totalPages) * (rOrder.size - 1)).toInt().coerceIn(0, rOrder.size - 1)
            if (index in rOrder.indices) {
                return go(rOrder[index])
            }
        }
        return false
    }

    suspend fun currentSelection(): Selection? {
        return (navigator as? SelectableNavigator)?.currentSelection()
            ?: (epubFragment as? SelectableNavigator)?.currentSelection()
    }

    fun clearSelection() {
        (navigator as? SelectableNavigator)?.clearSelection()
            ?: (epubFragment as? SelectableNavigator)?.clearSelection()
    }

    suspend fun applyDecorations(decorations: List<Decoration>, group: String = "highlights") {
        val fragment = epubFragment ?: return
        if (fragment.isAdded && fragment.view != null) fragment.applyDecorations(decorations, group)
    }

    suspend fun overlappingHighlights(): Set<String>? {
        val fragment = epubFragment?.takeIf { it.isAdded && it.view != null } ?: return null
        val result = fragment.evaluateJavascript(
            """
            (() => {
                const selection = window.getSelection();
                if (!selection || selection.isCollapsed || !selection.rangeCount) return null;
                const range = selection.getRangeAt(0);
                return readium.getDecorations("highlights").items.filter(({range: saved}) =>
                    range.compareBoundaryPoints(Range.END_TO_START, saved) < 0 &&
                    range.compareBoundaryPoints(Range.START_TO_END, saved) > 0
                ).map(({decoration}) => decoration.id);
            })()
            """.trimIndent()
        )
        if (result == null || result == "null") return null
        val ids = JSONArray(result)
        return (0 until ids.length()).mapTo(mutableSetOf()) { ids.getString(it) }
    }

    fun applyTheme(themeMode: ReaderThemeMode, fontScale: Double) {
        val frag = epubFragment ?: return
        if (!frag.isAdded || frag.isDetached || frag.activity == null) return
        try {
            frag.submitPreferences(epubPreferences(themeMode, fontScale))
        } catch (e: Exception) {
            android.util.Log.w("ReadiumController", "Could not apply theme: ${e.message}")
        }
    }
}

@Composable
fun ReadiumReaderContainer(
    bookFile: File,
    fileType: String,
    initialLocatorJson: String?,
    readerTheme: ReaderThemeMode,
    fontScale: Double,
    onLocatorChanged: suspend (Locator) -> Unit,
    onCenterTap: () -> Unit,
    onControllerReady: (ReadiumController) -> Unit,
    onHighlightRequested: (Locator) -> Unit,
    onRemoveHighlights: (Set<String>) -> Unit,
    onHighlightedPassageTapped: (String) -> Unit,
    onCloseReader: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    var frame by remember { mutableStateOf<FragmentContainerView?>(null) }
    var controller by remember(bookFile) { mutableStateOf<ReadiumController?>(null) }
    var openError by remember(bookFile) { mutableStateOf<String?>(null) }
    val viewport = remember(bookFile) { MutableStateFlow(IntSize.Zero) }
    val latestTheme by rememberUpdatedState(readerTheme)
    val latestFontScale by rememberUpdatedState(fontScale)
    val latestLocatorChanged by rememberUpdatedState(onLocatorChanged)
    val latestCenterTap by rememberUpdatedState(onCenterTap)
    val latestControllerReady by rememberUpdatedState(onControllerReady)
    val latestHighlightRequested by rememberUpdatedState(onHighlightRequested)
    val latestRemoveHighlights by rememberUpdatedState(onRemoveHighlights)
    val latestHighlightedPassageTapped by rememberUpdatedState(onHighlightedPassageTapped)

    LaunchedEffect(controller, readerTheme, fontScale) {
        controller?.applyTheme(readerTheme, fontScale)
    }

    val hostView = frame
    LaunchedEffect(hostView, bookFile) {
        val host = hostView ?: return@LaunchedEffect
        val fragmentManager = activity.supportFragmentManager
        val tag = "readium_frag_${host.id}"
        var publication: Publication? = null
        var attachedFragment: Fragment? = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val opened = ReadiumManager.openPublication(context, bookFile)
                    ?: return@withContext null
                publication = opened
                val active = ReadiumController(opened, fileType.equals("pdf", ignoreCase = true))
                active.positions = try {
                    opened.positions()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("FolioReader", "Could not load $fileType positions", e)
                    emptyList()
                }
                active
            }
            if (loaded == null) {
                openError = "The publication could not be parsed."
                return@LaunchedEffect
            }

            val navigator = activity.lifecycle.withResumed {
                if (activity.isFinishing) return@withResumed null
                val initialLocator = ReadiumManager.parseLocatorJson(initialLocatorJson)
                val fragment: Fragment
                val navigator: OverflowableNavigator
                if (loaded.isPdf) {
                    val factory = PdfNavigatorFragment.createFactory(
                        publication = loaded.publication,
                        initialLocator = initialLocator,
                        pdfEngineProvider = PdfiumEngineProvider()
                    )
                    val pdf = factory.instantiate(
                        activity.classLoader, PdfNavigatorFragment::class.java.name
                    ) as PdfNavigatorFragment<*, *>
                    fragment = pdf
                    navigator = pdf
                } else {
                    val factory = EpubNavigatorFactory(loaded.publication).createFragmentFactory(
                        initialLocator = initialLocator,
                        initialPreferences = epubPreferences(latestTheme, latestFontScale),
                        configuration = folioEpubConfiguration()
                    )
                    val epub = factory.instantiate(
                        activity.classLoader, EpubNavigatorFragment::class.java.name
                    ) as EpubNavigatorFragment
                    loaded.epubFragment = epub
                    loaded.epubPageCurl = (host.parent as? ReaderSelectionHost)?.pageCurl
                    fragment = epub
                    navigator = epub
                }
                val inputListener = object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val width = (fragment.view ?: host).width
                        if (width <= 0) return false
                        val curl = loaded.epubPageCurl
                        when {
                            event.point.x < width * 0.20f -> {
                                if (curl?.tapEdge(fromRight = false) != true) loaded.goBackward()
                            }
                            event.point.x > width * 0.80f -> {
                                if (curl?.tapEdge(fromRight = true) != true) loaded.goForward()
                            }
                            else -> latestCenterTap()
                        }
                        return true
                    }
                }
                when (fragment) {
                    is EpubNavigatorFragment -> fragment.addInputListener(inputListener)
                    is PdfNavigatorFragment<*, *> -> fragment.addInputListener(inputListener)
                }
                attachedFragment = fragment
                fragmentManager.commitNow {
                    setReorderingAllowed(true)
                    add(host, fragment, tag)
                }
                if (fragment is EpubNavigatorFragment) {
                    fragment.addDecorationListener("highlights", object : DecorableNavigator.Listener {
                        override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                            latestHighlightedPassageTapped(event.decoration.id)
                            return true
                        }
                    })
                }
                loaded.navigator = navigator
                controller = loaded
                latestControllerReady(loaded)
                Log.d("FolioReader", "Attached $fileType navigator ($tag)")
                navigator
            } ?: return@LaunchedEffect

            if (loaded.isPdf) {
                navigator.currentLocator.collect { latestLocatorChanged(it) }
            } else {
                coroutineScope {
                    launch {
                        if (!loaded.reflowsText) return@launch
                        combine(viewport, snapshotFlow { latestFontScale }) { size, scale -> size to scale }
                            .collectLatest { (size, scale) ->
                                loaded.renderedPages.value = null
                                loaded.paginationFailed.value = false
                                val parent = host.parent as? FrameLayout ?: return@collectLatest
                                if (size.width <= 0 || size.height <= 0) return@collectLatest
                                delay(250)
                                val layout = EpubLayoutKey(bookFile.absolutePath, size.width, size.height, scale)
                                val pages = try {
                                    measureEpubPages(
                                        activity, parent, loaded.publication, epubPreferences(latestTheme, scale), layout
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w("FolioReader", "Could not measure rendered EPUB pages", e)
                                    null
                                }
                                loaded.paginationFailed.value = pages == null
                                loaded.renderedPages.value = pages
                            }
                    }
                    var lastSaved: Pair<Locator, EpubRenderedPages?>? = null
                    combine(navigator.currentLocator, loaded.renderedPages) { _, _ -> Unit }.collect {
                        loaded.epubPageCurl?.awaitSettled()
                        val committed = navigator.currentLocator.value to loaded.renderedPages.value
                        if (lastSaved != committed) {
                            latestLocatorChanged(committed.first)
                            lastSaved = committed
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (controller != null) throw e
            Log.e("FolioReader", "Could not open $fileType publication", e)
            openError = "Error reading publication: ${e.message}"
        } finally {
            controller?.navigator = null
            controller?.epubFragment = null
            val fragment = attachedFragment
            if (fragment != null && fragment.isAdded && !fragmentManager.isDestroyed) {
                fragmentManager.commitNow(allowStateLoss = true) { remove(fragment) }
            }
            publication?.close()
            Log.d("FolioReader", "Released $fileType reader ($tag)")
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().onSizeChanged { viewport.value = it },
            factory = { ctx ->
                val container = FragmentContainerView(ctx).apply { id = View.generateViewId() }
                frame = container
                if (fileType.equals("epub", ignoreCase = true)) {
                    ReaderSelectionHost(
                        context = ctx,
                        window = activity.window,
                        scope = scope,
                        navigator = { controller?.epubFragment },
                        paperColor = { latestTheme.backgroundArgb.toInt() },
                        overlaps = { controller?.overlappingHighlights() }
                    ) { mode ->
                        scope.launch {
                            val overlaps = controller?.overlappingHighlights() ?: return@launch
                            if (overlaps.isNotEmpty()) {
                                mode.finish()
                                latestRemoveHighlights(overlaps)
                            } else {
                                val selection = controller?.currentSelection() ?: return@launch
                                mode.finish()
                                latestHighlightRequested(selection.locator)
                            }
                        }
                    }.apply { addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
                } else {
                    container
                }
            }
        )
        when {
            controller == null && openError == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = FolioTheme.extendedColors.accent
                    )
                }
            }

            openError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Failed to Open Book",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FolioTheme.extendedColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = openError ?: "An unexpected error occurred.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FolioTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onCloseReader) {
                            Text("Back to Library")
                        }
                    }
                }
            }

        }
    }
}

private fun epubPreferences(theme: ReaderThemeMode, fontScale: Double): EpubPreferences = EpubPreferences(
    backgroundColor = ReadiumColor(theme.backgroundArgb.toInt()),
    fontSize = fontScale,
    columnCount = ColumnCount.ONE,
    spread = Spread.NEVER,
    theme = when (theme) {
        ReaderThemeMode.DARK -> Theme.DARK
        ReaderThemeMode.SEPIA -> Theme.SEPIA
        else -> Theme.LIGHT
    }
)

// WebView rebuilds its native menu during preparation. Decorate the original
// callback so Chromium keeps handling Copy, selection handles, and its menu bounds.
private class ReaderSelectionHost(
    context: Context,
    window: Window,
    private val scope: CoroutineScope,
    navigator: () -> EpubNavigatorFragment?,
    paperColor: () -> Int,
    private val overlaps: suspend () -> Set<String>?,
    private val onHighlight: (ActionMode) -> Unit
) : FrameLayout(context) {
    val pageCurl = EpubPageCurl(this, window, scope, navigator, paperColor)
    private var selectionMode: ActionMode? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        pageCurl.dispatchTouch(event, selectionMode != null) { super.dispatchTouchEvent(it) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pageCurl.cancelGesture()
        if (Build.VERSION.SDK_INT >= 29 && w > 0 && h > 0) {
            val edge = (20 * resources.displayMetrics.density).toInt().coerceAtMost(w)
            val corner = (64 * resources.displayMetrics.density).toInt().coerceAtMost(h / 4)
            systemGestureExclusionRects = listOf(Rect(w - edge, 0, w, corner), Rect(w - edge, h - corner, w, h))
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) pageCurl.cancelGesture()
    }

    override fun onDetachedFromWindow() {
        pageCurl.detach()
        super.onDetachedFromWindow()
    }

    override fun startActionModeForChild(
        child: View,
        callback: ActionMode.Callback,
        type: Int
    ): ActionMode? = super.startActionModeForChild(child, withHighlight(callback), type)

    override fun startActionModeForChild(child: View, callback: ActionMode.Callback): ActionMode? =
        super.startActionModeForChild(child, withHighlight(callback))

    private fun withHighlight(callback: ActionMode.Callback): ActionMode.Callback =
        if (callback is HighlightCallback) callback else HighlightCallback(callback)

    private inner class HighlightCallback(private val delegate: ActionMode.Callback) : ActionMode.Callback2() {
        private var checking: Job? = null
        private var removeExisting: Boolean? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (!delegate.onCreateActionMode(mode, menu)) return false
            selectionMode = mode
            pageCurl.cancelGesture()
            refresh(mode)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val changed = delegate.onPrepareActionMode(mode, menu)
            refresh(mode)
            return updateAction(menu) || changed
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != HIGHLIGHT_ID) return delegate.onActionItemClicked(mode, item)
            onHighlight(mode)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            checking?.cancel()
            if (selectionMode === mode) selectionMode = null
            delegate.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            (delegate as? ActionMode.Callback2)?.onGetContentRect(mode, view, outRect)
                ?: super.onGetContentRect(mode, view, outRect)
            refresh(mode)
        }

        private fun refresh(mode: ActionMode) {
            if (checking?.isActive == true) return
            checking = scope.launch {
                val removing = overlaps()?.isNotEmpty() ?: return@launch
                if (selectionMode === mode && removeExisting != removing) {
                    removeExisting = removing
                    updateAction(mode.menu)
                    mode.invalidate()
                }
            }
        }

        private fun updateAction(menu: Menu): Boolean {
            val removing = removeExisting ?: return false
            val title = if (removing) "Remove Highlight" else "Highlight"
            val item = menu.findItem(HIGHLIGHT_ID)
            if (item?.title == title) return false
            if (item != null) item.title = title else {
                menu.add(Menu.NONE, HIGHLIGHT_ID, Menu.NONE, title)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            return true
        }
    }

    private companion object {
        const val HIGHLIGHT_ID = 99991
    }
}
