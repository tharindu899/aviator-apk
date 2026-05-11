package com.aviator.predictor.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.ui.theme.*

// ── Tap-to-use examples ───────────────────────────────────────────────────────

private data class Example(val input: String, val tag: String)

private val EXAMPLES = listOf(
    Example("12.32x 09:18:18",   "Standard"),
    Example("150x 23:34:00",     "High multiplier"),
    Example("1437.20x 06:31:56", "Very high"),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun GenerateScreen(
    isGenerating: Boolean,
    onGenerate: (String, (Boolean, String) -> Unit) -> Unit
) {
    var input     by remember { mutableStateOf("") }
    var resultMsg by remember { mutableStateOf<String?>(null) }
    var resultOk  by remember { mutableStateOf(false) }
    val context   = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
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
                .border(1.dp, BrandPurple.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Label
                Text(
                    text       = "Enter Odd & Time",
                    color      = BrandPurple,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )

                // Input row: text field + paste button
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {

                    // The text field
                    OutlinedTextField(
                        value         = input,
                        onValueChange = { input = it; resultMsg = null },
                        modifier      = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 80.dp),
                        placeholder   = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "12.32x",
                                    color      = Color(0xFF475569),
                                    fontSize   = 18.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "09:18:18",
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
                        singleLine = false,
                        maxLines   = 3,
                        shape      = RoundedCornerShape(14.dp),
                        colors     = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BrandPurple.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color(0xFF2D3F5A),
                            focusedContainerColor   = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            cursorColor          = BrandPurple
                        )
                    )

                    // Paste button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BrandPurple)
                            .clickable {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as ClipboardManager
                                val text = cb.primaryClip
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                    ?.trim()
                                if (!text.isNullOrBlank()) {
                                    input     = text
                                    resultMsg = null
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ContentPaste,
                            contentDescription = "Paste from clipboard",
                            tint     = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Calculate button
                Button(
                    onClick = {
                        if (input.isNotBlank() && !isGenerating) {
                            resultMsg = null
                            onGenerate(input.replace("\n", " ").trim()) { ok, msg ->
                                resultOk  = ok
                                resultMsg = msg
                                if (ok) input = ""
                            }
                        }
                    },
                    enabled  = input.isNotBlank() && !isGenerating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape  = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = BrandPurple,
                        disabledContainerColor = BrandPurple.copy(alpha = 0.4f)
                    )
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            color       = Color.White,
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Calculating…",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Calculate Signal",
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp
                        )
                    }
                }

                // Result feedback
                resultMsg?.let { msg ->
                    val color = if (resultOk) GreenActive else RedClosed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (resultOk) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint     = color,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text       = msg,
                            color      = color,
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Text(
                    text       = "Examples (tap to use)",
                    color      = BrandPurple,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )

                EXAMPLES.forEach { example ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F172A))
                            .clickable {
                                input     = example.input
                                resultMsg = null
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = example.input,
                            color      = Color.White,
                            fontSize   = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text     = example.tag,
                            color    = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}
