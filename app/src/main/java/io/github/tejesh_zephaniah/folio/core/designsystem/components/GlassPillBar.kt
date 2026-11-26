package io.github.tejesh_zephaniah.folio.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.GlassBackdrop
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.captureGlassSource
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.rememberGlassSource
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.GlassSource
import io.github.tejesh_zephaniah.folio.core.designsystem.effects.rememberGlassRim
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FolioTab(val label: String) {
    Home("Home"),
    Library("Library"),
    Explore("Explore"),
    Search("Search")
}

@Composable
internal fun GlassPillBar(
    selectedTab: FolioTab,
    onTabSelected: (FolioTab) -> Unit,
    backdrop: GlassSource,
    modifier: Modifier = Modifier,
    libraryReceivePulseTick: Int = 0,
    onLibraryIconPositioned: (Offset) -> Unit = {}
) {
    val tabHeight = 48.dp
    val spacing = 4.dp
    val inset = 6.dp
    val tabs = FolioTab.entries
    val density = LocalDensity.current
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val tabWidth = ((windowWidth - 24.dp - inset * 2 - spacing * tabs.lastIndex) / tabs.size)
        .coerceIn(1.dp, 76.dp)
    val tabStep = tabWidth + spacing
    val firstCenter = inset + tabWidth / 2

    var held by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableStateOf<Float?>(null) }
    var releaseIndex by remember { mutableStateOf<Float?>(null) }
    var releaseCount by remember { mutableIntStateOf(0) }
    val previewIndex by remember { derivedStateOf { dragIndex?.roundToInt() } }
    val animatedIndex = remember { Animatable(selectedTab.ordinal.toFloat()) }
    val libraryCatchScale = remember { Animatable(1f) }
    val selectTab by rememberUpdatedState(onTabSelected)

    LaunchedEffect(libraryReceivePulseTick) {
        if (libraryReceivePulseTick > 0) {
            libraryCatchScale.snapTo(1f)
            libraryCatchScale.animateTo(1.26f, tween(130))
            libraryCatchScale.animateTo(
                1f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    var barCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dockSurface = rememberGlassSource()

    LaunchedEffect(selectedTab, releaseCount) {
        releaseIndex?.let {
            animatedIndex.snapTo(it)
            releaseIndex = null
        }
        animatedIndex.animateTo(
            selectedTab.ordinal.toFloat(),
            tween(235)
        )
    }

    val pop = animateFloatAsState(
        targetValue = if (held || previewIndex != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
        label = "Tab pop"
    )
    val indicatorIndex = { dragIndex ?: releaseIndex ?: animatedIndex.value }
    val highlightedTab = previewIndex?.let(tabs::get) ?: selectedTab
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.45f
    val selectedInk = lerp(colors.primary, colors.onSurface, if (dark) 0.26f else 0.62f)
    val contrast = if (dark) Color.Black else Color.White
    val labelShadow = with(density) {
        Shadow(contrast.copy(alpha = 0.90f), Offset(0f, 0.35.dp.toPx()), 1.1.dp.toPx())
    }
    val pillTranslation = {
        with(density) {
            val distance = tabStep.toPx() * indicatorIndex()
            if (layoutDirection == LayoutDirection.Ltr) distance else -distance
        }
    }
    val rim = rememberGlassRim()

    Box(
        modifier = modifier
            .onGloballyPositioned { barCoordinates = it }
            .selectableGroup()
            .pointerInput(layoutDirection, tabStep) {
                fun indexAt(x: Float): Float {
                    val distance = if (layoutDirection == LayoutDirection.Ltr) x else size.width - x
                    return (distance - firstCenter.toPx()) / tabStep.toPx()
                }

                coroutineScope {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val grip = indexAt(down.position.x) - indicatorIndex()
                        val onPill = abs(grip) <= tabWidth / tabStep / 2f
                        val grabOffset = if (onPill) grip else 0f
                        fun positionAt(x: Float) =
                            (indexAt(x) - grabOffset).coerceIn(0f, tabs.lastIndex.toFloat())

                        val hold = if (onPill) launch {
                            delay(140)
                            held = true
                        } else null
                        var landed = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (!change.pressed) {
                                    landed = !change.isConsumed
                                    if (dragIndex != null && landed) {
                                        dragIndex = positionAt(change.position.x)
                                        change.consume()
                                    }
                                    break
                                }
                                if (change.isConsumed) break
                                if (dragIndex == null) {
                                    val movement = change.position - down.position
                                    val slop = viewConfiguration.touchSlop
                                    if (abs(movement.y) > slop && abs(movement.y) > abs(movement.x)) break
                                    if (abs(movement.x) < slop) continue
                                    hold?.cancel()
                                }
                                change.consume()
                                dragIndex = positionAt(change.position.x)
                            }
                        } finally {
                            hold?.cancel()
                            held = false
                            dragIndex?.let { index ->
                                releaseIndex = index
                                dragIndex = null
                                if (landed) selectTab(tabs[index.roundToInt()])
                                releaseCount++
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopStart
    ) {
        GlassBackdrop(
            source = backdrop,
            targetCoordinates = { barCoordinates },
            modifier = Modifier
                .matchParentSize()
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .captureGlassSource(dockSurface)
                .border(1.dp, rim, CircleShape)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(inset.roundToPx(), 5.dp.roundToPx()) }
                .size(tabWidth, tabHeight)
                .graphicsLayer {
                    val expanded = pop.value
                    translationX = pillTranslation()
                    scaleX = 1f + expanded * 0.10f
                    scaleY = 1f + expanded * 0.15f
                    shadowElevation = expanded.coerceAtLeast(0f) * 5.dp.toPx()
                    shape = CircleShape
                }
        ) {
            GlassBackdrop(
                // The lens samples the already frosted dock strip instead of replaying the page.
                source = dockSurface,
                targetCoordinates = { barCoordinates },
                sampleOffset = {
                    with(density) {
                        val start = if (layoutDirection == LayoutDirection.Ltr) inset.roundToPx() else {
                            (barCoordinates?.size?.width ?: 0) - inset.roundToPx() - tabWidth.roundToPx()
                        }
                        Offset(start + pillTranslation(), 5.dp.roundToPx().toFloat())
                    }
                },
                isLens = true,
                modifier = Modifier.matchParentSize().clip(CircleShape).border(1.dp, rim, CircleShape)
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = inset, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            tabs.forEach { tab ->
                val highlighted = tab == highlightedTab
                val ink by animateColorAsState(
                    if (highlighted) selectedInk else colors.onSurface,
                    animationSpec = tween(120),
                    label = "Tab ink"
                )
                Column(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(tabHeight)
                        .clip(CircleShape)
                        .selectable(
                            selected = tab == selectedTab,
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) }
                        )
                        .graphicsLayer {
                            val proximity = (1f - 1.5f * abs(indicatorIndex() - tab.ordinal))
                                .coerceAtLeast(0f)
                            val expanded = pop.value * proximity
                            val catchScale = if (tab == FolioTab.Library) libraryCatchScale.value else 1f
                            scaleX = (1f + expanded * 0.06f) * catchScale
                            scaleY = scaleX
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .then(
                                if (tab == FolioTab.Library) {
                                    Modifier.onGloballyPositioned { coords ->
                                        val origin = coords.positionInWindow()
                                        onLibraryIconPositioned(
                                            Offset(
                                                origin.x + coords.size.width / 2f,
                                                origin.y + coords.size.height / 2f
                                            )
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        tab.Icon(
                            tint = contrast.copy(alpha = 0.62f),
                            modifier = Modifier.matchParentSize().graphicsLayer {
                                translationY = 0.5.dp.toPx()
                            }
                        )
                        tab.Icon(tint = ink, modifier = Modifier.matchParentSize())
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                            shadow = labelShadow
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FolioTab.Icon(tint: Color, modifier: Modifier) {
    when (this) {
        FolioTab.Home -> FolioHomeIcon(tint, modifier)
        FolioTab.Library -> FolioLibraryIcon(tint, modifier)
        FolioTab.Explore -> FolioExploreIcon(tint, modifier)
        FolioTab.Search -> FolioSearchIcon(tint, modifier)
    }
}
