# Yoga Nidra app

A small, installable Android-friendly app that collects guided yoga nidra /
NSDR (non-sleep deep rest) sessions. Each session can be played **inside the
app** (embedded YouTube player, no YouTube app needed) or opened **in YouTube**
with one tap.

The catalogue follows the NSDR chapter of Andrew Huberman's *Protocols* and the
Huberman Lab NSDR page: Huberman's own 10 / 20 / 30 minute recordings plus the
teachers he points to (Kelly Boys, Ally Boothroyd, Liam Gillen).

## Install on an Android phone

Pick whichever is easier. Both give you a home-screen icon that opens full screen.

### Option A: install from the browser (no APK, ~30 seconds)

1. Host the `www/` folder anywhere static, or enable GitHub Pages for this repo
   (Settings → Pages → Source: *GitHub Actions*). The
   `yoga-nidra-pages.yml` workflow publishes `www/` automatically.
2. Open the URL in Chrome on the phone.
3. Chrome menu (⋮) → **Add to Home screen** → **Install**.

### Option B: sideload the APK

1. Run the **Yoga Nidra – build Android APK** workflow (Actions tab → Run
   workflow) or push to `main`. It publishes `yoga-nidra.apk` on a GitHub
   Release tagged `yoga-nidra-latest`.
2. On the phone, open the Releases page, tap the `.apk`, and allow
   "install from this source" when Android asks.

The APK is a debug build signed with a debug key, which is fine for personal
phones. For Play Store distribution you would need a release keystore.

## Run locally

```bash
cd yoga-nidra-app
npm start          # serves www/ on http://localhost:5173
```

No build step. The app is plain HTML, CSS and JavaScript.

## Build the APK on your own machine

Requires Node 18+, JDK 17 and Android Studio (or the Android SDK).

```bash
npm install
npx cap add android      # first time only; creates android/ (git-ignored)
npm run android:apk      # -> android/app/build/outputs/apk/debug/app-debug.apk
# or: npm run android:open  to open in Android Studio and run on a device
```

## Add or change sessions

- **Permanently:** edit `www/videos.js`. Each entry is a YouTube video ID plus
  title, teacher, minutes and a short note.
- **On the phone only:** use "Add your own video" at the bottom of the app.
  Favourites, play counts and added videos live in the phone's local storage.

## Layout

```
yoga-nidra-app/
├── www/                   the whole app (PWA)
│   ├── index.html
│   ├── app.js             UI, filters, player, favourites, custom videos
│   ├── videos.js          session catalogue
│   ├── styles.css
│   ├── sw.js              offline cache for the app shell
│   ├── manifest.webmanifest
│   └── icons/
├── scripts/make-icons.py  regenerates the icons (needs Pillow)
├── capacitor.config.json  Android wrapper config
└── package.json
```

## Notes

- Videos stream from YouTube, so playback needs a connection. The app shell
  itself works offline.
- The embedded player uses `youtube-nocookie.com` on the web and
  `youtube.com` inside the Android wrapper, where the privacy domain refuses
  to play.
