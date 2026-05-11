package com.aviator.predictor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviator.predictor.ui.theme.*

@Composable
fun GenerateScreen(
    isGenerating: Boolean,
    onGenerate: (String, (Boolean, String) -> Unit) -> Unit
) {
    var input       by remember { mutableStateOf("") }
    var resultMsg   by remember { mutableStateOf<String?>(null) }
    var resultOk    by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(BrandPurple, BrandPink))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow, null,
                    tint     = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Generate Signal",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp
            )
            Text(
                "Enter odd and base time to calculate your signal",
                color     = Color(0xFF94A3B8),
                fontSize  = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        // ── Format hint card ──────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandPurple.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BrandPurple.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Info, null, tint = BrandPurple, modifier = Modifier.size(16.dp))
                    Text("Input Format", color = BrandPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                listOf(
                    "2.02x 21:31:22",
                    "150x 23:34:00",
                    "2.02x21.31.22"
                ).forEach { example ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BrandPurple.copy(alpha = 0.5f))
                        )
                        Text(
                            example,
                            color      = Color(0xFFCBD5E1),
                            fontSize   = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ── Input field ───────────────────────────────────────────────────
        OutlinedTextField(
            value         = input,
            onValueChange = { input = it; resultMsg = null },
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text("Odd × Time") },
            placeholder   = { Text("e.g. 2.02x 21:31:22", color = Color(0xFF475569)) },
            leadingIcon   = {
                Icon(Icons.Filled.Edit, null, tint = BrandPurple)
            },
            trailingIcon  = {
                if (input.isNotBlank()) {
                    IconButton(onClick = { input = ""; resultMsg = null }) {
                        Icon(Icons.Filled.Clear, null, tint = Color(0xFF94A3B8))
                    }
                }
            },
            singleLine = true,
            shape      = RoundedCornerShape(16.dp),
            colors     = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = BrandPurple,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor     = Color.White,
                unfocusedTextColor   = Color.White,
                focusedLabelColor    = BrandPurple,
                unfocusedLabelColor  = Color(0xFF64748B),
                cursorColor          = BrandPurple
            )
        )

        // ── Generate button ───────────────────────────────────────────────
        Button(
            onClick  = {
                if (input.isNotBlank() && !isGenerating) {
                    resultMsg = null
                    onGenerate(input) { ok, msg ->
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
            shape  = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandPurple,
                disabledContainerColor = BrandPurple.copy(alpha = 0.4f)
            )
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    color     = Color.White,
                    modifier  = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Generating…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            } else {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Signal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // ── Result feedback ───────────────────────────────────────────────
        resultMsg?.let { msg ->
            val bgColor  = if (resultOk) GreenActive else RedClosed
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, bgColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier              = Modifier.padding(14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (resultOk) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        null,
                        tint     = bgColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(msg, color = bgColor, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
