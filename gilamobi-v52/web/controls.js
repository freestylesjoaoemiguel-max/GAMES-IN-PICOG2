'use strict';
window.gila={ready:false,immersive:false,force3dof:true,notice:'',command:null};
// Godot 4.4 consumes projection-layer textures. Legacy Gecko/Pico browsers
// commonly expose only XRWebGLLayer. The maintained polyfill actually renders
// those textures into the native base layer; it does NOT invent WebXR support.
if (navigator.xr && typeof XRSession !== 'undefined') {
 try { window.gila.layersPolyfilled = new WebXRLayersPolyfill().injected; }
 catch(e) { window.gila.notice='Camada WebXR indisponível: '+e.message; }
}
document.addEventListener('DOMContentLoaded',async()=>{
 const style=document.createElement('link');style.rel='stylesheet';style.href='controls.css';document.head.append(style);
 const panel=document.createElement('section');panel.id='g2-panel';
 panel.innerHTML=`<header><strong>GilaMobi v52 <small>G2 WebXR · 1.0.0</small></strong><button id="g2-fold">Recolher</button></header><div id="g2-body"><p id="g2-status">Carregando o projeto original…</p><button id="g2-vr" data-action="vr" disabled>INICIAR VR</button><p id="g2-capability">Verificando WebXR…</p><div class="row"><button data-action="base">Base</button><button data-action="backrooms">Backrooms</button><button data-action="sandbox">Sandbox</button></div><div class="row"><button data-action="menu">Menu VR</button><button data-action="original_menu">Menu original</button><button data-action="calibrate">Calibrar mãos</button></div><label><input id="g2-arm" type="checkbox" checked> Braço estimado 3DoF — Pico G2</label><details><summary>Controles e limites</summary><p>Entre em VR com o controle apontado para a frente. Incline para baixo e empurre contra o chão ou parede para se mover. Levante para soltar. Não precisa segurar o gatilho para locomover.</p><p>Gatilho curto: selecionar no painel / soltar objeto. Gatilho por 1,1 s: abrir ou fechar o menu. O menu VR contém os mapas, modo Grinner, motosserra, câmera e menus originais.</p><p>O controle do G2 mede rotação, não posição real. Só a mão conectada interage; não há uma segunda mão inventada. Desmarque braço estimado apenas num dispositivo 6DoF.</p><p>WebGL 2 e WebXR immersive-vr são obrigatórios. Não é APK VR nativo. Teste físico e desempenho no G2 ainda pendentes. Voz/rede precisam de permissão e conexão; o broker original é público.</p><a href="https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/gorilla-rush-v45/">Voltar à versão anterior separada</a></details></div>`;
 document.body.append(panel);
 let vr=false;try{vr=!!navigator.xr&&await navigator.xr.isSessionSupported('immersive-vr');}catch(_){ }
 const probe=document.createElement('canvas'),gl=probe.getContext('webgl2');
 const multiview=!!gl?.getExtension('OVR_multiview2');gl?.getExtension('WEBGL_lose_context')?.loseContext();
 const capability=!gl?'WebGL 2 indisponível.':!vr?'Este navegador não oferece WebXR imersivo. O teste na tela não é VR.':!multiview?'WebGL multiview indisponível; este export Godot não pode renderizar estéreo neste navegador.':'WebXR e multiview detectados. Validação física no G2 pendente.';
 vr=vr&&!!gl&&multiview;
 document.querySelector('#g2-capability').textContent=capability;
 panel.querySelectorAll('[data-action]').forEach(b=>b.addEventListener('click',()=>{if(window.gila.ready&&typeof window.gila.command==='function')window.gila.command(b.dataset.action);}));
 document.querySelector('#g2-arm').addEventListener('change',e=>{window.gila.force3dof=e.target.checked;});
 document.querySelector('#g2-fold').addEventListener('click',e=>{const p=document.querySelector('#g2-body');p.hidden=!p.hidden;e.target.textContent=p.hidden?'Abrir painel':'Recolher';});
 setInterval(()=>{const g=window.gila;panel.hidden=g.immersive;panel.querySelectorAll('[data-action]').forEach(b=>{b.disabled=!g.ready||(b.dataset.action==='vr'&&!vr);});document.querySelector('#g2-status').textContent=g.notice||(g.ready?'Pronto · projeto v52 carregado':'Importando e preparando o mapa…');},250);
});
