# Game Design Evaluation & Tuning — Words of Wisdom (& Life Lessons)

This document records the playability evaluation, scoring math, anti-spoiler tuning, and UX decisions engineered to make **Words of Wisdom (& Life Lessons)** genuinely fun, hilarious, and smooth in a real living room.

---

## 1. Core Party Loop Evaluation

Each turn casts one player as the **Sage** and all remaining players as **Seekers**:

1. **Secret Prompt (`WRITING`)**: The Sage privately receives a curated English life lesson (e.g., *"Nobody can be happy alone"*) on their phone, alongside 3 optional imagery sparks (*"a lone scarecrow"*, *"a solo violin"*, *"an empty dinner table"*) and **1 free reroll** if inspiration does not strike immediately.
2. **Metaphorical Rewrite**: The Sage writes an English metaphor (e.g., *"A single scarecrow in the field only smiles when he sees the crows"*). Real-time inline validation flags literal word reuse before submission, while the TV displays an animated **Sage at the Quill** spotlight with live typing-energy progress (zero text leaked!).
3. **Confusable Decode (`GUESSING`)**: The TV unveils the Sage's metaphor inside an **Illuminated Parchment Scroll** alongside **4 or 5 options** (1 true original lesson + 3 or 4 hand-crafted, thematically adjacent decoys).
4. **Theatrical Reveal & Bravo (`REVEAL`)**: The true lesson glows green with confetti and fanfare, voter avatars appear beside each option so the couch sees who fell for which decoy, points animate, and Seekers can send a **👏 Bravo** charm reaction to great metaphors.
5. **Sudden-Death Tie-Breaker (`TIE_BREAKER`)**: If two or more leaders tie for 1st place (`score > 0`), an automatic **Golden Duel** presents a bonus metaphor; the fastest contender to decode it claims the crown.
6. **Grand Podium (`PODIUM`)**: Animated 3-tier Gold/Silver/Bronze podium with continuous confetti, spotlight-spreading superlative awards (*Golden Quill*, *Oracle Eye*, *Crowd Favorite* / *Hot Streak*), and an auto-cycling **Hall of Metaphors** carousel.

---

## 2. Failure Modes Identified & Tuned Solutions

| Party Game Pitfall | Observed Risk | Engineered Fix |
| :--- | :--- | :--- |
| **Dead air during typing** | Couch loses energy staring at a static wait screen for 60–90s. | 1 free lesson reroll, optional imagery sparks, animated Quill Spotlight + live typing-energy bar on TV, immediate transition on submit, and graceful auto-submit/skip on timeout. |
| **Literal copying / spoilers** | Sage writes *"Nobody feels happy alone"* instead of a metaphor. | `MetaphorValidator` enforces 12–180 chars, ≥ 3 English words, anti-gibberish/repeat-char guard, and morphological stem-collision blocking (`happy`/`happiness`, `alone`/`loneliness`) while whitelisting common English stop words (`until`, `can`, `be`, etc.). |
| **Trivially obvious decoys** | Random proverbs make guessing effortless even for vague clues. | 120 curated lessons organized into 10 thematic clusters, each paired with **5 hand-crafted adjacent-theme decoys** that share emotional territory yet remain semantically distinct. |
| **Score skew across party sizes** | Flat per-guesser points make a 7-seeker round dwarf a 2-seeker round. | Monotonic normalized Sage scale: `round(150 * correct / seekers) + 30` clarity bonus when 100% of ≥ 2 seekers decode it (max 180 pts whether 3 players or 10 players). |
| **1–2 player testing / small rooms** | Hard to try the game solo or with just a couple on the couch. | Built-in **Practice AI Bots** (`+ Add AI Bot` from phone or TV remote) with distinct personas, simulated typing meters, curated metaphors, and realistic 58%–78% guessing accuracy. |
| **Phone lock / browser refresh** | Reloading a mobile tab loses seat and score mid-game. | Persistent `localStorage` player tokens reconnect seamlessly to the exact player seat; host authority migrates automatically after 10s disconnect, and TV remote OK always works. |

---

## 3. Scoring Formulas

- **Seeker (per correct guess)**:
  - Base: `+100 pts`
  - Quick-Wit Speed Bonus: `+0..30 pts` (`round(30 * remainingMs / totalGuessMs)`)
  - Wisdom Streak Bonus: `+20 pts` per consecutive prior hit, capped at `+60 pts`
- **Sage (per turn)**:
  - `0 pts` if nobody guesses correctly
  - `150 pts` in 2-player mode when the single Seeker guesses correctly
  - `round(150 * correctCount / seekerCount)` for ≥ 2 Seekers, plus `+30 pts` **Master Sage Clarity Bonus** when every Seeker decodes the metaphor (`180 pts` total)
- **Bravo / Poet's Star**: `+25 pts` per Seeker Poet's Star during Reveal (capped at `+50 pts` per round) to reward hilarious, creative metaphors over dry synonyms.
- **Wildcard Metaphor World**: Optional thematic challenge (`+40 pts` Style Bonus when accepted and decoded by ≥ 1 Seeker).
- **Golden Owl 2× Double-Down**: One-shot per-player wager during Guessing (`2×` total Seeker points on hit; `−50 pts` floored at `0` on miss).
- **🔥 2× Final Cycle**: In multi-cycle games (`Classic` / `Marathon`), the entire final cycle doubles both Seeker and Sage points symmetrically so every player stays alive until the finish.

---

## 4. v1.1.0 Party Playability Upgrades Shipped

1. **Simultaneous "Party Sprint" Writing (Default on TV)**: Replaces sequential typing wait with one shared writing sprint for all players, followed by nonstop rapid-fire showcases (`GUESSING → REVEAL`).
2. **3-Beat Theatrical Reveal + Offline TV Narrator**: Reveals voters first (`0–1.8s`), illuminates the true lesson with confetti (`1.8s`), then rolls up scores (`2.5s+`); Android TV `TextToSpeech` dramatically narrates metaphors aloud with automatic BGM ducking.
3. **Sage Decoy Preview**: Pre-builds each scroll's options during `WRITING` and previews 2 competing decoys privately to the Sage so metaphors avoid accidental overlap.
4. **Golden Owl 2× Bet + 2× Final Cycle**: Adds high-stakes couch drama and fair late-game comebacks.
5. **Wildcard Metaphor Worlds + Boosted Poet's Star**: Shifts optimal play from safe synonym-swapping to genuinely funny storytelling.
6. **Modern & Funny Pack (+30 Lessons = 150 Total) + Decoy Audit**: Sharpens overlapping decoys and adds selectable *Modern & Funny* life lessons.
7. **Live Floating Couch Reactions (`🔥 😂 🤔 👏 🤯 🦉`)**: Keeps phones lively while waiting on writers, locked in, or celebrating on the Podium.
8. **Mid-Game Late Joiners + Android 17 Chrome QR Bridge**: Routes QR scans through `https://grwilke.github.io/words-of-wisdom/join#<ip>:<port>` so Android 17 Chrome prompts cleanly for Local Network Access.
