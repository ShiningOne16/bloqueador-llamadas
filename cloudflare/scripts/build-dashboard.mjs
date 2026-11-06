import { build } from 'esbuild';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = readdirSync(new URL('../src/', import.meta.url)).filter((name) => name.endsWith('.js')).sort()
  .map((name) => `${name}\0${readFileSync(new URL(`../src/${name}`, import.meta.url), 'utf8')}`).join('\0');
const digest = createHash('sha256').update(source).digest('hex');
await build({
  absWorkingDir: root,
  stdin: { contents: "import worker from './src/index.js'; export default worker;", resolveDir: root, sourcefile: 'dashboard-entry.js' },
  outfile: 'dashboard/worker.js',
  bundle: true,
  format: 'esm',
  platform: 'browser',
  target: 'es2022',
  legalComments: 'none',
  banner: { js: `// Generado con npm run build:dashboard. Pegar en el Worker existente.\n// source-sha256: ${digest}` },
});
