# 🦉 Words of Wisdom (& Life Lessons)

A theatrical **Google TV / Android TV** party game where players join on their phones with zero app install, rewrite secret life lessons into clever English metaphors, and race to decode each other's metaphors from among sneaky thematic decoys.

---

## Highlights (v1.1.0)

- **⚡ Party Sprint (Simultaneous Writing)**: Zero couch dead air! Everyone receives a secret life lesson at once for one shared writing sprint, then plays back-to-back rapid-fire guessing & reveals (toggleable with *Classic Turns* on TV remote or Host phone).
- **🎭 3-Beat Theatrical Reveal & Offline TV Narrator**: Builds real game-show suspense (voters pop in first → true lesson illuminates with confetti → animated score rollup) while offline Android TV `TextToSpeech` reads each metaphor aloud dramatically with automatic music ducking.
- **⚠️ Sage Decoy Preview**: Shows the Sage 2 competing board decoys while writing so metaphors become clever tactical puzzles instead of blind guesswork.
- **🦉 Golden Owl 2× Double-Down Bet & 🔥 2× Final Round**: Every Seeker gets 1 high-stakes Double-Down token per game (`2×` on hit, `−50` on miss), plus a fair `2×` Final Cycle comeback multiplier in multi-cycle games.
- **🏴‍☠️ Wildcard Metaphor Worlds (+40) & ⭐ Poet's Star (+25)**: Optional turn challenges (*Kitchen, Pirates, Space, Animals, Fairytale, Weather, Circus, Detective*) reward bold, funny metaphors over boring synonyms.
- **150 Curated Life Lessons (120 Classic + 30 Modern & Funny)**: Switch between *All Packs*, *Classic Wisdom*, or *Modern & Funny* everyday comedy across 10 themes, each paired with 5 sharpened confusable decoys.
- **Live Floating Couch Reactions (`🔥 😂 🤔 👏 🤯 🦉`)**: Waiting players and early finishers toss floating emoji reactions straight onto the TV screen.
- **Zero-Install Mobile Web Controller & Android 17 QR Bridge**: Embedded Ktor CIO HTTP + WebSocket LAN server (`port 8777`) with HTTPS GitHub Pages join bridge (`https://grwilke.github.io/words-of-wisdom/join`) and mid-game late joiner support.

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
