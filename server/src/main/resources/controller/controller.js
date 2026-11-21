// Words of Wisdom — Zero-dependency phone controller (ES module).
// Exports pure helpers for Node.js unit testing and boots UI when document exists.

export const AVATARS = [
  { id: 'owl', emoji: '🦉' },
  { id: 'fox', emoji: '🦊' },
  { id: 'octopus', emoji: '🐙' },
  { id: 'cat', emoji: '🐱' },
  { id: 'frog', emoji: '🐸' },
  { id: 'dragon', emoji: '🐉' },
  { id: 'penguin', emoji: '🐧' },
  { id: 'peacock', emoji: '🦚' },
  { id: 'bear', emoji: '🐻' },
  { id: 'turtle', emoji: '🐢' },
  { id: 'bee', emoji: '🐝' },
  { id: 'flamingo', emoji: '🦩' },
];

export function stemWord(word) {
  const w = word.toLowerCase().replace(/[^a-z]/g, '');
  if (w.length <= 3) return w;
  const suffixes = ['iness', 'ingly', 'edly', 'ment', 'ness', 'tion', 'sion', 'able', 'ible', 'less', 'ful', 'ing', 'ies', 'ied', 'ers', 'est', 'ly', 'ed', 'es', 's'];
  for (const s of suffixes) {
    if (w.endsWith(s) && w.length - s.length >= 3) {
      let base = w.slice(0, -s.length);
      if (s === 'ies' || s === 'ied' || s === 'iness') base += 'y';
      return base;
    }
  }
  return w;
}

/** Client-side live metaphor check matching core MetaphorValidator for instant typing feedback. */
export function evaluateDraft(text, forbiddenRoots = [], strictGuard = true) {
  const trimmed = (text || '').trim();
  const words = trimmed.match(/[A-Za-z']+/g) || [];
  if (trimmed.length < 12 || words.length < 3) {
    return { ok: false, message: 'Write at least 3 words (12+ characters)', hits: [] };
  }
  if (trimmed.length > 180) {
    return { ok: false, message: 'Keep it under 180 characters for TV readability', hits: [] };
  }
  if (!strictGuard || forbiddenRoots.length === 0) {
    return { ok: true, message: 'Looks poetic! Ready to cast ✨', hits: [] };
  }
  const forbiddenStems = forbiddenRoots.map((r) => ({ word: r, stem: stemWord(r) }));
  const draftStems = new Set(words.map((w) => stemWord(w.replace(/'/g, ''))));
  const hits = forbiddenStems
    .filter(({ stem }) => draftStems.has(stem))
    .map(({ word }) => word);
  if (hits.length > 0) {
    return {
      ok: false,
      message: `Swap out literal word${hits.length > 1 ? 's' : ''}: ${hits.join(', ')}`,
      hits,
    };
  }
  return { ok: true, message: 'Brilliant metaphor! Ready to cast ✨', hits: [] };
}

export function formatSeconds(ms) {
  const sec = Math.max(0, Math.ceil((ms || 0) / 1000));
  return `${sec}s`;
}

export function wsUrlForLocation(loc) {
  const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${loc.host}/ws`;
}

// ---- Browser Runtime ----
if (typeof document !== 'undefined') {
  const STORAGE_KEY = 'wow_player_token';
  const STORAGE_NAME = 'wow_player_name';

  let socket = null;
  let state = null;
  let selectedAvatar = AVATARS[0].id;
  let countdownAnchorMs = 0;
  let countdownAt = Date.now();
  let lastTurnKey = '';
  let typingThrottle = 0;
  let reconnectTimer = null;

  const $ = (id) => document.getElementById(id);
  const views = ['view-join', 'view-lobby', 'view-sage-write', 'view-wait-write', 'view-guess', 'view-reveal', 'view-podium'];

  function showView(id) {
    for (const v of views) {
      const el = $(v);
      if (el) el.hidden = v !== id;
    }
  }

  function showToast(msg) {
    const t = $('toast');
    if (!t) return;
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(t._timer);
    t._timer = setTimeout(() => { t.hidden = true; }, 2600);
  }

  function vibrate(pattern) {
    try { navigator.vibrate?.(pattern); } catch {}
  }

  function send(obj) {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(obj));
    }
  }

  function initAvatarPicker() {
    const grid = $('avatar-grid');
    grid.innerHTML = '';
    AVATARS.forEach((av, idx) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'avatar-choice' + (idx === 0 ? ' selected' : '');
      btn.textContent = av.emoji;
      btn.dataset.avatarId = av.id;
      btn.addEventListener('click', () => {
        selectedAvatar = av.id;
        grid.querySelectorAll('.avatar-choice').forEach((b) => b.classList.toggle('selected', b === btn));
      });
      grid.appendChild(btn);
    });
    const savedName = localStorage.getItem(STORAGE_NAME);
    if (savedName) $('player-name').value = savedName;
  }

  function connect() {
    clearTimeout(reconnectTimer);
    $('conn-status').textContent = 'Connecting to TV…';
    const ws = new WebSocket(wsUrlForLocation(window.location));
    socket = ws;

    ws.addEventListener('open', () => {
      $('conn-status').textContent = 'Connected to TV ✓';
      const token = localStorage.getItem(STORAGE_KEY);
      if (token) {
        send({ t: 'join', name: localStorage.getItem(STORAGE_NAME) || 'Player', token, avatarId: selectedAvatar });
      }
    });

    ws.addEventListener('message', (ev) => {
      let msg;
      try { msg = JSON.parse(ev.data); } catch { return; }
      if (msg.t === 'joined') {
        localStorage.setItem(STORAGE_KEY, msg.token);
        $('join-error').hidden = true;
        vibrate(25);
      } else if (msg.t === 'state') {
        state = msg;
        countdownAnchorMs = msg.remainingMs || 0;
        countdownAt = Date.now();
        renderState(msg);
      } else if (msg.t === 'metaphor_rejected') {
        $('metaphor-error').textContent = msg.reason || 'Try making it more metaphorical!';
        $('metaphor-error').hidden = false;
        vibrate([30, 40, 30]);
      } else if (msg.t === 'error') {
        if (msg.code === 'bad_token') {
          localStorage.removeItem(STORAGE_KEY);
          showView('view-join');
          $('topbar').hidden = true;
        } else {
          const text = msg.message || msg.code || 'Action could not be completed';
          if (!$('view-join').hidden) {
            $('join-error').textContent = text;
            $('join-error').hidden = false;
          } else {
            showToast(text);
          }
        }
      }
    });

    ws.addEventListener('close', () => {
      $('conn-status').textContent = 'Reconnecting to TV…';
      reconnectTimer = setTimeout(connect, 1500);
    });
  }

  function renderState(s) {
    $('topbar').hidden = false;
    $('me-avatar').textContent = s.you.avatar.emoji;
    $('me-name').textContent = s.you.name + (s.you.isHost ? ' 👑' : '');
    $('me-sub').textContent = s.you.streak >= 2 ? `Rank #${s.you.rank} · 🔥${s.you.streak}` : `Rank #${s.you.rank}`;
    $('me-score').textContent = String(s.you.score);

    const turnKey = `${s.phase}:${s.turnNumber}:${s.secretLesson || ''}`;
    const isNewTurn = turnKey !== lastTurnKey;
    if (isNewTurn) {
      lastTurnKey = turnKey;
      if (s.phase === 'WRITING' && s.isSage) {
        $('metaphor-input').value = '';
        $('metaphor-error').hidden = true;
        $('sparks-list').hidden = true;
      }
    }

    if (s.phase === 'LOBBY') {
      showView('view-lobby');
      $('lobby-room').textContent = s.roomCode;
      $('lobby-players').innerHTML = s.players.map((p) =>
        `<span class="p-chip ${p.connected ? '' : 'offline'}">${p.avatar.emoji} ${escapeHtml(p.name)}${p.isHost ? ' 👑' : ''}${p.isBot ? ' 🤖' : ''}</span>`
      ).join('');
      const hostPanel = $('host-panel');
      hostPanel.hidden = !s.you.isHost;
      if (s.you.isHost) {
        syncSeg('length', s.config.length);
        syncSeg('optionCount', String(s.config.optionCount));
        syncSeg('writeSeconds', String(s.config.writeSeconds));
        const strictBtn = $('toggle-strict');
        strictBtn.textContent = s.config.strictMetaphorGuard ? 'ON' : 'OFF';
        strictBtn.classList.toggle('on', !!s.config.strictMetaphorGuard);
        $('btn-start').disabled = !s.canStart;
        $('start-hint').textContent = s.startHint || `${s.players.length} players ready to play!`;
      }
      return;
    }

    if (s.phase === 'WRITING') {
      if (s.isSage) {
        showView('view-sage-write');
        $('sage-theme').textContent = s.currentTheme || 'Wisdom';
        $('secret-lesson').textContent = s.secretLesson || '';
        const fBar = $('forbidden-bar');
        const roots = s.forbiddenRoots || [];
        fBar.hidden = !s.config.strictMetaphorGuard || roots.length === 0;
        $('forbidden-list').innerHTML = roots.map((w) => `<span class="forbidden-tag" data-word="${escapeHtml(w)}">${escapeHtml(w)}</span>`).join('');
        $('sparks-list').innerHTML = (s.secretSparks || []).map((sp) => `<span class="spark-chip">✨ ${escapeHtml(sp)}</span>`).join('');
        const rerollBtn = $('btn-reroll');
        rerollBtn.disabled = (s.rerollsLeft || 0) <= 0;
        rerollBtn.textContent = s.rerollsLeft > 0 ? `🎲 Swap Secret Lesson (${s.rerollsLeft} left)` : '🎲 Lesson Already Swapped';
        updateDraftFeedback();
      } else {
        showView('view-wait-write');
        $('wait-turn').textContent = `${s.turnNumber}/${s.totalTurns}`;
        $('wait-sage-title').textContent = `${s.sageAvatar?.emoji || '🪶'} ${s.sageName || 'Sage'} is crafting a metaphor…`;
      }
      updateTimers();
      return;
    }

    if (s.phase === 'GUESSING' || s.phase === 'TIE_BREAKER') {
      showView('view-guess');
      const isTb = s.phase === 'TIE_BREAKER';
      $('guess-kicker').textContent = isTb ? '⚡ SUDDEN DEATH TIE-BREAKER' : `ROUND ${s.turnNumber}/${s.totalTurns} · DECODE THE METAPHOR`;
      $('guess-metaphor').textContent = `“${s.metaphorText || ''}”`;
      $('guess-author').textContent = isTb ? '— Golden Duel Bonus Metaphor' : `— ${s.sageAvatar?.emoji || ''} ${s.sageName || 'Sage'}`;

      const cannotVote = (!isTb && s.isSage) || (isTb && !s.isTieBreakerContender);
      $('guess-sage-notice').hidden = !cannotVote;
      if (isTb && !s.isTieBreakerContender) {
        $('guess-sage-notice').querySelector('h3').textContent = 'Sudden Death Duel!';
        $('guess-sage-notice').querySelector('p').textContent = 'Watch the tied leaders race to decode the final wisdom!';
      } else {
        $('guess-sage-notice').querySelector('h3').textContent = 'You are the Sage!';
        $('guess-sage-notice').querySelector('p').textContent = 'Root for your friends to decode your metaphor — you earn points for every right guess!';
      }

      const locked = !cannotVote && !!s.myChoiceId;
      $('guess-locked-notice').hidden = !locked;
      if (locked) {
        const chosen = (s.options || []).find((o) => o.id === s.myChoiceId);
        $('locked-choice-text').textContent = chosen ? `You picked ${chosen.id}: “${chosen.text}”` : 'Choice locked!';
      }

      const list = $('options-list');
      list.hidden = cannotVote || locked;
      if (!list.hidden) {
        list.innerHTML = '';
        (s.options || []).forEach((opt) => {
          const btn = document.createElement('button');
          btn.type = 'button';
          btn.className = `option-btn opt-${opt.id}`;
          btn.innerHTML = `<span class="opt-badge">${escapeHtml(opt.id)}</span><span>${escapeHtml(opt.text)}</span>`;
          btn.addEventListener('click', () => {
            vibrate(30);
            send({ t: 'vote', optionId: opt.id });
          });
          list.appendChild(btn);
        });
      }
      updateTimers();
      return;
    }

    if (s.phase === 'REVEAL') {
      showView('view-reveal');
      const d = s.myDelta;
      const earned = d ? d.pointsEarned : 0;
      if (s.isSage) {
        $('reveal-icon').textContent = earned > 0 ? '🪶' : '🌫️';
        $('reveal-title').textContent = earned >= 180 ? 'Master Sage!' : (earned > 0 ? 'Clever Metaphor!' : 'Too Mysterious!');
      } else if (d && d.wasCorrect) {
        $('reveal-icon').textContent = '🎉';
        $('reveal-title').textContent = 'Wisdom Decoded!';
      } else {
        $('reveal-icon').textContent = '🎭';
        $('reveal-title').textContent = 'Fooled by a Decoy!';
      }
      $('reveal-points').textContent = earned > 0 ? `+${earned} PTS` : '+0 PTS';
      const parts = [];
      if (d?.basePoints) parts.push(`Base +${d.basePoints}`);
      if (d?.speedBonus) parts.push(`Speed +${d.speedBonus}`);
      if (d?.streakBonus) parts.push(`Streak +${d.streakBonus}`);
      if (d?.clarityBonus) parts.push(`Clarity +${d.clarityBonus}`);
      if (d?.applauseBonus) parts.push(`Bravo +${d.applauseBonus}`);
      $('reveal-breakdown').textContent = parts.join(' · ') || 'Next round awaits!';
      $('reveal-truth').textContent = s.originalLesson || '';

      const canApplaud = !s.isSage && !s.hasApplauded;
      $('btn-applause').hidden = !canApplaud;
      $('applause-sent').hidden = !(!s.isSage && s.hasApplauded);
      $('reveal-host-footer').hidden = !s.you.isHost;
      return;
    }

    if (s.phase === 'PODIUM') {
      showView('view-podium');
      const winner = s.players[0];
      $('podium-headline').textContent = winner ? `${winner.avatar.emoji} ${winner.name} Wins!` : 'Grand Finale!';
      $('final-standings').innerHTML = s.players.map((p) =>
        `<div class="standing-row ${p.rank === 1 ? 'top1' : ''}">
          <span><strong>#${p.rank}</strong> ${p.avatar.emoji} ${escapeHtml(p.name)}</span>
          <strong>${p.score} pts</strong>
        </div>`
      ).join('');
      $('podium-host-footer').hidden = !s.you.isHost;
    }
  }

  function syncSeg(setting, value) {
    document.querySelectorAll(`.seg-group[data-setting="${setting}"] .seg`).forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.val === value);
    });
  }

  function updateDraftFeedback() {
    if (!state) return;
    const text = $('metaphor-input').value;
    $('char-count').textContent = `${text.length} / 180`;
    const check = evaluateDraft(text, state.forbiddenRoots || [], state.config.strictMetaphorGuard);
    const gm = $('guard-msg');
    gm.textContent = check.message;
    gm.className = 'guard-msg ' + (check.ok ? 'ok' : (text.trim().length > 0 ? 'bad' : ''));
    const hitSet = new Set(check.hits);
    document.querySelectorAll('.forbidden-tag').forEach((tag) => {
      tag.classList.toggle('hit', hitSet.has(tag.dataset.word));
    });
  }

  function updateTimers() {
    if (!state) return;
    const elapsed = Date.now() - countdownAt;
    const left = Math.max(0, countdownAnchorMs - elapsed);
    const formatted = formatSeconds(left);
    const urgent = left > 0 && left <= 10_000;
    ['write-timer', 'wait-timer', 'guess-timer'].forEach((id) => {
      const el = $(id);
      if (el) {
        el.textContent = formatted;
        el.classList.toggle('urgent', urgent);
      }
    });
  }

  function escapeHtml(str) {
    return String(str).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  // Event bindings
  initAvatarPicker();
  connect();
  setInterval(updateTimers, 250);

  $('join-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const name = $('player-name').value.trim();
    if (!name) return;
    localStorage.setItem(STORAGE_NAME, name);
    send({ t: 'join', name, token: null, avatarId: selectedAvatar });
  });

  document.querySelectorAll('.seg-group').forEach((grp) => {
    grp.addEventListener('click', (e) => {
      const btn = e.target.closest('.seg');
      if (!btn || !state?.you?.isHost) return;
      const setting = grp.dataset.setting;
      const val = btn.dataset.val;
      const payload = { t: 'config' };
      payload[setting] = setting === 'length' ? val : Number(val);
      send(payload);
    });
  });

  $('toggle-strict').addEventListener('click', () => {
    if (!state?.you?.isHost) return;
    send({ t: 'config', strictMetaphorGuard: !state.config.strictMetaphorGuard });
  });
  $('btn-add-bot').addEventListener('click', () => send({ t: 'add_bot' }));
  $('btn-rm-bot').addEventListener('click', () => send({ t: 'remove_bot' }));
  $('btn-start').addEventListener('click', () => send({ t: 'start' }));

  $('metaphor-input').addEventListener('input', () => {
    $('metaphor-error').hidden = true;
    updateDraftFeedback();
    const now = Date.now();
    if (now - typingThrottle > 350) {
      typingThrottle = now;
      send({ t: 'typing', chars: $('metaphor-input').value.trim().length });
    }
  });

  $('spark-toggle').addEventListener('click', () => {
    const list = $('sparks-list');
    list.hidden = !list.hidden;
  });

  $('btn-submit-metaphor').addEventListener('click', () => {
    const text = $('metaphor-input').value.trim();
    send({ t: 'submit_metaphor', text });
  });

  $('btn-reroll').addEventListener('click', () => send({ t: 'reroll' }));
  $('btn-applause').addEventListener('click', () => {
    vibrate(25);
    send({ t: 'applause' });
  });
  $('btn-next').addEventListener('click', () => send({ t: 'next' }));
  $('btn-end-early').addEventListener('click', () => send({ t: 'end_game' }));
  $('btn-play-again').addEventListener('click', () => send({ t: 'play_again' }));
}
