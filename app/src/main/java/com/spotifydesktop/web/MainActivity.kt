package com.spotifydesktop.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var webView: BackgroundWebView
    private lateinit var nativeListContainer: LinearLayout
    private lateinit var nativeScrollView: ScrollView
    private lateinit var trackTitleView: TextView
    private lateinit var trackArtistView: TextView
    private lateinit var playPauseBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var searchInput: EditText
    private var showingRawWebView = false

    private val desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        startPlaybackForegroundService()

        setContentView(buildMobileShellLayout())
        configureWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        webView.loadUrl("https://open.spotify.com/")
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun buildMobileShellLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // --- TOP SEARCH BAR ---
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#181818"))
        }

        searchInput = EditText(this).apply {
            hint = "Search songs, artists, playlists..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setBackgroundColor(Color.parseColor("#282828"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    val q = text.toString().trim()
                    if (q.isNotEmpty()) {
                        webView.loadUrl("https://open.spotify.com/search/" + android.net.Uri.encode(q))
                    }
                    true
                } else false
            }
        }

        val searchBtn = createStyledButton("Search", "#1DB954", Color.BLACK).apply {
            setOnClickListener {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    webView.loadUrl("https://open.spotify.com/search/" + android.net.Uri.encode(q))
                }
            }
        }

        searchRow.addView(searchInput)
        searchRow.addView(searchBtn)
        root.addView(searchRow)

        // --- TOP NAVIGATION TABS ---
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            setBackgroundColor(Color.parseColor("#181818"))
        }

        val playlistsTab = createTabButton("Playlists") {
            setRawWebViewVisible(false)
            requestBackendList("playlists")
        }
        val tracksTab = createTabButton("Songs") {
            setRawWebViewVisible(false)
            requestBackendList("tracks")
        }
        val connectAudioTab = createTabButton("Activate Audio") {
            activateWebPlayerDevice()
        }
        val loginWebTab = createTabButton("Login / Web") {
            setRawWebViewVisible(!showingRawWebView)
        }

        tabRow.addView(playlistsTab)
        tabRow.addView(tracksTab)
        tabRow.addView(connectAudioTab)
        tabRow.addView(loginWebTab)
        root.addView(tabRow)

        // --- CENTER STACK: BACKEND WEBVIEW + NATIVE MOBILE LIST UI ON TOP ---
        val centerFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        webView = BackgroundWebView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        nativeScrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        nativeListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(16))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        nativeScrollView.addView(nativeListContainer)
        centerFrame.addView(webView)
        centerFrame.addView(nativeScrollView)
        root.addView(centerFrame)

        // --- PROGRESS BAR ---
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        root.addView(progressBar)

        // --- NATIVE BOTTOM NOW PLAYING BAR ---
        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#202020"))
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }

        trackTitleView = TextView(this).apply {
            text = "Loading Spotify Desktop Backend..."
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        trackArtistView = TextView(this).apply {
            text = "Tap 'Login / Web' if you need to sign in first"
            setTextColor(Color.parseColor("#B3B3B3"))
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(6))
        }

        playerCard.addView(trackTitleView)
        playerCard.addView(trackArtistView)

        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val shuffleBtn = createControlButton("Shuffle") {
            triggerSpotifyButton("[data-testid='control-button-shuffle']")
        }
        val prevBtn = createControlButton("Prev") {
            triggerSpotifyButton("[data-testid='control-button-skip-back']")
        }
        playPauseBtn = createStyledButton("Play / Pause", "#1DB954", Color.BLACK).apply {
            setOnClickListener {
                triggerSpotifyButton("[data-testid='control-button-playpause']")
            }
        }
        val nextBtn = createControlButton("Next") {
            triggerSpotifyButton("[data-testid='control-button-skip-forward']")
        }
        val repeatBtn = createControlButton("Repeat") {
            triggerSpotifyButton("[data-testid='control-button-repeat']")
        }

        controlsRow.addView(shuffleBtn)
        controlsRow.addView(prevBtn)
        controlsRow.addView(playPauseBtn)
        controlsRow.addView(nextBtn)
        controlsRow.addView(repeatBtn)

        playerCard.addView(controlsRow)
        root.addView(playerCard)

        return root
    }

    private fun setRawWebViewVisible(visible: Boolean) {
        showingRawWebView = visible
        nativeScrollView.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun createTabButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createControlButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createStyledButton(label: String, bgHex: String, textColor: Int): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            setBackgroundColor(Color.parseColor(bgHex))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply {
                setMargins(dp(6), 0, dp(6), 0)
            }
        }
    }

    private fun triggerSpotifyButton(selector: String) {
        webView.evaluateJavascript(
            """
            (function() {
                var btn = document.querySelector("$selector");
                if (btn) { btn.click(); }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun requestBackendList(mode: String) {
        webView.evaluateJavascript(
            "if (window.__spExtractList) window.__spExtractList('$mode');",
            null
        )
    }

    private fun activateWebPlayerDevice() {
        Toast.makeText(this, "Connecting to local web player audio...", Toast.LENGTH_SHORT).show()
        webView.evaluateJavascript(
            """
            (function() {
                var connectBtn = document.querySelector('[data-testid="control-button-npv"], [aria-label*="Connect to a device"], button[aria-label*="device"]');
                if (connectBtn) connectBtn.click();
                setTimeout(function() {
                    var items = document.querySelectorAll('button, div[role="button"], li');
                    for (var i = 0; i < items.length; i++) {
                        var txt = (items[i].innerText || '').toLowerCase();
                        if (txt.indexOf('this web browser') !== -1 || txt.indexOf('web player') !== -1) {
                            items[i].click();
                            break;
                        }
                    }
                    var playBtn = document.querySelector('[data-testid="control-button-playpause"]');
                    if (playBtn) playBtn.click();
                }, 600);
            })();
            """.trimIndent(),
            null
        )
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun configureWebView() {
        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = desktopUserAgent
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(SpotifyBridge(), "AndroidBridge")

        // Use DocumentStartJavaScript so desktop properties exist BEFORE Spotify's bundle runs
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                SAFE_DOCUMENT_START_SPOOF_JS,
                setOf("*")
            )
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("intent://") || url.startsWith("market://") || url.startsWith("spotify:")) {
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                view?.evaluateJavascript(BRIDGE_EXTRACTOR_JS, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                runOnUiThread {
                    try {
                        request.grant(request.resources)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    inner class SpotifyBridge {
        @JavascriptInterface
        fun onTrackUpdate(title: String, artist: String, isPlaying: Boolean, progressPermille: Int) {
            runOnUiThread {
                if (title.isNotBlank()) {
                    trackTitleView.text = title
                }
                if (artist.isNotBlank()) {
                    trackArtistView.text = artist
                }
                playPauseBtn.text = if (isPlaying) "Pause" else "Play"
                progressBar.progress = progressPermille.coerceIn(0, 1000)
            }
        }

        @JavascriptInterface
        fun onItemsExtracted(mode: String, jsonArrayStr: String) {
            runOnUiThread {
                renderNativeMobileList(mode, jsonArrayStr)
            }
        }
    }

    private fun renderNativeMobileList(mode: String, jsonArrayStr: String) {
        nativeListContainer.removeAllViews()
        val arr = try {
            JSONArray(jsonArrayStr)
        } catch (_: Exception) {
            JSONArray()
        }

        val header = TextView(this).apply {
            text = if (mode == "playlists") "Your Playlists & Library (${arr.length()})" else "Songs & Results (${arr.length()})"
            setTextColor(Color.parseColor("#1DB954"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(6), dp(4), dp(12))
        }
        nativeListContainer.addView(header)

        if (arr.length() == 0) {
            val emptyView = TextView(this).apply {
                text = "Loading items from Spotify Desktop backend...

If you haven't signed in yet, tap 'Login / Web' at the top right, log in to your Spotify account, then tap 'Playlists' or 'Songs' again."
                setTextColor(Color.parseColor("#CCCCCC"))
                textSize = 15f
                setPadding(dp(8), dp(16), dp(8), dp(16))
            }
            nativeListContainer.addView(emptyView)
            return
        }

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val itemTitle = obj.optString("title", "Untitled")
            val itemSubtitle = obj.optString("subtitle", "")
            val domIndex = obj.optInt("index", i)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dp(8))
                }
                setOnClickListener {
                    if (mode == "playlists") {
                        webView.evaluateJavascript("window.__spClickPlaylist($domIndex);", null)
                        Toast.makeText(this@MainActivity, "Opening $itemTitle...", Toast.LENGTH_SHORT).show()
                    } else {
                        webView.evaluateJavascript("window.__spClickTrack($domIndex);", null)
                        Toast.makeText(this@MainActivity, "Playing $itemTitle", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val titleTv = TextView(this).apply {
                text = itemTitle
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val subTv = TextView(this).apply {
                text = if (itemSubtitle.isNotBlank()) itemSubtitle else if (mode == "playlists") "Tap to open playlist" else "Tap to play song"
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            card.addView(titleTv)
            card.addView(subTv)
            nativeListContainer.addView(card)
        }
    }

    private fun startPlaybackForegroundService() {
        val serviceIntent = Intent(this, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    override fun onDestroy() {
        val serviceIntent = Intent(this, PlaybackService::class.java)
        stopService(serviceIntent)
        super.onDestroy()
    }

    companion object {
        /**
         * Safe desktop property spoofing on Screen.prototype & Navigator.prototype
         * so V8 never throws Illegal invocation on window.screen.orientation.
         */
        private val SAFE_DOCUMENT_START_SPOOF_JS = """
            (function() {
                try {
                    Object.defineProperty(Navigator.prototype, 'platform', {
                        get: function() { return 'Win32'; },
                        configurable: true
                    });
                    Object.defineProperty(Navigator.prototype, 'maxTouchPoints', {
                        get: function() { return 0; },
                        configurable: true
                    });
                    Object.defineProperty(Navigator.prototype, 'userAgentData', {
                        get: function() {
                            return {
                                brands: [
                                    { brand: 'Chromium', version: '128' },
                                    { brand: 'Google Chrome', version: '128' },
                                    { brand: 'Not;A=Brand', version: '24' }
                                ],
                                mobile: false,
                                platform: 'Windows',
                                getHighEntropyValues: function() {
                                    return Promise.resolve({
                                        architecture: 'x86',
                                        bitness: '64',
                                        mobile: false,
                                        model: '',
                                        platform: 'Windows',
                                        platformVersion: '15.0.0',
                                        uaFullVersion: '128.0.0.0'
                                    });
                                }
                            };
                        },
                        configurable: true
                    });
                    Object.defineProperty(Screen.prototype, 'width', { get: function() { return 1920; }, configurable: true });
                    Object.defineProperty(Screen.prototype, 'height', { get: function() { return 1080; }, configurable: true });
                    Object.defineProperty(Screen.prototype, 'availWidth', { get: function() { return 1920; }, configurable: true });
                    Object.defineProperty(Screen.prototype, 'availHeight', { get: function() { return 1040; }, configurable: true });
                } catch (e) {}
            })();
        """.trimIndent()

        /**
         * Extracts Playlists and Songs from the hidden desktop DOM and renders them
         * inside the Native Android Mobile UI list, plus syncs Now Playing bar.
         */
        private val BRIDGE_EXTRACTOR_JS = """
            (function() {
                if (window.__spBridgeInitialized) return;
                window.__spBridgeInitialized = true;

                window.__spCurrentMode = 'tracks';

                window.__spExtractList = function(mode) {
                    window.__spCurrentMode = mode;
                    var results = [];
                    if (mode === 'playlists') {
                        var items = document.querySelectorAll('#Desktop_LeftSidebar_Id li, nav[aria-label="Main"] li, [data-testid="rootlist-item"]');
                        for (var i = 0; i < items.length; i++) {
                            var text = (items[i].innerText || '').trim().split('\n');
                            var title = (text[0] || '').trim();
                            var sub = text.slice(1).join(' • ').trim();
                            if (title) {
                                results.push({ index: i, title: title, subtitle: sub });
                            }
                        }
                    } else {
                        var rows = document.querySelectorAll('[data-testid="tracklist-row"], [data-testid="herocard-click-handler"], [data-testid="card-click-handler"]');
                        for (var j = 0; j < rows.length; j++) {
                            var lines = (rows[j].innerText || '').trim().split('\n').filter(Boolean);
                            if (lines.length > 0) {
                                var tTitle = lines.length > 1 && /^\d+$/.test(lines[0]) ? lines[1] : lines[0];
                                var tSub = lines.slice(lines.indexOf(tTitle) + 1).join(' • ');
                                results.push({ index: j, title: tTitle, subtitle: tSub });
                            }
                        }
                    }
                    if (window.AndroidBridge && window.AndroidBridge.onItemsExtracted) {
                        window.AndroidBridge.onItemsExtracted(mode, JSON.stringify(results));
                    }
                };

                window.__spClickPlaylist = function(idx) {
                    var items = document.querySelectorAll('#Desktop_LeftSidebar_Id li, nav[aria-label="Main"] li, [data-testid="rootlist-item"]');
                    if (items[idx]) {
                        var clickable = items[idx].querySelector('a, div[role="button"], button') || items[idx];
                        clickable.click();
                        setTimeout(function() { window.__spExtractList('tracks'); }, 1200);
                    }
                };

                window.__spClickTrack = function(idx) {
                    var rows = document.querySelectorAll('[data-testid="tracklist-row"], [data-testid="herocard-click-handler"], [data-testid="card-click-handler"]');
                    if (rows[idx]) {
                        var playBtn = rows[idx].querySelector('button[aria-label*="Play"], [data-testid="play-button"]');
                        if (playBtn) {
                            playBtn.click();
                        } else {
                            rows[idx].click();
                            var dblEvt = new MouseEvent('dblclick', { bubbles: true, cancelable: true, view: window });
                            rows[idx].dispatchEvent(dblEvt);
                        }
                    }
                };

                setInterval(function() {
                    try {
                        var titleEl = document.querySelector('[data-testid="context-item-info-title"], [data-testid="now-playing-widget"] a');
                        var artistEl = document.querySelector('[data-testid="context-item-info-subtitles"], [data-testid="context-item-info-artist"]');
                        var playBtn = document.querySelector('[data-testid="control-button-playpause"]');

                        var title = titleEl ? (titleEl.innerText || '').trim() : '';
                        var artist = artistEl ? (artistEl.innerText || '').trim() : '';
                        var aria = playBtn ? (playBtn.getAttribute('aria-label') || '').toLowerCase() : '';
                        var isPlaying = aria.indexOf('pause') !== -1;

                        var permille = 0;
                        var posEl = document.querySelector('[data-testid="playback-position"]');
                        var durEl = document.querySelector('[data-testid="playback-duration"]');
                        if (posEl && durEl) {
                            var parseTime = function(s) {
                                var p = (s || '').replace('-', '').split(':').map(Number);
                                return p.length === 2 ? (p[0] * 60 + p[1]) : 0;
                            };
                            var pos = parseTime(posEl.innerText);
                            var dur = parseTime(durEl.innerText);
                            if (dur > 0) permille = Math.round((pos / dur) * 1000);
                        }

                        if (window.AndroidBridge && window.AndroidBridge.onTrackUpdate) {
                            window.AndroidBridge.onTrackUpdate(title, artist, isPlaying, permille);
                        }
                        window.__spExtractList(window.__spCurrentMode || 'tracks');
                    } catch(err) {}
                }, 1500);
            })();
        """.trimIndent()
    }
}
