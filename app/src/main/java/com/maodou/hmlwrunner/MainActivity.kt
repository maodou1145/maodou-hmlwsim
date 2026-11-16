package com.maodou.hmlwrunner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maodou.hmlwrunner.runtime.RuntimeController
import com.maodou.hmlwrunner.ui.AppScaffold
import com.maodou.hmlwrunner.ui.theme.HmlwTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            HmlwTheme {
                val controller: RuntimeController = viewModel()
                AppScaffold(controller)
            }
        }
    }
}
