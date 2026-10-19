# SaavnDL (Android)

Jetpack Compose app on top of the open-source `jiosaavn-api` (default server: https://saavn.sumit.co).

## Build the APK with GitHub Actions
1. Create a new GitHub repo, upload **everything in this folder** (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab -> *Build APK* -> **Run workflow** (it also runs on every push).
3. When it finishes, download the artifact **SaavnDL-debug-apk**, unzip, install the `.apk`.

The workflow uses Gradle 8.9 directly (no wrapper jar needed) and JDK 17.
If the optional LAME dependency can't be resolved, the workflow automatically retries without it
(MP3 option then falls back to tagged M4A).

## Features
Search songs / albums / playlists / artists (paged) · paste or share a JioSaavn link into the app ·
preview player with mini-player · favorites · similar songs · song info · multi-select download ·
album/playlist/artist download into sub-folders with track numbers · download queue with progress,
cancel, retry, foreground notification · formats: Original M4A, M4A + full metadata, MP3 + full metadata
(title, artist, album, album artist, year, track no., label, copyright, comment link, cover art) ·
library with play / share / delete · CSV export · batch import from a .txt file ·
quality, file-name style, parallel downloads, theme (System/Light/Dark/AMOLED), custom API URL.

Songs are saved to `Music/SaavnDL` via MediaStore (no storage permission needed, Android 10+).

## Logo
The launcher icon is an original design. To use your own logo, replace
`res/drawable/ic_launcher_foreground.xml` (or add a PNG and point `mipmap-anydpi/ic_launcher.xml` to it).
