'use strict';
// Plain data bridge: commands run synchronously in the click gesture, so
// requestSession does not lose WebXR's transient user activation.
window.opensaber = {ready:false,armModel:true,immersive:false,state:'loading',command:null};
(async () => {
  let supported = false;
  const status = document.getElementById('xr-status');
  try { supported = !!navigator.xr && await navigator.xr.isSessionSupported('immersive-vr'); }
  catch (_) { /* Permissions/browser policy may refuse the probe. */ }
  status.textContent = supported ? 'WebXR disponível neste navegador. Teste físico no G2 ainda pendente.' : 'VR indisponível neste navegador. Você pode testar o jogo na tela; isso não é VR.';
  window.opensaber.vrSupported = supported;
})();
document.querySelectorAll('[data-command]').forEach(button => {
  button.addEventListener('click', () => {
    if (typeof window.opensaber.command === 'function') {
      window.opensaber.command(button.dataset.command);
      document.querySelector('#canvas').focus();
    }
  });
});
document.querySelector('#arm-model').addEventListener('change', event => {
  window.opensaber.armModel = event.target.checked;
});
document.querySelector('#collapse').addEventListener('click', event => {
  const panel = document.querySelector('#panel');
  panel.hidden = !panel.hidden;
  event.target.textContent = panel.hidden ? 'Abrir painel' : 'Recolher';
  event.target.setAttribute('aria-expanded', String(!panel.hidden));
});
setInterval(() => {
  const state = window.opensaber;
  document.body.classList.toggle('immersive', state.immersive);
  document.querySelectorAll('[data-command]').forEach(button => {
    button.disabled = !state.ready || (button.dataset.command === 'vr' && !state.vrSupported);
  });
  const names = {menu:'Menu',playing:'Tocando',paused:'Pausado',complete:'Concluído',loading:'Carregando'};
  document.querySelector('#game-status').textContent = `${names[state.state] || state.state} · ${Math.floor(state.audioTime || 0)} s · ${state.points || 0} pontos`;
}, 300);
