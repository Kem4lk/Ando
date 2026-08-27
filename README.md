# Ando

A minimal Android home-screen launcher built with Jetpack Compose. It registers
as a `HOME`/`LAUNCHER` activity and renders a feed of app cards — each showing
a small, **entirely fabricated** "recent activity" preview (recent chats,
recently played tracks, recent photos, upcoming calendar events, etc.) so the
home screen feels alive without any account access or network calls.

Apps included (dummy data only, no real integrations): Telegram, WhatsApp,
Google Photos, Chrome, Spotify, Mail, Maps, ChatGPT, Flux (notes), Settings,
Reddit, Messages, Phone, X, Camera, Calendar.

## Structure

- `app/src/main/java/com/ando/launcher/model` — `AppEntry` / `RecentItem` data classes.
- `app/src/main/java/com/ando/launcher/data/DummyData.kt` — the hardcoded, fake
  "recent content" feed for every app tile.
- `app/src/main/java/com/ando/launcher/MainActivity.kt` — the Compose UI: a
  clock header plus a scrollable list of app cards.
- `app/src/main/res/drawable` — app icon artwork used for each tile.

## Build

```
./gradlew assembleDebug
```

Requires the Android SDK (`ANDROID_HOME`/`local.properties` with `sdk.dir`)
and network access to the Google Maven repository to resolve the Android
Gradle Plugin — this sandbox had neither, so the build has not been verified
end-to-end here.

## Run

Install the APK, then set it as the default home app (or launch it directly)
to see the dummy recent-activity feed.
