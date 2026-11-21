// Simulated API only; not a physical PICO test. Adapted from the supplied test.

(()=>{
 const st={frames:0,presenting:false,submits:0,buttons:[{pressed:false,touched:false,value:0},{pressed:false,touched:false,value:0},{pressed:false,touched:false,value:0}],axes:[0,0],connected:true,q:[0,0,0,1]};
 window.__st=st;
 window.VRFrameData=function(){this.leftProjectionMatrix=new Float32Array(16);this.rightProjectionMatrix=new Float32Array(16);this.leftViewMatrix=new Float32Array(16);this.rightViewMatrix=new Float32Array(16);this.pose={orientation:[0,0,0,1],position:null}};
 const I=[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1];
 const proj=(f)=>{const a=1/Math.tan(f/2),n=.1,fa=100;return new Float32Array([a/(1920/2160),0,0,0,0,a,0,0,0,0,(fa+n)/(n-fa),-1,0,0,2*fa*n/(n-fa),0])};
 const d={displayName:'Pico G2 4K',capabilities:{canPresent:true,hasOrientation:true,hasPosition:false,hasExternalDisplay:false},isPresenting:false,depthNear:.1,depthFar:100,
  getEyeParameters(e){return {renderWidth:1920,renderHeight:2160,offset:new Float32Array([e=='left'?-.032:.032,0,0])}},
  requestPresent(l){d.isPresenting=true;st.presenting=true;st.layerW=l[0].source.width;st.layerH=l[0].source.height;setTimeout(()=>dispatchEvent(new Event('vrdisplaypresentchange')),0);return Promise.resolve()},
  exitPresent(){d.isPresenting=false;dispatchEvent(new Event('vrdisplaypresentchange'));return Promise.resolve()},
  requestAnimationFrame(cb){return requestAnimationFrame(cb)},
  getFrameData(fd){fd.pose.orientation=st.q;fd.leftProjectionMatrix.set(proj(1.7));fd.rightProjectionMatrix.set(proj(1.7));fd.leftViewMatrix.set(I);fd.rightViewMatrix.set(I);return true},
  submitFrame(){st.submits++}};
 Object.defineProperty(navigator,'xr',{value:undefined,configurable:true});navigator.getVRDisplays=()=>Promise.resolve([d]);
 const pad={id:'Pico G2 Controller (3DoF)',index:0,get connected(){return st.connected},hand:'right',pose:{hasOrientation:true,orientation:st.q},get buttons(){return st.buttons},get axes(){return st.axes},mapping:''};
 Object.defineProperty(pad.pose,'orientation',{get(){return st.q}});
 navigator.getGamepads=()=>[pad];
})();
