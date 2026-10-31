'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

function bridge(scenario = true) {
  const listeners = {}, events = {}, messages = [], dom = {};
  const parent = {postMessage(message,origin) {messages.push({message,origin});}};
  class MarkerSet {constructor() {this.data={};} add() {} remove() {}}
  class HtmlMarker {updateFromData(data) {this.data=data;}}
  const window = {parent,location:{origin:'https://panel.example',search:scenario ? '?scenario=ground_zero' : '',hash:'#ground_zero:0:80:0:900:0:0.5:0:0:perspective'},
    BlueMap:{MarkerSet,HtmlMarker},addEventListener(type,listener){listeners[type]=listener;},document:{head:{appendChild(style){this.style=style;}},createElement(){return {};},addEventListener(type,listener){dom[type]=listener;}},
    bluemap:{events:{addEventListener(type,listener){events[type]=listener;}},mapViewer:{map:{data:{id:scenario ? 'ground_zero' : 'world'}},markers:{add(){}}},
      switchMap(id){this.mapViewer.map.data.id=id;events.bluemapMapChanged();return Promise.resolve();}}};
  vm.runInNewContext(fs.readFileSync(require.resolve('./public/map-picker.js'),'utf8'),{window,URLSearchParams});
  return {window,events,messages,dom,send(data,extra={}){listeners.message({source:parent,origin:window.location.origin,data:{source:'duckcatmine-map-picker',...data},...extra});}};
}

test('camera focus keeps orientation and bounds distance without reloading the map',()=>{
  const b=bridge();
  b.send({type:'focus',world:'ground_zero',x:12,y:68,z:-9,distance:240});
  assert.equal(b.window.location.hash,'#ground_zero:12:68:-9:240:0:0.5:0:0:perspective');
  b.send({type:'focus',world:'ground_zero',x:12,y:68,z:-9,distance:99999});
  assert.match(b.window.location.hash,/:5000:/);
});

test('only the same-origin parent can focus the scenario, using finite coordinates',()=>{
  const b=bridge(), before=b.window.location.hash;
  const focus={type:'focus',world:'ground_zero',x:12,y:68,z:9};
  b.send(focus,{origin:'https://foreign.example'});
  b.send(focus,{source:{}});
  b.send({...focus,world:'world'});
  b.send({...focus,x:'12:stop'});
  b.send({...focus,z:Infinity});
  b.send({...focus,y:30000001});
  assert.equal(b.window.location.hash,before);
});

test('native BlueMap player markers select the survivor instead of placing a point',()=>{
  const b=bridge();
  b.events.bluemapMapInteraction({detail:{object:{parent:{isPlayerMarker:true,data:{name:'Alex'}}}}});
  assert.equal(b.messages.at(-1).message.type,'player');
  assert.equal(b.messages.at(-1).message.name,'Alex');
  assert.equal(b.messages.at(-1).origin,'https://panel.example');
});

test('native HTML player labels work even when BlueMap consumes its 3D marker event',()=>{
  const b=bridge();let intercepted=0;
  const event={target:{closest(){return {querySelector(){return {textContent:' DrSLiD '};}};}},preventDefault(){intercepted++;},stopPropagation(){intercepted++;}};
  b.dom.click(event);
  assert.equal(b.messages.at(-1).message.name,'DrSLiD');
  assert.equal(intercepted,2);
  assert.match(b.window.document.head.style.textContent,/pointer-events:auto/);
  assert.equal(bridge(false).dom.click,undefined,'World retains its native player popup');
});

test('changing maps returns a Ground Zero embed to its scenario',()=>{
  const b=bridge();
  b.window.bluemap.mapViewer.map.data.id='world';
  b.events.bluemapMapChanged();
  assert.equal(b.window.bluemap.mapViewer.map.data.id,'ground_zero');
  assert.equal(b.messages.at(-1).message.world,'ground_zero');
});

test('the original World picker retains point selection and clear messages',()=>{
  const b=bridge(false);
  b.events.bluemapMapInteraction({detail:{hiresHit:{point:{x:3.9,y:65.4,z:-2.2}}}});
  const point=b.messages.at(-1).message;
  assert.equal(point.type,'point');assert.equal(point.world,'world');
  assert.equal(point.x,3);assert.equal(point.y,65);assert.equal(point.z,-3);
  b.send({type:'clear'});
  assert.equal(b.messages.at(-1).message.type,'clear');
});
