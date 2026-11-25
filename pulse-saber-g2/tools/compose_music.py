"""Original synthesized instrumental tracks + deterministic single-controller charts.
All oscillators, arrangement and percussion are generated here; no commercial samples.
Requires numpy==2.2.6. Audio stays at 22050 Hz mono PCM to suit older Android.
"""
from pathlib import Path
import wave,json
import numpy as np
ROOT=Path(__file__).resolve().parents[1]/'web/assets'
SR=22050
songs=[('neon-drift','Neon Drift',108,45,[0,3,7,10],'SYNTHWAVE','Ciano'),('orbital','Orbital Run',124,48,[0,7,10,5],'ELECTRO','Violeta'),('solar','Solar Rush',140,50,[0,5,8,3],'BASS / ARCADE','Âmbar')]
manifest=[]
for sid,title,bpm,root,chords,genre,color in songs:
 beat=60/bpm;beats=96;duration=4+beats*beat;audio=np.zeros(int((duration+1)*SR));rng=np.random.default_rng(bpm)
 def add(t,signal,gain=1):
  i=int(t*SR);size=min(len(signal),len(audio)-i)
  if size>0:audio[i:i+size]+=signal[:size]*gain
 def tone(midi,dur,kind=0):
  t=np.arange(int(dur*SR))/SR;f=440*2**((midi-69)/12);env=np.minimum(t/.008,1)*np.exp(-t/(dur*.38));s=np.sin(2*np.pi*f*t)
  if kind:s+=.28*np.sin(4*np.pi*f*t)+.13*np.sin(6*np.pi*f*t)
  return s*env
 for b in range(beats):
  t=3+b*beat;ch=chords[(b//8)%4];energy=.8 if 32<=b<64 else .6
  x=np.arange(int(.28*SR))/SR;kick=np.sin(2*np.pi*(48*x+65*.035*(1-np.exp(-x/.035))))*np.exp(-x*15);add(t,kick,.55)
  if b%2:
   x=np.arange(int(.18*SR))/SR;add(t,rng.uniform(-1,1,len(x))*np.exp(-x*25)+.18*np.sin(2*np.pi*185*x)*np.exp(-x*22),.22)
  for half in [0,.5]:
   x=np.arange(int(.07*SR))/SR;noise=rng.uniform(-1,1,len(x));noise=np.r_[0,np.diff(noise)]/2;add(t+half*beat,noise*np.exp(-x*60),.09)
  add(t,tone(root+ch,.8*beat,1),.28)
  for step in range(2):
   note=root+24+ch+[0,7,12,3][(b*2+step)%4];add(t+step*beat/2,tone(note,beat*.45,1),.12*energy)
  if b%4==0:
   for interval in [0,3,7]:add(t,tone(root+12+ch+interval,beat*3.8),.07)
 # A soft fade avoids clicks and excessive peak loudness.
 audio*=np.minimum(np.arange(len(audio))/SR/1.5,1)*np.minimum((len(audio)-np.arange(len(audio)))/SR/2,1)
 audio=np.tanh(audio*1.15)*.82
 with wave.open(str(ROOT/(sid+'.wav')),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes((audio*32767).astype('<i2').tobytes())
 charts={}
 for difficulty in ['easy','normal','expert']:
  notes=[];step={'easy':2,'normal':1,'expert':.5}[difficulty];last=1
  for j,b in enumerate(np.arange(4,beats-2,step)):
   # Adjacent columns only, no simultaneous notes or two-hand patterns.
   lane=[0,1,2,1,0,1,2,1][j%8];row=(j//4)%2;direction=[0,1,2,3][j%4]
   kind='bomb' if difficulty=='expert' and j%19==18 else 'note'
   notes.append({'t':round(3+float(b)*beat,5),'lane':lane,'row':row,'dir':direction,'kind':kind})
  charts[difficulty]=notes
 chart={'id':sid,'bpm':bpm,'duration':round(duration+1,5),'charts':charts}
 (ROOT/(sid+'.json')).write_text(json.dumps(chart,separators=(',',':')))
 manifest.append({'id':sid,'title':title,'bpm':bpm,'duration':round(duration+1,2),'genre':genre,'color':color})
(ROOT/'songs.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print('Created three original tracks and nine single-controller charts.')
