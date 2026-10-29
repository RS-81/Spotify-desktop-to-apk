# Spotify Desktop Backend + Mobile UI for Android

This project runs Spotify's **Desktop Web Player** as a backend engine inside an Android WebView (unlocking on-demand song selection, unlimited skips, and queue control) while presenting a **clean, full-size Mobile UI** with native Android Search, Navigation Tabs (Home / Search / Playlists), and a Native Bottom Play Bar.

## Why This Fixes Playback & Tiny Desktop UI
1. **Deep Desktop Spoofing (`navigator.userAgentData` + `window.screen`)**: Modern Spotify checks `navigator.userAgentData.mobile` and `window.screen.width` in addition to the User-Agent string. This app overrides those properties before Spotify loads so Widevine DRM and the Web Playback SDK initialize properly.
2. **Full-Size Mobile Layout (1.0x Scale)**: Instead of shrinking a 1280px monitor onto a phone screen, CSS rules collapse the desktop sidebars and scale whichever view you select (Home, Search, or Library/Playlists) to 100% mobile width with touch-friendly rows.
3. **Native Android Play Bar & Tab Bar**: Native Android bottom playback controls (Play/Pause, Previous, Next, Shuffle, Track Title & Artist) and top navigation tabs communicate with the desktop web player backend via a JavaScript bridge.
