import { cpSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

export function copyDemoAssets(directory) {
  for(const folder of ['horror','weapons']){
    mkdirSync(resolve(directory,folder),{recursive:true});
    cpSync(resolve('../public',folder),resolve(directory,folder),{recursive:true});
  }
}
