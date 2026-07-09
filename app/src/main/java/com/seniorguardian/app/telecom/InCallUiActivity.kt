package com.seniorguardian.app.telecom

import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class InCallUiActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1B1B1B)) {
                    val call by CallBridge.currentCall.collectAsState()
                    val state by CallBridge.currentCallState.collectAsState()
                    val isMuted by CallBridge.isMuted.collectAsState()
                    val isSpeakerOn by CallBridge.isSpeakerOn.collectAsState()
                    val callerName by CallBridge.callerName.collectAsState()
                    InCallScreen(
                        call = call,
                        state = state,
                        isMuted = isMuted,
                        isSpeakerOn = isSpeakerOn,
                        callerName = callerName
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallScreen(
    call: Call?,
    state: Int?,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    callerName: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        if (call == null) {
            Text(text = "No active call", color = Color.White, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(1.dp))
        } else {
            val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
            val displayName = callerName ?: number
            val callState = state ?: call.state

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = displayName, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = callStateLabel(callState), color = Color.LightGray, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(1.dp))

            if (callState == Call.STATE_RINGING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallActionButton(
                        label = "✖",
                        backgroundColor = Color(0xFFD32F2F),
                        onClick = { call.reject(false, null) }
                    )
                    CallActionButton(
                        label = "📞",
                        backgroundColor = Color(0xFF2E7D32),
                        onClick = { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallActionButton(
                        label = if (isMuted) "🔇" else "🎤",
                        backgroundColor = if (isMuted) Color(0xFF616161) else Color(0xFF424242),
                        onClick = { GuardianInCallService.instance?.toggleMute() }
                    )
                    CallActionButton(
                        label = "🔊",
                        backgroundColor = if (isSpeakerOn) Color(0xFF1565C0) else Color(0xFF424242),
                        onClick = { GuardianInCallService.instance?.toggleSpeaker() }
                    )
                    CallActionButton(
                        label = "✖",
                        backgroundColor = Color(0xFFD32F2F),
                        onClick = { call.disconnect() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallActionButton(
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        modifier = modifier.size(80.dp)
    ) {
        Text(text = label, fontSize = 32.sp)
    }
}

private fun callStateLabel(state: Int): String = when (state) {
    Call.STATE_RINGING -> "Incoming call..."
    Call.STATE_DIALING -> "Dialing..."
    Call.STATE_ACTIVE -> "In call"
    Call.STATE_HOLDING -> "On hold"
    Call.STATE_DISCONNECTED -> "Call ended"
    Call.STATE_CONNECTING -> "Connecting..."
    else -> "State $state"
}
