import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { tvAddress, asksFirst } from '../../../../docs/join/join.js';

test('tvAddress accepts private LAN hash and query fallback', () => {
  assert.equal(tvAddress('#192.168.86.81:8777', ''), '192.168.86.81:8777');
  assert.equal(tvAddress('', '?tv=192.168.86.81:8777'), '192.168.86.81:8777');
  assert.equal(tvAddress('#10.0.0.42:8777', ''), '10.0.0.42:8777');
  assert.equal(tvAddress('#172.16.4.9:8777', ''), '172.16.4.9:8777');
});

test('tvAddress rejects public IPs, invalid ports, and hostnames', () => {
  assert.equal(tvAddress('#8.8.8.8:8777', ''), null);
  assert.equal(tvAddress('#172.32.0.1:8777', ''), null);
  assert.equal(tvAddress('#192.168.1.5:70000', ''), null);
  assert.equal(tvAddress('#evil.example.com:8777', ''), null);
  assert.equal(tvAddress('', ''), null);
});

test('asksFirst detects Android Chrome only', () => {
  assert.equal(
    asksFirst('Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/134.0.0.0 Mobile Safari/537.36'),
    true,
  );
  assert.equal(
    asksFirst('Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 Version/18.3 Mobile Safari/604.1'),
    false,
  );
  assert.equal(
    asksFirst('Mozilla/5.0 (Linux; Android 15; Pixel 9) Gecko/134.0 Firefox/134.0'),
    false,
  );
});

test('NetworkUtils uses trailing slash on GitHub Pages join bridge URL to prevent 301 hash stripping', () => {
  const src = readFileSync(
    new URL('../../../../app/src/main/kotlin/com/cubeworks/wordsofwisdom/NetworkUtils.kt', import.meta.url),
    'utf8',
  );
  assert.match(src, /JOIN_BRIDGE_URL\s*=\s*"https:\/\/grwilke\.github\.io\/words-of-wisdom\/join\/"/);
});
