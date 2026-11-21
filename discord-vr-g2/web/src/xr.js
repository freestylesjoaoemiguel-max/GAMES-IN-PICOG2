import * as THREE from 'three';
import {KEYS,keyPosition} from './model.js';
let api,renderer,scene,camera,root,panel,panelTexture,keyboard,floor,stars,raycaster,mouse,hover,session;
let targets=[],hitboxes=[],controllers=[],lastPrefs='',visible=false,drag=null,offset=0,clickUntil=0;
const stage=document.getElementById('xrStage');
const canvas=document.createElement('canvas');canvas.width=1440;canvas.height=900;const ctx=canvas.getContext('2d');
function rounded(x,y,w,h,r,color){ctx.fillStyle=color;ctx.beginPath();ctx.roundRect(x,y,w,h,r);ctx.fill();}
function text(value,x,y,size=22,color='#c5c8df',weight='400'){ctx.font=`${weight} ${size}px system-ui`;ctx.fillStyle=color;ctx.fillText(value,x,y);}
function wrap(value,x,y,maxWidth,line=30,maxLines=3){let current='',count=0;for(const word of String(value).split(' ')){if(ctx.measureText(current+word).width>maxWidth&&current){ctx.fillText(current,x,y);y+=line;current='';if(++count>=maxLines)return y;}current+=word+' ';}ctx.fillText(current,x,y);return y+line;}
function hit(x,y,w,h,action){hitboxes.push({x,y,w,h,action});}
function drawPanel(){
 if(!api)return;const s=api.snapshot();hitboxes=[];ctx.clearRect(0,0,1440,900);
 rounded(0,0,1440,900,30,'#191d2b');rounded(0,0,85,900,25,'#101420');rounded(85,0,280,900,0,'#141927');
 text('◈',26,63,37,'#ac9af5');['G2','LA','CI'].forEach((v,i)=>{rounded(18,115+i*80,50,54,13,i===0?'#514377':'#293146');text(v,27,150+i*80,19,'#b9b0dc','600');hit(10,110+i*80,66,65,()=>api.selectGuild(i));});
 text(s.guild.slice(0,19),108,58,23,'#ddd9f2','600');text(s.local?'ESPAÇO LOCAL':'OAUTH2 • INTEGRAÇÃO',108,92,12,'#9293b8');
 ['geral','boas-vindas','ideias'].forEach((v,i)=>{rounded(100,140+i*62,245,52,9,s.channel===v?'#38314e':'#1b2030');text('# '+v,118,174+i*62,21,s.channel===v?'#bfb0f1':'#8794b0');hit(100,140+i*62,245,52,()=>{if(s.local)api.selectChannel(v);else api.notify('Navegação de canais Discord não está disponível por OAuth2 básico.');});});
 text('VOZ NÃO INTEGRADA',112,415,13,'#78819b');text('Seu microfone não é',112,448,16,'#6f7d99');text('transmitido pelo app.',112,474,16,'#6f7d99');
 rounded(105,742,237,55,10,'#2b2941');text('⊙ Recentrar',137,778,21,'#c6b3f2');hit(105,742,237,55,recenter);
 rounded(105,815,237,55,10,'#24283b');text('Configurações',130,850,19,'#a4aecb');hit(105,815,237,55,()=>api.notify('Saia da sessão imersiva para ajustar configurações na página.'));
 text('# '+s.channel,398,58,25,'#d9d3ee','600');text(s.local?'MENSAGENS LOCAIS — NÃO SÃO PUBLICADAS':'SÓ SEUS ENVIOS POR WEBHOOK; NÃO É O HISTÓRICO DO CANAL',400,101,13,'#a799cf');
 const messages=s.messages.slice(0,Math.max(1,s.messages.length-offset)).slice(-4);let y=153;
 for(const m of messages){rounded(400,y,48,48,14,m.kind==='system'?'#4d3f67':'#34465f');text(m.kind==='system'?'✦':m.author.slice(0,1),414,y+32,24,'#cfbaf3');text(m.author,467,y+19,20,'#cac1e5','600');text(m.kind==='webhook'?'INTEGRAÇÃO':'LOCAL',1015,y+19,12,'#8f83b4');ctx.fillStyle='#98a6bf';ctx.font='19px system-ui';wrap(m.content,468,y+55,850,29,3);y+=135;}
 rounded(398,757,870,80,13,'#272f43');text(s.draft?Array.from(s.draft).slice(-65).join(''):'Escreva com o teclado VR…',419,805,21,s.draft?'#ded8f7':'#73819b');hit(398,757,870,80,()=>{if(!api.snapshot().keyboard)api.toggleKeyboard();});
 rounded(1282,757,115,80,13,'#9481d8');text('Enviar',1300,805,22,'#1d1930','600');hit(1282,757,115,80,api.send);
 text('⌨ Teclado curvado',410,875,18,'#a897d1');hit(398,847,300,46,api.toggleKeyboard);
 text('↑',1300,179,28,'#a999d6');text('↓',1360,179,28,'#a999d6');hit(1280,140,60,55,()=>{offset=Math.min(4,offset+1);drawPanel();});hit(1340,140,60,55,()=>{offset=Math.max(0,offset-1);drawPanel();});
 if(panelTexture)panelTexture.needsUpdate=true;
}
function curvedPanel(w,h,bend){const segments=32,positions=[],uv=[],indices=[];const radius=bend===0?1000:w/bend;for(let i=0;i<=segments;i++){const u=i/segments,a=(u-.5)*w/radius;for(let j=0;j<2;j++){positions.push(radius*Math.sin(a),(j===0?1:-1)*h/2,radius*(1-Math.cos(a)));uv.push(u,j===0?1:0);}}for(let i=0;i<segments;i++){const a=i*2;indices.push(a,a+1,a+2,a+2,a+1,a+3);}const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));g.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));g.setIndex(indices);g.computeVertexNormals();return g;}
function rebuild(){
 if(!api)return;const s=api.snapshot(),p=s.prefs;const json=JSON.stringify([p,s.keyboard]);if(json===lastPrefs){drawPanel();return;}lastPrefs=json;hover=null;
 if(panel){root.remove(panel);panel.geometry.dispose();panel.material.dispose();}
 const w=[2.4,3.2,4.2,5.6][p.size];panel=new THREE.Mesh(curvedPanel(w,w*.625,[0,.25,.45,.65][p.panelCurve]),new THREE.MeshBasicMaterial({map:panelTexture,side:THREE.DoubleSide,transparent:true,alphaTest:.01}));panel.position.set(0,.18,-[2.7,3.3,4.1][p.distance]);panel.userData.panel=true;root.add(panel);targets=[panel];
 if(keyboard){root.remove(keyboard);keyboard.traverse(o=>{o.geometry?.dispose();if(o.material)o.material.dispose();});keyboard.userData.texture?.dispose();}
 keyboard=new THREE.Group();root.add(keyboard);keyboard.visible=s.keyboard;
 const atlas=document.createElement('canvas');atlas.width=1024;atlas.height=512;const a=atlas.getContext('2d');let index=0;
 for(const row of KEYS)for(const label of row){const x=(index%10)*102.4,y=Math.floor(index/10)*102.4;a.fillStyle='#2b3047';a.beginPath();a.roundRect(x+4,y+7,94,85,11);a.fill();a.strokeStyle='#7f6da052';a.stroke();a.fillStyle='#e1d6f8';a.font=`${label.length>2?17:29}px system-ui`;a.textAlign='center';a.textBaseline='middle';a.fillText(label,x+51,y+50);index++;}
 const texture=new THREE.CanvasTexture(atlas);texture.colorSpace=THREE.SRGBColorSpace;keyboard.userData.texture=texture;index=0;
 KEYS.forEach((row,r)=>row.forEach((label,i)=>{const wide=r===4,pos=keyPosition(i*(wide?1.6:1),(row.length-1)*(wide?1.6:1)+1,r,p.curve,p.keyboardSize);const geometry=new THREE.PlaneGeometry((wide?.246:.157)*p.keyboardSize,.116*p.keyboardSize);const col=index%10,line=Math.floor(index/10),attr=geometry.attributes.uv;for(let v=0;v<attr.count;v++)attr.setXY(v,(col+attr.getX(v))/10,1-(line+1-attr.getY(v))/5);const mesh=new THREE.Mesh(geometry,new THREE.MeshBasicMaterial({map:texture,transparent:true,side:THREE.DoubleSide}));mesh.position.set(pos.x,pos.y,pos.z);mesh.rotation.set(-.15,-pos.angle,0);mesh.userData.key=label;keyboard.add(mesh);if(s.keyboard)targets.push(mesh);index++;}));
 renderer.setPixelRatio([.7,1,Math.min(devicePixelRatio,1.5)][p.quality]);floor.visible=!p.cinema;stars.visible=!p.cinema;drawPanel();
}
function intersection(ray){const hits=ray.intersectObjects(targets,false);return hits[0];}
function highlight(hit){const mesh=hit?.object;if(hover!==mesh){if(hover&&!hover.userData.panel){hover.material.color.set('#ffffff');hover.scale.setScalar(1);}hover=mesh;if(hover&&!hover.userData.panel)hover.material.color.set('#d9c0ff');}}
function activate(hit){if(!hit)return;if(hit.object.userData.key){api.key(hit.object.userData.key);hit.object.scale.setScalar(.91);clickUntil=performance.now()+120;}else if(hit.uv){const x=hit.uv.x*1440,y=(1-hit.uv.y)*900;hitboxes.find(b=>x>=b.x&&x<=b.x+b.w&&y>=b.y&&y<=b.y+b.h)?.action();}drawPanel();}
function controllerRay(controller){const rotation=new THREE.Matrix4().extractRotation(controller.matrixWorld);raycaster.ray.origin.setFromMatrixPosition(controller.matrixWorld);raycaster.ray.direction.set(0,0,-1).applyMatrix4(rotation);return intersection(raycaster);}
export async function init(actions){
 api=actions;scene=new THREE.Scene();scene.background=new THREE.Color('#0b0e19');scene.fog=new THREE.Fog('#0b0e19',7,20);
 renderer=new THREE.WebGLRenderer({antialias:true,alpha:false,powerPreference:'low-power'});renderer.xr.enabled=true;renderer.outputColorSpace=THREE.SRGBColorSpace;stage.appendChild(renderer.domElement);
 camera=new THREE.PerspectiveCamera(65,innerWidth/innerHeight,.05,30);camera.position.set(0,1.65,0);root=new THREE.Group();scene.add(root);
 floor=new THREE.GridHelper(25,50,0x31304f,0x181d32);floor.position.y=-.05;scene.add(floor);
 const points=[];for(let i=0;i<140;i++){const n=i*2.39996;points.push(Math.sin(n)*(4+i%7),1+((i*73)%60)/12,Math.cos(n)*(4+i%7));}const g=new THREE.BufferGeometry();g.setAttribute('position',new THREE.Float32BufferAttribute(points,3));stars=new THREE.Points(g,new THREE.PointsMaterial({color:0x8174b1,size:.015,sizeAttenuation:true}));scene.add(stars);
 panelTexture=new THREE.CanvasTexture(canvas);panelTexture.colorSpace=THREE.SRGBColorSpace;raycaster=new THREE.Raycaster();mouse=new THREE.Vector2();
 for(let i=0;i<2;i++){const c=renderer.xr.getController(i);const line=new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0,0,0),new THREE.Vector3(0,0,-5)]),new THREE.LineBasicMaterial({color:0xb09aee}));c.add(line);c.addEventListener('selectstart',()=>activate(controllerRay(c)));c.addEventListener('connected',e=>{c.userData.source=e.data;});c.addEventListener('disconnected',()=>{c.userData.source=null;});scene.add(c);controllers.push(c);}
 const element=renderer.domElement;
 element.addEventListener('pointermove',e=>{if(drag&&e.buttons===2){const p=api.snapshot().prefs;camera.rotation.order='YXZ';camera.rotation.y-=(e.clientX-drag.x)*.002*p.sensitivity;camera.rotation.x=THREE.MathUtils.clamp(camera.rotation.x-(e.clientY-drag.y)*.002*p.sensitivity*(p.invert?-1:1),-.65,.65);drag={x:e.clientX,y:e.clientY};}mouse.set(e.clientX/innerWidth*2-1,1-e.clientY/innerHeight*2);raycaster.setFromCamera(mouse,camera);highlight(intersection(raycaster));});
 element.addEventListener('pointerdown',e=>{if(e.button===2){drag={x:e.clientX,y:e.clientY};return;}mouse.set(e.clientX/innerWidth*2-1,1-e.clientY/innerHeight*2);raycaster.setFromCamera(mouse,camera);activate(intersection(raycaster));});element.addEventListener('pointerup',()=>drag=null);element.addEventListener('contextmenu',e=>e.preventDefault());
 element.addEventListener('wheel',e=>{e.preventDefault();offset=Math.max(0,Math.min(4,offset+Math.sign(e.deltaY)));drawPanel();},{passive:false});
 window.addEventListener('resize',resize);window.addEventListener('dvg:change',rebuild);
 document.getElementById('xrCenter').onclick=recenter;document.getElementById('xrKeyboard').onclick=api.toggleKeyboard;document.getElementById('xrExit').onclick=close;document.getElementById('xrImmersive').onclick=()=>open(true);
 let scrollAt=0,lastFrame=0;
 renderer.setAnimationLoop(()=>{
  if(!visible&&!session)return;
  const now=performance.now();
  if(!session&&now-lastFrame<1000/[30,45,60][api.snapshot().prefs.quality])return;
  lastFrame=now;
  if(session){
   let active=false;
   for(const c of controllers){
    if(!c.userData.source)continue;
    const h=controllerRay(c);if(h&&!active){highlight(h);active=true;}
    const axes=c.userData.source.gamepad?.axes||[],vertical=axes.length>3?axes[3]:axes[1]||0;
    if(Math.abs(vertical)>.65&&now>scrollAt){offset=Math.max(0,Math.min(4,offset+Math.sign(vertical)));scrollAt=now+250;drawPanel();}
   }
   if(!active)highlight(null);
  }
  if(hover&&!hover.userData.panel&&now>clickUntil)hover.scale.setScalar(1);
  renderer.render(scene,camera);
 });
 recenter();rebuild();resize();
}
function resize(){renderer?.setSize(innerWidth,innerHeight);if(camera){camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();}}
export function recenter(){if(!root)return;const cam=renderer.xr.isPresenting?renderer.xr.getCamera():camera;const position=new THREE.Vector3(),direction=new THREE.Vector3();cam.getWorldPosition(position);cam.getWorldDirection(direction);root.position.copy(position);root.rotation.set(0,Math.atan2(-direction.x,-direction.z),0);}
export async function open(immersive=false){
 stage.hidden=false;visible=true;resize();recenter();rebuild();
 if(!immersive)return;
 if(!navigator.xr||!await navigator.xr.isSessionSupported('immersive-vr')){api.notify('WebXR imersivo não está disponível neste navegador. Esta é a prévia 3D, não uma sessão VR.');return;}
 try{session=await navigator.xr.requestSession('immersive-vr',{optionalFeatures:['local-floor']});await renderer.xr.setSession(session);session.addEventListener('end',()=>{session=null;document.getElementById('xrMode').textContent='Prévia espacial · botão direito para olhar';});document.getElementById('xrMode').textContent='WebXR imersivo · poses fornecidas pelo runtime';setTimeout(recenter,150);}catch(e){api.notify('Sessão VR não iniciada: '+e.message);}
}
export function close(){session?.end();stage.hidden=true;visible=false;}
