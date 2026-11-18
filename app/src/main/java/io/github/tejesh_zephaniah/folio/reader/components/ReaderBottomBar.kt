package io.github.tejesh_zephaniah.folio.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioMoreDotsIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassCircleButton
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import kotlin.math.roundToInt
import io.github.tejesh_zephaniah.folio.data.model.pagePercentage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    visible: Boolean,
    keepSliderSpace: Boolean = false,
    paginationStatus: String? = null,
    onOptionsClick: () -> Unit,
    onPageSelected: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var scrubPage by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat().coerceAtLeast(1f)) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, FolioTheme.extendedColors.textSecondary.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Interactive Scrubber Slider with breathing space
                    if (totalPages > 1 || keepSliderSpace) {
                        Slider(
                            value = if (isDragging) scrubPage else currentPage.toFloat().coerceIn(1f, totalPages.toFloat()),
                            onValueChange = { newVal ->
                                isDragging = true
                                scrubPage = newVal
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                onPageSelected(scrubPage.roundToInt().coerceIn(1, totalPages))
                            },
                            enabled = paginationStatus == null && totalPages > 1,
                            valueRange = 1f..maxOf(2, totalPages).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = FolioTheme.extendedColors.accent,
                                activeTrackColor = FolioTheme.extendedColors.accent,
                                inactiveTrackColor = FolioTheme.extendedColors.textSecondary.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Page Indicator ("7 of 91") & Options button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Invisible placeholder to keep page indicator centered
                        Box(modifier = Modifier.size(36.dp))

                        val displayPage = if (isDragging) scrubPage.roundToInt().coerceIn(1, totalPages) else currentPage.coerceIn(1, totalPages)
                        val percent = pagePercentage(displayPage, totalPages)

                        Text(
                            text = paginationStatus ?: "$displayPage of $totalPages ($percent%)",
                            color = FolioTheme.extendedColors.textPrimary.copy(alpha = 0.90f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        GlassCircleButton(
                            onClick = onOptionsClick,
                            size = 36.dp
                        ) {
                            FolioMoreDotsIcon(
                                tint = FolioTheme.extendedColors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
