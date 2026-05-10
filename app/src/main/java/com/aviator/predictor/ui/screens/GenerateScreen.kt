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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.ui.components.GradientCard
import com.aviator.predictor.ui.components.SectionHeader
import com.aviator.predictor.ui.theme.*

@Composable
fun GenerateScreen(
    isGenerating: Boolean,
    onGenerate: (String, (Boolean, String) -> Unit) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val clipboard = LocalClipboardManager.current

    val examples = listOf(
        "2.02x 21:31:22" to "Standard format",
        "150x 23:34:00" to "High multiplier",
        "1437.20x 06:31:56" to "Very high multiplier"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader("Generate Signal")

        // Main input card
        GradientCard {
            Text(
                "Enter Odd & Time",
                color = Color(0xFFC084FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        statusMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("2.02x 21:31:22", color = Color(0xFF64748B))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (input.isNotBlank() && !isGenerating) {
                            onGenerate(input) { ok, msg ->
                                statusMessage = ok to msg
                                if (ok) input = ""
                            }
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPurple,
                        unfocusedBorderColor = Color(0xFF475569),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = BrandPurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Paste button
                IconButton(
                    onClick = {
                        val pasted = clipboard.getText()?.text ?: ""
                        if (pasted.isNotBlank()) {
                            input = pasted.trim().replace(Regex("([0-9.]+)x(\\d)"), "$1x $2")
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandPurple.copy(alpha = 0.2f))
                        .size(56.dp)
                ) {
                    Icon(Icons.Filled.ContentPaste, "Paste", tint = BrandPurple)
                }
            }

            // Status message
            statusMessage?.let { (ok, msg) ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (ok) GreenActive.copy(alpha = 0.15f) else RedClosed.copy(alpha = 0.15f)
                        )
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        null,
                        tint = if (ok) GreenActive else RedClosed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(msg, color = if (ok) GreenActive else RedClosed, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onGenerate(input) { ok, msg ->
                        statusMessage = ok to msg
                        if (ok) input = ""
                    }
                },
                enabled = !isGenerating && input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPurple,
                    disabledContainerColor = BrandPurple.copy(alpha = 0.4f)
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculating...")
                } else {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculate Signal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // Examples card
        GradientCard {
            Text(
                "Examples  (tap to use)",
                color = Color(0xFFC084FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            examples.forEach { (format, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33475569))
                        .clickable { input = format; statusMessage = null }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        format,
                        color = Color.White,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(desc, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // How it works card
        GradientCard {
            Text(
                "How it works",
                color = Color(0xFFC084FC),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "1" to "Copy the odd and time from the game",
                "2" to "Paste it into the input field above",
                "3" to "Tap Calculate Signal to generate",
                "4" to "Signal is added to your queue with a 90s bet window"
            ).forEach { (num, text) ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandPurple.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(num, color = BrandPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(text, color = Color(0xFFCBD5E1), fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
