package app.rustbenz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import app.rustbenz.ui.App
import app.rustbenz.ui.RustBenzTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity gets the original intent again; only handle fresh launches.
        if (savedInstanceState == null) handleRedirect(intent)
        setContent {
            RustBenzTheme {
                App(vm = vm, onSignIn = ::openLogin)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "rismycar" && uri.host == "login-callback") {
            vm.completeLogin(uri.toString())
        }
    }

    /** Mercedes' own login page in a Custom Tab; the password never touches this app. */
    private fun openLogin() {
        val url = vm.beginLogin() ?: return
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
    }
}
