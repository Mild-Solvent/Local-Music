# Hushwave — offline music player for Android

No ads. No account. No Wi-Fi needed. Plays the music that's on the phone, and has a safe
built-in downloader so you never need sketchy "YouTube to MP3" websites.

## Features
**Library** · songs, albums, artists, folders · sort by title / artist / album / date added / length · instant search (ignores accents and case) with search history · hides ringtones & voice notes (adjustable)

**Playback** · background playback with notification + lock-screen controls · gapless · shuffle · repeat all / repeat one (loop a playlist forever) · queue: play next, add to queue, reorder, remove, save queue as playlist · resumes exactly where you left off after a restart · pauses when headphones are unplugged · skips broken files instead of stopping

**Playlists** · create, rename, pin, delete · add songs from anywhere or with a picker · reorder · Liked songs · "On repeat" (most played) · Recently added · Jump back in (recently played)

**Extras** · sleep timer with gentle fade-out (or "end of this song") · equalizer with presets, bass boost, 3D surround, volume boost · speed & pitch (with "slowed" and "sped up" presets) · skip silence · ±10 s seek · swipe the cover to change song · dark / light / wallpaper colours · share or delete a song file

**Downloader** · search YouTube or SoundCloud inside the app, or paste any link yt-dlp supports · Share → Hushwave from the YouTube app · whole playlists · M4A / MP3 / Opus · cover art and artist/title cleaned up automatically · saved to `Music/Hushwave` · runs in the background with a progress notification · self-updating engine (yt-dlp + ffmpeg run locally on the phone)

Only download music you have the right to download.

## Build
Requires JDK 17+ and the Android SDK (platform 35).

```
./gradlew assembleRelease
```
APKs land in `app/build/outputs/apk/release/` (one per CPU type; almost every phone today wants `arm64-v8a`).
Release signing reads `keystore.properties` (not committed); without it the build is signed with the debug key.
