package com.audibleclone.ui.splash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audibleclone.ui.theme.AudibleCloneTheme

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudibleCloneTheme {
                SplashScreen()
            }
        }
        // Navigate to main after short delay
        androidx.lifecycle.lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            val intent = android.content.Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}

@Composable
fun SplashScreen() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            // App icon/name
            Text(
                text = "AudibleClone",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = com.audibleclone.ui.theme.MaterialTheme.colorScheme.primary
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
            CircularProgressIndicator(
                color = com.audibleclone.ui.theme.MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}