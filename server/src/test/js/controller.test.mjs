import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateDraft, formatSeconds, stemWord, wsUrlForLocation } from '../../main/resources/controller/controller.js';

test('evaluateDraft accepts poetic metaphor without forbidden roots', () => {
  const res = evaluateDraft(
    'A single scarecrow in the field only smiles when he sees the crows',
    ['happy', 'alone'],
    true,
  );
  assert.equal(res.ok, true);
  assert.deepEqual(res.hits, []);
});

test('evaluateDraft flags morphological variants of forbidden roots', () => {
  const res = evaluateDraft(
    'True happiness never shines on anyone walking through dark woods',
    ['happy', 'alone'],
    true,
  );
  assert.equal(res.ok, false);
  assert.deepEqual(res.hits, ['happy']);
});

test('evaluateDraft rejects short drafts and overlong drafts', () => {
  assert.equal(evaluateDraft('too short', ['happy'], true).ok, false);
  assert.equal(evaluateDraft('a '.repeat(95) + 'scarecrow', [], true).ok, false);
});

test('formatSeconds rounds up cleanly and clamps at zero', () => {
  assert.equal(formatSeconds(0), '0s');
  assert.equal(formatSeconds(-500), '0s');
  assert.equal(formatSeconds(1200), '2s');
  assert.equal(formatSeconds(35000), '35s');
});

test('stemWord handles common English suffixes', () => {
  assert.equal(stemWord('happiness'), 'happy');
  assert.equal(stemWord('running'), 'runn');
  assert.equal(stemWord('crows'), 'crow');
});

test('wsUrlForLocation switches ws/wss properly', () => {
  assert.equal(wsUrlForLocation({ protocol: 'http:', host: '192.168.86.81:8777' }), 'ws://192.168.86.81:8777/ws');
  assert.equal(wsUrlForLocation({ protocol: 'https:', host: 'example.com' }), 'wss://example.com/ws');
});
