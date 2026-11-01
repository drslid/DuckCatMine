import { cpSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';

export function copyDemoAssets(directory) {
  mkdirSync(resolve(directory,'horror'),{recursive:true});
  cpSync(resolve('../public/horror'),resolve(directory,'horror'),{recursive:true});
}
