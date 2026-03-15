package com.planmyplate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.planmyplate.ui.navigation.AppNavigation
import com.planmyplate.ui.sync.SyncCheckState
import com.planmyplate.ui.sync.SyncCheckViewModel
import com.planmyplate.ui.sync.SyncCheckViewModelFactory
import com.planmyplate.ui.theme.PlanMyPlateTheme

class MainActivity : ComponentActivity() {

    private val syncCheckViewModel: SyncCheckViewModel by viewModels {
        val app = application as PlanMyPlateApp
        SyncCheckViewModelFactory(this, app.driveRepository, app.userRepository, app.syncLogRepository, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            syncCheckViewModel.state.value is SyncCheckState.Checking
        }

        (applicationContext as PlanMyPlateApp).userRepository.enqueueDbSync()

        setContent {
            PlanMyPlateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(syncCheckViewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (applicationContext as PlanMyPlateApp).userRepository.enqueueDbSync()
    }
}
