#!/usr/bin/env python3
"""Original synthesized practice track and v2 charts. Requires numpy, soundfile.
No borrowed recording; deterministic composition, 100 BPM, 52 seconds.
"""
import json
from pathlib import Path
import numpy as np
import soundfile as sf
ROOT = Path(__file__).resolve().parents[1] / 'training'
ROOT.mkdir(exist_ok=True)
sr = 22050
length = 52
track = np.zeros(sr * length)
notes = [220, 261.625565, 329.627557, 293.664768, 220, 196, 261.625565, 293.664768]
for beat in range(84):
    start = int(beat * .6 * sr)
    t = np.arange(int(.48 * sr)) / sr
    f = notes[(beat // 2) % len(notes)]
    # Soft melody and a low kick. Fixed phases, no sampled copyrighted music.
    tone = .18 * (np.sin(2*np.pi*f*t) + .3*np.sin(4*np.pi*f*t)) * np.exp(-t*9)
    kick = .25 * np.sin(2*np.pi*(48*t + 2.8*(1-np.exp(-t*30)))) * np.exp(-t*23)
    tick = .04 * np.sin(2*np.pi*1800*t) * np.exp(-t*100)
    track[start:start+len(t)] += tone + kick + tick
track[-sr:] *= np.linspace(1, 0, sr)
sf.write(ROOT / 'pulse.ogg', track, sr, format='OGG', subtype='VORBIS')
diffs=[]
for index,(name,label) in enumerate([('Right','1 controle - DIREITA'),('Left','1 controle - ESQUERDA'),('Both','2 controles')]):
    chart=[]
    for n,beat in enumerate(range(8,81,2)):
        color = 1 if name=='Right' else 0 if name=='Left' else n%2
        chart.append({'_time':beat,'_lineIndex':1+n%2,'_lineLayer':n%3,'_type':color,'_cutDirection':8})
    (ROOT / (name+'.dat')).write_text(json.dumps({'_version':'2.2.0','_notes':chart,'_obstacles':[],'_events':[]},indent=2)+'\n')
    diffs.append({'_difficulty':['Easy','Normal','Hard'][index],'_difficultyRank':[1,3,5][index],'_beatmapFilename':name+'.dat','_noteJumpMovementSpeed':6,'_noteJumpStartBeatOffset':0,'_customData':{'_difficultyLabel':label}})
info={'_version':'2.0.0','_songName':'Pico Pulse - Treino','_songSubName':'G2 Web 1.0.0','_songAuthorName':'Sintese original','_levelAuthorName':'Adaptacao Web G2','_beatsPerMinute':100,'_previewStartTime':6,'_previewDuration':10,'_songFilename':'pulse.ogg','_coverImageFilename':'cover.png','_environmentName':'DefaultEnvironment','_songTimeOffset':0,'_difficultyBeatmapSets':[{'_beatmapCharacteristicName':'Standard','_difficultyBeatmaps':diffs}]}
(ROOT/'Info.dat').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n')
print('Generated 52 seconds / 37 notes per chart / mono Ogg Vorbis')
