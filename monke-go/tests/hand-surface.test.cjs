const test=require('node:test'),assert=require('node:assert/strict'),THREE=require('../lib/three.min.js'),{solve}=require('../js/hand-surface.js');
const v=(x=0,y=0,z=0)=>new THREE.Vector3(x,y,z);
function plane(normal,offset=0){return (origin,dir,far)=>{const dot=normal.dot(dir);if(Math.abs(dot)<1e-8)return null;const t=(offset-origin.dot(normal))/dot;if(t<=.00001||t>far)return null;return {distance:t,point:origin.clone().addScaledVector(dir,t),n:normal.clone().multiplyScalar(dot<0?1:-1),cls:2}}}
test('open hand touches actual floor at radius, not half metre above it',()=>{const r=solve(v(0,.5),v(0,.4),.075,plane(v(0,1)));assert.equal(r.contact,false);assert.equal(r.position.y,.4)});
test('fast hand sweep cannot pass through a floor',()=>{const r=solve(v(0,.2),v(0,-.3),.075,plane(v(0,1)));assert(r.contact);assert(r.position.y>=.075&&r.position.y<.09);assert(r.position.y-(-.3)>.37)});
test('hand pushing a wall slides tangentially instead of being glued',()=>{const r=solve(v(0,0,.3),v(.3,0,-.2),.075,plane(v(0,0,1)));assert(r.contact);assert(Math.abs(r.position.x-.3)<.02);assert(r.position.z>=.075&&r.position.z<.09)});
test('leaving a surface releases the hand with no phantom contact',()=>{const r=solve(v(0,.077),v(.1,.4),.075,plane(v(0,1)));assert.equal(r.contact,false);assert(r.position.distanceTo(v(.1,.4))<1e-6)});
test('no surface means no artificial propulsion',()=>{const r=solve(v(0,.2),v(.3,-.3),.075,()=>null);assert.equal(r.contact,false);assert(r.position.distanceTo(v(.3,-.3))<1e-6)});
test('slightly overlapping offset ray cannot catch underside when lifting from ground',()=>{const r=solve(v(0,.074),v(.1,.4),.075,plane(v(0,1)));assert.equal(r.contact,false);assert(r.position.distanceTo(v(.1,.4))<1e-6)});
