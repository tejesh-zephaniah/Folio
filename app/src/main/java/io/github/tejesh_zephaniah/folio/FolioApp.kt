package io.github.tejesh_zephaniah.folio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.tejesh_zephaniah.folio.bookshelf.BookActions
import io.github.tejesh_zephaniah.folio.bookshelf.BookshelfScreen
import io.github.tejesh_zephaniah.folio.bookshelf.rememberBookshelfState
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

    // App Appearance Mode State (System / Light / Dark)
    var appThemeMode by remember {
        mutableStateOf(PreferencesManager.getAppThemeMode(context))
    }

    // Active Tab in Browsing Mode
    var currentTab by remember { mutableStateOf(FolioTab.Library) }

    // Every normal app launch starts at the Library screen (Priority 4)
    var currentScreen by remember { mutableStateOf<FolioScreen>(FolioScreen.Browsing(FolioTab.Library)) }

    val bookshelfState = rememberBookshelfState(context)
    val exploreScope = rememberCoroutineScope()
    val exploreSession = remember(context, exploreScope) { ExploreSession(context, exploreScope) }
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
        PreferencesManager.setLastOpenedBookId(context, latestBook.id)
        bookshelfState.refreshRecentlyRead(PreferencesManager.getRecentlyReadBookIds(context))
        currentScreen = FolioScreen.Reader(latestBook)
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
        Box(modifier = modifier.fillMaxSize()) {
            // Main Content Screen Transition (Book-Opening Animation)
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState is FolioScreen.Reader) {
                        // Transition INTO book
                        (scaleIn(
                            initialScale = 0.90f,
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(350))) togetherWith
                        (scaleOut(
                            targetScale = 1.05f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(300)))
                    } else {
                        // Transition BACK to shelf
                        (scaleIn(
                            initialScale = 1.05f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(300))) togetherWith
                        (scaleOut(
                            targetScale = 0.90f,
                            animationSpec = tween(350, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(350)))
                    }
                },
                label = "ScreenTransition",
                modifier = Modifier.fillMaxSize()
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
                                    SlidingTab(currentTab) {
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
                                                    onBookOpen = { openBook(it) }
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
                                                ExploreScreen(
                                                    session = exploreSession,
                                                    onLeave = { currentTab = FolioTab.Library },
                                                    modifier = Modifier.statusBarsPadding().padding(bottom = bottom)
                                                )
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
                                    backdrop = if (isExploring) themeBackdrop else backdrop
                                )
                            }
                            BookActions(bookshelfState)
                        }
                    }

                    is FolioScreen.Reader -> {
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
        }
    }
}

@Composable
private fun SlidingTab(tab: FolioTab, content: @Composable () -> Unit) {
    var previousTab by remember { mutableStateOf(tab) }
    val slide = remember(tab) { Animatable(tab.ordinal.compareTo(previousTab.ordinal).toFloat()) }
    val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    LaunchedEffect(slide) {
        previousTab = tab
        if (slide.value != 0f) slide.animateTo(0f, tween(165, easing = FastOutSlowInEasing))
    }
    // Move a single display layer. A full-screen crossfade needs two rendered tabs at once.
    Box(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationX = 22.dp.toPx() * layoutSign * slide.value
            }
        ) {
            key(tab) { content() }
        }
    }
}
