package com.seniorguardian.app.telecom

import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DialerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefilledNumber = intent?.data?.schemeSpecificPart ?: ""

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DialerScreen(
                        initialNumber = prefilledNumber,
                        onCall = { number -> placeCall(number) }
                    )
                }
            }
        }
    }

    private fun placeCall(number: String) {
        if (number.isBlank()) return
        val telecomManager = getSystemService(TelecomManager::class.java)
        telecomManager.placeCall(Uri.fromParts("tel", number, null), null)
    }
}

private val KEYPAD_ROWS = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to "")
)

@Composable
private fun DialerScreen(initialNumber: String, onCall: (String) -> Unit) {
    var number by remember { mutableStateOf(initialNumber) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = number.ifEmpty { "Enter number" },
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            KEYPAD_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { (digit, letters) ->
                        KeypadButton(digit = digit, letters = letters) {
                            number += digit
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onCall(number) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.size(72.dp)
            ) {
                Text(text = "📞", fontSize = 28.sp)
            }
            if (number.isNotEmpty()) {
                TextButton(
                    onClick = { number = number.dropLast(1) },
                    modifier = Modifier.padding(start = 24.dp)
                ) {
                    Text(text = "⌫", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(digit: String, letters: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color(0xFFF0F0F0),
        modifier = Modifier
            .size(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = digit, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                if (letters.isNotEmpty()) {
                    Text(text = letters, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}
