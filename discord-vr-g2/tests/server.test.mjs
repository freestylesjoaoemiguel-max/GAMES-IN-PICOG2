import test from 'node:test';import assert from 'node:assert/strict';import {createApp} from '../server/app.mjs';
const origin='https://test.example';
async function fixture(configured=true){
 const sent=[];
 const transport=async(url,options={})=>{
  sent.push({url,options});
  if(url.endsWith('/oauth2/token'))return Response.json({access_token:'mock-access-only-for-tests',expires_in:3600,webhook:{id:'111',token:'mock-webhook',guild_id:'222',channel_id:'333'}});
  if(url.endsWith('/users/@me'))return Response.json({id:'444',username:'Test user',global_name:'Test user'});
  if(url.includes('/users/@me/guilds'))return Response.json([{id:'222',name:'Authorized test guild'}]);
  if(url.includes('/webhooks/'))return Response.json({id:'555'});
  if(url.endsWith('/token/revoke'))return Response.json({});
  throw new Error('Unexpected mock request');
 };
 const app=createApp(configured?{origin,clientId:'test-client',secret:'test-secret'}:{},transport);
 const server=app.listen(0,'127.0.0.1');await new Promise(resolve=>server.on('listening',resolve));
 const base=`http://127.0.0.1:${server.address().port}`;
 return {app,server,base,sent,close:async()=>{app.locals.dispose();await new Promise(resolve=>server.close(resolve));}};
}
async function login(f,post=true){
 const start=await fetch(f.base+`/auth/start${post?'?post=1':''}`,{redirect:'manual'});const url=new URL(start.headers.get('location'));
 const cookie=start.headers.get('set-cookie').split(';')[0];
 const finish=await fetch(f.base+'/auth/callback?code=mock-code&state='+url.searchParams.get('state'),{headers:{Cookie:cookie},redirect:'manual'});
 const combined=finish.headers.get('set-cookie');const session=/dvg_session=([^; ,]+)/.exec(combined)[1];
 return {cookie:`dvg_session=${session}`,state:url.searchParams.get('state'),oauthCookie:cookie};
}
test('unconfigured login is explicit, not fake',async()=>{const f=await fixture(false);try{assert.equal((await fetch(f.base+'/auth/start')).status,503);assert.equal((await(await fetch(f.base+'/api/config')).json()).configured,false);}finally{await f.close();}});
test('OAuth state is bound to a secure browser cookie',async()=>{const f=await fixture();try{const start=await fetch(f.base+'/auth/start',{redirect:'manual'});assert(start.headers.get('set-cookie').includes('HttpOnly; Secure; SameSite=Lax'));const state=new URL(start.headers.get('location')).searchParams.get('state');assert.equal((await fetch(f.base+'/auth/callback?code=mock&state='+state)).status,400);assert.equal(f.sent.length,0);}finally{await f.close();}});
test('basic OAuth returns only approved public data, no secrets',async()=>{const f=await fixture();try{const auth=await login(f,false),res=await fetch(f.base+'/api/me',{headers:{Cookie:auth.cookie}}),s=await res.json();assert.equal(s.user.name,'Test user');assert.equal(s.posting,null);assert.equal(s.guilds.length,1);assert(!JSON.stringify(s).includes('mock-access'));assert.equal(s.capabilities.voice,false);assert.equal(s.capabilities.readMessages,false);}finally{await f.close();}});
test('OAuth state cannot be replayed',async()=>{const f=await fixture();try{const auth=await login(f);assert.equal((await fetch(f.base+'/auth/callback?code=again&state='+auth.state,{headers:{Cookie:auth.oauthCookie},redirect:'manual'})).status,400);}finally{await f.close();}});
test('no unauthenticated messages',async()=>{const f=await fixture();try{assert.equal((await fetch(f.base+'/api/messages',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'})).status,401);}finally{await f.close();}});
test('message posting is channel-bound, attributed and mention-safe',async()=>{const f=await fixture();try{const auth=await login(f);const headers={Cookie:auth.cookie,Origin:origin,'Content-Type':'application/json'};let r=await fetch(f.base+'/api/messages',{method:'POST',headers,body:JSON.stringify({channelId:'999',requestId:'request01',content:'test'})});assert.equal(r.status,403);r=await fetch(f.base+'/api/messages',{method:'POST',headers,body:JSON.stringify({channelId:'333',requestId:'request01',content:'hello @everyone'})});assert.equal(r.status,200);const sent=f.sent.find(x=>x.url.includes('/webhooks/'));const body=JSON.parse(sent.options.body);assert(body.content.includes('via Discord VR G2'));assert.deepEqual(body.allowed_mentions,{parse:[]});assert(body.username.includes('integração'));}finally{await f.close();}});
test('repeat request id is idempotent',async()=>{const f=await fixture();try{const auth=await login(f);const options={method:'POST',headers:{Cookie:auth.cookie,Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({channelId:'333',requestId:'repeat001',content:'once'})};assert.equal((await fetch(f.base+'/api/messages',options)).status,200);assert.equal((await fetch(f.base+'/api/messages',options)).status,200);assert.equal(f.sent.filter(x=>x.url.includes('/webhooks/')).length,1);}finally{await f.close();}});
test('cross-site logout/message requests are rejected',async()=>{const f=await fixture();try{const auth=await login(f);assert.equal((await fetch(f.base+'/api/logout',{method:'POST',headers:{Cookie:auth.cookie,Origin:'https://evil.example','Content-Type':'application/json'},body:'{}'})).status,403);}finally{await f.close();}});
test('logout deletes session and attempts revocation',async()=>{const f=await fixture();try{const auth=await login(f);const r=await fetch(f.base+'/api/logout',{method:'POST',headers:{Cookie:auth.cookie,Origin:origin,'Content-Type':'application/json'},body:'{}'});assert.equal(r.status,200);assert.equal((await fetch(f.base+'/api/me',{headers:{Cookie:auth.cookie}})).status,401);assert(f.sent.some(x=>x.url.endsWith('/token/revoke')));}finally{await f.close();}});
test('native pairing requires its private polling credential and is single use',async()=>{const f=await fixture();try{const pair=await(await fetch(f.base+'/api/pair',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"post":true}'})).json();assert.equal((await fetch(f.base+'/api/pair/'+pair.id)).status,404);const start=await fetch(f.base+'/auth/start?pair='+pair.id,{redirect:'manual'});const state=new URL(start.headers.get('location')).searchParams.get('state');await fetch(f.base+'/auth/callback?code=mock&state='+state,{headers:{Cookie:start.headers.get('set-cookie').split(';')[0]}});const result=await fetch(f.base+'/api/pair/'+pair.id,{headers:{Authorization:'Bearer '+pair.poll}});const data=await result.json();assert(data.token);assert.equal(data.posting.channel_id,'333');assert.equal((await fetch(f.base+'/api/pair/'+pair.id,{headers:{Authorization:'Bearer '+pair.poll}})).status,404);}finally{await f.close();}});
