package com.spotifydesktop.web

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

class BackgroundWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}
