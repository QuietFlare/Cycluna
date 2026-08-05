package app.cycluna.android.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cycluna.android.designsystem.Crescent
import app.cycluna.android.designsystem.Theme
import app.cycluna.android.designsystem.serif

/**
 * Full-screen cover shown while the app is locked. Offers a retry, in case the system prompt
 * was cancelled.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Theme.backgroundGradient),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Crescent(
                Modifier.size(44.dp),
                Brush.linearGradient(listOf(Theme.primary, Theme.primary)),
            )
            Text("Cycluna is locked", style = serif(28).copy(color = Theme.ink))
            Text(
                "Your data stays private on this device.",
                fontSize = 14.sp,
                color = Theme.inkSoft,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onUnlock,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Theme.primary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 30.dp,
                    vertical = 14.dp,
                ),
            ) {
                Text("Unlock", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
