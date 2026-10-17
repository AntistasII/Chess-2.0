import assert from 'node:assert/strict';
const base = process.argv[2] || process.env.CHESS_TEST_URL || 'http://127.0.0.1:8080';
async function call(path, body) {
  const response = await fetch(`${base}/api/${path}`, body === undefined ? undefined : {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  return {status:response.status, data:await response.json()};
}
const page=await fetch(base); assert.equal(page.status,200);assert.match(await page.text(),/Шахматы с картами/);
let {data:game}=await call('new',{mode:'ai',difficulty:'normal',handLimit:5});
assert.ok(game.defaultMode in game.newGameDefaults);
for (const mode of ['ai', 'chatgpt', 'network', 'human']) {
  const settings = game.newGameDefaults[mode];
  assert.ok(settings.handLimit >= 1 && settings.handLimit <= 12);
  assert.ok(['easy', 'normal', 'hard'].includes(settings.difficulty));
}
assert.deepEqual(game.boardSize,{width:8,height:8,originFile:0,originRank:0});
assert.equal(game.hand.length,0);assert.equal(game.actions.length,21);assert.equal(game.handLimit,5);
const invalid=await call('action',{version:game.version,actionId:'move:4:60:'});assert.equal(invalid.status,400);
assert.equal((await call('missing',{})).status,404);
assert.equal((await fetch(`${base}/api/state`,{method:'DELETE'})).status,405);
assert.equal((await fetch(`${base}/api/action`,{method:'POST',body:'{}'})).status,415);
assert.equal((await fetch(`${base}/api/action`,{method:'POST',headers:{'Content-Type':'application/json'},body:'invalid'})).status,400);
assert.equal((await fetch(`${base}/api/action`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({padding:'x'.repeat(17000)})})).status,413);
assert.equal((await call('scout',{})).status,409);
const stale=await call('action',{version:game.version-1,actionId:'draw'});assert.equal(stale.status,409);
({data:game}=await call('action',{version:game.version,actionId:'draw'}));assert.equal(game.whiteTurn,false);assert.equal(game.hand.length,1);
({data:game}=await call('ai',{version:game.version}));assert.equal(game.whiteTurn,true);assert.equal(game.ply,2);
assert.equal(game.scouting.length,0);assert.ok(!('deck' in game));assert.ok(!('blackHand' in game));
const next=await call('new',{mode:'human',difficulty:'normal',handLimit:4});assert.equal(next.data.mode,'human');assert.equal(next.data.handWhite,true);
assert.ok(next.data.version>game.version);
const badOrigin=await fetch(`${base}/api/action`,{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://example.com'},body:JSON.stringify({version:next.data.version,actionId:'draw'})});assert.equal(badOrigin.status,403);
await call('new',{mode:'ai',difficulty:'normal',handLimit:3});
console.log('HTTP smoke passed: page, rules validation, revisions, cards, AI, hotseat, hidden data and origin checks.');
