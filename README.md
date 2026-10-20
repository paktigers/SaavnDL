# SaavnDL (Android)

Jetpack Compose app on top of the open-source `jiosaavn-api` (default server: https://saavn.sumit.co).

## Build the APK (GitHub Actions)
1. Create a GitHub repo and upload **everything inside this folder** (keep `.github/workflows/build.yml`).
2. Actions tab -> **Build APK** -> *Run workflow*. Download artifact **SaavnDL-apk**.
3. Gradle 8.9 + JDK 17 are set up by the workflow (no wrapper jar needed). If the optional LAME
   dependency can't be resolved the workflow retries without it (MP3 then falls back to tagged M4A).

## Publish releases + keep the same signature (recommended)
Create a keystore once (on any computer with Java):
```
keytool -genkeypair -v -keystore release.jks -alias saavndl -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks      # copy the output
```
Repo -> Settings -> Secrets and variables -> Actions -> add: `KEYSTORE_BASE64` (the base64 text),
`KEYSTORE_PASSWORD`, `KEY_ALIAS` (saavndl), `KEY_PASSWORD`.
Then push a tag such as `v1.0.1` (`git tag v1.0.1 && git push --tags`): the workflow builds a **signed release APK**
and attaches it to a GitHub Release. In the app: Settings -> Updates -> enter `owner/repo` to get update prompts.
Always keep the same keystore, otherwise Android will refuse to install updates over the old app.

## Features
- Search songs/albums/playlists/artists, live suggestions, language filter, "For you" from your favorites
- Paste or share a JioSaavn link into the app; open artist pages, download all albums
- Full player: background playback, lock-screen/notification controls, seek, shuffle, repeat, sleep timer,
  play next / queue, lyrics (LRCLIB, synced highlighting), home-screen widget
- Downloads: Original M4A, M4A + metadata, MP3 + metadata (tags, cover art, optional lyrics), queue with pause/resume,
  Wi-Fi only, auto-retry, parallel downloads, sub-folders + track numbers, skip existing
- Library: sort, filter, tag editor, duplicate finder, remove missing entries, share/delete; favorites; your own playlists
- Extras: auto-download favorites, backup/restore (JSON), batch import (.txt), CSV export, update checker,
  crash log, Material You / AMOLED themes, English / Urdu / Hindi (main screens)

Songs are saved to `Music/SaavnDL` via MediaStore (Android 10+, no storage permission).

## Logo
The launcher icon is an original design. To use your own, replace `res/drawable/ic_launcher_foreground.xml`.
