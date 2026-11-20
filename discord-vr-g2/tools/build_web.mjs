// Produce a portable static WebXR site, including Three.js and the real APK.
// No server secrets, node_modules tree or OAuth credentials enter the output.
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const out=path.join(root,'build/webxr');
await fs.rm(out,{recursive:true,force:true});
await fs.mkdir(out,{recursive:true});
await fs.cp(path.join(root,'web'),out,{recursive:true});
const index=path.join(out,'index.html');
let html=await fs.readFile(index,'utf8');
html=html.replace('<head>','<head><meta name="discord-vr-static" content="true">')
 .replace('href="/style.css"','href="./style.css"')
 .replace('"/vendor/three/build/three.module.js"','"./vendor/three/build/three.module.js"')
 .replace('src="/src/app.js"','src="./src/app.js"');
await fs.writeFile(index,html);
await fs.mkdir(path.join(out,'vendor/three/build'),{recursive:true});
await fs.copyFile(path.join(root,'node_modules/three/build/three.module.js'),path.join(out,'vendor/three/build/three.module.js'));
await fs.copyFile(path.join(root,'node_modules/three/LICENSE'),path.join(out,'vendor/three/LICENSE'));
await fs.copyFile(path.join(root,'docs/THIRD_PARTY.txt'),path.join(out,'THIRD_PARTY.txt'));
await fs.copyFile(path.join(root,'../LICENSE'),path.join(out,'LICENSE'));
await fs.writeFile(path.join(out,'.nojekyll'),'');
await fs.writeFile(path.join(out,'LEIA-ME.txt'),`Discord VR G2 — WebXR estático\n\nSirva esta pasta por HTTPS. Pode ficar na raiz ou em uma subpasta.\nNão abra index.html por file:// (módulos exigem servidor).\nModo local somente: sem backend OAuth2, chamadas ou mensagens reais Discord.\nEntrar WebXR requer navegador/headset compatível e permissão de sessão imersiva.\nO navegador antigo do PICO G2 pode não ser compatível.\nAPK em downloads/: desenvolvimento, sem validação no G2 ou runtime PICO.\nCada build CI gera chave de desenvolvimento nova: atualizações entre builds\npodem exigir reinstalação (perda de dados locais). Use chave própria para releases.\n\nNenhum site existente é substituído automaticamente por esta build.\n`);
console.log('Static WebXR ready:',out);
