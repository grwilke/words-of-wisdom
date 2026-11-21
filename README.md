# 🦉 Words of Wisdom (& Life Lessons)

A theatrical **Google TV / Android TV** party game where players join on their phones with zero app install, rewrite secret life lessons into clever English metaphors, and race to decode each other's metaphors from among sneaky thematic decoys.

---

## Highlights

- **TV-First Couch Experience**: Deep Midnight Parlor & Golden Fable Jetpack Compose UI with animated fireflies, confetti celebrations, circular countdown rings, 5% overscan safe zones, and full TV remote (D-pad / OK / Back / Mute) support.
- **Zero-Install Mobile Web Controller**: Embedded Ktor CIO HTTP + WebSocket LAN server (`port 8777`) runs directly inside the TV app — scan the QR code or open the local URL on any iPhone or Android browser on the same Wi-Fi.
- **120 Curated Life Lessons across 10 Themes**: *Connection, Patience, Courage, Humility, Truth, Growth, Gratitude, Trust, Craft,* and *Kindness* — each paired with 5 hand-crafted confusable decoys, 3 imagery sparks, and a validated bot metaphor.
- **Smart Anti-Spoiler Metaphor Guard**: Blocks literal word/stem reuse (`happy`/`happiness`, `alone`/`loneliness`) with live inline feedback on the Sage's phone before submission.
- **Custom Synthesized Party Soundtrack & SFX**: 3 seamless looping lounge/party tracks (`bgm_lobby`, `bgm_play`, `bgm_podium`) plus 9 zero-latency `SoundPool` effects with 1-click mute.
- **Practice AI Bots & Sudden-Death Tie-Breaker**: Add witty AI bots from the lobby for solo/2-player play, and settle tied 1st-place finishes with a live **Golden Duel** sudden-death round!

---

## Architecture

| Module | Tech | Responsibility |
| :--- | :--- | :--- |
| `:core` | Pure Kotlin (JVM) | Deterministic game state machine (`WisdomGameEngine`), `MetaphorValidator`, `ScoringEngine`, `LessonBank`, `BotBrain`, `WireProtocol`, and 120-lesson `wisdom_bank.json`. |
| `:server` | Kotlin + Ktor CIO WebSockets | Embedded LAN HTTP/WS server (`PartyLanServer`), zero-dependency mobile web controller (`index.html`, `style.css`, `controller.js`), and desktop `DevHost`. |
| `:app` | Android TV + Jetpack Compose | TV Compose screens (`Lobby`, `Writing`, `Guessing`, `Reveal`, `TieBreaker`, `Podium`), `SoundEngine`, ZXing QR generator, and `PartyViewModel`. |
| `docs/join/` | Static HTML/JS | HTTPS GitHub Pages bridge for Android 17 Chrome Local Network Access permission prompt. |
| `tools/` | Python + Node.js + Bash | Content & audio generators, store asset packager, live 4-player E2E WebSocket verifier (`simulate_party_e2e.mjs`), Play API uploader (`play_upload.sh`), and ADB TV installer (`install_tv.sh`). |

---

## Build, Test & Verify

```bash
# 1. Pure Kotlin core unit tests (validator, scoring, lesson bank, full FSM, tie-breaker, bots, reconnect)
./gradlew :core:test

# 2. Embedded LAN server HTTP + live WebSocket integration tests
./gradlew :server:test

# 3. Mobile web controller JS unit tests
node --test server/src/test/js/controller.test.mjs

# 4. Build Android TV debug APK & signed release AAB
./gradlew :app:assembleDebug :app:bundleRelease

# 5. Install on an Android TV / Google TV over ADB
tools/install_tv.sh app/build/outputs/apk/release/app-release.apk <tv-ip>:5555

# 6. Upload signed AAB to Google Play Console Internal Testing track
tools/play_upload.sh --build --track internal --status draft
```

See [DESIGN.md](DESIGN.md) for the full game design evaluation, scoring math, and playability tuning notes.
