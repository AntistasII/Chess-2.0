import assert from 'node:assert/strict';
// Этот сценарий меняет партию: передавайте адрес отдельного тестового сервера явно.
const base = process.argv[2];
if (!base) throw new Error('Укажите URL отдельного тестового сервера');
async function call(path, body) {
  const response = await fetch(`${base}/api/${path}`, body === undefined ? undefined : {
    method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)});
  const data = await response.json();
  assert.equal(response.status, 200, `${path}: ${JSON.stringify(data)}`);
  return data;
}
let state = await call('new', {mode: 'human', handLimit: 12});
const template = await call('export', {version: state.version});
async function seed(edit) {
  const save = structuredClone(template);
  save.cards = [[[100,'EXPAND_LEFT'],[101,'EXPAND_BOTTOM'],[102,'TRAP'],[103,'HIDDEN_TRAP'],
    [104,'WHITE_HOLE'],[105,'SACRIFICE'],[106,'DESTROY_CARD'],[107,'UNDO_MOVE']],
    [[200,'EXPAND_TOP'],[201,'EXPAND_RIGHT'],[202,'SHIELD'],[203,'UNDO_MOVE']], [], []];
  save.repetitions = {}; delete save.lastMove;
  edit?.(save);
  state = await call('import', {version: state.version, save});
}
async function play(id) { state = await call('action', {version: state.version, actionId: id}); }
async function cast(type, to = -1, other) {
  const card = state.hand.find(c => c.type === type); assert.ok(card, type);
  const action = state.actions.find(a => a.cardId === card.id && a.to === to && (other === undefined || a.other === other));
  assert.ok(action, `${type} target ${to}/${other}`); await play(action.id);
}
await seed();
await cast('EXPAND_LEFT'); await cast('EXPAND_TOP'); await cast('EXPAND_BOTTOM'); await cast('EXPAND_RIGHT');
assert.deepEqual(state.boardSize, {width:10,height:10,originFile:1,originRank:1});
assert.ok(state.board.some(p => p.square === 15 && p.type === 'K' && p.white));
const expanded = await call('export', {version:state.version});
state = await call('import', {version:state.version,save:expanded});
assert.deepEqual(state.boardSize,{width:10,height:10,originFile:1,originRank:1});
await seed();
await cast('TRAP',27); await play('move:57:42:'); await cast('HIDDEN_TRAP',29);
assert.ok(!state.squareEffects.some(e => e.type === 'HIDDEN_TRAP'));
assert.ok(!state.history.at(-1).includes('f4'));
await play('move:42:27:');
assert.ok(state.board.find(p => p.square === 27).frozen);
assert.equal(state.board.find(p => p.square === 27).frozenTurns,3);
await cast('WHITE_HOLE',28);
assert.ok(state.squareEffects.some(e => e.type === 'WHITE_HOLE'));
assert.ok(state.actions.every(a => a.kind !== 'move' || a.to !== 28));
await seed(); await play('move:12:28:'); await cast('UNDO_MOVE',28);
assert.ok(state.board.some(p => p.square === 12 && p.type === 'P' && p.frozen));
assert.ok(!state.board.some(p => p.square === 28));
await seed(save => { save.board = save.board.filter(([square]) => square !== 8); });
await cast('SACRIFICE',1,8);
assert.ok(!state.board.some(p => p.square === 1)); assert.ok(state.board.some(p => p.square === 8 && p.type === 'P'));
await seed(); const before = state.enemyCards; await cast('DESTROY_CARD'); assert.equal(state.hand.length,before-1);
const afterDestroy = await call('export',{version:state.version}); assert.equal(afterDestroy.cards[3].length,1);
await seed(save => {
  save.geometry = [10,9,1,1];
  save.board = save.board.map(([square,piece]) => [(Math.floor(square/8)+1)*10+square%8+1,piece]);
  save.squareEffects = [[44,'TRAP',true,6,3],[46,'HIDDEN_TRAP',true,6,3],[55,'WHITE_HOLE',false,6,0]];
});
console.log('New cards HTTP passed: four expansions, save/load, hidden fields, trap freeze, white hole, undo, sacrifice and destruction. UI fixture ready.');
