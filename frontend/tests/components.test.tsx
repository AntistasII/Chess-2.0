import assert from 'node:assert/strict';
import { renderToStaticMarkup } from 'react-dom/server';
import { ChessBoard } from '../src/components/ChessBoard';
import { CardHand } from '../src/components/CardHand';
import { NewGameForm } from '../src/components/NewGameForm';
import { gameStatus } from '../src/game/catalog';
import { GameRules } from '../src/components/GameRules';
import { boardOrder, fileName, squareName } from '../src/game/board';
import type { Card, State } from '../src/game/types';

const cards: Card[] = [
  {id: 1, type: 'SHIELD', name: 'Щит'},
  {id: 2, type: 'ASTRAL', name: 'Астрал'},
  {id: 3, type: 'SCOUT', name: 'Разведка'},
  {id: 4, type: 'FREEZE', name: 'Заморозка'},
  {id: 5, type: 'CLEANSE', name: 'Очищение'},
];
const state: State = {
  board: [], boardSize: {width: 8, height: 8, originFile: 0, originRank: 0}, whiteTurn: true, mode: 'human', difficulty: 'normal', handLimit: 5,
  version: 0, ply: 0, hand: [], handWhite: true, enemyCards: 2, deckCount: 20,
  discardCount: 0, check: false, result: '', canClaimDraw: false, actions: [],
  history: [], captured: [], scouting: [],
};
function hand(count: number, covered = false) {
  return renderToStaticMarkup(<CardHand game={{...state, hand: cards.slice(0, count)}}
    covered={covered} canAct={true} cardId={null} activeCard={undefined}
    cardActions={[]} hint="" selectCard={() => {}} execute={() => {}} onScout={() => {}}/>);
}
assert.match(hand(0), /Пока нет карт/);
for (const count of [1, 3, 5]) {
  assert.equal((hand(count).match(/<button class="card /g) ?? []).length, count);
}
assert.doesNotMatch(hand(5, true), /Астрал|Разведка|Очищение/);
assert.match(hand(5, true), /Карты скрыты/);

for (const flipped of [false, true]) {
  const board = renderToStaticMarkup(<ChessBoard board={[]} check={false} flipped={flipped}
    selected={null} firstTarget={null} targets={new Set()} spellActive={false}
    lastAction="" onSquare={() => {}}/>);
  assert.equal((board.match(/<button /g) ?? []).length, 64);
  assert.ok(board.indexOf(`aria-label="${flipped ? 'h1' : 'a8'}, пусто"`)
    < board.indexOf(`aria-label="${flipped ? 'a8' : 'h1'}, пусто"`));
}
const marked = renderToStaticMarkup(<ChessBoard board={[
  {id: 1, square: 8, type: 'P', white: true, boost: true, shield: true, frozen: false, astral: false},
  {id: 2, square: 48, type: 'R', white: false, boost: false, shield: false, frozen: true, astral: false},
  {id: 3, square: 32, type: 'B', white: false, boost: false, shield: false, frozen: false, astral: true},
]} check={false} flipped={false} selected={null} firstTarget={null}
  targets={new Set()} spellActive={false} lastAction="" onSquare={() => {}}/>);
for (const marker of ['badge boost', 'badge shield', 'badge ice', 'piece ghost', 'badge astral-badge']) {
  assert.ok(marked.includes(marker), `Missing modifier ${marker}`);
}
// Прямоугольные доски и подписи новых вертикалей должны работать в обеих ориентациях.
for (const size of [
  {width: 10, height: 8, originFile: 1, originRank: 0},
  {width: 8, height: 12, originFile: 0, originRank: 2},
  {width: 10, height: 12, originFile: 1, originRank: 2},
  {width: 28, height: 8, originFile: 10, originRank: 0},
]) {
  for (const flipped of [false, true]) {
    const order = boardOrder(size, flipped);
    assert.equal(new Set(order).size, size.width * size.height);
    const markup = renderToStaticMarkup(<ChessBoard board={[]} size={size} check={false} flipped={flipped}
      selected={null} firstTarget={null} targets={new Set()} spellActive={false}
      lastAction="Белые: Ладья a10 → b10" onSquare={() => {}}/>);
    assert.equal((markup.match(/<button /g) ?? []).length, size.width * size.height);
    assert.ok(markup.indexOf(`aria-label="${squareName(order[0], size.width)}, пусто"`)
      < markup.indexOf(`aria-label="${squareName(order.at(-1)!, size.width)}, пусто"`));
    assert.match(markup, new RegExp(`repeat\\(${size.width}, minmax`));
    if (size.height >= 10) {
      assert.equal((markup.match(/class="square [^"]* last(?: [^"]*)?"/g) ?? []).length, 2);
    }
  }
}
assert.equal(fileName(26), 'aa');
assert.equal(squareName(119, 10), 'j12');
const rules = renderToStaticMarkup(<GameRules/>);
assert.match(rules, /Возрождение фигуры/);
assert.match(rules, /Обмен планами/);
const chatForm = renderToStaticMarkup(<NewGameForm mode="chatgpt" limit={3} difficulty="normal"
  busy={false} setMode={() => {}} setLimit={() => {}} setDifficulty={() => {}} onStart={async () => {}}/>);
assert.match(chatForm, /С ChatGPT/);
assert.match(chatForm, /application.yml/);
assert.doesNotMatch(chatForm, /<select/);
assert.equal(gameStatus({...state, mode: 'chatgpt', whiteTurn: false}, true), 'ChatGPT обдумывает ход…');
console.log('UI checks passed: dynamic hands, concealed cards, variable board sizes, board orientation and modifier badges.');

// Повторяется только ошибка 502 и только до десяти раз после исходного запроса.
// Новые поля не меняют число клеток, работают в обеих ориентациях и на прямоугольнике.
for (const flipped of [false, true]) {
  const html = renderToStaticMarkup(<ChessBoard board={[]} size={{width: 10, height: 9, originFile: 1, originRank: 1}}
    effects={[{square: 31, type: 'TRAP', white: true, turns: 3, freezeTurns: 3},
      {square: 42, type: 'HIDDEN_TRAP', white: true, turns: 2, freezeTurns: 3},
      {square: 53, type: 'WHITE_HOLE', white: false, turns: 1, freezeTurns: 0}]}
    check={false} flipped={flipped} selected={null} firstTarget={null} targets={new Set()}
    spellActive={false} lastAction="" onSquare={() => {}}/>);
  assert.equal((html.match(/<button /g) ?? []).length, 90);
  assert.equal((html.match(/trap-cell/g) ?? []).length, 2);
  assert.equal((html.match(/white-hole/g) ?? []).length, 1);
  assert.match(html, /невидимый капкан, осталось 2/);
  assert.match(html, /белая дыра, осталось 1/);
}
for (const type of ['EXPAND_BOTTOM', 'EXPAND_TOP', 'EXPAND_LEFT', 'EXPAND_RIGHT', 'DESTROY_CARD'] as const) {
  const card = {id: 99, type, name: type};
  const html = renderToStaticMarkup(<CardHand game={{...state, hand: [card]}} covered={false} canAct={true}
    cardId={99} activeCard={card} cardActions={[{id: 'test', kind: 'card', from: -1, to: -1, other: -1, cardId: 99, option: ''}]}
    hint="" selectCard={() => {}} execute={() => {}} onScout={() => {}}/>);
  assert.match(html, />Применить<\/button>/);
  assert.doesNotMatch(html, /undefined/);
}
assert.match(renderToStaticMarkup(<GameRules/>), /22 типа/);
console.log('New card UI checks passed: fields, 90 squares, both orientations, apply buttons and catalog.');

async function retryChecks() {
  const { ApiError, requestOpponent, request } = await import('../src/game/api');
  let calls = 0;
  const payload = {version: 7};
  const pauses: number[] = [];
  const result = await requestOpponent(payload, 10, async body => {
    assert.equal(body, payload);
    if (++calls < 4) throw new ApiError('temporary', 502);
    return state;
  }, async ms => { pauses.push(ms); });
  assert.equal(result, state);
  assert.equal(calls, 4);
  assert.deepEqual(pauses, [1000, 2000, 3000]);
  calls = 0;
  await assert.rejects(requestOpponent(payload, 10, async () => {
    calls++; throw new ApiError('still failing', 502);
  }, async () => {}), /still failing/);
  assert.equal(calls, 11);
  for (const status of [400, 401, 403, 409, 424, 500]) {
    calls = 0;
    await assert.rejects(requestOpponent(payload, 10, async () => {
      calls++; throw new ApiError('no retry', status);
    }, async () => {}), /no retry/);
    assert.equal(calls, 1);
  }
  for (const limit of [0, 2]) {
    calls = 0;
    await assert.rejects(requestOpponent(payload, limit, async () => {
      calls++; throw new ApiError('configured limit', 502);
    }, async () => {}), /configured limit/);
    assert.equal(calls, limit + 1);
  }
  const savedFetch = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response('<html>Bad Gateway</html>', {status: 502});
    await assert.rejects(request('ai', payload), error => error instanceof ApiError && error.status === 502);
  } finally { globalThis.fetch = savedFetch; }
  console.log('Retry checks passed: recovery, ten retries, stable version, non-JSON 502 and no retry on other errors.');
}
void retryChecks().catch(error => { console.error(error); process.exitCode = 1; });
