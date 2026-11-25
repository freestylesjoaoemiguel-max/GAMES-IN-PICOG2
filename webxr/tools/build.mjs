import { build } from 'esbuild';
import { mkdirSync, copyFileSync, readFileSync, writeFileSync } from 'node:fs';
mkdirSync('dist', { recursive: true });
await build({
  entryPoints: ['src/main.js'], bundle: true, minify: !process.argv.includes('--dev'), sourcemap: process.argv.includes('--dev'),
  format: 'iife', target: ['chrome80'], outfile: 'dist/app.js', legalComments: 'linked', logLevel: 'info',
});
// aviso de licença do three.js (MIT) junto do bundle
const lic = readFileSync('node_modules/three/LICENSE', 'utf8');
writeFileSync('dist/THIRD-PARTY-LICENSES.txt', 'three.js (MIT)\n\n' + lic);
console.log('build ok');
