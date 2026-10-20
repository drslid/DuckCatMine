'use strict';

const fs = require('node:fs/promises');

// One reader per process. Unchanged files are never read or parsed again.
class LogReader {
  constructor(filename, maxBytes = 512 * 1024) {
    this.filename = filename;
    this.maxBytes = maxBytes;
    this.ino = null;
    this.offset = 0;
    this.lines = [];
    this.partial = null;
    this.messages = [];
    this.logs = '';
    this.flight = null;
  }
  read() {
    if (!this.flight) this.flight = this.update().finally(() => { this.flight = null; });
    return this.flight;
  }
  async update() {
    let file;
    try {
      file = await fs.open(this.filename, 'r');
      const stat = await file.stat();
      if (this.ino === stat.ino && this.offset === stat.size) return this;
      const reset = this.ino !== stat.ino || stat.size < this.offset || stat.size - this.offset > this.maxBytes;
      let start = reset ? Math.max(0, stat.size - this.maxBytes) : this.offset;
      const bytes = Buffer.alloc(stat.size - start);
      const { bytesRead } = await file.read(bytes, 0, bytes.length, start);
      let content = bytes.subarray(0, bytesRead);
      if (reset) { this.lines = []; this.partial = null; }
      if (reset && start > 0) {
        const first = content.indexOf(10);
        start += first < 0 ? content.length : first + 1;
        content = first < 0 ? Buffer.alloc(0) : content.subarray(first + 1);
      }
      if (this.partial) {
        this.lines.pop();
        start = this.partial.start;
        content = Buffer.concat([this.partial.bytes, content]);
      }
      this.partial = null;
      let cursor = 0;
      while (cursor < content.length) {
        const newline = content.indexOf(10, cursor);
        const end = newline < 0 ? content.length : newline;
        const raw = content.subarray(cursor, end);
        this.lines.push({ id: `${stat.ino}:${start + cursor}`, line: raw.toString('utf8').replace(/\r$/, '') });
        if (newline < 0) {
          const tail = raw.subarray(Math.max(0, raw.length - 8192));
          this.partial = { start: start + cursor + raw.length - tail.length, bytes: Buffer.from(tail) };
        }
        cursor = end + 1;
      }
      this.lines = this.lines.slice(-2000);
      this.offset = stat.size;
      this.ino = stat.ino;
      this.logs = this.lines.map(e => e.line).filter(line => !/RCON (?:Client|Listener).*Thread RCON Client .* (?:started|shutting down)/.test(line)).slice(-100).join('\n');
      this.messages = this.lines.flatMap(({ id, line }) => {
        const entry = line.match(/^\[([^\]]+)\] \[(?:Async Chat Thread[^\]]*|Server thread)\/INFO\]: (.*)$/);
        if (!entry) return [];
        const player = entry[2].match(/^<([A-Za-z0-9_]{3,16})> (.{1,500})$/);
        const announcement = entry[2].match(/^(?:\[Not Secure\] )?\[Rcon\] (.{1,500})$/i);
        if (player) return [{ id, at: entry[1], author: player[1], message: player[2] }];
        if (!announcement) return [];
        const web = announcement[1].match(/^\[Web:([A-Za-z0-9_]{3,16})\] (.{1,180})$/);
        return [{ id, at: entry[1], author: web?.[1] || 'Serveur', message: web?.[2] || announcement[1], ...(web ? { source: 'web' } : {}) }];
      }).slice(-100);
      return this;
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      this.ino = null; this.offset = 0; this.lines = []; this.partial = null; this.messages = []; this.logs = '';
      return this;
    } finally { await file?.close(); }
  }
}

// Sharing the in-flight promise prevents a new Minecraft batch for each browser.
class SharedSnapshot {
  constructor(collect, ttl = 5000) { this.collect = collect; this.ttl = ttl; this.value = null; this.at = 0; this.flight = null; this.generation = 0; }
  invalidate() { this.at = 0; this.generation++; }
  async get() {
    if (this.value && Date.now() - this.at < this.ttl) return this.value;
    if (!this.flight) {
      const generation = this.generation;
      this.flight = Promise.resolve().then(this.collect).then(value => {
        this.value = value; this.at = generation === this.generation ? Date.now() : 0; return value;
      }).finally(() => { this.flight = null; });
    }
    return this.flight;
  }
}

module.exports = { LogReader, SharedSnapshot };
