package com.picog2.pulsesaber;
import org.json.*;
import java.util.*;
/** Pure scoring rules: one controller, no simultaneous two-hand notes. */
final class RhythmGame {
 static final float[] LANES={-.65f,0,.65f},ROWS={1.15f,1.8f};
 static final float[][] DIRECTIONS={{0,1},{0,-1},{-1,0},{1,0}};
 static final class Note{float time;int lane,row,dir;boolean bomb,judged;Note(JSONObject n){time=(float)n.optDouble("t");lane=n.optInt("lane");row=n.optInt("row");dir=n.optInt("dir");bomb=n.optString("kind").equals("bomb");}}
 final ArrayList<Note> notes=new ArrayList<>();final float duration,window;final boolean assist,practice;int score,combo,bestCombo,hits,misses,total,earned;float health=100,time;boolean finished;String last="";Note lastHit;
 RhythmGame(JSONObject chart,int difficulty,boolean assist,boolean practice)throws Exception{this.assist=assist;this.practice=practice;duration=(float)chart.getDouble("duration");window=new float[]{.2f,.16f,.13f}[difficulty];JSONArray ns=chart.getJSONObject("charts").getJSONArray(new String[]{"easy","normal","expert"}[difficulty]);for(int i=0;i<ns.length();i++){Note n=new Note(ns.getJSONObject(i));notes.add(n);if(!n.bomb)total++;}}
 void update(float t){if(finished)return;time=t;for(Note n:notes)if(!n.judged&&t>n.time+window){n.judged=true;if(!n.bomb){misses++;combo=0;health=Math.max(0,health-7);last="PERDEU";}}if(t>=duration||(!practice&&health<=0))finished=true;}
 static float distance(float px,float py,float ax,float ay,float bx,float by){float x=bx-ax,y=by-ay,d=x*x+y*y,t=d>0?Math.max(0,Math.min(1,((px-ax)*x+(py-ay)*y)/d)):0;return(float)Math.hypot(px-ax-t*x,py-ay-t*y);}
 int cut(float ax,float ay,float bx,float by,float t,boolean force){if(finished)return 0;float dx=bx-ax,dy=by-ay,travel=(float)Math.hypot(dx,dy);if(travel>1.6||(!force&&travel<.025))return 0;Note hit=null;for(Note n:notes)if(!n.judged&&Math.abs(n.time-t)<=window&&distance(LANES[n.lane],ROWS[n.row],ax,ay,bx,by)<=.24f&&(hit==null||Math.abs(n.time-t)<Math.abs(hit.time-t)))hit=n;if(hit==null)return 0;Note n=hit;if(n.bomb){n.judged=true;combo=0;health=Math.max(0,health-15);last="BOMBA";lastHit=n;return -1;}float[] dir=DIRECTIONS[n.dir];if(!assist&&(force||travel<.025||(dx*dir[0]+dy*dir[1])/travel<.45)){last="SIGA A SETA";return 0;}n.judged=true;float q=Math.max(0,1-Math.abs(n.time-t)/window);int points=Math.round(70+30*q);combo++;bestCombo=Math.max(bestCombo,combo);hits++;earned+=points;score+=points*Math.min(8,1+combo/8);health=Math.min(100,health+2);last=q>.7?"PERFEITO":q>.35?"ÓTIMO":"BOM";lastHit=n;return 1;}
 float accuracy(){return total>0?earned/(total*100f)*100:0;}
 String rank(){float p=accuracy();return p>=95?"S":p>=85?"A":p>=70?"B":p>=50?"C":"D";}
}
