# Spotify Desktop Web Player for Android

This project builds an Android APK that runs Spotify's **Desktop Web Player** (`https://open.spotify.com`) inside a custom, DRM-enabled Android WebView with background audio playback.

## Features Included
1. **Desktop User-Agent Spoofing**: Forces Spotify to serve the full desktop web player instead of redirecting to the mobile app or Play Store.
2. **Widevine DRM Support (`RESOURCE_PROTECTED_MEDIA_ID`)**: Automatically grants Encrypted Media Extensions (EME) permissions in `WebChromeClient` so Spotify encrypted streams play natively.
3. **Background Audio Service**: Runs a foreground `mediaPlayback` service with CPU and Wi-Fi wake locks so music continues playing when your screen turns off or you switch apps.
4. **Desktop Viewport Adaptation**: Configures wide viewport overview mode with pinch-to-zoom and custom CSS adjustments for mobile screens.

---

## How to Get Your APK in 2 Minutes (No PC or Android Studio Needed)

This project includes an automated **GitHub Actions** cloud compiler workflow (`.github/workflows/build-apk.yml`).

1. Create a free account at [github.com](https://github.com) and create a **New Repository**.
2. Upload all the files inside this folder to your repository (making sure the `.github` folder is included).
3. Click the **Actions** tab at the top of your GitHub repository.
4. Click on the **Build Android APK** workflow run. Once it finishes (~2 minutes), scroll down to **Artifacts** and download **`SpotifyDesktopWeb-APK`** (which contains `app-debug.apk`).
5. Open `app-debug.apk` on your Android phone to install!

---

## Building Locally with Android Studio

1. Open Android Studio and select **Open** -> choose this project folder.
2. Let Gradle sync the dependencies.
3. Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Your installable APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
