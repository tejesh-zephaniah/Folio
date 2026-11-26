package io.github.tejesh_zephaniah.folio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.tejesh_zephaniah.folio.bookshelf.BookActions
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfScreen
import io.github.tejesh_zephaniah.folio.bookshelf.rememberBookshelfState
import io.github.tejesh_zephaniah.folio.core.FolioWebView
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioBookBadgeIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioTab
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassPillBar
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.LocalSearchGlassSource
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.captureGlassSource
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.rememberGlassSource
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.browsingBackdrop
import io.github.tejesh_zephaniah.folio.core.storage.AppThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.BookImporter
import io.github.tejesh_zephaniah.folio.core.storage.PreferencesManager
import io.github.tejesh_zephaniah.folio.data.model.Book
import io.github.tejesh_zephaniah.folio.explore.ExploreMessage
import io.github.tejesh_zephaniah.folio.explore.ExploreScreen
import io.github.tejesh_zephaniah.folio.explore.ExploreSession
import io.github.tejesh_zephaniah.folio.home.HomeScreen
import io.github.tejesh_zephaniah.folio.reader.ReaderScreen
import io.github.tejesh_zephaniah.folio.search.SearchScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private data class JumpingBookEvent(
    val id: String,
    val startInWindow: Offset
)

sealed interface FolioScreen {
    data class Browsing(val tab: FolioTab = FolioTab.Library) : FolioScreen
    data class Reader(val book: Book) : FolioScreen
}

internal fun resolveLastSessionBook(
    lastOpenedBookId: String?,
    library: List<Book>
): Book? = library.firstOrNull { it.id == lastOpenedBookId }

@Composable
fun FolioApp(
    incomingUri: android.net.Uri? = null,
    onIncomingUriConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exploreWebReady by produceState<Boolean?>(null, context) {
        withFrameNanos { }
        value = FolioWebView.awaitReady(context)
    }

    // App Appearance Mode State (System / Light / Dark)
    var appThemeMode by remember {
        mutableStateOf(PreferencesManager.getAppThemeMode(context))
    }

    // Active Tab in Browsing Mode
    var currentTab by remember { mutableStateOf(FolioTab.Library) }

    // Every normal app launch starts at the Library screen (Priority 4)
    var currentScreen by remember { mutableStateOf<FolioScreen>(FolioScreen.Browsing(FolioTab.Library)) }

    val bookshelfState = rememberBookshelfState(context)
    val appScope = rememberCoroutineScope()
    val windowSize = LocalWindowInfo.current.containerSize
    var rootOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var libraryIconInWindow by remember { mutableStateOf<Offset?>(null) }
    var libraryCatchTick by remember { mutableIntStateOf(0) }
    var activeJumps by remember { mutableStateOf<List<JumpingBookEvent>>(emptyList()) }

    val exploreSession = remember(context, appScope, bookshelfState) {
        ExploreSession(
            context = context,
            scope = appScope,
            onBookImported = bookshelfState::showExploreImport,
            onDownloadStarted = bookshelfState::registerExploreDownload,
            onDownloadProgress = bookshelfState::updateExploreDownloadProgress,
            onDownloadReadyToJump = { id, originInWindow ->
                val fallbackStart = Offset(
                    rootOriginInWindow.x + windowSize.width * 0.5f,
                    rootOriginInWindow.y + windowSize.height * 0.45f
                )
                activeJumps = activeJumps + JumpingBookEvent(id, originInWindow ?: fallbackStart)
            },
            onDownloadFinished = bookshelfState::completeExploreDownload,
            onDownloadFailed = bookshelfState::failExploreDownload
        )
    }
    val exploreNotice = exploreSession.notice
    LaunchedEffect(exploreNotice) {
        val notice = exploreNotice ?: return@LaunchedEffect
        delay(3_800)
        exploreSession.dismissNotice(notice)
    }
    DisposableEffect(exploreSession) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = exploreSession.flushSiteData()
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            exploreSession.close()
        }
    }

    fun openBook(book: Book) {
        val latestBook = bookshelfState.uiState.books.firstOrNull { it.id == book.id } ?: book
        bookshelfState.markBookOpened(latestBook.id)
        PreferencesManager.setLastOpenedBookId(context, latestBook.id)
        bookshelfState.refreshRecentlyRead(PreferencesManager.getRecentlyReadBookIds(context))
        currentScreen = FolioScreen.Reader(latestBook)
    }

    fun importBooks(uris: List<android.net.Uri>) {
        appScope.launch {
            var firstImported: Book? = null
            for (uri in uris) {
                val book = BookImporter.importBook(context, uri).getOrNull()
                if (firstImported == null) firstImported = book
            }
            if (currentScreen is FolioScreen.Browsing && currentTab == FolioTab.Library) {
                firstImported?.let(::openBook)
            }
        }
    }

    // Handle intent-based incoming books (Open With)
    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            val result = BookImporter.importBook(context, incomingUri)
            onIncomingUriConsumed()
            result.onSuccess(::openBook).onFailure { err ->
                android.util.Log.e("FolioApp", "Import failure: ${err.message}", err)
            }
        }
    }

    FolioTheme(appThemeMode = appThemeMode) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOriginInWindow = it.positionInWindow() }
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    scaleIn(
                        initialScale = if (targetState is FolioScreen.Reader) 0.975f else 1.017f,
                        animationSpec = tween(215, easing = FastOutSlowInEasing)
                    ) togetherWith ExitTransition.None
                },
                modifier = Modifier.fillMaxSize(),
                label = "Book transition"
            ) { screen ->
                when (screen) {
                    is FolioScreen.Browsing -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            var navigationHeight by remember { mutableIntStateOf(0) }
                            val backdrop = rememberGlassSource()
                            val themeBackdrop = rememberGlassSource()
                            val isExploring = currentTab == FolioTab.Explore
                            Box(
                                Modifier.fillMaxSize().then(
                                    if (isExploring) Modifier else Modifier.captureGlassSource(backdrop)
                                )
                            ) {
                                Box(
                                    Modifier.matchParentSize()
                                        .captureGlassSource(themeBackdrop)
                                        .browsingBackdrop(appThemeMode, MaterialTheme.colorScheme.background)
                                )
                                CompositionLocalProvider(LocalSearchGlassSource provides themeBackdrop) {
                                    TabGlide(currentTab) {
                                        when (currentTab) {
                                            FolioTab.Home -> {
                                                val continueReadingId = PreferencesManager.getLastOpenedBookId(context)
                                                val continueBook = resolveLastSessionBook(continueReadingId, bookshelfState.uiState.books)
                                                val recentBooks = bookshelfState.uiState.recentlyReadIds
                                                    .mapNotNull { id -> bookshelfState.uiState.books.firstOrNull { it.id == id } }
                                                    .filter { it.id != continueBook?.id }

                                                HomeScreen(
                                                    continueReadingBook = continueBook,
                                                    recentlyReadBooks = recentBooks,
                                                    onBookOpen = { openBook(it) },
                                                    onBookOptions = bookshelfState::onOpenBookDetails,
                                                    onNavigateToLibrary = { currentTab = FolioTab.Library }
                                                )
                                            }

                                            FolioTab.Library -> {
                                                BookshelfScreen(
                                                    state = bookshelfState,
                                                    appThemeMode = appThemeMode,
                                                    onAppThemeChange = { newMode ->
                                                        appThemeMode = newMode
                                                        PreferencesManager.setAppThemeMode(context, newMode)
                                                    },
                                                    onBookOpen = { openBook(it) },
                                                    onImportBooks = ::importBooks
                                                )
                                            }

                                            FolioTab.Search -> {
                                                SearchScreen(
                                                    onBookOpen = { openBook(it) },
                                                    onBookOptions = bookshelfState::onOpenBookDetails
                                                )
                                            }

                                            FolioTab.Explore -> {
                                                val bottom = with(LocalDensity.current) { navigationHeight.toDp() }
                                                val safeTop = Modifier.statusBarsPadding()
                                                when (exploreWebReady) {
                                                    true -> ExploreScreen(
                                                        session = exploreSession,
                                                        onLeave = { currentTab = FolioTab.Library },
                                                        modifier = safeTop.padding(bottom = bottom)
                                                    )
                                                    false -> Text("Explore is unavailable.", safeTop.padding(20.dp))
                                                    null -> LinearProgressIndicator(safeTop.fillMaxWidth())
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Floating Liquid Glass Navigation Bar (Pinned at Bottom Center)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .onSizeChanged { navigationHeight = it.height }
                                    .navigationBarsPadding()
                                    .padding(bottom = 7.dp)
                            ) {
                                GlassPillBar(
                                    selectedTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    backdrop = if (isExploring) themeBackdrop else backdrop,
                                    libraryReceivePulseTick = libraryCatchTick,
                                    onLibraryIconPositioned = { libraryIconInWindow = it }
                                )
                            }
                            BookActions(bookshelfState)
                        }
                    }

                    is FolioScreen.Reader -> key(screen.book.id) {
                        ReaderScreen(
                            book = screen.book,
                            onNavigateBack = {
                                bookshelfState.refreshRecentlyRead(PreferencesManager.getRecentlyReadBookIds(context))
                                currentScreen = FolioScreen.Browsing(currentTab)
                            }
                        )
                    }
                }
            }
            exploreNotice?.let {
                ExploreMessage(
                    notice = it,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
            activeJumps.forEach { jump ->
                key(jump.id) {
                    val fallbackTarget = Offset(
                        rootOriginInWindow.x + windowSize.width * 0.38f,
                        rootOriginInWindow.y + windowSize.height * 0.90f
                    )
                    JumpingBookOverlay(
                        startOffset = jump.startInWindow - rootOriginInWindow,
                        targetOffset = (libraryIconInWindow ?: fallbackTarget) - rootOriginInWindow,
                        onLanded = {
                            libraryCatchTick++
                            bookshelfState.revealExploreDownloadInLibrary(jump.id)
                            activeJumps = activeJumps.filterNot { it.id == jump.id }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpingBookOverlay(
    startOffset: Offset,
    targetOffset: Offset,
    onLanded: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val density = LocalDensity.current
    val badgeSize = 34.dp
    val halfBadgePx = with(density) { (badgeSize / 2).toPx() }
    val arcLiftPx = with(density) { 108.dp.toPx() }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
        )
        onLanded()
    }

    val t = progress.value
    val x = startOffset.x + (targetOffset.x - startOffset.x) * t
    val linearY = startOffset.y + (targetOffset.y - startOffset.y) * t
    val arcY = linearY - sin(t * PI).toFloat() * arcLiftPx
    val badgeScale = when {
        t < 0.25f -> 0.72f + (t / 0.25f) * 0.42f
        else -> 1.14f - ((t - 0.25f) / 0.75f) * 0.56f
    }
    val tiltDeg = -12f + 22f * t
    val badgeAlpha = if (t > 0.9f) ((1f - t) / 0.1f).coerceIn(0f, 1f) else 1f
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (x - halfBadgePx).roundToInt(),
                    (arcY - halfBadgePx).roundToInt()
                )
            }
            .size(badgeSize)
            .graphicsLayer {
                scaleX = badgeScale
                scaleY = badgeScale
                rotationZ = tiltDeg
                alpha = badgeAlpha
            }
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(accent)
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        FolioBookBadgeIcon(
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TabGlide(tab: FolioTab, content: @Composable () -> Unit) {
    var displayedTab by remember { mutableStateOf(tab) }
    val offset = remember(tab) { Animatable(tab.ordinal.compareTo(displayedTab.ordinal).toFloat()) }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    LaunchedEffect(offset) {
        displayedTab = tab
        if (offset.value != 0f) {
            // Give the incoming screen a frame to mount, then move only its drawing layer.
            withFrameNanos { }
            offset.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = 22.dp.toPx() * direction * offset.value
        }) {
            key(tab) { content() }
        }
    }
}
