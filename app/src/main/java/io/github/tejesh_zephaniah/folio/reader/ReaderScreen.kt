package io.github.tejesh_zephaniah.folio.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCloseIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioReaderTheme
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.storage.BookRepository
import io.github.tejesh_zephaniah.folio.core.storage.PreferencesManager
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.ReadiumManager
import io.github.tejesh_zephaniah.folio.core.storage.StoredBook
import io.github.tejesh_zephaniah.folio.core.storage.StoredBookmark
import io.github.tejesh_zephaniah.folio.core.storage.StoredHighlight
import io.github.tejesh_zephaniah.folio.core.storage.readerTextScales
import io.github.tejesh_zephaniah.folio.data.model.Book
import io.github.tejesh_zephaniah.folio.data.model.bookmarkPageTitle
import io.github.tejesh_zephaniah.folio.reader.components.ReaderBookmarksSheet
import io.github.tejesh_zephaniah.folio.reader.components.ReaderBottomBar
import io.github.tejesh_zephaniah.folio.reader.components.ReaderSearchSheet
import io.github.tejesh_zephaniah.folio.reader.components.ReaderThemeMenu
import io.github.tejesh_zephaniah.folio.reader.components.ReaderTocSheet
import io.github.tejesh_zephaniah.folio.reader.components.ReaderTopBar
import io.github.tejesh_zephaniah.folio.reader.components.ReadiumController
import io.github.tejesh_zephaniah.folio.reader.components.ReadiumReaderContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.publication.Locator
import java.io.File

private const val READER_CONTROLS_TIMEOUT_MILLIS = 4_000L

data class HighlightColorOption(
    val name: String,
    val displayColor: Color,
    val tint: Int
)

val HighlightPalette = listOf(
    HighlightColorOption("Yellow", Color(0xFFFFEB3B), 0x55FFEB3B),
    HighlightColorOption("Green", Color(0xFF81C784), 0x5581C784),
    HighlightColorOption("Blue", Color(0xFF64B5F6), 0x5564B5F6),
    HighlightColorOption("Pink", Color(0xFFF06292), 0x55F06292),
    HighlightColorOption("Purple", Color(0xFFBA68C8), 0x55BA68C8),
    HighlightColorOption("Orange", Color(0xFFFFB74D), 0x55FFB74D)
)

@Composable
fun ReaderScreen(
    book: Book,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { BookRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    var storedBook by remember(book.id) { mutableStateOf<StoredBook?>(null) }
    var isResolvingBook by remember(book.id) { mutableStateOf(true) }
    LaunchedEffect(book.id) {
        storedBook = repository.getStoredBook(book.id)
        isResolvingBook = false
    }

    val importedBook = storedBook?.takeIf {
        it.fileType.equals("epub", ignoreCase = true) || it.fileType.equals("pdf", ignoreCase = true)
    }

    var readiumController by remember(book.id) { mutableStateOf<ReadiumController?>(null) }
    val renderedPages = readiumController?.renderedPages?.collectAsState()?.value
    val paginationFailed = readiumController?.paginationFailed?.collectAsState()?.value == true
    val reflowsText = readiumController?.reflowsText == true
    val paginationStatus = when {
        !reflowsText || renderedPages != null -> null
        paginationFailed -> "Page count unavailable"
        else -> "Counting pages…"
    }

    var currentPage by remember(book.id) { mutableIntStateOf(book.currentPage.coerceAtLeast(1)) }
    var lastCountedPage by remember(book.id) { mutableIntStateOf(book.currentPage) }
    var totalPages by remember(book.id) {
        mutableIntStateOf(if (book.totalPages > 0) book.totalPages else 1)
    }

    var readerTheme by remember {
        mutableStateOf(PreferencesManager.getReaderThemeMode(context))
    }
    var textStep by remember { mutableIntStateOf(PreferencesManager.getReaderTextStep(context)) }

    // Overlays & Sheets visibility
    var areControlsVisible by remember(book.id) { mutableStateOf(true) }
    var isThemeMenuVisible by remember { mutableStateOf(false) }
    var isBookmarksSheetVisible by remember { mutableStateOf(false) }
    var isSearchSheetVisible by remember { mutableStateOf(false) }
    var isTocSheetVisible by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var footerHeight by remember { mutableIntStateOf(0) }

    // Highlight state
    var savedHighlights by remember(book.id) { mutableStateOf<List<StoredHighlight>>(emptyList()) }
    var pendingHighlightLocator by remember { mutableStateOf<Locator?>(null) }
    var isHighlightPickerVisible by remember { mutableStateOf(false) }
    var highlightToRemove by remember(book.id) { mutableStateOf<String?>(null) }

    var activeControlTouches by remember(book.id) { mutableIntStateOf(0) }
    var completedControlTouches by remember(book.id) { mutableIntStateOf(0) }
    val hasOpenReaderOverlay = isThemeMenuVisible || isBookmarksSheetVisible ||
        isSearchSheetVisible || isTocSheetVisible || isHighlightPickerVisible || highlightToRemove != null
    val shouldHideControls = readiumController != null && areControlsVisible &&
        !hasOpenReaderOverlay && activeControlTouches == 0
    val controlsTimeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        READER_CONTROLS_TIMEOUT_MILLIS,
        containsIcons = true,
        containsText = true,
        containsControls = true
    ) ?: READER_CONTROLS_TIMEOUT_MILLIS
    LaunchedEffect(book.id, shouldHideControls, completedControlTouches, controlsTimeout) {
        if (!shouldHideControls) return@LaunchedEffect
        delay(controlsTimeout)
        areControlsVisible = false
    }

    // Observe the gesture without consuming it so controls such as the slider can keep handling it.
    val keepControlsWhileTouched = Modifier.pointerInput(book.id) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            activeControlTouches++
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                activeControlTouches--
                completedControlTouches++
            }
        }
    }

    fun removeHighlights(ids: Set<String>) {
        highlightToRemove = null
        scope.launch {
            ids.forEach { repository.deleteHighlight(it) }
            savedHighlights = repository.getHighlights(book.id)
        }
    }

    LaunchedEffect(readiumController, savedHighlights) {
        val ctrl = readiumController ?: return@LaunchedEffect
        val decorations = savedHighlights.mapNotNull { highlight ->
            val locator = ReadiumManager.parseLocatorJson(highlight.locatorJson) ?: return@mapNotNull null
            Decoration(highlight.id, locator, Decoration.Style.Highlight(tint = highlight.color, isActive = false))
        }
        ctrl.applyDecorations(decorations)
    }

    // Saved Bookmarks for this book
    var bookmarks by remember(book.id) { mutableStateOf<List<StoredBookmark>>(emptyList()) }
    LaunchedEffect(book.id) {
        bookmarks = repository.getBookmarks(book.id)
        savedHighlights = repository.getHighlights(book.id)
    }

    var isBookmarked by remember(book.id, bookmarks) {
        mutableStateOf(bookmarks.isNotEmpty() || PreferencesManager.isBookmarked(context, book.id))
    }

    BackHandler {
        when {
            isHighlightPickerVisible -> {
                isHighlightPickerVisible = false
                pendingHighlightLocator = null
                readiumController?.clearSelection()
            }
            isThemeMenuVisible -> isThemeMenuVisible = false
            isBookmarksSheetVisible -> isBookmarksSheetVisible = false
            isSearchSheetVisible -> isSearchSheetVisible = false
            isTocSheetVisible -> isTocSheetVisible = false
            else -> onNavigateBack()
        }
    }

    val currentBgColor = Color(readerTheme.backgroundArgb)
    val density = LocalDensity.current
    val topClearance = with(density) { headerHeight.toDp() } + 6.dp
    // Keep the same viewport when the header and footer fade away.
    val footerClearance = with(density) { footerHeight.toDp() }
    val bottomClearance = footerClearance + 6.dp

    FolioReaderTheme(readerTheme) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(currentBgColor)
                .safeDrawingPadding()
        ) {
            if (importedBook != null) {
                ReadiumReaderContainer(
                    bookFile = File(importedBook.filePath),
                    fileType = importedBook.fileType,
                    initialLocatorJson = importedBook.lastLocatorJson,
                    readerTheme = readerTheme,
                    fontScale = readerTextScales[textStep],
                    onLocatorChanged = { loc ->
                        val ctrl = readiumController
                        val page = ctrl?.pageOf(loc)
                        if (ctrl != null && page != null) {
                            totalPages = ctrl.totalPages
                            currentPage = page.coerceIn(1, totalPages)
                            lastCountedPage = currentPage
                        }
                        repository.updateProgress(
                            book.id,
                            lastCountedPage,
                            totalPages,
                            loc.toJSON().toString()
                        )
                    },
                    onCenterTap = {
                        areControlsVisible = !areControlsVisible
                        if (isThemeMenuVisible) isThemeMenuVisible = false
                    },
                    onControllerReady = { ctrl ->
                        readiumController = ctrl
                        if (!ctrl.reflowsText) totalPages = ctrl.totalPages
                    },
                    onHighlightRequested = { locator ->
                        isThemeMenuVisible = false
                        pendingHighlightLocator = locator
                        isHighlightPickerVisible = true
                    },
                    onRemoveHighlights = ::removeHighlights,
                    onHighlightedPassageTapped = { id ->
                        if (savedHighlights.any { it.id == id }) highlightToRemove = id
                    },
                    onCloseReader = onNavigateBack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topClearance, bottom = bottomClearance)
                        .clipToBounds()
                )
            } else if (isResolvingBook) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = FolioTheme.extendedColors.accent
                )
            } else {
                // Neutral fallback when book file is not present or invalid
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
                            text = "Book Unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FolioTheme.extendedColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The file for \"${book.title}\" is missing or in an unsupported format.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FolioTheme.extendedColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Back to Library")
                        }
                    }
                }
            }

            // Reader Top Bar
            ReaderTopBar(
                bookTitle = book.title,
                visible = areControlsVisible,
                isBookmarked = isBookmarked,
                onCloseClick = onNavigateBack,
                onSearchClick = { isSearchSheetVisible = true },
                onTocClick = { isTocSheetVisible = true },
                onBookmarksClick = { isBookmarksSheetVisible = true },
                onBookmarkClick = {
                    isBookmarked = !isBookmarked
                    PreferencesManager.setBookmarked(context, book.id, isBookmarked)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { if (it.height > 0) headerHeight = it.height }
                    .then(keepControlsWhileTouched)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = footerClearance),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Theme Menu
                AnimatedVisibility(
                    visible = isThemeMenuVisible && areControlsVisible,
                    enter = androidx.compose.animation.scaleIn(
                        transformOrigin = TransformOrigin(0.88f, 0.95f),
                        initialScale = 0.96f,
                        animationSpec = androidx.compose.animation.core.tween(110)
                    ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(95)),
                    exit = androidx.compose.animation.scaleOut(
                        transformOrigin = TransformOrigin(0.88f, 0.95f),
                        targetScale = 0.97f,
                        animationSpec = androidx.compose.animation.core.tween(85)
                    ) + fadeOut(animationSpec = androidx.compose.animation.core.tween(85)),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    ReaderThemeMenu(
                        selectedTheme = readerTheme,
                        onThemeSelected = { newTheme ->
                            readerTheme = newTheme
                            PreferencesManager.setReaderThemeMode(context, newTheme)
                        },
                        textStep = textStep.takeIf { reflowsText },
                        onTextStepChange = { step ->
                            textStep = step
                            PreferencesManager.setReaderTextStep(context, step)
                        }
                    )
                }

                // Floating Highlight Color Picker Pill (appears on text selection -> Highlight)
                AnimatedVisibility(
                    visible = isHighlightPickerVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        tonalElevation = 12.dp,
                        shadowElevation = 16.dp,
                        border = BorderStroke(1.dp, FolioTheme.extendedColors.textSecondary.copy(alpha = 0.18f))
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Highlight",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FolioTheme.extendedColors.textSecondary,
                                modifier = Modifier.padding(end = 4.dp)
                            )

                            HighlightPalette.forEach { opt ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .semantics { contentDescription = opt.name }
                                        .clip(CircleShape)
                                        .background(opt.displayColor)
                                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                        .clickable {
                                            val loc = pendingHighlightLocator
                                            if (loc != null) {
                                                pendingHighlightLocator = null
                                                isHighlightPickerVisible = false
                                                scope.launch {
                                                    repository.addHighlight(book.id, loc.toJSON().toString(), opt.tint)
                                                    savedHighlights = repository.getHighlights(book.id)
                                                    readiumController?.clearSelection()
                                                }
                                            }
                                        }
                                )
                            }

                            IconButton(
                                onClick = {
                                    readiumController?.clearSelection()
                                    isHighlightPickerVisible = false
                                    pendingHighlightLocator = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                FolioCloseIcon(
                                    tint = FolioTheme.extendedColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

            }

            // The footer alone reserves space in the reading viewport.
            ReaderBottomBar(
                currentPage = currentPage,
                totalPages = totalPages,
                visible = areControlsVisible,
                keepSliderSpace = reflowsText,
                paginationStatus = paginationStatus,
                onOptionsClick = {
                    isHighlightPickerVisible = false
                    pendingHighlightLocator = null
                    isThemeMenuVisible = !isThemeMenuVisible
                },
                onPageSelected = { readiumController?.goToPage(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .onSizeChanged { if (it.height > 0) footerHeight = it.height }
                    .then(keepControlsWhileTouched)
            )

            highlightToRemove?.let { id ->
                AlertDialog(
                    onDismissRequest = { highlightToRemove = null },
                    title = { Text("Remove Highlight?") },
                    confirmButton = { TextButton(onClick = { removeHighlights(setOf(id)) }) { Text("Remove Highlight") } },
                    dismissButton = { TextButton(onClick = { highlightToRemove = null }) { Text("Cancel") } }
                )
            }

            // Bookmarks Modal Sheet
            ReaderBookmarksSheet(
                visible = isBookmarksSheetVisible,
                bookmarks = bookmarks,
                totalPages = totalPages,
                renderedPageOf = if (reflowsText) ({ locator -> renderedPages?.pageOf(locator) }) else null,
                onDismiss = { isBookmarksSheetVisible = false },
                onAddCurrentBookmark = {
                    scope.launch {
                        val ctrl = readiumController ?: return@launch
                        val loc = ctrl.currentLocator ?: return@launch
                        val page = ctrl.pageOf(loc)?.coerceIn(1, totalPages)

                        repository.addBookmark(
                            bookId = book.id,
                            title = page?.let { bookmarkPageTitle(it, totalPages) } ?: "Bookmark",
                            locatorJson = loc.toJSON().toString(),
                            chapter = loc.title ?: "Chapter",
                            progression = page?.let { it.toFloat() / totalPages }
                                ?: (loc.locations.totalProgression ?: 0.0).toFloat(),
                            excerpt = loc.text.highlight.orEmpty()
                        )
                        bookmarks = repository.getBookmarks(book.id)
                        isBookmarked = true
                    }
                },
                onBookmarkClick = { bm ->
                    isBookmarksSheetVisible = false
                    val loc = ReadiumManager.parseLocatorJson(bm.locatorJson)
                    if (loc != null) {
                        val page = renderedPages?.pageOf(loc)
                        if (page != null) readiumController?.goToPage(page) else readiumController?.go(loc)
                    }
                },
                onDeleteBookmark = { bm ->
                    scope.launch {
                        repository.deleteBookmark(bm.id)
                        bookmarks = repository.getBookmarks(book.id)
                        if (bookmarks.isEmpty()) {
                            isBookmarked = false
                            PreferencesManager.setBookmarked(context, book.id, false)
                        }
                    }
                }
            )

            // Table of Contents Sheet
            ReaderTocSheet(
                visible = isTocSheetVisible,
                publication = readiumController?.publication,
                book = book,
                onDismiss = { isTocSheetVisible = false },
                onEntryClick = { entry ->
                    isTocSheetVisible = false
                    if (entry.link != null) {
                        readiumController?.go(entry.link)
                    }
                }
            )

            // Search in Book Sheet
            ReaderSearchSheet(
                visible = isSearchSheetVisible,
                publication = readiumController?.publication,
                book = book,
                showPageNumbers = !reflowsText,
                onDismiss = { isSearchSheetVisible = false },
                onResultClick = { res ->
                    isSearchSheetVisible = false
                    if (res.locator != null) {
                        readiumController?.go(res.locator)
                    }
                }
            )
        }
    }
}
