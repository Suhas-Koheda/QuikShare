package dev.haas.quickshare

import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class QuickShareApp : Application() {

    companion object {
        const val POSTHOG_API_KEY = "phc_sCjB9GVPTUw1v1wxa57neDkTyX2i16YVjsR0Jk8uEwp"
        const val POSTHOG_HOST = "https://us.i.posthog.com"
    }

    override fun onCreate() {
        super.onCreate()

        val config = PostHogAndroidConfig(
            apiKey = POSTHOG_API_KEY,
            host = POSTHOG_HOST
        ).apply {
            // Enable debugging to see if it's working
            debug = true
            // Capture application lifecycle events automatically
            captureApplicationLifecycleEvents = true
            // Capture screen views automatically
            captureScreenViews = true
        }
        PostHogAndroid.setup(this, config)
    }
}
