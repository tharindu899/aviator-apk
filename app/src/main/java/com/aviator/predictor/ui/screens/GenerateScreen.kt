package com.aviator.predictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.ui.theme.*

@Composable
fun GenerateScreen(
    isGenerating: Boolean,
    onGenerate: (String, (Boolean, String) -> Unit) -> Unit
) {
    var input         by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val clipboard     = LocalClipboardManager.current

    val examples = listOf(
        "12.32x 09:18:18"   to "Standard",
        "150x 23:34:00"     to "High multiplier",
        "1437.20x 06:31:56" to "Very high"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Page title ────────────────────────────────────────────────────
        Text(
            text       = "Generate Signal",
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = 26.sp
        )

        // ── Input card ────────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2744)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BrandPurple.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Label
                Text(
                    text       = "Enter Odd & Time",
                    color      = BrandPurple,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Input row: text field + solid purple paste button
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {

                    OutlinedTextField(
                        value         = input,
                        onValueChange = { input = it; statusMessage = null },
                        modifier      = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 80.dp),
                        placeholder = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text       = "12.32x",
                                    color      = Color(0xFF475569),
                                    fontSize   = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text       = "09:18:18",
                                    color      = Color(0xFF475569),
                                    fontSize   = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        },
                        textStyle = TextStyle(
                            color      = Color.White,
                            fontSize   = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        singleLine      = false,
                        minLines        = 2,
                        maxLines        = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (input.isNotBlank() && !isGenerating) {
                                    onGenerate(normalizeSignalInput(input)) { ok, msg ->
                                        statusMessage = ok to msg
                                        if (ok) input = ""
                                    }
                                }
                            }
                        ),
                        shape  = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = BrandPurple,
                            unfocusedBorderColor    = Color(0xFF2D3F5A),
                            focusedContainerColor   = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor        = Color.White,
                            unfocusedTextColor      = Color.White,
                            cursorColor             = BrandPurple
                        )
                    )

                    // Solid purple paste button — matches screenshot
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BrandPurple)
                            .clickable {
                                val pasted = clipboard.getText()?.text?.trim() ?: ""
                                if (pasted.isNotBlank()) {
                                    input         = normalizeSignalInput(pasted)
                                    statusMessage = null
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint               = Color.White,
                            modifier           = Modifier.size(26.dp)
                        )
                    }
                }

                // Status message
                statusMessage?.let { (ok, msg) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (ok) GreenActive.copy(alpha = 0.15f)
                                else    RedClosed.copy(alpha = 0.15f)
                            )
                            .padding(10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector        = if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint               = if (ok) GreenActive else RedClosed,
                            modifier           = Modifier.size(18.dp)
                        )
                        Text(
                            text     = msg,
                            color    = if (ok) GreenActive else RedClosed,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Calculate button
                Button(
                    onClick = {
                        onGenerate(normalizeSignalInput(input)) { ok, msg ->
                            statusMessage = ok to msg
                            if (ok) input = ""
                        }
                    },
                    enabled  = !isGenerating && input.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = BrandPurple,
                        disabledContainerColor = BrandPurple.copy(alpha = 0.4f)
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(22.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculating…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculate Signal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        // ── Examples card ─────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2744)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BrandPurple.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    text       = "Examples (tap to use)",
                    color      = BrandPurple,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                examples.forEach { (format, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .clickable { input = format; statusMessage = null }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = format,
                            color      = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text     = desc,
                            color    = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ── Input normalizer ──────────────────────────────────────────────────────────

private fun normalizeSignalInput(raw: String): String {
    var s = raw.replace("\n", " ").replace("\r", " ").trim()
    s = s.replace(Regex("""(\d{1,2})\.(\d{2})\.(\d{2})"""), "$1:$2:$3")
    s = s.replace(Regex("""([0-9.]+)[xX]\s*([0-9]{1,2}:[0-9]{2}:[0-9]{2})"""), "$1x $2")
    return s
}
