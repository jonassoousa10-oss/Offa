package com.arenarush.demo;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
  Arena engine;
  GLView world;
  Overlay overlay;
  SoundFX sounds;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    sounds=new SoundFX(this);
    engine=new Arena();
    engine.stage=getPreferences(0).getInt("stage",0);
    engine.reset();
    world=new GLView(this);
    overlay=new Overlay(this);
    FrameLayout root=new FrameLayout(this);
    root.addView(world);
    root.addView(overlay);
    setContentView(root);
  }
  @Override protected void onPause() { super.onPause(); world.onPause(); }
  @Override protected void onResume() { super.onResume(); if(world!=null)world.onResume(); }
  class GLView extends GLSurfaceView {
    GLView(Context c) {
      super(c);
      setEGLContextClientVersion(2);
      setRenderer(engine);
      setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
      setPreserveEGLContextOnPause(true);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
      int a=event.getActionMasked();
      if (a==MotionEvent.ACTION_DOWN) {
        if(engine.mode==0) {engine.mode=1; sounds.play(2);}
        else if(engine.mode==2) {
          engine.stage=(engine.stage+1)%5;
          getPreferences(0).edit().putInt("stage",engine.stage).apply();
          queueEvent(()->{engine.reset();engine.mode=1;});
          sounds.play(2);
        } else if(engine.mode==3) {queueEvent(()->{engine.reset();engine.mode=1;});}
        engine.firing=true;
      } else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL) engine.firing=false;
      if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){
        engine.aim=Math.max(-4.2f,Math.min(4.2f,(event.getX()/Math.max(1,getWidth())-0.5f)*11f));
      }
      return true;
    }
  }
  class Overlay extends View {
    Paint p=new Paint(3);
    Overlay(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void label(Canvas c,String s,float x,float y,int size,int col,boolean center){
      p.setShader(null);p.setStyle(Paint.Style.FILL);p.setTypeface(android.graphics.Typeface.create("sans-serif-black",1));p.setTextSize(size);p.setColor(0x99001325);
      float tx=center?x-p.measureText(s)/2:x;
      c.drawText(s,tx+2,y+3,p);p.setColor(col);c.drawText(s,tx,y,p);
    }
    void round(Canvas c,int color,float l,float t,float r,float b,float rad) {
      p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,rad,rad,p);
    }
    @Override protected void onDraw(Canvas c) {
      super.onDraw(c);
      float w=getWidth(),h=getHeight(),s=Math.min(w/420f,h/790f);
      c.save();c.scale(s,s);
      float ww=w/s,hh=h/s;
      p.setShader(new LinearGradient(0,0,0,118,0xD908203A,0x0008203A,Shader.TileMode.CLAMP));c.drawRect(0,0,ww,118,p);p.setShader(null);
      label(c,"ARENA",20,43,32,0xFFFFFFFF,false);
      label(c,"RUSH 3D",21,66,16,0xFF58E4FF,false);
      round(c,0xC90B223B,ww-144,18,ww-16,77,15);
      label(c,"FASE "+(engine.stage+1)+"/5",ww-80,43,20,0xFFFFFFFF,true);
      label(c,"BASE "+Math.max(0,engine.baseHP)+" PV",ww-80,65,13,0xFFFFD685,true);
      round(c,0xC10A263A,16,hh-88,ww-16,hh-18,18);
      label(c,"AZUIS "+engine.blueCount,31,hh-57,17,0xFF72E6FF,false);
      label(c,"VERMELHOS "+engine.redCount,ww-30,hh-57,17,0xFFFF8883,true);
      label(c,"ARRASTE PARA MIRAR  •  SEGURE PARA ATIRAR",ww/2,hh-33,12,0xFFE9F5FF,true);
      if(engine.mode==1){
        for(int i=0;i<2;i++){
          float[] pos=engine.gatePoints[i];if(pos[2]>0){
            round(c,0xE71D5978,pos[0]/s-35,pos[1]/s-28,pos[0]/s+35,pos[1]/s+3,9);
            label(c,"x"+(i==0?"2":"3"),pos[0]/s,pos[1]/s-7,19,0xFFFFFFFF,true);
          }
        }
      }
      if(engine.mode!=1){
        round(c,0xD70B1B32,26,hh/2-124,ww-26,hh/2+120,24);
        String title=engine.mode==0?"PREPARE A TROPA":engine.mode==2?"VITÓRIA!":"BASE PERDIDA";
        int col=engine.mode==3?0xFFFF746C:0xFF66EFCB;
        label(c,title,ww/2,hh/2-56,29,col,true);
        label(c,engine.mode==0?"PORTAIS • SOLDADOS • CONQUISTA":engine.mode==2?"PARABÉNS, COMANDANTE!":"TENTE NOVA ESTRATÉGIA",ww/2,hh/2-20,13,0xFFE1EFF9,true);
        label(c,engine.mode==0?"5 FASES 3D PARA TESTAR":"FASE "+(engine.stage+1)+" CONCLUÍDA / JOGADA",ww/2,hh/2+13,15,0xFFFFFFFF,true);
        round(c,0xFF159FBB,52,hh/2+34,ww-52,hh/2+95,14);
        label(c,engine.mode==0?"TOQUE PARA COMEÇAR":engine.mode==2?"PRÓXIMA FASE  →":"TENTAR NOVAMENTE",ww/2,hh/2+74,18,Color.WHITE,true);
      }
      c.restore();
      postInvalidateDelayed(55);
    }
  }
  class SoundFX {
    final SoundPool pool;final int[] ids=new int[3]; final Random rand=new Random(12);
    SoundFX(Context c) {
      pool=new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
      for(int i=0;i<3;i++)try{
        File f=new File(c.getCacheDir(),"arena_fx_"+i+".wav");
        if(!f.exists()) makeWave(f,i);
        ids[i]=pool.load(f.getAbsolutePath(),1);
      }catch(Exception ex){}
    }
    void play(int id){if(id>=0&&id<ids.length&&ids[id]!=0)pool.play(ids[id],id==0?.19f:.55f,id==0?.19f:.55f,1,0,1);}
    void makeWave(File f,int type)throws Exception{
      int rate=16000,len= type==2?8500:2600;
      byte[] pcm=new byte[len*2];Random r=new Random(type+17);
      for(int i=0;i<len;i++){
        double t=i/(double)rate,k=i/(double)len;
        double frequency=type==0?760-420*k:type==1?160-120*k:460+480*k;
        double tone=Math.sin(2*Math.PI*(frequency*t+19*k*k));
        double noise=r.nextDouble()*2-1;
        double sample=type==0?tone*.73+noise*.27:type==1?tone*.27+noise*.73:tone;
        sample*=Math.pow(1-k,type==2?1:2);
        short a=(short)(sample*20000);pcm[i*2]=(byte)a;pcm[i*2+1]=(byte)(a>>8);
      }
      try(FileOutputStream o=new FileOutputStream(f)){
        int bytes=pcm.length;
        o.write(new byte[]{'R','I','F','F'});put4(o,36+bytes);o.write(new byte[]{'W','A','V','E','f','m','t',' '});
        put4(o,16);put2(o,1);put2(o,1);put4(o,rate);put4(o,rate*2);put2(o,2);put2(o,16);
        o.write(new byte[]{'d','a','t','a'});put4(o,bytes);o.write(pcm);
      }
    }
    void put4(FileOutputStream o,int n)throws Exception {for(int i=0;i<4;i++)o.write((n>>(i*8))&255);}
    void put2(FileOutputStream o,int n)throws Exception {o.write(n&255);o.write((n>>8)&255);}
  }
  static class Unit {
    float x,z,wiggle;int team;float age;
    Unit(float x,float z,int team){this.x=x;this.z=z;this.team=team;wiggle=x*8+z;}
  }
  static class Spark {
    float x,y,z,vx,vy,vz,life;int color;
    Spark(float x,float z,Random r,int col){this.x=x;this.y=.7f;this.z=z;vx=(r.nextFloat()-.5f)*7;vy=1+r.nextFloat()*6;vz=(r.nextFloat()-.5f)*7;life=.55f+r.nextFloat()*.35f;color=col;}
  }
  class Arena implements GLSurfaceView.Renderer {
    volatile float aim=0;
    volatile boolean firing=false;
    volatile int stage=0,mode=0,baseHP=8,playerHP=7,blueCount=0,redCount=0;
    volatile float[][] gatePoints={{0,0,0},{0,0,0}};
    ArrayList<Unit> blue=new ArrayList<>(),red=new ArrayList<>();
    ArrayList<Spark> fx=new ArrayList<>();
    Random rnd=new Random();
    float runTime=0,fireClock=0,enemyClock=0,shotFlash=0;
    int program,aPos,aNormal,uMVP,uColor; float[] view=new float[16],projection=new float[16],vp=new float[16],model=new float[16],mvp=new float[16];
    Mesh cube,sphere,cylinder;
    long lastFrame=0;int width=1,height=1;
    float[] BLU={.04f,.62f,1f},RED={1f,.22f,.22f},DARK={.13f,.22f,.32f},WHITE={.90f,.94f,1f};
    void reset(){
      blue.clear();red.clear();fx.clear();runTime=0;fireClock=0;enemyClock=0;shotFlash=0;
      baseHP=7+stage*4;playerHP=8;blueCount=0;redCount=0;mode=0;
    }
    @Override public void onSurfaceCreated(GL10 g,EGLConfig cfg){
      String vert="attribute vec3 aPos; attribute vec3 aNormal; uniform mat4 uMVP; varying float vLight; void main(){gl_Position=uMVP*vec4(aPos,1.0);vLight=clamp(0.67+dot(normalize(aNormal),normalize(vec3(-0.38,0.94,0.24)))*0.31,0.37,1.0);}";
      String frag="precision mediump float;uniform vec3 uColor;varying float vLight;void main(){gl_FragColor=vec4(uColor*vLight,1.0);}";
      int vs=shader(GLES20.GL_VERTEX_SHADER,vert),fs=shader(GLES20.GL_FRAGMENT_SHADER,frag);
      program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
      aPos=GLES20.glGetAttribLocation(program,"aPos");aNormal=GLES20.glGetAttribLocation(program,"aNormal");uMVP=GLES20.glGetUniformLocation(program,"uMVP");uColor=GLES20.glGetUniformLocation(program,"uColor");
      cube=Mesh.makeCube();sphere=Mesh.makeSphere(9,7);cylinder=Mesh.makeCylinder(11);
      GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDepthFunc(GLES20.GL_LEQUAL);
      GLES20.glDisable(GLES20.GL_CULL_FACE);
      lastFrame=System.nanoTime();
    }
    int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
    @Override public void onSurfaceChanged(GL10 g,int w,int h){
      width=w;height=h;GLES20.glViewport(0,0,w,h);
      Matrix.perspectiveM(projection,0,62,(float)w/h,.1f,145f);
      Matrix.setLookAtM(view,0,0,22,25,0,0,-12,0,1,0);
      Matrix.multiplyMM(vp,0,projection,0,view,0);
    }
    @Override public void onDrawFrame(GL10 gl){
      long now=System.nanoTime();float delta=Math.min(.042f,(now-lastFrame)/1e9f);lastFrame=now;
      if(mode==1) update(delta);
      GLES20.glClearColor(.55f,.80f,.96f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
      GLES20.glUseProgram(program);
      drawGround();
      drawScenery();
      drawFort();
      drawGate(-12,stage%2==0?2:3,-2.3f);
      drawGate(-12,3,2.3f);
      drawGate(-23,2,-2.3f);
      drawGate(-23,2+stage%2,2.3f);
      for(Unit u:red)drawSoldier(u);
      for(Unit u:blue)drawSoldier(u);
      drawCannon();
      for(Spark s:fx)draw(cube,s.x,s.y,s.z,.15f,.15f,.15f,0,s.color==0?new float[]{.2f,.8f,1}:new float[]{1,.69f,.1f});
      project(-2.3f,3,-12,gatePoints[0]);
      project(2.3f,3,-12,gatePoints[1]);
    }
    void update(float dt) {
      runTime+=dt;fireClock-=dt;enemyClock-=dt;shotFlash=Math.max(0,shotFlash-dt);
      if(firing&&fireClock<=0) {
        fireClock=.18f;
        blue.add(new Unit(aim,8,0));
        shotFlash=.08f;sounds.play(0);
      }
      int goal=30+stage*12;
      if(runTime<90 &&enemyClock<=0){
        enemyClock=Math.max(.3f,1.4f-stage*.13f);
        int count=2+stage/2;
        for(int i=0;i<count;i++)if(red.size()<35)red.add(new Unit((rnd.nextFloat()-.5f)*8,-34-rnd.nextFloat()*2,1));
      }
      ArrayList<Unit> copies=new ArrayList<>();
      for(int i=blue.size()-1;i>=0;i--){
        Unit u=blue.get(i);float prev=u.z;u.z-=dt*(6.8f+stage*.18f);u.age+=dt;
        for(int gate=0;gate<2;gate++){
          float gz=gate==0?-12:-23;
          if(prev>=gz&&u.z<gz){
            int multiply= (u.x<0?2:3);
            if(gate==1)multiply=u.x<0?2:2+stage%2;
            for(int extra=1;extra<multiply;extra++)if(blue.size()+copies.size()<85)
              copies.add(new Unit(Math.max(-4.3f,Math.min(4.3f,u.x+(rnd.nextFloat()-.5f)*.7f)),u.z,0));
            sounds.play(1);
          }
        }
        if(u.z<-34){
          blue.remove(i);baseHP--;burst(u.x,u.z,0);
          if(baseHP<=0){mode=2;sounds.play(2);firing=false;break;}
        }
      }
      if(mode!=1)return;
      blue.addAll(copies);
      for(int i=red.size()-1;i>=0;i--){
        Unit u=red.get(i);u.z+=dt*(3.2f+stage*.32f);u.age+=dt;
        if(u.z>10){
          red.remove(i);playerHP--;burst(u.x,9,1);
          if(playerHP<=0){mode=3;firing=false;sounds.play(1);break;}
        }
      }
      for(int i=blue.size()-1;i>=0;i--){
        Unit a=blue.get(i);boolean dead=false;
        for(int j=red.size()-1;j>=0;j--){
          Unit b=red.get(j);
          float dx=a.x-b.x,dz=a.z-b.z;
          if(dx*dx+dz*dz<.68f){
            red.remove(j);dead=true;burst(a.x,a.z,1);sounds.play(1);break;
          }
        }
        if(dead)blue.remove(i);
      }
      for(int i=fx.size()-1;i>=0;i--){
        Spark p=fx.get(i);p.life-=dt;p.x+=p.vx*dt;p.z+=p.vz*dt;p.y+=p.vy*dt;p.vy-=19*dt;
        if(p.life<=0||p.y<0)fx.remove(i);
      }
      blueCount=blue.size();redCount=red.size();
      if(blue.size()>85)blue.subList(0,blue.size()-85).clear();
    }
    void burst(float x,float z,int color){
      for(int k=0;k<7&&fx.size()<90;k++)fx.add(new Spark(x,z,rnd,color));
    }
    void drawGround(){
      draw(cube,0,-.35f,-15,64,.4f,90,0,new float[]{.34f,.67f,.48f});
      draw(cube,0,-.12f,-12,10,.22f,53,0,new float[]{.24f,.29f,.38f});
      draw(cube,0,-.02f,-12,.1f,.04f,50,0,new float[]{.44f,.59f,.61f});
      for(int z=-37;z<13;z+=4){
        draw(cube,-4.9f,.02f,z,.12f,.035f,1.8f,0,new float[]{.68f,.76f,.79f});
        draw(cube,4.9f,.02f,z,.12f,.035f,1.8f,0,new float[]{.68f,.76f,.79f});
        draw(cube,0,0,z,.08f,.04f,1.5f,0,new float[]{.35f,.42f,.50f});
      }
    }
    void drawScenery(){
      for(int n=0;n<16;n++) {
        float z=9-n*3.35f;float left= n%2==0?-9.3f:-8.0f; float right= n%2==1?9.3f:8.5f;
        tree(left,z,n);tree(right,z-1.6f,n+3);
      }
      for(int i=0;i<9;i++){
        float zz=-2-i*5;
        draw(cube,-11,.0f,zz,1.0f,.45f,1.2f, i*12,new float[]{.50f,.57f,.51f});
        draw(cube,11,.0f,zz-.7f,1.2f,.35f,.85f, -i*7,new float[]{.50f,.57f,.51f});
      }
    }
    void tree(float x,float z,int seed) {
      draw(cylinder,x,.55f,z,.42f,1.4f,.42f,0,new float[]{.51f,.35f,.24f});
      draw(sphere,x,1.7f,z,1.1f,1.25f,1.1f,0,new float[]{.08f,.48f+(seed%3)*.05f,.34f});
      draw(sphere,x-.38f,2.0f,z,.55f,.75f,.6f,0,new float[]{.12f,.60f,.38f});
    }
    void drawFort(){
      float bz=-37.2f;
      draw(cube,0,.5f,bz,8.0f,1.0f,3.2f,0,new float[]{.42f,.22f,.28f});
      draw(cube,0,2.0f,bz,6.4f,2.3f,2.1f,0,new float[]{.68f,.29f,.30f});
      for(int a=-1;a<=1;a+=2) {
        draw(cube,a*3.0f,3.1f,bz,1.6f,4.1f,1.7f,0,new float[]{.48f,.22f,.28f});
        draw(cube,a*3.0f,5.2f,bz,2.1f,.55f,2.2f,0,new float[]{.86f,.39f,.34f});
      }
      draw(cube,0,3.6f,bz,1.0f,1.0f,.3f,0,new float[]{.18f,.14f,.19f});
      for(int i=0;i<Math.max(0,baseHP);i++){
        float x=-3.8f+(i%8)*.99f,z=bz+2.3f+(i/8)*.7f;
        draw(cube,x,.30f,z,.48f,.45f,.45f,0,new float[]{.98f,.54f,.28f});
      }
    }
    void drawGate(float z,int multiplier,float x){
      float[] co=multiplier>=3?new float[]{.05f,.83f,.86f}:new float[]{.25f,.64f,1f};
      draw(cube,x-1.82f,1.55f,z,.28f,3.2f,.30f,0,co);
      draw(cube,x+1.82f,1.55f,z,.28f,3.2f,.30f,0,co);
      draw(cube,x,3.18f,z,4.05f,.38f,.33f,0,new float[]{1,.78f,.18f});
      draw(cube,x,3.70f,z,2.9f,.2f,.35f,0,new float[]{.90f,.90f,1f});
    }
    void drawCannon(){
      draw(cylinder,aim,.25f,11,1.35f,.65f,1.3f,0,DARK);
      draw(sphere,aim,.95f,11,.95f,.9f,1f,0,BLU);
      draw(cylinder,aim,1.08f,9.85f,.4f,.49f,2.3f,90,new float[]{.24f,.68f,.91f});
      draw(sphere,aim,1.08f,8.68f,.47f,.45f,.47f,0,new float[]{.13f,.26f,.36f});
      if(shotFlash>0)draw(sphere,aim,1.08f,8.24f,.65f,.65f,.65f,0,new float[]{1f,.92f,.25f});
    }
    void drawSoldier(Unit u){
      boolean enemy=u.team==1;float[] armor=enemy?RED:BLU;
      float bob=(float)Math.sin(u.age*12+u.wiggle)*.10f;
      draw(sphere,u.x,.10f,u.z,.6f,.05f,.4f,0,new float[]{.14f,.24f,.25f});
      draw(cube,u.x,1.02f+bob,u.z,.57f,.8f,.34f,0,armor);
      draw(sphere,u.x,1.65f+bob,u.z,.30f,.35f,.30f,0,new float[]{1,.74f,.54f});
      draw(cube,u.x,1.96f+bob,u.z,.41f,.15f,.46f,0,enemy?new float[]{.67f,.12f,.20f}:new float[]{.10f,.26f,.63f});
      float walk=(float)Math.sin(u.age*13+u.wiggle)*.21f;
      draw(cube,u.x-.16f,.38f+bob,u.z+walk,.17f,.68f,.20f,0,DARK);
      draw(cube,u.x+.16f,.38f+bob,u.z-walk,.17f,.68f,.20f,0,DARK);
      draw(cube,u.x-.41f,1.05f+bob,u.z-walk*.45f,.16f,.65f,.19f,0,armor);
      draw(cube,u.x+.41f,1.05f+bob,u.z+walk*.45f,.16f,.65f,.19f,0,armor);
      draw(cube,u.x,1.13f+bob,u.z+(enemy?-.20f:.20f),.42f,.18f,.12f,0,new float[]{.98f,.89f,.27f});
    }
    void draw(Mesh mesh,float x,float y,float z,float sx,float sy,float sz,float yaw,float[] rgb){
      Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);
      if(yaw!=0)Matrix.rotateM(model,0,yaw,0,1,0);
      Matrix.scaleM(model,0,sx,sy,sz);
      Matrix.multiplyMM(mvp,0,vp,0,model,0);
      GLES20.glUniformMatrix4fv(uMVP,1,false,mvp,0);
      GLES20.glUniform3fv(uColor,1,rgb,0);
      mesh.vertices.position(0);mesh.normals.position(0);
      GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,0,mesh.vertices);
      GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,0,mesh.normals);
      GLES20.glEnableVertexAttribArray(aPos);GLES20.glEnableVertexAttribArray(aNormal);
      GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,mesh.count);
    }
    void project(float x,float y,float z,float[] target){
      float[] point=new float[4]; Matrix.multiplyMV(point,0,vp,0,new float[]{x,y,z,1},0);
      if(point[3]<=0){target[2]=0;return;}
      target[0]=(point[0]/point[3]*.5f+.5f)*width;
      target[1]=(.5f-point[1]/point[3]*.5f)*height;
      target[2]=1;
    }
  }
  static class Mesh {
    FloatBuffer vertices,normals;int count;
    static Mesh of(ArrayList<Float> vv,ArrayList<Float> nn){
      Mesh mesh=new Mesh();mesh.count=vv.size()/3;
      ByteBuffer vb=ByteBuffer.allocateDirect(vv.size()*4).order(ByteOrder.nativeOrder());
      ByteBuffer nb=ByteBuffer.allocateDirect(nn.size()*4).order(ByteOrder.nativeOrder());
      mesh.vertices=vb.asFloatBuffer();mesh.normals=nb.asFloatBuffer();
      for(float f:vv)mesh.vertices.put(f);
      for(float f:nn)mesh.normals.put(f);
      mesh.vertices.position(0);mesh.normals.position(0);return mesh;
    }
    static void vertex(ArrayList<Float> v,ArrayList<Float> n,float x,float y,float z,float nx,float ny,float nz){
      v.add(x);v.add(y);v.add(z);n.add(nx);n.add(ny);n.add(nz);
    }
    static void tri(ArrayList<Float> v,ArrayList<Float> n,float[] a,float[] b,float[] c,float nx,float ny,float nz){
      vertex(v,n,a[0],a[1],a[2],nx,ny,nz);
      vertex(v,n,b[0],b[1],b[2],nx,ny,nz);
      vertex(v,n,c[0],c[1],c[2],nx,ny,nz);
    }
    static Mesh makeCube(){
      ArrayList<Float> v=new ArrayList<>(),n=new ArrayList<>();
      float[][][] faces={{{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1}},{{1,-1,-1},{-1,-1,-1},{-1,1,-1},{1,1,-1}},{{-1,1,1},{1,1,1},{1,1,-1},{-1,1,-1}},{{-1,-1,-1},{1,-1,-1},{1,-1,1},{-1,-1,1}},{{1,-1,1},{1,-1,-1},{1,1,-1},{1,1,1}},{{-1,-1,-1},{-1,-1,1},{-1,1,1},{-1,1,-1}}};
      float[][] norms={{0,0,1},{0,0,-1},{0,1,0},{0,-1,0},{1,0,0},{-1,0,0}};
      for(int i=0;i<6;i++){float[][] f=faces[i];float[] d=norms[i];tri(v,n,f[0],f[1],f[2],d[0],d[1],d[2]);tri(v,n,f[0],f[2],f[3],d[0],d[1],d[2]);}
      return of(v,n);
    }
    static Mesh makeSphere(int seg,int lat){
      ArrayList<Float> v=new ArrayList<>(),n=new ArrayList<>();
      for(int j=0;j<lat;j++) for(int k=0;k<seg;k++){
        float[] a=ball(k, j, seg,lat),b=ball(k+1,j,seg,lat),c=ball(k,j+1,seg,lat),d=ball(k+1,j+1,seg,lat);
        for(float[] x:new float[][]{a,b,c,b,d,c})vertex(v,n,x[0],x[1],x[2],x[0],x[1],x[2]);
      }
      return of(v,n);
    }
    static float[] ball(int k,int j,int seg,int lat){
      double a=Math.PI*j/lat,b=2*Math.PI*k/seg;
      return new float[]{(float)(Math.sin(a)*Math.cos(b)),(float)Math.cos(a),(float)(Math.sin(a)*Math.sin(b))};
    }
    static Mesh makeCylinder(int seg){
      ArrayList<Float> v=new ArrayList<>(),n=new ArrayList<>();
      for(int k=0;k<seg;k++){
        double a=k*Math.PI*2/seg,b=(k+1)*Math.PI*2/seg;
        float[] q={(float)Math.cos(a),(float)Math.sin(a)},r={(float)Math.cos(b),(float)Math.sin(b)};
        float[] aa={q[0],-.5f,q[1]},bb={r[0],-.5f,r[1]},cc={q[0],.5f,q[1]},dd={r[0],.5f,r[1]};
        for(float[] point:new float[][]{aa,bb,cc,bb,dd,cc})vertex(v,n,point[0],point[1],point[2],point[0],0,point[2]);
        tri(v,n,new float[]{0,.5f,0},cc,dd,0,1,0);
        tri(v,n,new float[]{0,-.5f,0},bb,aa,0,-1,0);
      }
      return of(v,n);
    }
  }
}
