'use strict';
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

// Only the bundled, verified, non-personal pack is public. No arbitrary filesystem path is accepted.
function resourcePackRoute(directory) {
  const packs=new Map();
  try {
    const manifest = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
    if (!/^duckcatmine-horror-[a-f0-9]{16}\.zip$/.test(manifest.filename)
        || !/^[a-f0-9]{40}$/.test(manifest.sha1) || manifest.filename !== `duckcatmine-horror-${manifest.sha1.slice(0,16)}.zip`)
      throw new Error('Invalid resource pack manifest');
    const file = path.join(directory, manifest.filename), bytes = fs.readFileSync(file);
    if (bytes.length > 1024*1024 || bytes.length !== manifest.size || crypto.createHash('sha1').update(bytes).digest('hex') !== manifest.sha1)
      throw new Error('Resource pack checksum mismatch');
    packs.set(`/packs/${manifest.filename}`,{ file, size: bytes.length, sha1: manifest.sha1 });
    // Keep previous immutable URLs valid for clients receiving a pack during a rolling update.
    for(const name of fs.readdirSync(directory)){
      if(!/^duckcatmine-horror-[a-f0-9]{16}\.zip$/.test(name)||name===manifest.filename)continue;
      const oldFile=path.join(directory,name),old=fs.readFileSync(oldFile);
      if(old.length>1024*1024)continue;
      const sha1=crypto.createHash('sha1').update(old).digest('hex');
      if(name!==`duckcatmine-horror-${sha1.slice(0,16)}.zip`)continue;
      packs.set(`/packs/${name}`,{file:oldFile,size:old.length,sha1});
    }
  } catch (error) {
    if (error.code !== 'ENOENT') console.error(`Ground Zero pack disabled: ${error.message}`);
  }
  return (req, res, url) => {
    const pack=packs.get(url.pathname);
    if (!pack || !['GET','HEAD'].includes(req.method)) return false;
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
    res.setHeader('X-Robots-Tag', 'noindex, nofollow, noarchive');
    res.setHeader('ETag', `"${pack.sha1}"`);
    if (req.headers['if-none-match'] === `"${pack.sha1}"`) { res.writeHead(304); res.end(); return true; }
    res.setHeader('Content-Length', pack.size);
    if (req.method === 'HEAD') res.end(); else fs.createReadStream(pack.file).pipe(res);
    return true;
  };
}
module.exports = { resourcePackRoute };
