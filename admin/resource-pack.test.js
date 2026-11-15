'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const http=require('node:http');
const path=require('node:path');
const {resourcePackRoute}=require('./resource-pack');

test('only the verified pack is public; immutable bytes, HEAD and cache validation work',async t=>{
  const dir=path.join(__dirname,'public','packs'),manifest=JSON.parse(fs.readFileSync(path.join(dir,'manifest.json')));
  const route=resourcePackRoute(dir);
  const server=http.createServer((req,res)=>{if(!route(req,res,new URL(req.url,'http://localhost'))){res.writeHead(401);res.end();}});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));t.after(()=>new Promise(resolve=>server.close(resolve)));
  const origin=`http://127.0.0.1:${server.address().port}`,url=origin+manifest.path;
  const get=await fetch(url);assert.equal(get.status,200);assert.equal(get.headers.get('content-type'),'application/zip');
  assert.equal(get.headers.get('x-robots-tag'),'noindex, nofollow, noarchive');
  assert.match(get.headers.get('cache-control'),/immutable/);
  assert.deepEqual(Buffer.from(await get.arrayBuffer()),fs.readFileSync(path.join(dir,manifest.filename)));
  const head=await fetch(url,{method:'HEAD'});assert.equal(head.status,200);assert.equal((await head.arrayBuffer()).byteLength,0);
  assert.equal(head.headers.get('content-length'),String(manifest.size));
  const cache=await fetch(url,{headers:{'if-none-match':head.headers.get('etag')}});assert.equal(cache.status,304);
  for(const candidate of ['/packs/manifest.json','/packs/../server.js','/packs/other.zip','/api/apocalypse']){
    const response=await fetch(origin+candidate);assert.equal(response.status,401,candidate);await response.text();
  }
  const post=await fetch(url,{method:'POST'});assert.equal(post.status,401);await post.text();
  const old=await fetch(origin+'/packs/duckcatmine-horror-51d517b28e20bfcb.zip');assert.equal(old.status,200);assert.equal((await old.arrayBuffer()).byteLength,102062);
});
