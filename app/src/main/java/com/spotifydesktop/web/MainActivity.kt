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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: BackgroundWebView
    private lateinit var trackTitleView: TextView
    private lateinit var trackArtistView: TextView
    private lateinit var playPauseBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var searchInput: EditText

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

    /**
     * Builds a clean Native Android Mobile UI around the Desktop Web Player backend:
     * 1. Top Search Bar + Quick View Tabs (Home, Search, Playlists/Library, Activate Audio)
     * 2. Center Full-Width Mobile-Adapted WebView
     * 3. Native Bottom Now Playing Bar (Song Title, Artist, Shuffle, Prev, Play/Pause, Next)
     */
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
                        switchMobileMode("main")
                        webView.loadUrl("https://open.spotify.com/search/" + android.net.Uri.encode(q))
                    }
                    true
                } else false
            }
        }

        val searchBtn = createStyledButton("Go", "#1DB954", Color.BLACK).apply {
            setOnClickListener {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) {
                    switchMobileMode("main")
                    webView.loadUrl("https://open.spotify.com/search/" + android.net.Uri.encode(q))
                } else {
                    switchMobileMode("main")
                    webView.loadUrl("https://open.spotify.com/search")
                }
            }
        }

        searchRow.addView(searchInput)
        searchRow.addView(searchBtn)
        root.addView(searchRow)

        // --- TOP NAVIGATION TABS (HOME | PLAYLISTS | SEARCH | ACTIVATE PLAYER) ---
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            setBackgroundColor(Color.parseColor("#181818"))
        }

        val homeTab = createTabButton("Home") {
            switchMobileMode("main")
            webView.loadUrl("https://open.spotify.com/")
        }
        val libraryTab = createTabButton("My Playlists") {
            switchMobileMode("library")
        }
        val searchTab = createTabButton("Browse") {
            switchMobileMode("main")
            webView.loadUrl("https://open.spotify.com/search")
        }
        val fixAudioTab = createTabButton("Connect Audio") {
            activateWebPlayerDevice()
        }

        tabRow.addView(homeTab)
        tabRow.addView(libraryTab)
        tabRow.addView(searchTab)
        tabRow.addView(fixAudioTab)
        root.addView(tabRow)

        // --- CENTER WEBVIEW (MOBILE-RESTYLED DESKTOP BACKEND) ---
        webView = BackgroundWebView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        root.addView(webView)

        // --- THIN PROGRESS BAR ---
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        root.addView(progressBar)

        // --- NATIVE ANDROID BOTTOM PLAY BAR ---
        val playerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#202020"))
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }

        val trackInfoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        trackTitleView = TextView(this).apply {
            text = "Tap any song to start playing"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        trackArtistView = TextView(this).apply {
            text = "If silent, tap 'Connect Audio' at the top"
            setTextColor(Color.parseColor("#B3B3B3"))
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        trackInfoLayout.addView(trackTitleView)
        trackInfoLayout.addView(trackArtistView)
        playerCard.addView(trackInfoLayout)

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

    private fun switchMobileMode(mode: String) {
        webView.evaluateJavascript(
            """
            (function() {
                document.body.setAttribute('data-mobile-view', '$mode');
            })();
            """.trimIndent(),
            null
        )
    }

    private fun activateWebPlayerDevice() {
        Toast.makeText(this, "Activating local web player audio...", Toast.LENGTH_SHORT).show()
        webView.evaluateJavascript(
            """
            (function() {
                // Resume any suspended Web Audio contexts
                if (window.__spAudioContexts) {
                    window.__spAudioContexts.forEach(function(ctx) {
                        try { ctx.resume(); } catch(e) {}
                    });
                }
                // Click the Connect Device button to reveal 'This web browser' and select it
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            // Spoof Windows Desktop Chrome
            userAgentString = desktopUserAgent

            // Keep 1:1 mobile readability while spoofing desktop screen dimensions in JS
            useWideViewPort = false
            loadWithOverviewMode = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.addJavascriptInterface(SpotifyBridge(), "AndroidBridge")

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

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Inject desktop environment spoof BEFORE Spotify's scripts check navigator.userAgentData
                view?.evaluateJavascript(DESKTOP_ENVIRONMENT_SPOOF_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                view?.evaluateJavascript(DESKTOP_ENVIRONMENT_SPOOF_JS, null)
                view?.evaluateJavascript(MOBILE_UI_TRANSFORM_JS, null)
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
         * Spoofs Windows Desktop properties so Spotify's Web Playback SDK & Widevine EME
         * treat the Android WebView as a real desktop Chrome browser.
         */
        private val DESKTOP_ENVIRONMENT_SPOOF_JS = """
            (function() {
                if (window.__spDesktopSpoofed) return;
                window.__spDesktopSpoofed = true;

                // Track all created AudioContexts so we can resume them anytime
                window.__spAudioContexts = [];
                var OrigAudioContext = window.AudioContext || window.webkitAudioContext;
                if (OrigAudioContext) {
                    var PatchedAudioContext = function() {
                        var ctx = new OrigAudioContext();
                        window.__spAudioContexts.push(ctx);
                        return ctx;
                    };
                    PatchedAudioContext.prototype = OrigAudioContext.prototype;
                    window.AudioContext = PatchedAudioContext;
                }

                try {
                    Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; } });
                    Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 0; } });
                    Object.defineProperty(navigator, 'userAgentData', {
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
                        }
                    });
                } catch(e) {}

                try {
                    var fakeScreen = Object.create(window.screen);
                    Object.defineProperty(fakeScreen, 'width', { get: function() { return 1920; } });
                    Object.defineProperty(fakeScreen, 'height', { get: function() { return 1080; } });
                    Object.defineProperty(fakeScreen, 'availWidth', { get: function() { return 1920; } });
                    Object.defineProperty(fakeScreen, 'availHeight', { get: function() { return 1040; } });
                    Object.defineProperty(window, 'screen', { get: function() { return fakeScreen; } });
                } catch(e) {}
            })();
        """.trimIndent()

        /**
         * Injects mobile-responsive CSS that hides the tiny desktop multi-column chrome,
         * scales the active view (Main content OR Library Playlists) to 100% mobile screen width
         * at readable 1.0x mobile font size, and syncs track info to the native Android bottom bar.
         */
        private val MOBILE_UI_TRANSFORM_JS = """
            (function() {
                if (window.__spMobileUiInjected) return;
                window.__spMobileUiInjected = true;

                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'viewport';
                    document.head.appendChild(meta);
                }
                meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                var style = document.createElement('style');
                style.id = 'sp-mobile-bridge-css';
                style.innerHTML = `
                    /* Hide desktop clutter, upgrade banners, right buddy-feed, and tiny bottom bar */
                    aside[aria-label*="Now Playing"],
                    div[data-testid="now-playing-bar"],
                    footer,
                    a[href*="/download"],
                    button[aria-label*="Upgrade"],
                    div[aria-label*="Upgrade"] {
                        display: none !important;
                    }

                    /* Force main layout container to single mobile column */
                    body, #main, .Root__top-container {
                        width: 100vw !important;
                        max-width: 100vw !important;
                        overflow-x: hidden !important;
                        display: block !important;
                        padding: 0 !important;
                        margin: 0 !important;
                    }

                    /* Default Mode: Show Main Content (Home / Search / Playlist Tracks) full width */
                    body:not([data-mobile-view="library"]) #Desktop_LeftSidebar_Id,
                    body:not([data-mobile-view="library"]) nav[aria-label="Main"] {
                        display: none !important;
                    }
                    body:not([data-mobile-view="library"]) .Root__main-view,
                    body:not([data-mobile-view="library"]) main {
                        width: 100vw !important;
                        max-width: 100vw !important;
                        height: 100vh !important;
                        margin: 0 !important;
                        border-radius: 0 !important;
                    }

                    /* Library Mode: Show Your Library / Playlists full width when 'My Playlists' tab is tapped */
                    body[data-mobile-view="library"] .Root__main-view {
                        display: none !important;
                    }
                    body[data-mobile-view="library"] #Desktop_LeftSidebar_Id,
                    body[data-mobile-view="library"] nav[aria-label="Main"] {
                        display: block !important;
                        width: 100vw !important;
                        max-width: 100vw !important;
                        height: 100vh !important;
                    }

                    /* Make track rows touch-friendly on mobile screens */
                    div[data-testid="tracklist-row"] {
                        min-height: 58px !important;
                        padding: 6px 8px !important;
                    }
                `;
                document.head.appendChild(style);

                // When user clicks a playlist inside Library view, automatically switch to Main view to show its songs
                document.addEventListener('click', function(e) {
                    if (document.body.getAttribute('data-mobile-view') === 'library') {
                        var row = e.target.closest('li, [role="listitem"], a[href*="/playlist/"], a[href*="/album/"], a[href*="/collection/"]');
                        if (row) {
                            setTimeout(function() {
                                document.body.setAttribute('data-mobile-view', 'main');
                            }, 250);
                        }
                    }
                }, true);

                // Poll Now Playing state and send to Native Android Bottom Play Bar
                setInterval(function() {
                    try {
                        var titleEl = document.querySelector('[data-testid="context-item-info-title"], [data-testid="now-playing-widget"] a');
                        var artistEl = document.querySelector('[data-testid="context-item-info-subtitles"], [data-testid="context-item-info-artist"]');
                        var playBtn = document.querySelector('[data-testid="control-button-playpause"]');
                        var progBar = document.querySelector('[data-testid="playback-progressbar"] input[type="range"], [data-testid="playback-position"]');

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
                    } catch(err) {}
                }, 800);
            })();
        """.trimIndent()
    }
}
