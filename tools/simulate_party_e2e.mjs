#!/usr/bin/env node
/**
 * Live End-to-End Multi-Player Party Verification + TV Screenshot Capture (v1.1.0).
 *
 * Exercises ALL v1.1.0 fun upgrades live on the Android TV release APK:
 *  - Party Sprint simultaneous writing (all 4 players write concurrently)
 *  - Decoy preview verification for every writer
 *  - Wildcard Metaphor World acceptance (+40 Style Bonus)
 *  - Anti-spoiler rejection of literal copy
 *  - Live floating emoji reactions on TV
 *  - Golden Owl 2x Double-Down bet during Guessing
 *  - 3-Beat Theatrical Reveal + Poet's Star (+25)
 *  - Grand Podium + Hall of Metaphors
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
  const html = await fetch(`${BASE}/`).then((r) => r.text());
  if (!html.includes('Golden Owl')) throw new Error('Controller HTML missing v1.1.0 Golden Owl UI');
  console.log('[OK] HTTP v1.1.0 controller assets served cleanly.');

  const players = [
    new PhoneClient('Maya', 'owl'),
    new PhoneClient('Leo', 'fox'),
    new PhoneClient('Sofia', 'octopus'),
    new PhoneClient('Mateo', 'dragon'),
  ];
  for (const p of players) {
    await p.connect();
    await sleep(180);
  }
  await players[0].waitFor((s) => s.players.length === 4);
  console.log(`[OK] 4 players joined Room ${players[0].state.roomCode} (default mode=${players[0].state.config.mode}).`);

  // Configure Party Sprint + All Packs + QUICK (1 simultaneous sprint -> 4 rapid showcases!)
  players[0].send({
    t: 'config',
    mode: 'PARTY_SPRINT',
    pack: 'ALL',
    length: 'QUICK',
    writeSeconds: 75,
    guessSeconds: 35,
    optionCount: 4,
  });
  await sleep(1_400);
  snapTv('tv-1-lobby.png');

  // Start Game -> Simultaneous Party Sprint WRITING!
  players[0].send({ t: 'start' });
  await players[0].waitFor((s) => s.phase === 'WRITING' && s.isSimultaneousWriting);

  // Verify every player got a unique lesson + 2 decoy previews + wildcard offer
  for (const p of players) {
    if (!p.state.secretLesson) throw new Error(`${p.name} missing secretLesson in Party Sprint!`);
    if ((p.state.decoyPreview || []).length !== 2) throw new Error(`${p.name} missing 2 decoy previews!`);
    if (!p.state.offeredWildcard) throw new Error(`${p.name} missing Wildcard offer!`);
  }
  console.log('[OK] Party Sprint WRITING active: all 4 writers received secret lessons, decoy previews & wildcards.');

  // Anti-spoiler check on Maya
  players[0].send({ t: 'submit_metaphor', text: players[0].state.secretLesson, wildcardAccepted: true });
  await sleep(300);
  if (!players[0].lastRejected) throw new Error('Expected literal copy rejection!');
  console.log(`[OK] Anti-spoiler rejected literal copy: ${players[0].lastRejected.reason}`);

  const poeticLines = [
    'A single scarecrow in the field only smiles when he sees the crows',
    'Two lanterns carried side by side turn a foggy alley into summer',
    'A tiny acorn resting in winter mud already holds an oak canopy',
    'Carving a quiet birch spoon beats shouting from an empty balcony',
  ];

  // Maya & Leo submit early (with Wildcard accepted!), Sofia & Mateo still typing -> capture lively Sprint Board + floating reaction!
  players[0].send({ t: 'submit_metaphor', text: poeticLines[0], wildcardAccepted: true });
  players[1].send({ t: 'submit_metaphor', text: poeticLines[1], wildcardAccepted: true });
  players[2].send({ t: 'typing', chars: 48 });
  players[3].send({ t: 'typing', chars: 26 });
  await sleep(600);
  players[0].send({ t: 'react', emoji: '🔥' });
  players[1].send({ t: 'react', emoji: '😂' });
  await sleep(700);
  snapTv('tv-2-writing.png');

  // Sofia & Mateo submit -> straight into rapid-fire showcases!
  players[2].send({ t: 'submit_metaphor', text: poeticLines[2], wildcardAccepted: false });
  players[3].send({ t: 'submit_metaphor', text: poeticLines[3], wildcardAccepted: true });
  await players[0].waitFor((s) => s.phase === 'GUESSING', 6_000);
  console.log('[OK] All 4 scrolls sealed -> rapid-fire GUESSING started!');

  let capturedGuessing = false;
  let capturedReveal = false;

  for (let round = 0; round < 8; round++) {
    const phase = players[0].state.phase;
    if (phase === 'PODIUM') break;

    if (phase === 'GUESSING') {
      const seekers = players.filter((p) => !p.state.isSage);
      // First seeker slams Golden Owl 2x Double-Down on Turn 1!
      seekers[0].send({ t: 'vote', optionId: 'A', doubleDown: round === 0 });
      await sleep(1_200);
      if (!capturedGuessing) {
        snapTv('tv-3-guessing.png');
        capturedGuessing = true;
      }
      seekers[1].send({ t: 'vote', optionId: 'B', doubleDown: false });
      await sleep(250);
      seekers[2].send({ t: 'vote', optionId: 'C', doubleDown: false });

      await players[0].waitFor((s) => s.phase === 'REVEAL', 8_000);
      console.log(`[OK] Showcase ${round + 1}: REVEAL (correct=${players[0].state.correctOptionId}).`);

      seekers[0].send({ t: 'applause' });
      seekers[1].send({ t: 'applause' });
      seekers[2].send({ t: 'react', emoji: '🤯' });

      // Wait 3.3s on Reveal 1 so all 3 Theatrical Reveal beats (Voters -> Truth -> Animated Scores) finish!
      await sleep(capturedReveal ? 700 : 3_300);
      if (!capturedReveal) {
        snapTv('tv-4-reveal.png');
        capturedReveal = true;
      }

      players[0].send({ t: 'next' });
      await players[0].waitFor((s) => s.phase !== 'REVEAL', 6_000);
    }

    if (players[0].state.phase === 'TIE_BREAKER') {
      await sleep(1_000);
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
  players[0].send({ t: 'react', emoji: '🦉' });
  await sleep(1_200);
  snapTv('tv-6-podium.png');

  const top = players[0].state.players[0];
  console.log(`[SUCCESS] Full v1.1.0 Party Sprint E2E reached PODIUM! Winner: ${top.name} (${top.score} pts), highlights=${players[0].state.highlights.length}, awards=${players[0].state.awards.length}`);
  for (const p of players) p.close();
}

main().catch((err) => {
  console.error('[FAIL]', err);
  process.exit(1);
});
