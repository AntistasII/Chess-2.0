// Run only against a disposable emulator with the debug APK already installed and launched.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import path from 'node:path';
const serial=process.env.ANDROID_SERIAL ?? 'emulator-5556';
assert.match(serial,/^emulator-\d+$/,'Use a disposable emulator: this test replaces its game.');
const adb=path.join(process.env.ANDROID_HOME ?? path.resolve('.tools/android-sdk'),'platform-tools/adb');
const native=(...args)=>execFileSync(adb,['-s',serial,...args],{encoding:'utf8'}).trim();
const pid=native('shell','pidof','app.cardchess.mobile');
assert.match(pid,/^\d+$/,'Launch the Android debug app before running this test.');
native('forward','tcp:9224',`localabstract:webview_devtools_remote_${pid}`);
const pages=await (await fetch('http://127.0.0.1:9224/json/list')).json();
const socket=new WebSocket(pages.find(p=>p.type==='page').webSocketDebuggerUrl);
await new Promise(resolve=>socket.addEventListener('open',resolve));
let sequence=0;
const requests=new Map();
socket.addEventListener('message',event=>{
 const result=JSON.parse(event.data);if(!result.id)return;
 const request=requests.get(result.id);requests.delete(result.id);
 if(result.error)request.reject(result.error);else request.resolve(result.result);
});
async function evaluate(expression){
 const id=++sequence;
 const response=await new Promise((resolve,reject)=>{
  requests.set(id,{resolve,reject});socket.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));
 });
 if(response.exceptionDetails)throw Error(JSON.stringify(response.exceptionDetails));
 return response.result.value;
}
async function api(command,body){
 const options=body===undefined?{}:{method:'POST',body:JSON.stringify(body)};
 const result=await evaluate(`fetch('/api/${command}',${JSON.stringify(options)}).then(async r=>({status:r.status,body:await r.json()}))`);
 assert.equal(result.status,200,JSON.stringify(result));return result.body;
}
try{
 let state=await api('new',{mode:'ai',difficulty:'easy'});
 const replies=[];
 for(let turn=0;turn<5;turn++){
  const action=state.actions.find(a=>a.kind==='move');assert.ok(action);
  state=await api('action',{version:state.version,actionId:action.id});
  state=await api('ai',{version:state.version});assert.equal(state.whiteTurn,true);replies.push(state.history.at(-1));
 }
 assert.ok(replies.some(reply=>reply.includes('→')),'Exercise real search after forced card draws.');
 state=await api('new',{mode:'human'});
 const initial=await api('export',{version:state.version});
 for(const type of ['EXPAND_TOP','EXPAND_BOTTOM','EXPAND_LEFT','EXPAND_RIGHT','SHIELD']){
  const save=structuredClone(initial);
  const card=save.cards[2].find(c=>c[1]===type);assert.ok(card);
  save.cards[2]=save.cards[2].filter(c=>c[0]!==card[0]);save.cards[0]=[card];
  state=await api('import',{version:state.version,save});
  const actions=state.actions.filter(a=>a.cardId===card[0]);assert.ok(actions.length);
  if(type==='SHIELD'){
   assert.equal(actions.some(a=>a.to===4),false);assert.ok(actions.some(a=>a.to===8));continue;
  }
  const pieces=state.board;state=await api('action',{version:state.version,actionId:actions[0].id});
  assert.equal(state.board.length,32);
  assert.equal(state.boardSize.width,['EXPAND_LEFT','EXPAND_RIGHT'].includes(type)?9:8);
  assert.equal(state.boardSize.height,['EXPAND_TOP','EXPAND_BOTTOM'].includes(type)?9:8);
  for(const old of pieces){
   const piece=state.board.find(p=>p.id===old.id);
   const rank=Math.floor(old.square/8)+(type==='EXPAND_BOTTOM'?1:0);
   const file=old.square%8+(type==='EXPAND_LEFT'?1:0);
   assert.equal(piece.square,rank*state.boardSize.width+file);
  }
 }
 const hole=structuredClone(initial);
 hole.board=[[4,[1,'K',true,false,false,false,0,0]],[60,[2,'K',false,false,false,false,0,0]],[52,[3,'R',false,true,false,false,0,0]]];
 hole.flags=[true,false,false,false,false];hole.squareEffects=[[28,'WHITE_HOLE',true,6,0]];
 hole.counters=[0,100,-1,0,-1];hole.repetitions={};delete hole.lastMove;
 state=await api('import',{version:state.version,save:hole});assert.equal(state.check,true);
 state=await api('new',{mode:'human'});
 state=await api('action',{version:state.version,actionId:'move:12:28:'});
 await api('save',{version:state.version});
 state=await api('new',{mode:'human'});state=await api('load',{version:state.version});
 assert.equal(state.ply,1);assert.equal(state.whiteTurn,false);
 assert.ok(state.board.some(p=>p.white&&p.type==='P'&&p.square===28));
 assert.equal(await evaluate('document.querySelectorAll(".square").length'),64);
 console.log('Android passed: actual local AI search, four expansions preserve pieces, shield excludes king, white hole leaves check, manual save/load. No OpenAI requests.');
}finally{socket.close();native('forward','--remove','tcp:9224');}
