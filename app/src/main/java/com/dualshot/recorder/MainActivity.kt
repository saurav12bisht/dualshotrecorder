package com.dualshot.recorder

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dualshot.recorder.presentation.camera.CameraScreen
import com.dualshot.recorder.presentation.gallery.GalleryScreen
import com.dualshot.recorder.presentation.settings.SettingsScreen
import com.dualshot.recorder.ui.theme.DualShotTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host with Jetpack Navigation Compose [NavHost].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            DualShotTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Routes.CAMERA,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.CAMERA) {
                        CameraScreen(
                            onNavigateSettings = { navController.navigate(Routes.SETTINGS) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.GALLERY) {
                        GalleryScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

/**
 * Navigation route constants.
 */
object Routes {
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val GALLERY = "gallery"
}
