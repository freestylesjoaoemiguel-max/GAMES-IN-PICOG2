// Áudio sintetizado (WebAudio) + trilha ambiente opcional (ogg do APK do usuário). Falha em silêncio se bloqueado.
export class Sound {
  constructor() { this.ctx = null; this.master = null; this.amb = null; this.volume = 0.7; }
  unlock() {
    try {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
        this.ctx = new AC(); this.master = this.ctx.createGain(); this.master.gain.value = this.volume; this.master.connect(this.ctx.destination);
      }
      if (this.ctx.state === 'suspended') this.ctx.resume();
    } catch (e) { /* sem áudio */ }
  }
  setVolume(v) { this.volume = v; if (this.master) this.master.gain.value = v; if (this.amb) this.amb.volume = Math.min(1, v * 0.6); }
  tone(f0, f1, dur, type = 'sine', vol = 0.3) {
    if (!this.ctx) return;
    try {
      const t = this.ctx.currentTime, o = this.ctx.createOscillator(), g = this.ctx.createGain();
      o.type = type; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(Math.max(20, f1), t + dur);
      g.gain.setValueAtTime(vol, t); g.gain.exponentialRampToValueAtTime(0.001, t + dur);
      o.connect(g); g.connect(this.master); o.start(t); o.stop(t + dur + 0.02);
    } catch (e) { /* ok */ }
  }
  noise(dur, vol = 0.3, hp = 800) {
    if (!this.ctx) return;
    try {
      const n = Math.floor(this.ctx.sampleRate * dur), b = this.ctx.createBuffer(1, n, this.ctx.sampleRate), d = b.getChannelData(0);
      for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / n);
      const s = this.ctx.createBufferSource(), f = this.ctx.createBiquadFilter(), g = this.ctx.createGain();
      s.buffer = b; f.type = 'highpass'; f.frequency.value = hp; g.gain.value = vol;
      s.connect(f); f.connect(g); g.connect(this.master); s.start();
    } catch (e) { /* ok */ }
  }
  shot() { this.noise(0.12, 0.35, 1200); this.tone(520, 90, 0.12, 'square', 0.18); }
  hit() { this.tone(900, 500, 0.06, 'triangle', 0.25); }
  kill() { this.tone(300, 80, 0.3, 'sawtooth', 0.25); }
  hurt() { this.tone(160, 60, 0.25, 'sawtooth', 0.35); }
  click() { this.tone(1200, 900, 0.04, 'square', 0.12); }
  thud() { this.tone(110, 50, 0.1, 'sine', 0.3); }
  tag() { this.tone(400, 1200, 0.25, 'square', 0.25); }
  reload() { this.tone(300, 600, 0.08, 'square', 0.15); setTimeout(() => this.tone(600, 300, 0.08, 'square', 0.15), 700); }
  playAmbient(url) {
    this.stopAmbient();
    try { this.amb = new Audio(url); this.amb.loop = true; this.amb.volume = Math.min(1, this.volume * 0.6); this.amb.play().catch(() => {}); } catch (e) { /* ok */ }
  }
  stopAmbient() { if (this.amb) { try { this.amb.pause(); } catch (e) { /* ok */ } this.amb = null; } }
}
