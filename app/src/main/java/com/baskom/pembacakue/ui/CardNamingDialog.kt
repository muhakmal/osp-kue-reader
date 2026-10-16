package com.baskom.pembacakue.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.baskom.pembacakue.ui.theme.BgElevated
import com.baskom.pembacakue.ui.theme.Violet
import com.baskom.pembacakue.ui.theme.TextPrimary
import com.baskom.pembacakue.ui.theme.TextSecondary

@Composable
fun CardNamingDialog(
    currentName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
        title = { Text("Beri Nama Kartu", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 15) input = it },
                label = { Text("Alias (Maks 15 karakter)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Violet,
                    focusedLabelColor = Violet,
                    cursorColor = Violet,
                    unfocusedTextColor = TextPrimary,
                    focusedTextColor = TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }) {
                Text("Simpan", color = Violet, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        }
    )
}
