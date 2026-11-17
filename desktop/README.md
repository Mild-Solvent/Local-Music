# Muzza Desktop

Muzza for Windows and Linux: the same water theme, fish and features as the phone app
(library from your music folders, playlists, liked songs, shuffle / repeat, queue, search,
sleep timer, equalizer, custom covers, built-in downloader).

- Kotlin + Compose Multiplatform (desktop). The theme drawing (`ui/theme/Ocean.kt`) is copied
  unchanged from the Android app.
- Playback: the bundled ffmpeg decodes any format to PCM, played through Java Sound.
- Downloader: yt-dlp, fetched from its official GitHub release the first time it is used.
- Updates: checks GitHub Releases for tags `desktop-vX.Y.Z` (phone releases are ignored).

## Build (on the devbox, never in a cloud session)

```
desktop/packaging/package.sh ~/downloadables/muzza-desktop
```

This runs the tests and makes `Muzza-Desktop-<ver>-windows-x64.zip` (Muzza.exe + its own Java + ffmpeg)
and `Muzza-Desktop-<ver>-linux-x64.tar.gz`. Release them with tag `desktop-v<ver>` and
`make_latest=false`, so the phone updater keeps seeing the phone release as "latest".

Headless check of the real parts: `runtime/bin/java -jar app/muzza.jar --selftest <music folder> [--download]`.
