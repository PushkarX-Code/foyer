package app.foyer.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.foyer.launcher.ui.AppsScreen
import app.foyer.launcher.ui.HomeScreen
import app.foyer.launcher.ui.Palette
import app.foyer.launcher.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FoyerApp

        // A home screen never closes on Back; Back returns to Home from other screens.
        onBackPressedDispatcher.addCallback(this) {
            if (vm.screen != Screen.HOME) vm.screen = Screen.HOME
        }

        setContent {
            val profile = app.profile ?: return@setContent
            val loader = app.imageLoader ?: return@setContent
            MaterialTheme(colorScheme = darkColorScheme(background = Palette.Ink, surface = Palette.Ink)) {
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    when (vm.screen) {
                        Screen.HOME -> HomeScreen(vm, profile, loader)
                        Screen.APPS -> AppsScreen(vm, profile)
                        Screen.SETTINGS -> SettingsScreen(vm, profile)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageChanges, filter, ContextCompat.RECEIVER_EXPORTED)
        vm.refreshApps()
        vm.refreshWatchNext()
    }

    override fun onStop() {
        unregisterReceiver(packageChanges)
        super.onStop()
    }
}
