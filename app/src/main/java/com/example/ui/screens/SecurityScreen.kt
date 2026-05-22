package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.GitViewModel

@Composable
fun SecurityScreen(
    viewModel: GitViewModel,
    mode: String = "unlock" // "unlock" or "create"
) {
    var pin by remember { mutableStateOf("") }
    var pinConfirmation by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val headlineText = when {
        mode == "create" && !isConfirming -> "Establish Security Passcode"
        mode == "create" && isConfirming -> "Re-enter Security Passcode"
        else -> "GitSync Secured"
    }

    val subtitleText = when {
        mode == "create" -> "Enter a 4-digit passcode to protect your GitHub credentials."
        else -> "Enter your security code to unlock the mobile client."
    }

    fun handleNumPress(char: Char) {
        if (pin.length < 4) {
            pin += char
            errorMessage = null
        }
        
        if (pin.length == 4) {
            if (mode == "create") {
                if (!isConfirming) {
                    pinConfirmation = pin
                    pin = ""
                    isConfirming = true
                } else {
                    if (pin == pinConfirmation) {
                        viewModel.setupPIN(pin)
                        viewModel.navigateTo("dashboard")
                    } else {
                        errorMessage = "Passcodes do not match! Try again."
                        pin = ""
                        isConfirming = false
                        pinConfirmation = ""
                    }
                }
            } else {
                val success = viewModel.unlockWithPIN(pin)
                if (!success) {
                    pin = ""
                    errorMessage = "Incorrect Passcode!"
                }
            }
        }
    }

    fun handleBackspace() {
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Icon & Titles
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = headlineText,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Indicator bullets
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { idx ->
                    val filled = idx < pin.length
                    val color by animateColorAsState(
                        targetValue = if (filled) MaterialTheme.colorScheme.primary else Color(0xFF333333),
                        animationSpec = tween(150), label = ""
                    )
                    val size by animateDpAsState(
                        targetValue = if (filled) 16.dp else 12.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = ""
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Tactile Keypad (3x4 Grid)
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            val keyRows = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf(null, '0', 'B') // Null is filler, 'B' is backspace
            )

            for (row in keyRows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.2f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (key != null) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(if (key == 'B') Color.Transparent else Color(0xFF16161A))
                                        .clickable {
                                            if (key == 'B') handleBackspace() else handleNumPress(key)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == 'B') {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Backspace",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Text(
                                            text = key.toString(),
                                            fontSize = 28.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
