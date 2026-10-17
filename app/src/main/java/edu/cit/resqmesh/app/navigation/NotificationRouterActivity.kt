package edu.cit.resqmesh.app.navigation

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import edu.cit.resqmesh.MainActivity

/**
 * Non-exported trampoline for notification taps. Only PendingIntents created by this app can reach
 * it; it validates the extras, posts a typed request, then brings MainActivity to the front.
 */
class NotificationRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        route(intent)
        finish()
    }

    private fun route(source: Intent?) {
        NavigationRequestParser.fromIntent(source)?.let(NavigationRequestBus::post)
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        )
    }
}
