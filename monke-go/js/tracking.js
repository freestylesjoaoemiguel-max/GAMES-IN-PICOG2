// Orientation/mapping helpers shared by WebXR and legacy WebVR. No synthetic tracking.
(function(root){'use strict';
 function orientation(o){if(!o)return null;var v=Array.isArray(o)||ArrayBuffer.isView(o)?Array.from(o):[o.x,o.y,o.z,o.w];if(v.length!==4||!v.every(Number.isFinite))return null;var n=Math.hypot(v[0],v[1],v[2],v[3]);if(n<.00001)return null;return v.map(function(x){return x/n})}
 function choose(sources,hand,previous){var list=Array.from(sources||[]).filter(function(s){return s&&s.targetRayMode==='tracked-pointer'&&s.targetRaySpace});var match=list.filter(function(s){return hand!=='auto'&&s.handedness===hand});if(match.length)list=match;if(list.indexOf(previous)>=0)return previous;return list.find(function(s){return s.handedness==='right'})||list[0]||null}
 function controls(g,settings,selected){g=g||{};var b=g.buttons||[],a=g.axes||[],standard=g.mapping==='xr-standard';function down(i){return i>=0&&!!(b[i]&&(b[i].pressed||b[i].value>.5))}function touch(i){return i>=0&&!!(b[i]&&(b[i].touched||b[i].pressed))}function axis(i){return Number.isFinite(a[i])?Math.max(-1,Math.min(1,a[i])):0}
  var click=settings.padClick|0,trig=settings.padTrig|0,app=settings.padApp|0,offset=0;
  if(standard){trig=0;var pad=touch(2)||Math.abs(axis(0))+Math.abs(axis(1))>.01;var stick=!pad&&(touch(3)||Math.abs(axis(2))+Math.abs(axis(3))>.01);click=stick?3:2;offset=stick?2:0;app=4;}
  var x=axis(offset),y=axis(offset+1);if(settings.padSwap){var temp=x;x=y;y=temp}if(settings.padInvertY)y=-y;
  return {trigger:down(trig)||!!selected,click:down(click),touch:touch(click)||Math.abs(x)+Math.abs(y)>.01,x:x,y:y,menu:app!==click&&app!==trig&&down(app),pressed:b.map(function(v,i){return down(i)?i:-1}).filter(function(i){return i>=0}),count:b.length,axes:a.length,mapping:standard?'xr-standard':'configurável'};
 }
 var api={orientation:orientation,choose:choose,controls:controls};root.TRACKING=api;if(typeof module!=='undefined'&&module.exports)module.exports=api;
})(typeof window!=='undefined'?window:globalThis);
