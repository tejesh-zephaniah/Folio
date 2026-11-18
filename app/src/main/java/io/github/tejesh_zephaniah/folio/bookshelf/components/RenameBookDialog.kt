package io.github.tejesh_zephaniah.folio.bookshelf.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tejesh_zephaniah.folio.core.designsystem.theme.FolioTheme

@Composable
fun RenameBookDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var titleText by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Book",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = FolioTheme.extendedColors.textPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter a new title for this book:",
                    fontSize = 13.5.sp,
                    color = FolioTheme.extendedColors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FolioTheme.extendedColors.accent,
                        unfocusedBorderColor = FolioTheme.extendedColors.textTertiary.copy(alpha = 0.5f),
                        focusedTextColor = FolioTheme.extendedColors.textPrimary,
                        unfocusedTextColor = FolioTheme.extendedColors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleText.isNotBlank()) {
                        onConfirm(titleText.trim())
                    }
                },
                enabled = titleText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FolioTheme.extendedColors.accent),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Rename", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FolioTheme.extendedColors.textSecondary)
            }
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
