import express from 'express';
import crypto from 'node:crypto';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const API='https://discord.com/api/v10';
const opaque=()=>crypto.randomBytes(32).toString('base64url');
const parseCookies=req=>Object.fromEntries((req.headers.cookie||'').split(';').map(x=>x.trim().split('=')).filter(x=>x.length===2));
const cookie=(name,value,age)=>`${name}=${value}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=${age}`;
export function createApp(config={}, transport=fetch) {
 const app=express(); app.disable('x-powered-by');
 const origin=config.origin||'', clientId=config.clientId||'', secret=config.secret||'';
 const configured=!!(origin.startsWith('https://') && clientId && secret);
 const pending=new Map(), sessions=new Map(), pairs=new Map(), limits=new Map();
 const expired=(entry)=>!entry||Date.now()>entry.until;
 function sweep(){for(const map of [pending,sessions,pairs,limits]) for(const [key,value] of map) if(expired(value)) map.delete(key);}
 const timer=setInterval(sweep,60_000); timer.unref();
 app.use((req,res,next)=>{
  res.setHeader('Referrer-Policy','no-referrer'); res.setHeader('X-Content-Type-Options','nosniff');
  // Embedding is allowed for Arena's preview; OAuth redirects still require the configured origin.
  res.setHeader('Permissions-Policy','xr-spatial-tracking=(self), microphone=(self)');
  res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'");
  next();
 });
 app.use(express.json({limit:'12kb'}));
 app.use('/api',(req,res,next)=>{res.setHeader('Cache-Control','no-store');next();});
 function rate(req,res,next){
  const key=req.socket.remoteAddress||'unknown',now=Date.now(); let x=limits.get(key);
  if(!x||expired(x)){x={count:0,until:now+60_000};limits.set(key,x);}
  if(++x.count>240) return res.status(429).json({error:'Muitas requisições. Aguarde um minuto.'}); next();
 }
 app.use('/api',rate); app.use('/auth',rate);
 function sameOrigin(req,res,next){
  if(req.headers.origin && req.headers.origin!==origin) return res.status(403).json({error:'Origem não autorizada.'});
  if(req.headers['sec-fetch-site']==='cross-site') return res.status(403).json({error:'Requisição entre sites bloqueada.'}); next();
 }
 function session(req){const token=req.headers.authorization?.replace(/^Bearer /,'')||parseCookies(req).dvg_session;const s=sessions.get(token);return expired(s)?null:s;}
 function guard(req,res,next){req.login=session(req);if(!req.login)return res.status(401).json({error:'Entre novamente no Discord.'});next();}
 function publicState(s){return {user:s.user,guilds:s.guilds,posting:s.posting?{guild_id:s.posting.guild_id,channel_id:s.posting.channel_id}:null,history:s.history,capabilities:{readMessages:false,voice:false,friends:false,notifications:false,sendWebhook:!!s.posting}};}
 async function discord(url,options={}){
  const response=await transport(url,{...options,signal:AbortSignal.timeout(12000)});
  let data;try{data=await response.json();}catch{data={};}
  if(!response.ok){const error=new Error(response.status===429?'Discord limitou a frequência. Aguarde e tente novamente.':'A operação não foi aceita pelo Discord.'); error.status=response.status===429?429:502;throw error;}
  return data;
 }
 app.get('/api/config',(req,res)=>res.json({configured,scopes:['identify','guilds'],optionalScope:'webhook.incoming',nativePicoRuntime:false,limitations:['Histórico geral, DMs e notificações Discord não estão disponíveis nesta integração.','Amigos e voz exigem integração/aprovação adicional; não estão habilitados.','Envio real somente via webhook expressamente autorizado, identificado como integração.']}));
 app.post('/api/pair',sameOrigin,(req,res)=>{
  if(!configured)return res.status(503).json({error:'Configure o aplicativo OAuth2 e o servidor HTTPS primeiro.'});
  if(pairs.size>=500)return res.status(503).json({error:'Servidor ocupado.'});
  const id=opaque(),poll=opaque();pairs.set(id,{poll,post:req.body.post===true,until:Date.now()+300_000});
  res.json({id,poll,url:`${origin}/auth/start?pair=${id}`,expires_in:300});
 });
 app.get('/api/pair/:id',(req,res)=>{
  const p=pairs.get(req.params.id),authorization=req.headers.authorization;
  if(expired(p)||authorization!==`Bearer ${p.poll}`) return res.status(404).json({error:'Pareamento inválido ou expirado.'});
  if(!p.session)return res.json({pending:true});
  const s=sessions.get(p.session);pairs.delete(req.params.id);
  res.json({token:p.session,...publicState(s)});
 });
 app.get('/auth/start',(req,res)=>{
  if(!configured)return res.status(503).type('text').send('Login não configurado. Use Explorar interface local. Nenhuma senha é solicitada por este aplicativo.');
  if(pending.size>=500)return res.status(503).send('Servidor ocupado.');
  const p=req.query.pair?pairs.get(req.query.pair):null;
  if(req.query.pair&&expired(p))return res.status(400).send('Pareamento expirado.');
  const state=opaque(),binding=opaque(),post=p?p.post:req.query.post==='1';
  pending.set(state,{binding,pair:req.query.pair||null,post,until:Date.now()+300_000});
  res.setHeader('Set-Cookie',cookie('dvg_oauth',binding,300));
  const q=new URLSearchParams({client_id:clientId,response_type:'code',redirect_uri:`${origin}/auth/callback`,scope:`identify guilds${post?' webhook.incoming':''}`,state,prompt:'consent'});
  res.redirect(`https://discord.com/oauth2/authorize?${q}`);
 });
 app.get('/auth/callback',async(req,res,next)=>{
  const {code,state}=req.query; const attempt=pending.get(state);
  if(expired(attempt)||attempt.binding!==parseCookies(req).dvg_oauth)return res.status(400).send('Estado OAuth inválido/expirado. Recomece o login.');
  pending.delete(state);res.setHeader('Set-Cookie',cookie('dvg_oauth','',0));
  if(req.query.error||typeof code!=='string')return res.status(400).send('Autorização cancelada. Pode retornar ao aplicativo.');
  try{
   const tokens=await discord('https://discord.com/api/oauth2/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({client_id:clientId,client_secret:secret,grant_type:'authorization_code',code,redirect_uri:`${origin}/auth/callback`})});
   if(typeof tokens.access_token!=='string')throw new Error('Resposta OAuth incompleta.');
   const headers={Authorization:`Bearer ${tokens.access_token}`};
   const [u,g]=await Promise.all([discord(`${API}/users/@me`,{headers}),discord(`${API}/users/@me/guilds?limit=100`,{headers})]);
   const token=opaque(), s={until:Date.now()+Math.min(Number(tokens.expires_in)||3600,3600)*1000,access:tokens.access_token,user:{id:u.id,name:u.global_name||u.username},guilds:g.map(x=>({id:x.id,name:x.name})),history:[],requests:new Map(),posting:null,busy:false,lastPost:0};
   const w=tokens.webhook;
   // Construct a fixed Discord endpoint. Never use an arbitrary webhook URL from client input.
   if(attempt.post&&w&&/^\d+$/.test(w.id)&&/^\d+$/.test(w.channel_id)&&typeof w.token==='string')s.posting={id:w.id,token:w.token,guild_id:w.guild_id,channel_id:w.channel_id};
   sessions.set(token,s);
   if(attempt.pair){const p=pairs.get(attempt.pair);if(!expired(p))p.session=token;res.type('html').send('<meta name="viewport" content="width=device-width"><h1>Conexão autorizada</h1><p>Volte ao Discord VR G2. O aplicativo receberá a conexão. Nenhuma senha foi entregue ao app.</p>');}
   else {res.setHeader('Set-Cookie',[cookie('dvg_oauth','',0),cookie('dvg_session',token,3600)]);res.redirect('/?connected=1');}
  }catch(e){next(e);}
 });
 app.get('/api/me',guard,(req,res)=>res.json(publicState(req.login)));
 app.post('/api/messages',sameOrigin,guard,async(req,res,next)=>{
  const s=req.login, {content,requestId,channelId}=req.body;
  if(!s.posting||channelId!==s.posting.channel_id)return res.status(403).json({error:'Nenhum webhook autorizado para este canal.'});
  if(typeof content!=='string'||!content.trim()||content.length>1800||typeof requestId!=='string'||!/^[a-zA-Z0-9_-]{8,80}$/.test(requestId))return res.status(400).json({error:'Mensagem inválida (máximo 1800 caracteres).'});
  if(s.requests.has(requestId))return res.json(s.requests.get(requestId));
  if(s.busy||Date.now()-s.lastPost<1500)return res.status(429).json({error:'Aguarde antes de enviar novamente.'});
  s.busy=true;s.lastPost=Date.now();
  try{
   const safeName=s.user.name.replace(/[\r\n*_~`<>@]/g,'').slice(0,60);
   const result=await discord(`${API}/webhooks/${s.posting.id}/${encodeURIComponent(s.posting.token)}?wait=true`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({content:`${safeName} · via Discord VR G2\n${content.trim()}`,username:'Discord VR G2 • integração',allowed_mentions:{parse:[]}})});
   const message={id:result.id,author:s.user.name,content:content.trim(),time:new Date().toISOString(),kind:'webhook'};
   s.history.push(message);s.history=s.history.slice(-80);s.requests.set(requestId,message);if(s.requests.size>100)s.requests.delete(s.requests.keys().next().value);
   res.json(message);
  }catch(e){next(e);}finally{s.busy=false;}
 });
 app.post('/api/logout',sameOrigin,async(req,res)=>{
  const token=req.headers.authorization?.replace(/^Bearer /,'')||parseCookies(req).dvg_session,s=sessions.get(token);sessions.delete(token);
  res.setHeader('Set-Cookie',cookie('dvg_session','',0));
  let revoked=true;
  if(s){try{await discord('https://discord.com/api/oauth2/token/revoke',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({client_id:clientId,client_secret:secret,token:s.access})});}catch{revoked=false;}}
  res.json({ok:true,revoked,note:'Sessão removida. Webhooks autorizados podem permanecer no servidor Discord; remova-os em Configurações do servidor → Integrações.'});
 });
 app.use('/vendor/three',express.static(path.join(root,'node_modules/three'),{index:false}));
 app.use(express.static(path.join(root,'web')));
 app.use((err,req,res,next)=>{res.status(err.status||502).json({error:err.type==='entity.too.large'?'Dados muito grandes.':err.status?err.message:'Falha na conexão. Nenhuma operação foi confirmada; confira antes de repetir.'});});
 app.locals.dispose=()=>clearInterval(timer);
 return app;
}
