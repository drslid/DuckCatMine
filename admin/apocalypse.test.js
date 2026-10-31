'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { makeApocalypseCommands, failure, SETTINGS } = require('./apocalypse');
class HttpError extends Error { constructor(status, message) { super(message); this.status = status; } }
const { command } = makeApocalypseCommands(HttpError);
const status = () => ({ installed: true, stale: false, world: 'ground_zero',
  capabilities:['departure','teleport'], departure:{members:['Noah'],phase:'waiting'},
  survivors: [{ name: 'Alex', inRefuge: false }, { name: 'Sam', inRefuge: true }],
  items: [{ id: 'antidote' }, { id: 'pistol' }], });
const build = (action, value, extra = {}, state = status()) => command({ action, value, ...extra }, state);
const refused = (fn, code = 400) => assert.throws(fn, error => error instanceof HttpError && error.status === code);

test('all supported admin operations produce bounded, explicit commands', () => {
  for (const action of ['status', 'settings', 'enable', 'disable', 'selftest', 'reload'])
    assert.equal(build(action).command, `apocalypse admin ${action}`);
  assert.equal(build('give', { player: 'Alex', item: 'antidote', count: 2 }).command, 'apocalypse admin give Alex antidote 2');
  assert.equal(build('evacuate', 'Alex').command, 'apocalypse admin evacuate Alex');
  assert.equal(build('horde', 'Alex').command, 'apocalypse admin horde Alex');
  assert.equal(build('preset', 'relaxed').command, 'apocalypse admin preset relaxed');
  assert.equal(build('map', { radius: 1536, slice: 65 }).command, 'apocalypse admin map 1536 65');
  assert.equal(build('upgrade', 96, { confirmation: 'ACTUALISER' }).command, 'apocalypse admin upgrade 96');
});

test('every live setting enforces its kind and both bounds', () => {
  for (const [key, [kind, min, max]] of Object.entries(SETTINGS)) {
    if (kind === 'boolean') {
      assert.equal(build('set', { key, value: false }).command, `apocalypse admin set ${key} false`);
      refused(() => build('set', { key, value: 'false' }));
    } else {
      assert.equal(build('set', { key, value: min }).command, `apocalypse admin set ${key} ${min}`);
      assert.equal(build('set', { key, value: max }).command, `apocalypse admin set ${key} ${max}`);
      refused(() => build('set', { key, value: min - 1 }));
      refused(() => build('set', { key, value: max + 1 }));
      refused(() => build('set', { key, value: String(min) }));
      if (kind === 'integer') refused(() => build('set', { key, value: min + .5 }));
    }
  }
});

test('command injection, non-finite values and unlisted items are rejected before RCON', () => {
  for (const input of [null, [], { action: 'enable\nstop' }, { action: '__proto__' }]) refused(() => command(input, status()));
  for (const name of ['Alex\nstop', '@a', 'Alex;op']) refused(() => build('evacuate', name));
  for (const key of ['__proto__', 'constructor', 'spawning\nstop']) refused(() => build('set', { key, value: true }));
  for (const value of [NaN, Infinity, '2', null]) refused(() => build('threat', value));
  for (const item of ['diamond', 'antidote\nstop', '@a']) refused(() => build('give', { player: 'Alex', item, count: 1 }));
  for (const count of [0, 65, 1.5, '1']) refused(() => build('give', { player: 'Alex', item: 'antidote', count }));
  refused(() => build('preset', 'nightmare\nstop'));
  refused(() => build('map', { radius: 640, slice: '65\nstop' }));
});

test('stale state, missing targets and the refuge protect a running game', () => {
  refused(() => build('enable', null, {}, { ...status(), stale: true }), 409);
  refused(() => build('enable', null, {}, { installed: false }), 409);
  refused(() => build('horde', 'Sam'), 409);
  refused(() => build('evacuate', 'Offline'), 409);
  refused(() => build('give', { player: 'Offline', item: 'antidote', count: 1 }), 409);
});

test('reset and world operations require typed confirmation and preserve the active world', () => {
  refused(() => build('lives-reset'));
  refused(() => build('lives-reset', null, { confirmation: 'RECOMMENCER' }), 409);
  assert.equal(build('lives-reset', null, { confirmation: 'RECOMMENCER' }, { ...status(), survivors: [] }).command,
    'apocalypse admin lives reset confirm');
  refused(() => build('world-create', null, { confirmation: 'CREER' }), 409);
  const missing = { ...status(), world: null, problem: 'Le monde est introuvable.' };
  assert.equal(build('world-create', null, { confirmation: 'CREER' }, missing).command, 'apocalypse admin world create');
  refused(() => build('world-bind', 'world', { confirmation: 'world' }, missing));
  refused(() => build('world-bind', '../other', { confirmation: '../other' }, missing));
  refused(() => build('world-bind', 'dedicated', { confirmation: 'wrong' }, missing));
  assert.equal(build('world-bind', 'dedicated', { confirmation: 'dedicated' }, missing).command,
    'apocalypse admin world bind dedicated confirm');
  refused(() => build('world-recreate', null, { confirmation: 'RECREER' }), 409);
  assert.equal(build('world-recreate', null, { confirmation: 'RECREER' }, missing).command, 'apocalypse admin world recreate confirm');
});

test('refusals from both current and legacy plugins never appear as success', () => {
  assert.equal(failure('☣ DCAPO_ERROR: Sortie impossible.'), 'Sortie impossible.');
  assert.match(failure('☣ Le joueur doit être connecté et dans Ground Zero.'), /Le joueur doit/);
  assert.match(failure('☣ Alex est dans Ground Zero : attends que tous les survivants soient déconnectés ou sortis.'), /Alex/);
  assert.match(failure('☣ Le monde ground_zero existe encore : arrête le serveur.'), /existe/);
  assert.match(failure('☣ ÉCHEC objets : erreur'), /ÉCHEC/);
  assert.equal(failure('☣ OK objets\n☣ Autotest réussi.'), null);
});

test('rendezvous and shared departures require current supported state and two distinct survivors', () => {
  assert.equal(build('teleport',{player:'Alex',target:'Sam'}).command,'apocalypse admin teleport Alex Sam');
  assert.equal(build('departure-start').command,'apocalypse admin departure start');
  assert.equal(build('departure-cancel').command,'apocalypse admin departure cancel');
  refused(() => build('teleport',{player:'Alex',target:'Alex'}));
  refused(() => build('teleport',{player:'Alex',target:'Offline'}),409);
  refused(() => build('teleport',{player:'Alex',target:'Sam\nstop'}));
  refused(() => build('teleport',{player:'Alex',target:'Sam'},{},{...status(),capabilities:[]}),409);
  refused(() => build('departure-start',null,{},{...status(),departure:{members:[],phase:'waiting'}}),409);
  refused(() => build('departure-start',null,{},{...status(),departure:{members:['Noah'],phase:'countdown'}}),409);
  refused(() => build('set',{key:{toString:null},value:1}));
});
