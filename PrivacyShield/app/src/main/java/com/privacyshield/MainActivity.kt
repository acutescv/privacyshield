package com.privacyshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.privacyshield.navigation.AppNavGraph
import com.privacyshield.ui.theme.PrivacyShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PrivacyShieldTheme {
                AppNavGraph()
            }
        }
    }
}
