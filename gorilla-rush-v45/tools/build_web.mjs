import fs from 'node:fs';import path from 'node:path';import {fileURLToPath} from 'node:url';import {execFileSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
for(const f of fs.readdirSync(path.join(root,'js')))if(f.endsWith('.js'))execFileSync(process.execPath,['--check',path.join(root,'js',f)]);
const out=path.join(root,'build/webxr');fs.rmSync(out,{recursive:true,force:true});fs.mkdirSync(out,{recursive:true});
for(const f of ['index.html','css','js','lib','assets','docs','README.md','CREDITS.md','CHANGELOG.md'])fs.cpSync(path.join(root,f),path.join(out,f),{recursive:true});
fs.mkdirSync(path.join(out,'downloads'),{recursive:true});
for(const f of ['Gorilla-Rush-v45-PicoG2.apk','Gorilla-Rush-v45-PicoG2.apk.sha256'])fs.copyFileSync(path.join(root,'downloads',f),path.join(out,'downloads',f));
fs.writeFileSync(path.join(out,'.nojekyll'),'');console.log('Exported actual v45 game, local dependencies and screen-mode APK:',out);
