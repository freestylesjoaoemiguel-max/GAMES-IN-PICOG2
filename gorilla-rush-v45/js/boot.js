// Explicit offline-capable dependency order, replacing 34 MB of inline/base64 scripts.
(function(){'use strict';
 const buffers=new Map();window.V45={
  bytes(path){const b=buffers.get(path);if(!b)throw Error('Missing preloaded asset: '+path);return b},
  binary(path){const b=this.bytes(path);let s='';for(let i=0;i<b.length;i+=8192)s+=String.fromCharCode.apply(null,b.subarray(i,i+8192));return s},
  load(src){return new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=src;s.onload=resolve;s.onerror=()=>reject(Error('Failed to load '+src));document.body.appendChild(s)})}
 };
 const $=id=>document.getElementById(id);
 window.LOADSCR={show(){$('ldscr').classList.add('on')},set(v){$('pct').textContent=Math.round(v)+'%';$('fill').style.width=v+'%'},hide(){$('ldscr').classList.remove('on')},confirm(){return Promise.resolve()}};
 async function boot(){
  $('boot').style.display='none';$('intro').style.display='none';LOADSCR.show();
  document.querySelectorAll('.tab').forEach(b=>{b.dataset.l=b.textContent.trim()});$('profnick').appendChild($('nick'));
  document.querySelectorAll('.pane:not([data-t=play])').forEach(p=>{const b=document.createElement('button');b.className='pclose';b.textContent='VOLTAR';b.onclick=()=>document.querySelector('.tab[data-t=play]').click();p.prepend(b)});
  const r=await fetch('docs/INPUT_PROVENANCE.json');if(!r.ok)throw Error('Manifesto HTTP '+r.status);const manifest=await r.json();let done=0,next=0;
  await Promise.all(Array.from({length:4},async()=>{while(next<manifest.rawAssets.length){const path=manifest.rawAssets[next++],r=await fetch(path);if(!r.ok)throw Error(path+' HTTP '+r.status);buffers.set(path,new Uint8Array(await r.arrayBuffer()));LOADSCR.set(++done/manifest.rawAssets.length*65)}}));
  for(const s of ['lib/three.min.js','lib/GLTFLoader.js','lib/SkeletonUtils.js','lib/Reflector.js','lib/peerjs-1.5.4.min.js','js/tracking.js','js/hand-surface.js','js/right-arm.js','js/arm-physics.js','js/source-01.js','js/source-02.js','js/source-04.js','js/source-05.js','js/source-06.js','js/pico.js','js/source-07.js'])await V45.load(s+'?v=v45-gorila-4');
  R.setAnimationLoop((t,f)=>{PICO.frame=f||null;try{frame(t)}finally{PICO.frame=null}});
  $('lobby').classList.add('in');document.body.classList.add('in','inlobby');
  new MutationObserver(()=>document.body.classList.toggle('inlobby',$('lobby').style.display!=='none')).observe($('lobby'),{attributes:true,attributeFilter:['style']});
  const until=Date.now()+90000;while(!pl||!mapDone){if(Date.now()>until)throw Error('Modelo/mapas nao ficaram prontos. Consulte o erro exibido.');await new Promise(r=>setTimeout(r,100))}
  $('pico-solo').disabled=false;PICO.refreshStart();LOADSCR.set(100);LOADSCR.hide();window.V45.ready=true;
 }
 boot().catch(e=>{LOADSCR.hide();$('pico-status').textContent='FALHA: '+e.message;$('pico-status').style.color='#ffaaaa';console.error(e)});
})();
