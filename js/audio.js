'use strict';
/* Áudio 100% sintetizado (WebAudio): efeitos, música generativa, ambiente e voz (TTS) */
const Sfx = (() => {
  let ctx = null, master, sfxG, musG, ambG, noiseBuf;
  const st = { music: true, sfx: true, vol: 0.8, voice: true };
  let musicTimer = null, musicKind = null, ambTimers = [], ambNodes = [], ambKind = null;

  function init() {
    if (ctx) { if (ctx.state === 'suspended') ctx.resume(); return; }
    const AC = window.AudioContext || window.webkitAudioContext; if (!AC) return;
    ctx = new AC();
    master = ctx.createGain(); master.connect(ctx.destination);
    sfxG = ctx.createGain(); sfxG.connect(master);
    musG = ctx.createGain(); musG.connect(master);
    ambG = ctx.createGain(); ambG.gain.value = 0.5; ambG.connect(master);
    noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
    const d = noiseBuf.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    apply();
    try { if (window.speechSynthesis) speechSynthesis.getVoices(); } catch (e) {}
  }
  function apply() {
    if (!ctx) return;
    master.gain.value = st.vol; sfxG.gain.value = st.sfx ? 1 : 0; musG.gain.value = st.music ? 0.5 : 0;
  }
  function set(k, v) {
    st[k] = v; apply();
    if (k === 'voice' && !v && window.speechSynthesis) try { speechSynthesis.cancel(); } catch (e) {}
  }

  function tone(o) {
    if (!ctx) return;
    const f = o.f || 440, d = o.d || 0.15, a = o.a || 0.005, v = o.v || 0.25, out = o.out || sfxG;
    const t = ctx.currentTime + (o.at || 0);
    const os = ctx.createOscillator(), g = ctx.createGain();
    os.type = o.type || 'sine';
    os.frequency.setValueAtTime(f, t);
    if (o.slide) os.frequency.exponentialRampToValueAtTime(Math.max(20, f * o.slide), t + d);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(v, t + a);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + d);
    os.connect(g); g.connect(out); os.start(t); os.stop(t + a + d + 0.05);
  }
  function noise(o) {
    if (!ctx) return;
    const t = ctx.currentTime + (o.at || 0), d = o.d || 0.1, v = o.v || 0.2;
    const s = ctx.createBufferSource(); s.buffer = noiseBuf; s.loop = false;
    const f = ctx.createBiquadFilter(); f.type = o.type || 'lowpass'; f.Q.value = o.q || 1;
    f.frequency.setValueAtTime(o.f || 1000, t);
    if (o.fend) f.frequency.exponentialRampToValueAtTime(o.fend, t + d);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(v, t + (o.a || 0.01));
    g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    s.connect(f); f.connect(g); g.connect(o.out || sfxG);
    s.start(t, Math.random() * 1.5); s.stop(t + d + 0.05);
  }

  const S = {
    click() { tone({ f: 620, d: 0.09, type: 'triangle', v: 0.28, slide: 1.6 }); tone({ f: 1240, d: 0.07, v: 0.1, at: 0.025 }); },
    hover() { tone({ f: 1500, d: 0.035, type: 'sine', v: 0.06 }); },
    key() { tone({ f: 900 + Math.random() * 120, d: 0.04, type: 'square', v: 0.05 }); noise({ d: 0.03, f: 3000, type: 'highpass', v: 0.04 }); },
    back() { tone({ f: 500, d: 0.1, type: 'triangle', v: 0.2, slide: 0.6 }); },
    open() { tone({ f: 300, d: 0.35, type: 'sine', v: 0.18, slide: 3 }); tone({ f: 600, d: 0.3, v: 0.1, at: 0.08, slide: 2.2 }); noise({ d: 0.3, f: 800, fend: 4000, type: 'bandpass', v: 0.06 }); },
    close() { tone({ f: 900, d: 0.3, type: 'sine', v: 0.16, slide: 0.3 }); },
    error() { tone({ f: 180, d: 0.18, type: 'sawtooth', v: 0.15 }); tone({ f: 150, d: 0.2, type: 'sawtooth', v: 0.15, at: 0.12 }); },
    star() { [784, 988, 1175, 1568].forEach((f, i) => tone({ f, d: 0.35, type: 'triangle', v: 0.2, at: i * 0.07 })); tone({ f: 2093, d: 0.6, v: 0.08, at: 0.28 }); },
    fanfare() { [523, 659, 784, 1047, 784, 1047, 1319].forEach((f, i) => { tone({ f, d: 0.28, type: 'square', v: 0.09, at: i * 0.13 }); tone({ f: f / 2, d: 0.3, type: 'triangle', v: 0.14, at: i * 0.13 }); }); },
    door() { tone({ f: 110, d: 0.5, type: 'sawtooth', v: 0.08, slide: 1.6 }); noise({ d: 0.5, f: 300, fend: 900, q: 6, type: 'bandpass', v: 0.12 }); },
    doorClose() { noise({ d: 0.15, f: 250, type: 'lowpass', v: 0.4 }); tone({ f: 70, d: 0.2, v: 0.3, slide: 0.5 }); },
    pop() { tone({ f: 400, d: 0.12, type: 'sine', v: 0.25, slide: 2.5 }); },
    send() { tone({ f: 500, d: 0.1, type: 'triangle', v: 0.2, slide: 2 }); tone({ f: 800, d: 0.12, type: 'triangle', v: 0.15, at: 0.07 }); },
    recv() { tone({ f: 800, d: 0.1, type: 'triangle', v: 0.18, slide: 0.7 }); tone({ f: 600, d: 0.14, type: 'triangle', v: 0.15, at: 0.07 }); },
    step(surface) {
      const sf = { snow: [2200, 0.16], sand: [900, 0.1], grass: [700, 0.09], stone: [1400, 0.12] }[surface] || [900, 0.1];
      noise({ d: 0.09, f: sf[0] * (0.8 + Math.random() * 0.4), type: surface === 'snow' ? 'bandpass' : 'lowpass', q: 1.5, v: sf[1] });
    },
    blip(p) { tone({ f: (p || 300) * (0.9 + Math.random() * 0.3), d: 0.05, type: 'square', v: 0.045, a: 0.003 }); },
    whoosh() { noise({ d: 0.45, f: 400, fend: 3000, type: 'bandpass', q: 2, v: 0.15 }); },
    calib() { tone({ f: 880, d: 0.12, type: 'triangle', v: 0.25 }); tone({ f: 1320, d: 0.2, type: 'triangle', v: 0.2, at: 0.1 }); }
  };
  function play(n, a) { if (!ctx || !st.sfx) return; const f = S[n]; if (f) f(a); }

  /* ----- Música generativa ----- */
  const MUS = {
    lobby:   { root: 220.0, minor: false, step: 0.28, prog: [[0, 4, 7], [5, 9, 12], [7, 11, 14], [4, 7, 11]], wave: 'triangle' },
    village: { root: 261.63, minor: false, step: 0.24, prog: [[0, 4, 7], [7, 11, 14], [9, 12, 16], [5, 9, 12]], wave: 'triangle' },
    desert:  { root: 196.0, minor: true, step: 0.3, prog: [[0, 3, 7], [-2, 2, 5], [-4, 0, 3], [-2, 2, 5]], wave: 'sawtooth' },
    snow:    { root: 246.94, minor: true, step: 0.34, prog: [[0, 3, 7], [-4, 0, 3], [-7, -3, 0], [-5, -2, 2]], wave: 'sine' }
  };
  const hz = (root, semi) => root * Math.pow(2, semi / 12);
  function startMusic(kind) {
    if (!ctx) return; if (musicKind === kind) return;
    stopMusic(); musicKind = kind;
    const c = MUS[kind] || MUS.lobby; let step = 0, next = ctx.currentTime + 0.15;
    const sched = (s, t) => {
      const chord = c.prog[Math.floor(s / 8) % c.prog.length];
      if (s % 8 === 0) {
        chord.forEach(n => {
          [-1, 1].forEach(det => {
            const os = ctx.createOscillator(), g = ctx.createGain();
            os.type = 'sine'; os.frequency.value = hz(c.root, n) * (1 + det * 0.003);
            const dur = c.step * 8;
            g.gain.setValueAtTime(0.0001, t); g.gain.linearRampToValueAtTime(0.035, t + dur * 0.4); g.gain.linearRampToValueAtTime(0.0001, t + dur * 1.1);
            os.connect(g); g.connect(musG); os.start(t); os.stop(t + dur * 1.2);
          });
        });
      }
      if (s % 4 === 0) {
        const os = ctx.createOscillator(), g = ctx.createGain();
        os.type = 'sine'; os.frequency.value = hz(c.root / 2, chord[0]);
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.12, t + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t + c.step * 3);
        os.connect(g); g.connect(musG); os.start(t); os.stop(t + c.step * 3.2);
      }
      if (s % 2 === 0 || Math.random() < 0.35) {
        const n = chord[Math.floor(Math.random() * chord.length)] + (Math.random() < 0.35 ? 12 : 0) + (Math.random() < 0.1 ? 12 : 0);
        const os = ctx.createOscillator(), g = ctx.createGain();
        os.type = c.wave; os.frequency.value = hz(c.root * 2, n);
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.07, t + 0.01); g.gain.exponentialRampToValueAtTime(0.0001, t + c.step * 2.4);
        os.connect(g); g.connect(musG); os.start(t); os.stop(t + c.step * 2.6);
      }
    };
    musicTimer = setInterval(() => { if (!ctx) return; while (next < ctx.currentTime + 0.5) { sched(step, next); next += c.step; step++; } }, 120);
  }
  function stopMusic() { if (musicTimer) clearInterval(musicTimer); musicTimer = null; musicKind = null; }

  /* ----- Ambiente: vento + pássaros / sinos ----- */
  function startAmbience(kind) {
    if (!ctx || ambKind === kind) return;
    stopAmbience(); ambKind = kind;
    if (kind === 'lobby') return;
    const src = ctx.createBufferSource(); src.buffer = noiseBuf; src.loop = true;
    const f = ctx.createBiquadFilter(); f.type = 'bandpass'; f.Q.value = 0.7;
    f.frequency.value = { village: 500, desert: 380, snow: 800 }[kind] || 500;
    const g = ctx.createGain(); g.gain.value = { village: 0.05, desert: 0.12, snow: 0.14 }[kind] || 0.06;
    const lfo = ctx.createOscillator(), lg = ctx.createGain();
    lfo.frequency.value = 0.12; lg.gain.value = g.gain.value * 0.7; lfo.connect(lg); lg.connect(g.gain); lfo.start();
    src.connect(f); f.connect(g); g.connect(ambG); src.start();
    ambNodes.push(src, lfo);
    const chirp = () => {
      if (!ctx) return;
      if (kind === 'village') {
        const base = 2200 + Math.random() * 1800, n = 2 + Math.floor(Math.random() * 3);
        for (let i = 0; i < n; i++) tone({ f: base, d: 0.07, type: 'sine', v: 0.05, slide: 1.3 + Math.random() * 0.4, at: i * 0.09, out: ambG });
      } else if (kind === 'snow') {
        const b = [1047, 1319, 1568, 1976][Math.floor(Math.random() * 4)];
        tone({ f: b, d: 1.4, type: 'sine', v: 0.04, out: ambG }); tone({ f: b * 2.01, d: 0.9, type: 'sine', v: 0.015, out: ambG });
      } else if (kind === 'desert') {
        tone({ f: 90, d: 1.2, type: 'sine', v: 0.03, slide: 0.8, out: ambG });
      }
      ambTimers.push(setTimeout(chirp, 2500 + Math.random() * 6000));
    };
    ambTimers.push(setTimeout(chirp, 1500));
  }
  function stopAmbience() {
    ambTimers.forEach(clearTimeout); ambTimers = [];
    ambNodes.forEach(n => { try { n.stop(); } catch (e) {} }); ambNodes = []; ambKind = null;
  }

  function speak(text, pitch) {
    if (!st.voice || !window.speechSynthesis) return;
    try {
      speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text); u.lang = 'pt-BR'; u.pitch = pitch || 1; u.rate = 1.03; u.volume = st.vol;
      const v = speechSynthesis.getVoices().find(v => /pt[-_]BR/i.test(v.lang)) || speechSynthesis.getVoices().find(v => /^pt/i.test(v.lang));
      if (v) u.voice = v;
      speechSynthesis.speak(u);
    } catch (e) {}
  }
  function shutUp() { try { window.speechSynthesis && speechSynthesis.cancel(); } catch (e) {} }

  return { init, set, st, play, startMusic, stopMusic, startAmbience, stopAmbience, speak, shutUp, tone };
})();
