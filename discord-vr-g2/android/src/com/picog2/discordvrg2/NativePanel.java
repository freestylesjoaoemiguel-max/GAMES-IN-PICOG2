package com.picog2.discordvrg2;

import android.graphics.*;
import java.util.*;

/** UI is drawn by Android Canvas into a GPU texture; all keyboard keys are separate 3D meshes. */
final class NativePanel {
 static final class Frame {Bitmap bitmap;ArrayList<SpatialView.Hit> hits=new ArrayList<>();}
 final Frame frame;final Canvas canvas;final Paint paint=new Paint(3);
 static final int BG=Color.rgb(25,29,42),PANEL=Color.rgb(20,25,37),TEXT=Color.rgb(218,214,239),MUTED=Color.rgb(138,151,175),PURPLE=Color.rgb(166,146,231),CYAN=Color.rgb(137,208,192);
 NativePanel(int quality){frame=new Frame();int w=new int[]{960,1280,1536}[quality];frame.bitmap=Bitmap.createBitmap(w,w*5/8,Bitmap.Config.ARGB_8888);canvas=new Canvas(frame.bitmap);canvas.scale(w/1280f,w/1280f);paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));}
 static Frame paint(MainActivity app){NativePanel p=new NativePanel(app.quality);p.draw(app);return p.frame;}
 void rect(float x,float y,float w,float h,int color,float radius){paint.setColor(color);canvas.drawRoundRect(x,y,x+w,y+h,radius,radius,paint);}
 void text(String value,float x,float y,int size,int color){paint.setColor(color);paint.setTextSize(size);canvas.drawText(value,x,y,paint);}
 void fit(String value,float x,float y,int size,int color,float maxWidth){paint.setTextSize(size);String shown=value;while(!shown.isEmpty()&&paint.measureText(shown)>maxWidth)shown=shown.substring(0,shown.length()-1);text(shown.equals(value)?shown:shown+"…",x,y,size,color);}
 int wrap(String text,float x,float y,float width,int font,int color,int maxLines){paint.setTextSize(font);paint.setColor(color);String line="";int rows=0;for(String word:text.replace('\n',' ').split(" ")){if(paint.measureText(line+word)>width&&!line.isEmpty()){canvas.drawText(line,x,y,paint);y+=font*1.42f;line="";if(++rows>=maxLines)return (int)y;}while(paint.measureText(word)>width&&word.length()>1){int cut=paint.breakText(word,true,width,null);canvas.drawText(word.substring(0,Math.max(1,cut)),x,y,paint);word=word.substring(Math.max(1,cut));y+=font*1.42f;if(++rows>=maxLines)return (int)y;}line+=word+" ";}canvas.drawText(line,x,y,paint);return(int)(y+font*1.42f);}
 void button(String label,float x,float y,float w,float h,String action,boolean active){rect(x,y,w,h,active?Color.rgb(79,64,115):Color.rgb(39,46,65),10);fit(label,x+16,y+h*.62f,21,active?Color.rgb(225,209,255):TEXT,w-36);frame.hits.add(new SpatialView.Hit(x,y,w,h,action));}
 void draw(MainActivity a){
  canvas.drawColor(BG);rect(0,0,1280,91,PANEL,0);text("◈  Discord VR G2",27,45,29,TEXT);text("INTERFACE INDEPENDENTE  /  ANDROID NATIVO  /  1.0.0",29,73,13,MUTED);
  button(a.connected?a.user:"Entrar no Discord",720,19,230,53,"connect",false);button("Configurações",966,19,210,53,"settings",false);button("←",1190,19,68,53,"back",false);
  rect(0,754,1280,46,Color.rgb(14,19,30),0);fit(a.status,22,783,17,CYAN,1235);
  if(a.page.equals("settings")){settings(a);return;}if(a.page.equals("connect")){connection(a);return;}if(a.page.equals("limits")){limits();return;}if(a.page.equals("search")){text("Pesquisar mensagens locais",50,155,31,TEXT);wrap("Digite com o teclado 3D ou um teclado físico. A pesquisa não acessa DMs nem histórico do Discord.",50,207,1100,22,MUTED,3);button(a.searchQuery.isEmpty()?"Pesquisar…":a.searchQuery,50,300,1140,70,"draft",true);button("Voltar à conversa",50,410,300,60,"back",false);return;}
  chat(a);
 }
 void chat(MainActivity a){
  rect(0,91,250,663,PANEL,0);text(a.remote?"SERVIDORES OAUTH2":"ESTAÇÃO LOCAL",23,133,17,PURPLE);
  button("◈  Espaço local",15,151,218,52,"local",!a.remote);
  if(a.connected){int first=a.guildOffset;for(int i=first;i<Math.min(first+3,a.guildNames.size());i++)button(a.guildNames.get(i),15,216+(i-first)*53,218,47,"guild:"+i,a.remote&&a.selectedGuild==i);}
  else {button("Amigos • API",15,218,218,47,"friends",false);button("Mensagens diretas",15,273,218,47,"direct",false);}
  if(a.connected&&a.guildNames.size()>3){button("←",15,376,103,26,"guildprev",false);button("→",130,376,103,26,"guildnext",false);}
  text("CANAIS",24,411,15,MUTED);
  String[] names={"geral","boas-vindas","ideias"};for(int i=0;i<3;i++)button("# "+names[i],15,428+i*55,218,48,"channel:"+names[i],!a.remote&&a.channel.equals(names[i]));
  button("Voz • não integrada",15,605,218,48,"voice",false);button("Sair da conta",15,675,218,48,"logout",false);
  fit("# "+a.channel,280,137,28,TEXT,520);button("↑",1040,103,65,45,"up",false);button("↓",1118,103,65,45,"down",false);button("⌕",1195,103,58,45,"search",false);
  text(a.remote?"INTEGRAÇÃO: SÓ SEUS ENVIOS, NÃO O HISTÓRICO DO CANAL":"MODO LOCAL • NÃO PUBLICA NO DISCORD",282,178,15,PURPLE);
  text("Entrada: toque / olhar / direcional. Pose do controle PICO não integrada.",282,205,16,MUTED);
  ArrayList<MainActivity.Message> messages=new ArrayList<>();for(MainActivity.Message m:(a.remote?a.remoteMessages:a.localMessages))if(a.searchQuery.isEmpty()||m.text.toLowerCase(java.util.Locale.ROOT).contains(a.searchQuery.toLowerCase(java.util.Locale.ROOT)))messages.add(m);
  if(!a.searchQuery.isEmpty())button("Limpar busca",840,102,185,46,"clearsearch",false);
  int end=Math.max(0,messages.size()-a.scroll),start=Math.max(0,end-3);float y=248;
  if(messages.isEmpty())wrap(a.remote?"OAuth2 básico fornece perfil e lista de servidores. Autorize um canal por webhook para publicar. Histórico geral, DMs e voz não estão habilitados.":"Seu espaço está pronto. Abra o teclado curvado e experimente escrever.",300,y,875,24,MUTED,5);
  for(int i=start;i<end;i++){MainActivity.Message m=messages.get(i);rect(280,y-22,43,43,Color.rgb(69,58,93),13);fit(m.author.substring(0,1),293,y+8,25,TEXT,25);fit(m.author,343,y,23,TEXT,650);text(a.remote?"INTEGRAÇÃO":"LOCAL",1100,y,13,PURPLE);wrap(m.text,343,y+36,870,21,MUTED,3);y+=127;}
  button(a.draft.isEmpty()?"Escreva uma mensagem…":a.draft,280,641,692,67,"draft",false);button(a.sending?"…":"Enviar",986,641,145,67,"send",true);button("☺",1144,641,49,67,"emoji",false);button("MIC",1201,641,64,67,"mic",false);
  button(a.keyboard?"⌨ Fechar teclado":"⌨ Teclado curvado",280,718,280,31,"keyboard",false);button("Recentrar",575,718,180,31,"recenter",false);fit(a.stereo?"ESTÉREO EXPERIMENTAL • não calibrado":"Java / OpenGL • sem WebView",790,741,14,MUTED,460);
 }
 void settings(MainActivity a){
  text("Seu espaço, do seu jeito.",40,144,32,TEXT);text("MEDIUM é o padrão. Mudanças visuais são locais ao aparelho.",40,181,20,MUTED);
  String[] sizes={"Pequena","Média","Grande","Gigante"},curves={"Plano","Suave","VR","Imersivo"};
  String[][] rows={{"Tela: "+sizes[a.size],"size","Distância: "+new String[]{"Próxima","Normal","Longe"}[a.distance],"distance"},{"Teclado: "+curves[a.curve],"curve","Tamanho teclado: "+String.format(java.util.Locale.ROOT,"%.1f",a.keyboardScale),"keyboardScale"},{"Painel: "+curves[a.panelCurve],"panelCurve","Sensibilidade: "+String.format(java.util.Locale.ROOT,"%.1f",a.sensitivity),"sensitivity"},{"Qualidade: "+new String[]{"LOW","MEDIUM","HIGH"}[a.quality],"quality","Velocidade cursor: "+String.format(java.util.Locale.ROOT,"%.1f",a.cursorSpeed),"cursorSpeed"},{"Cinema: "+(a.cinema?"sim":"não"),"cinema","Inverter eixo: "+(a.invert?"sim":"não"),"invert"},{"Orientação sensorial: "+(a.motion?"sim":"não"),"motion","Estéreo experimental: "+(a.stereo?"sim":"não"),"stereo"}};
  for(int i=0;i<rows.length;i++){button(rows[i][0],40,213+i*69,570,57,rows[i][1],false);button(rows[i][2],630,213+i*69,606,57,rows[i][3],false);}
  button("⊙ Recentrar",40,644,285,60,"recenter",true);button("Tela gigante",340,644,285,60,"giant",false);button("Limitações",640,644,285,60,"voice",false);button("Apagar local",940,644,295,60,"clear",false);
  text("Sensores Android ≠ runtime PICO. Não há correção de lentes, timewarp ou pose nativa do controle.",42,734,17,MUTED);
 }
 void connection(MainActivity a){
  text("DISCORD VR / PICO G2 EDITION",45,147,18,PURPLE);text("Conecte uma aplicação autorizada.",45,193,34,TEXT);
  wrap("O login abre a página oficial do Discord no navegador externo. Este app não recebe sua senha. Configure a origem HTTPS do servidor que acompanha o projeto.",45,244,1140,23,MUTED,3);
  text("SERVIDOR HTTPS (SEM CAMINHO)",45,367,16,PURPLE);button(a.serverInput.isEmpty()?"https://seu-servidor.example":a.serverInput,45,386,1000,68,"serverinput",false);button("Salvar",1060,386,170,68,"saveServer",true);
  button("Entrar no Discord",45,481,500,65,"login",true);button("Autorizar envio em um canal",565,481,665,65,"loginpost",false);
  wrap("Perfil e servidores: identify + guilds. Envio opcional: webhook.incoming, sempre identificado como integração. Não oferece histórico geral, DMs, lista de amigos ou chamadas de voz.",45,590,1130,22,MUTED,4);
  button("Continuar local",45,690,300,48,"local",false);button("Ver limitações",365,690,300,48,"voice",false);
 }
 void limits(){
  text("Integração transparente. Sem atalhos.",45,157,34,TEXT);
  String[] notes={"• OAuth2: perfil, lista básica de até 100 servidores e envio por webhook opcional autorizado.","• DMs, histórico geral, amigos e notificações Discord não estão habilitados nesta integração.","• Voz: não há chamada. O microfone é apenas um medidor local sem gravação/transmissão.","• PICO: este APK NÃO inclui o compositor, calibração de lentes ou pose do controle proprietário.","• Alternativas de entrada: toque, teclado físico, direcional e sensor Android experimental.","• O modo WebXR separado depende de navegador/headset compatível com a API.","• Aplicativo independente, não afiliado ao Discord ou à PICO."};
  float y=222;for(String note:notes)y=wrap(note,45,y,1160,23,MUTED,2)+24;
  button("Voltar ao espaço",45,683,370,57,"local",true);button("Configurações",440,683,370,57,"settings",false);
 }
}
