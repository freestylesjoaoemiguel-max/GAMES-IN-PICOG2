const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),{pathToFileURL}=require('node:url');
const THREE=require('../lib/three.min.js'),PORT=require('../js/gmotion-port.js');
const V=(x=0,y=0,z=0)=>new THREE.Vector3(x,y,z),arm=(error,active=true)=>({active,contact:true,grace:0,target:V(0,3,0).add(error),desired:V(0,3,0)});
test('all six supplied ZIP reference files retain their recorded SHA256',()=>{const m=require('../docs/GMOTION_IMPORT.json');for(const [file,hash]of Object.entries(m.files))assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(__dirname,'../reference/gorilla-movement',file))).digest('hex'),hash)});
test('ZIP parameters: eight samples, threshold .4, gain 1.1, capped throw 4, gravity 5',()=>{assert.deepEqual([PORT.HISTORY,PORT.THRESHOLD,PORT.JUMP,PORT.MAX_JUMP,PORT.GRAVITY],[8,.4,1.1,4,5])});
test('both contacting followers are averaged even if one is stationary; absent one has no vote',()=>{const m=PORT.create(THREE),v=V();m.apply([arm(V(.02,0,0)),arm(V())],v,.02,1);assert.equal(v.x,.5);m.apply([arm(V(.02,0,0)),arm(V(),false)],v,.02,1);assert.equal(v.x,1)});
test('history stores effective body displacement, not commanded hand speed',()=>{const m=PORT.create(THREE),v=V();for(let i=0;i<12;i++){m.apply([arm(V(.2,0,0))],v,.02,1);m.record(V(),.02)}m.apply([],v,.02,1);assert.equal(v.length(),0);assert(m.released)});
test('release applies the ZIP capped throw once, then leaves flight velocity alone',()=>{for(const scale of [.14,.25,1]){const m=PORT.create(THREE),v=V();for(let i=0;i<8;i++){m.apply([arm(V(.12*scale,0,0))],v,.02,scale);m.record(V(.12*scale,0,0),.02)}m.apply([],v,.02,scale);assert(Math.abs(v.length()/scale-4)<1e-8);v.y-=.1*scale;m.apply([],v,.02,scale);assert(Math.abs(v.y+.1*scale)<1e-8);assert(!m.released)}});
test('reset removes old support and launch history after teleport/loss/menu',()=>{const m=PORT.create(THREE),v=V(2,0,0);m.apply([arm(V(.1,0,0))],v,.02,1);m.record(V(.1,0,0),.02);m.reset();v.set(0,0,0);m.apply([],v,.02,1);assert.equal(v.length(),0);assert(!m.released);assert.equal(m.average.length(),0)});
test('unchanged supplied update sections 9-11 match the port with a deterministic collision/body backend',async()=>{
 // Execute the source file, replacing only module imports. The backend is a
 // fixture to isolate contact averaging/history/throw, NOT Rapier validation.
 const source=fs.readFileSync(path.join(__dirname,'../reference/gorilla-movement/gmotion.js'),'utf8')
  .replace("import * as THREE from 'three';",`import THREE from '${pathToFileURL(path.join(__dirname,'../lib/three.min.js')).href}';`)
  .replace("import RAPIER from '@dimforge/rapier3d-compat';","const RAPIER={Ray:class{},QueryFilterFlags:{EXCLUDE_DYNAMIC:1}};");
 const {GMotion}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
 for(const dt of [1/30,1/60,1/72,1/90]){
  const gm=new GMotion(),m=PORT.create(THREE),vel=V(),pos=V(0,2,0),gv=V();let targets=[],contacts=[];
  gm.pos.copy(pos);gm.previousPosition.copy(pos);
  gm.body={setLinvel(v){gv.copy(v)},linvel(){return gv},translation(){return gm.pos},setGravityScale(){},wakeUp(){}};
  gm.world={castRay(){return null},step(){gm.pos.addScaledVector(gv,dt)}};
  gm._moveFollower=(i)=>{gm.fol[i].copy(targets[i]);return contacts[i]};
  for(let i=0;i<60;i++){
   const errors=i<24?[V((i%8)*.008,0,.012),V(.008,0,0)]:i<40?[V(.00001,0,0),V()]:[V(.04,0,0),V()];
   contacts=i<16?[true,false]:i<24?[true,true]:i<28?[false,false]:i<40?[true,true]:i<48?[false,false]:[true,false];
   const desired=[V(0,3,0),V(0,3,0)];targets=desired.map((d,j)=>d.clone().add(errors[j]));
   gm.update(dt,...desired,{});
   m.apply(errors.map((e,j)=>({...arm(e),contact:contacts[j]})),vel,dt,1);const shift=vel.clone().multiplyScalar(dt);pos.add(shift);m.record(shift,dt);
   assert(vel.distanceTo(gm.vel)<1e-8,`velocity frame ${i}, dt ${dt}`);
   assert(m.average.distanceTo(gm.velocityAverage)<1e-8,`history frame ${i}`);
   assert(pos.distanceTo(gm.pos)<1e-8);
  }
 }
});
