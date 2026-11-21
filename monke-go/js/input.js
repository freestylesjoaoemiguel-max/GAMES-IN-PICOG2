// Gorilla Rush (Pico G2 / Oculus Go) — sistema de entrada CENTRALIZADO.
// Todas as fontes (controle 3DoF Pico G2 / Oculus Go via WebVR ou WebXR, gamepad comum, teclado, toque/Cardboard, olhar)
// são lidas aqui uma vez por quadro e convertidas em: estado contínuo (orientação do controle, gatilho,
// touchpad) + AÇÕES discretas roteadas para o contexto ativo ('game' ou 'menu'). Assim um mesmo botão
// nunca dispara duas coisas ao mesmo tempo (ex.: clique do touchpad = pular no jogo, confirmar no menu).
'use strict';
const INPUT=(function(){
  const I={
    context:'game',
    ctrl:{present:false,source:'',id:'',hand:'right',raw:new THREE.Quaternion(),quat:new THREE.Quaternion(),
      trigger:false,touch:false,x:0,y:0,click:false},
    gaze:true,           // sem controle -> menus por olhar (Cardboard)
    actions:[],          // fila de ações do quadro: {a:'menu'|'select'|'jump'|'turnL'|'turnR'|'up'|'down'|'left'|'right'|'back'}
    trigDown:false,trigUp:false,
    xrSources:[],        // preenchido por vr.js (WebXR)
    xrSelect:new WeakMap(),
    activeSource:null,trackingRevision:0,handIndex:1,
  };
  const PRESS_LONG=.6;let pad0=null,padT=0,padLong=false,touchStart=null,lastTrig=false,lastBack=false,swipeLock=0;
  const isVRPad=g=>!!(g&&(g.pose||/Oculus|Gear VR|Daydream|OpenVR|Pacific|Pico|Goblin|Neo/i.test(g.id||'')||g.hand||g.mapping==='xr-standard'));
  I.isVRPad=isVRPad;
  // eventos (teclado/toque) chegam entre quadros: ficam pendentes e entram na fila no próximo poll
  const pend=[];function push(a,x){pend.push({a,x})}
  I.push=push;
  // ---- fontes ----
  let sourceKey=null;
  function buttons(g,selected){const c=I.ctrl,m=TRACKING.controls(g,SETTINGS,selected);c.trigger=m.trigger;c.click=m.click;c.touch=m.touch;c.x=m.x;c.y=m.y;I.padRaw=m.pressed;I.padCount=m.count;I.padAxes=m.axes;I.mapping=m.mapping;if(m.menu&&!lastBack)push(/Gear VR/i.test(c.id)?'back':'menu');lastBack=m.menu;}
  function readWebVR(){
    const gps=navigator.getGamepads?navigator.getGamepads():[];let candidates=[];
    for(let i=0;i<gps.length;i++){const p=gps[i];if(p&&p.connected&&p.pose&&TRACKING.orientation(p.pose.orientation))candidates.push(p)}
    const desired='right';const g=candidates.find(p=>desired!=='auto'&&p.hand===desired)||candidates.find(p=>p.index===I.legacyIndex)||candidates[0];if(!g)return false;
    const c=I.ctrl;c.present=true;c.source='webvr';c.id=g.id;c.hand=g.hand||'right';c.raw.fromArray(TRACKING.orientation(g.pose.orientation));I.legacyIndex=g.index;sourceKey='legacy:'+g.index+':'+g.id;I.currentGamepad=g;I.selected=false;return true;
  }
  function readWebXR(){
    const s=TRACKING.choose(I.xrSources,'right',I.activeSource);if(!s)return false;
    if(!VRP.xrCtrlQuat(s,I.ctrl.raw))return false;
    I.activeSource=s;sourceKey=s;const c=I.ctrl;c.present=true;c.source='webxr';c.id=(s.profiles&&s.profiles[0])||'xr';c.hand=s.handedness==='left'?'left':'right';I.currentGamepad=s.gamepad;I.selected=I.xrSelect.get(s);return true;
  }
  // ---- teclado (desktop / depuração) ----
  addEventListener('keydown',e=>{if(e.target&&(e.target.tagName=='INPUT'||e.target.tagName=='SELECT'))return;
    const k=e.code;if(k=='Escape'||k=='KeyM'||k=='Tab'){push('menu');e.preventDefault()}
    else if(I.context=='menu'){if(k=='ArrowUp')push('up');else if(k=='ArrowDown')push('down');else if(k=='ArrowLeft')push('left');else if(k=='ArrowRight')push('right');else if(k=='Enter'||k=='Space'){push('select');e.preventDefault()}else if(k=='Backspace')push('back')}
    else if(k=='KeyQ')push('turnL');else if(k=='KeyE')push('turnR')});
  // ---- toque (Cardboard: o botão do Cardboard gera um toque na tela) ----
  let tDown=0,tPos=null,tLongFired=false;
  addEventListener('pointerdown',e=>{if(state!='play')return;tDown=performance.now();tPos=[e.clientX,e.clientY];tLongFired=false});
  addEventListener('pointerup',e=>{if(state!='play'||!tPos)return;const short=performance.now()-tDown<350&&Math.hypot(e.clientX-tPos[0],e.clientY-tPos[1])<14;
    if(I.context=='menu'&&short&&!tLongFired)push('select',{sx:e.clientX,sy:e.clientY});tPos=null});
  addEventListener('pointercancel',()=>{tPos=null});
  // botão "voltar" do navegador (Pico/Oculus Go fora do modo VR / Android): abre o menu em vez de sair da página
  try{history.pushState({monke:1},'');addEventListener('popstate',()=>{if(state=='play'){push('menu');try{history.pushState({monke:1},'')}catch(e){}}})}catch(e){}

  I.poll=function(dt){
    I.actions.length=0;while(pend.length)I.actions.push(pend.shift());const c=I.ctrl,was=c.present;
    const oldSource=sourceKey;c.present=false;sourceKey=null;
    if(VRP.mode==='webxr')readWebXR();else readWebVR();
    I.handIndex=1; // A sole G2 controller always drives the RIGHT avatar arm, regardless of firmware label.
    if(oldSource!==sourceKey){pad0=null;padLong=false;touchStart=null;lastTrig=false;lastBack=false;I.trackingRevision++;if(typeof LOCO!=='undefined')LOCO.reset();}
    if(!c.present){c.trigger=c.click=c.touch=false;c.x=c.y=0;I.padRaw=[];I.activeSource=null;}

    if(c.present)buttons(I.currentGamepad,I.selected);
    I.gaze=!c.present;
    if(c.present&&!was)toast('Controle conectado: '+(c.id||'controle 3DoF'),2500);
    // mão do controle
    I.handIndex=1;
    // orientação no mundo = giro (snap turn / yaw do rig) * orientação do controle
    VRP.toWorldQuat(c.raw,c.quat);
    // gatilho
    I.trigDown=c.trigger&&!lastTrig;I.trigUp=!c.trigger&&lastTrig;lastTrig=c.trigger;
    if(I.trigDown&&I.context=='menu')push('select');
    // touchpad: clique curto / longo, swipe
    const now=performance.now()/1000;
    if(c.click&&!pad0){pad0={x:c.x,y:c.y};padT=now;padLong=false}
    if(c.click&&pad0&&!padLong&&now-padT>PRESS_LONG){padLong=true;push('menu')}
    if(!c.click&&pad0){if(!padLong){const x=pad0.x,y=pad0.y;
        if(I.context=='menu'){if(Math.abs(x)>.6&&Math.abs(x)>Math.abs(y))push(x<0?'left':'right');else push('select')}
        else{if(SETTINGS.snap&&Math.abs(x)>.55&&Math.abs(x)>Math.abs(y))push(x<0?'turnL':'turnR');else push('jump')}}
      pad0=null}
    if(c.touch&&!c.click){if(!touchStart)touchStart={x:c.x,y:c.y};else if(I.context=='menu'&&now>swipeLock){const dy=c.y-touchStart.y;if(Math.abs(dy)>.55){push(dy<0?'up':'down');touchStart={x:c.x,y:c.y};swipeLock=now+.18}}}
    else if(!c.touch)touchStart=null;
    // toque longo na tela (Cardboard) = menu
    if(tPos&&!tLongFired&&(assist==0||I.context=='menu')&&performance.now()-tDown>800){tLongFired=true;push('menu')}
  };
  // Texto de diagnóstico (menu VR → Dispositivo): ajuda a confirmar o layout real dos botões no headset.
  I.diag=()=>{const c=I.ctrl;if(!c.present)return 'Controle: sem pose válida — reconecte/recentre no sistema';
    return 'Controle: '+(c.id||'?')+' ('+c.source+', '+(I.mapping||'?')+') · '+(VRP.threeDoF()?'braço estimado 3DoF':'posição XR')+' · mão direita · botões '+(I.padCount===undefined?'?':I.padCount)+' · pressionados: ['+((I.padRaw||[]).join(',')||'—')+']'};
  I.has=a=>{for(let i=0;i<I.actions.length;i++)if(I.actions[i].a===a)return I.actions[i];return null};
  // vetor de movimento assistido pelo touchpad (apenas tocado, sem clique) — suave, opcional
  I.padMove=()=>{const c=I.ctrl;if(!c.present||!c.touch||c.click||I.context!='game')return null;
    const r=Math.hypot(c.x,c.y);if(r<.25)return null;return {x:c.x,y:c.y,r:Math.min(1,(r-.25)/.6)}};
  return I})();
