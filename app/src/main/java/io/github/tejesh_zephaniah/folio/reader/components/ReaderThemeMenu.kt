package io.github.tejesh_zephaniah.folio.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioCheckmarkIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.components.GlassSurface
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.core.storage.ReaderThemeMode
import io.github.tejesh_zephaniah.folio.core.storage.readerTextScales
import kotlin.math.roundToInt

@Composable
fun ReaderThemeMenu(
    selectedTheme: ReaderThemeMode,
    onThemeSelected: (ReaderThemeMode) -> Unit,
    textStep: Int?,
    onTextStepChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "READING APPEARANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = FolioTheme.extendedColors.textPrimary.copy(alpha = 0.75f)
                )

                Text(
                    text = selectedTheme.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FolioTheme.extendedColors.accent
                )
            }

            if (textStep != null) {
                ReaderTextSizeControl(textStep, onTextStepChange)
            }

            // Theme Swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReaderThemeMode.entries.forEach { mode ->
                    val isSelected = mode == selectedTheme
                    val bgColor = Color(mode.backgroundArgb)
                    val textColor = Color(mode.textPrimaryArgb)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { onThemeSelected(mode) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .shadow(elevation = if (isSelected) 4.dp else 1.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) FolioTheme.extendedColors.accent else Color(0x33000000),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                FolioCheckmarkIcon(
                                    tint = if (mode == ReaderThemeMode.DARK) Color.White else FolioTheme.extendedColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = "Aa",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                            }
                        }

                        Text(
                            text = mode.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = FolioTheme.extendedColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTextSizeControl(step: Int, onChange: (Int) -> Unit) {
    val ink = FolioTheme.extendedColors.textPrimary
    val accent = FolioTheme.extendedColors.accent
    val sizePercent = (readerTextScales[step] * 100).roundToInt()
    Column(
        modifier = Modifier.fillMaxWidth().semantics { stateDescription = "Text size ${sizePercent}%" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
                .background(ink.copy(alpha = 0.07f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canDecrease = step > 0
            val canIncrease = step < readerTextScales.lastIndex
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(
                    enabled = canDecrease, role = Role.Button, onClickLabel = "Decrease text size"
                ) { onChange(step - 1) },
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = ink.copy(alpha = if (canDecrease) 1f else 0.35f),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(1.dp).height(24.dp).background(ink.copy(alpha = 0.15f)))
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable(
                    enabled = canIncrease, role = Role.Button, onClickLabel = "Increase text size"
                ) { onChange(step + 1) },
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = ink.copy(alpha = if (canIncrease) 1f else 0.35f),
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(
            modifier = Modifier.height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            readerTextScales.indices.forEach { index ->
                val color = when {
                    index == step -> accent
                    index < step -> ink.copy(alpha = 0.72f)
                    else -> ink.copy(alpha = 0.22f)
                }
                Box(Modifier.size(if (index == step) 7.dp else 5.dp).background(color, CircleShape))
            }
        }
    }
}
