package io.github.tejesh_zephaniah.folio.reader.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.components.FolioSearchIcon
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme
import io.github.tejesh_zephaniah.folio.data.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.use

data class ReaderSearchResult(
    val title: String,
    val snippet: String,
    val locator: Locator?,
    val pageNumber: Int?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSearchSheet(
    visible: Boolean,
    publication: Publication?,
    book: Book,
    showPageNumbers: Boolean = true,
    onDismiss: () -> Unit,
    onResultClick: (ReaderSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<ReaderSearchResult>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }

    fun executeSearch() {
        val q = query.trim()
        if (q.isBlank()) return
        keyboardController?.hide()
        isSearching = true
        hasSearched = true

        scope.launch {
            val list = mutableListOf<ReaderSearchResult>()
            if (publication != null) {
                withContext(Dispatchers.IO) {
                    try {
                        publication.search(q)?.use { iterator ->
                            while (list.size < 50) {
                                coroutineContext.ensureActive()
                                val collection = iterator.next()
                                    .onFailure { Log.w("FolioReader", "Publication search failed: $it") }
                                    .getOrNull() ?: break
                                if (collection.locators.isEmpty()) break
                                for (loc in collection.locators.take(50 - list.size)) {
                                    val text = loc.text
                                    val snippet = listOfNotNull(text.before, text.highlight, text.after).joinToString(" ").trim()
                                    list.add(
                                        ReaderSearchResult(
                                            title = loc.title ?: "Match in book",
                                            snippet = snippet.ifBlank { "Match found" },
                                            locator = loc,
                                            pageNumber = loc.locations.position
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("FolioReader", "Could not search the publication", e)
                    }
                }
            } else {
                // Search in mock book chapters
                for (chap in book.chapters) {
                    for (para in chap.content) {
                        if (para.contains(q, ignoreCase = true)) {
                            list.add(
                                ReaderSearchResult(
                                    title = chap.title,
                                    snippet = para.trim(),
                                    locator = null,
                                    pageNumber = chap.chapterNumber
                                )
                            )
                        }
                    }
                }
            }
            results = list
            isSearching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Search in Book",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = FolioTheme.extendedColors.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Find words or phrases…", color = FolioTheme.extendedColors.textTertiary) },
                singleLine = true,
                leadingIcon = {
                    FolioSearchIcon(tint = FolioTheme.extendedColors.textSecondary, modifier = Modifier.size(18.dp))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FolioTheme.extendedColors.accent,
                    unfocusedBorderColor = FolioTheme.extendedColors.textTertiary.copy(alpha = 0.5f),
                    focusedTextColor = FolioTheme.extendedColors.textPrimary,
                    unfocusedTextColor = FolioTheme.extendedColors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = FolioTheme.extendedColors.glassBorderEnd)
            Spacer(modifier = Modifier.height(10.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FolioTheme.extendedColors.accent, modifier = Modifier.size(32.dp))
                }
            } else if (hasSearched && results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matches found for \"$query\"",
                        fontSize = 14.sp,
                        color = FolioTheme.extendedColors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results) { res ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onDismiss()
                                    onResultClick(res)
                                }
                                .padding(vertical = 10.dp, horizontal = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = res.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FolioTheme.extendedColors.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (showPageNumbers && res.pageNumber != null) {
                                    Text(
                                        text = "p. ${res.pageNumber}",
                                        fontSize = 12.sp,
                                        color = FolioTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = res.snippet,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = FolioTheme.extendedColors.textPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
