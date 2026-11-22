// Orientation/mapping helpers shared by WebXR and legacy WebVR. No synthetic tracking.
(function(root){'use strict';
 function orientation(o){if(!o)return null;var v=Array.isArray(o)||ArrayBuffer.isView(o)?Array.from(o):[o.x,o.y,o.z,o.w];if(v.length!==4||!v.every(Number.isFinite))return null;var n=Math.hypot(v[0],v[1],v[2],v[3]);if(n<.00001)return null;return v.map(function(x){return x/n})}
 function choose(sources,hand,previous){var list=Array.from(sources||[]).filter(function(s){return s&&s.targetRayMode==='tracked-pointer'&&s.targetRaySpace});var match=list.filter(function(s){return hand!=='auto'&&s.handedness===hand});if(match.length)list=match;if(list.indexOf(previous)>=0)return previous;return list.find(function(s){return s.handedness==='right'})||list[0]||null}
 function position(p){if(!p)return null;var a=Array.isArray(p)||ArrayBuffer.isView(p)?Array.from(p):[p.x,p.y,p.z];return a.length===3&&a.every(Number.isFinite)?a:null}
 // Reserve named sources first. Anonymous single G2 goes to one side only.
 function pair(sources,previous){var list=Array.from(sources||[]),out={left:null,right:null},used=new Set();previous=previous||{};
  ['left','right'].forEach(function(side){var matches=list.filter(function(s){return (s.handedness||s.hand)===side});out[side]=matches.indexOf(previous[side])>=0?previous[side]:matches[0]||null;if(out[side])used.add(out[side])});
  ['right','left'].forEach(function(side){if(out[side])return;var free=list.filter(function(s){var h=s.handedness||s.hand;return !used.has(s)&&h!=='left'&&h!=='right'});out[side]=free.indexOf(previous[side])>=0?previous[side]:free[0]||null;if(out[side])used.add(out[side])});return out;
 }
 function measured(pose,legacy){if(!pose)return null;var p=position(legacy?pose.position:pose.transform&&pose.transform.position);if(!p||pose.emulatedPosition===true||pose.hasPosition===false)return null;if(Math.hypot.apply(null,p)<1e-6&&(legacy?pose.hasPosition!==true:pose.emulatedPosition!==false))return null;return p}
 function controls(g,settings,selected){g=g||{};var b=g.buttons||[],a=g.axes||[],standard=g.mapping==='xr-standard';function down(i){return i>=0&&!!(b[i]&&(b[i].pressed||b[i].value>.5))}function touch(i){return i>=0&&!!(b[i]&&(b[i].touched||b[i].pressed))}function axis(i){return Number.isFinite(a[i])?Math.max(-1,Math.min(1,a[i])):0}
  var click=settings.padClick|0,trig=settings.padTrig|0,app=settings.padApp|0,offset=0;
  if(standard){trig=0;var pad=touch(2)||Math.abs(axis(0))+Math.abs(axis(1))>.01;var stick=!pad&&(touch(3)||Math.abs(axis(2))+Math.abs(axis(3))>.01);click=stick?3:2;offset=stick?2:0;app=4;}
  var x=axis(offset),y=axis(offset+1);if(settings.padSwap){var temp=x;x=y;y=temp}if(settings.padInvertY)y=-y;
  // Some legacy runtimes lack touched entirely; only those may infer touch from
  // axes. An explicit false must win over stale axes after lifting the finger.
  var button=b[click],hasTouch=button&&typeof button.touched==='boolean';
  var touching=hasTouch?touch(click):(touch(click)||Math.hypot(x,y)>.18);
  var magnitude=Math.hypot(x,y),gain=touching&&magnitude>.18?(Math.min(1,magnitude)-.18)/((1-.18)*magnitude):0;
  return {moveX:gain?x*gain:0,moveY:gain?y*gain:0,trigger:down(trig)||!!selected,click:down(click),touch:touching,x:x,y:y,menu:app!==click&&app!==trig&&down(app),pressed:b.map(function(v,i){return down(i)?i:-1}).filter(function(i){return i>=0}),count:b.length,axes:a.length,mapping:standard?'xr-standard':'configurável'};
 }
 var api={orientation:orientation,choose:choose,position:position,pair:pair,measured:measured,controls:controls};root.TRACKING=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
