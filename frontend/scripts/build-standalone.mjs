import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { prepareTeamFixture } from './prepare-team-fixture.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const visited = new Set();
const sources = [];

// Bundle this dependency-free module graph so file:// works without module CORS.
async function visit(file) {
  if (visited.has(file)) return;
  visited.add(file);
  const source = await readFile(file, 'utf8');
  const importPattern = /^import\s+\{[^}]+\}\s+from\s+['"]([^'"]+)['"];?\s*$/gm;
  for (const match of source.matchAll(importPattern)) {
    await visit(path.resolve(path.dirname(file), match[1]));
  }
  sources.push(`// ${path.relative(root, file).replaceAll('\\', '/')}\n${source.replace(importPattern, '').replace(/^export\s+(?=(?:class|const|function)\b)/gm, '')}`);
}

await prepareTeamFixture();
await visit(path.join(root, 'src/app.js'));
const css = await readFile(path.join(root, 'src/styles.css'), 'utf8');
const html = await readFile(path.join(root, 'index.html'), 'utf8');
const script = `(() => {\n'use strict';\n${sources.join('\n\n')}\n})();`.replace(/<\/script/gi, '<\\/script');
const standalone = html
  .replace('<link rel="stylesheet" href="./src/styles.css">', () => `<style>\n${css}\n</style>`)
  .replace('<script type="module" src="./src/app.js"></script>', () => `<script>\n${script}\n</script>`);
if (standalone === html || standalone.includes('src="./src/')) throw new Error('Standalone assets were not fully inlined.');
await mkdir(path.join(root, 'dist'), { recursive: true });
await writeFile(path.join(root, 'index.standalone.html'), standalone);
await writeFile(path.join(root, 'dist/index.html'), standalone);
console.log(`Built index.standalone.html and dist/index.html (${Math.round(Buffer.byteLength(standalone) / 1024)} KB). No external assets or runtime dependencies.`);
