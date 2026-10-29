package com.spotifydesktop.web

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * Custom WebView that ignores visibility changes when backgrounded so that
 * Spotify's audio stream and JavaScript timers are not suspended when the
 * screen turns off or the user switches apps.
 */
class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Always report VISIBLE to the internal Chromium engine so media playback continues
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}
