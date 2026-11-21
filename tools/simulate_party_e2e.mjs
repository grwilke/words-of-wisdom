#!/usr/bin/env node
/**
 * Live End-to-End Multi-Player Party Verification + TV Screenshot Capture.
 *
 * Connects 4 real WebSocket phone controllers to the running Words of Wisdom TV app
 * (via `adb forward tcp:8777 tcp:8777` or DevHost), verifies HTTP controller serving,
 * anti-spoiler rejection, live typing indicator, voting, reveal + applause, and Podium,
 * while optionally capturing 1080p Android TV screenshots at every phase.
 */

import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.WOW_URL || 'http://127.0.0.1:8777';
const WS_URL = BASE.replace(/^http/, 'ws') + '/ws';
const CAPTURE_DIR = process.env.WOW_CAPTURE_DIR || '';
const ADB_BIN = process.env.ADB_BIN || `${process.env.HOME}/Android/Sdk/platform-tools/adb`;
const ADB_SERIAL = process.env.ADB_SERIAL || '';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function snapTv(filename) {
  if (!CAPTURE_DIR) return;
  fs.mkdirSync(CAPTURE_DIR, { recursive: true });
  const out = path.join(CAPTURE_DIR, filename);
  const serialFlag = ADB_SERIAL ? `-s ${ADB_SERIAL}` : '';
  execSync(`${ADB_BIN} ${serialFlag} exec-out screencap -p > "${out}"`, { stdio: 'inherit' });
  console.log(`[CAP] Saved TV screenshot -> ${out}`);
}

class PhoneClient {
  constructor(name, avatarId) {
    this.name = name;
    this.avatarId = avatarId;
    this.state = null;
    this.lastRejected = null;
    this.token = null;
    this.ws = null;
  }

  async connect() {
    await new Promise((resolve, reject) => {
      const ws = new WebSocket(WS_URL);
      this.ws = ws;
      ws.addEventListener('open', () => resolve());
      ws.addEventListener('error', (e) => reject(e));
      ws.addEventListener('message', (ev) => {
        const msg = JSON.parse(ev.data);
        if (msg.t === 'joined') this.token = msg.token;
        else if (msg.t === 'state') this.state = msg;
        else if (msg.t === 'metaphor_rejected') this.lastRejected = msg;
      });
    });
    this.send({ t: 'join', name: this.name, token: null, avatarId: this.avatarId });
    await this.waitFor((s) => s?.phase === 'LOBBY');
  }

  send(obj) {
    this.ws.send(JSON.stringify(obj));
  }

  async waitFor(pred, timeoutMs = 10_000) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      if (pred(this.state)) return this.state;
      await sleep(80);
    }
    throw new Error(`[${this.name}] Timed out waiting for state condition (phase=${this.state?.phase})`);
  }

  close() {
    try { this.ws?.close(); } catch {}
  }
}

async function main() {
  // 1. Verify HTTP endpoints
  const html = await fetch(`${BASE}/`).then((r) => r.text());
  if (!html.includes('Words of Wisdom')) throw new Error('Controller HTML missing title');
  const css = await fetch(`${BASE}/style.css`).then((r) => r.status);
  const js = await fetch(`${BASE}/controller.js`).then((r) => r.status);
  if (css !== 200 || js !== 200) throw new Error(`Expected 200 for css/js, got ${css}/${js}`);
  console.log('[OK] HTTP controller assets served cleanly.');

  // 2. Connect 4 live phone players
  const players = [
    new PhoneClient('Maya', 'owl'),
    new PhoneClient('Leo', 'fox'),
    new PhoneClient('Sofia', 'octopus'),
    new PhoneClient('Mateo', 'dragon'),
  ];
  for (const p of players) {
    await p.connect();
    await sleep(200);
  }
  await players[0].waitFor((s) => s.players.length === 4);
  console.log(`[OK] 4 players joined Room ${players[0].state.roomCode}.`);

  // Configure QUICK (4 turns = 1 per player)
  players[0].send({ t: 'config', length: 'QUICK', writeSeconds: 75, guessSeconds: 35, optionCount: 4 });
  await sleep(1_400);
  snapTv('tv-1-lobby.png');

  // 3. Start Game
  players[0].send({ t: 'start' });
  await players[0].waitFor((s) => s.phase === 'WRITING');
  console.log('[OK] Transitioned to WRITING.');

  const poeticLines = [
    'A single scarecrow in the field only smiles when he sees the crows',
    'Two lanterns carried side by side turn a foggy alley into summer',
    'A tiny acorn resting in winter mud already holds an oak canopy',
    'Carving a quiet birch spoon beats shouting from an empty balcony',
  ];

  let capturedWriting = false;
  let capturedGuessing = false;
  let capturedReveal = false;

  for (let round = 0; round < 10; round++) {
    const curPhase = players[0].state.phase;
    if (curPhase === 'PODIUM') break;

    if (curPhase === 'WRITING') {
      const sage = players.find((p) => p.state.isSage);
      if (!sage) throw new Error('No Sage found in WRITING');

      // Test anti-spoiler on round 0!
      if (round === 0) {
        sage.send({ t: 'submit_metaphor', text: sage.state.secretLesson });
        await sleep(300);
        if (!sage.lastRejected) throw new Error('Expected literal copy to be rejected by anti-spoiler guard!');
        console.log(`[OK] Anti-spoiler rejected literal copy: ${sage.lastRejected.reason}`);
      }

      // Simulate live typing indicator so TV quill energy bar animates
      const text = poeticLines[round % poeticLines.length];
      sage.send({ t: 'typing', chars: Math.floor(text.length * 0.7) });
      await sleep(1_400);
      if (!capturedWriting) {
        snapTv('tv-2-writing.png');
        capturedWriting = true;
      }

      sage.send({ t: 'submit_metaphor', text });
      await players[0].waitFor((s) => s.phase === 'GUESSING');
      console.log(`[OK] Turn ${round + 1}: ${sage.name} submitted metaphor -> GUESSING.`);
    }

    if (players[0].state.phase === 'GUESSING') {
      const seekers = players.filter((p) => !p.state.isSage);
      // Have first seeker vote so TV shows mixed "Locked in ✓" + "Thinking…"
      seekers[0].send({ t: 'vote', optionId: 'A' });
      await sleep(1_400);
      if (!capturedGuessing) {
        snapTv('tv-3-guessing.png');
        capturedGuessing = true;
      }
      // Remaining seekers vote varied options
      seekers[1].send({ t: 'vote', optionId: 'B' });
      await sleep(250);
      seekers[2].send({ t: 'vote', optionId: 'C' });

      await players[0].waitFor((s) => s.phase === 'REVEAL', 8_000);
      console.log(`[OK] Turn ${round + 1}: All voted -> REVEAL (correct=${players[0].state.correctOptionId}).`);

      // Seekers applaud!
      seekers[0].send({ t: 'applause' });
      seekers[1].send({ t: 'applause' });
      await sleep(1_600);
      if (!capturedReveal) {
        snapTv('tv-4-reveal.png');
        capturedReveal = true;
      }

      // Host advances
      players[0].send({ t: 'next' });
      await players[0].waitFor((s) => s.phase !== 'REVEAL', 6_000);
    }

    if (players[0].state.phase === 'TIE_BREAKER') {
      await sleep(700);
      snapTv('tv-5-tiebreaker.png');
      for (const p of players) {
        if (p.state.isTieBreakerContender) {
          p.send({ t: 'vote', optionId: 'A' });
          await sleep(200);
        }
      }
      await players[0].waitFor((s) => s.phase === 'PODIUM', 10_000);
    }
  }

  await players[0].waitFor((s) => s.phase === 'PODIUM', 10_000);
  await sleep(1_200);
  snapTv('tv-6-podium.png');

  const top = players[0].state.players[0];
  console.log(`[SUCCESS] Full 4-player live game reached PODIUM! Winner: ${top.name} (${top.score} pts), highlights=${players[0].state.highlights.length}, awards=${players[0].state.awards.length}`);

  for (const p of players) p.close();
}

main().catch((err) => {
  console.error('[FAIL]', err);
  process.exit(1);
});
