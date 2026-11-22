package dev.odin.controls;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.ContextThemeWrapper;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The tile is immediately usable; vendor mode is refreshed away from the UI thread. */
public final class CpuTile extends TileService {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private AlertDialog popup;private boolean listening,destroyed,busy;
 private int actualMode=-1,modeGeneration;private boolean probing;
 private final Runnable poll=new Runnable(){public void run(){probeMode();if(listening)ui.postDelayed(this,1000);}};
 private final ContentObserver observer=new ContentObserver(ui){@Override public void onChange(boolean self){modeChanged();}};
 private final SharedPreferences.OnSharedPreferenceChangeListener changed=(p,key)->ui.post(()->{if(key==null||key.startsWith("performance"))modeChanged();else update();});
 private void modeChanged(){modeGeneration++;actualMode=-1;update();probeMode();}
 private int readMode(){
  Process child=null;int result=-1;
  try{child=new ProcessBuilder("/system/bin/getprop","persist.vendor.debug.mode").redirectErrorStream(true).start();
   if(child.waitFor(300,java.util.concurrent.TimeUnit.MILLISECONDS)&&child.exitValue()==0)try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream()))){result=CpuTileStatus.mode(reader.readLine());}
  }catch(Exception ignored){}finally{if(child!=null)child.destroyForcibly();}
  if(result<0)try{result=CpuTileStatus.mode(Root.read("getprop persist.vendor.debug.mode").trim());}catch(Exception ignored){}
  return result;
 }
 private void probeMode(){
  if(probing||destroyed||!Build.MODEL.equals("Odin3"))return;
  probing=true;final int epoch=modeGeneration;
  worker.execute(()->{final int mode=readMode();ui.post(()->{probing=false;if(destroyed)return;if(epoch==modeGeneration){actualMode=mode;update();}else if(listening||(popup!=null&&popup.isShowing()))probeMode();});});
 }
 static void refresh(android.content.Context c){TileService.requestListeningState(c,new android.content.ComponentName(c,CpuTile.class));}
 private String current(){
  SharedPreferences p=getSharedPreferences("profile",0);
  int selected=-1;String name=null;
  if(PerformanceOwnership.active(this))try{
   org.json.JSONObject config=new org.json.JSONObject(p.getString("performanceActiveConfig","{}"));
   selected=config.optInt("mode",-1);name=p.getString("performanceActiveName",null);
  }catch(Exception ignored){}
  return CpuTileStatus.label(actualMode,PerformanceOwnership.active(this),selected,name);
 }
 private void update(){
  Tile t=getQsTile();if(t==null||destroyed)return;
  boolean supported=Build.MODEL.equals("Odin3");String name=supported?current():"Requires Odin 3";
  t.setIcon(Icon.createWithResource(this,getResources().getIdentifier("ic_tile_cpu","drawable",getPackageName())));
  t.setLabel("Raven CPU");t.setSubtitle(name);t.setContentDescription("Raven CPU: "+name);
  t.setState(supported?Tile.STATE_ACTIVE:Tile.STATE_UNAVAILABLE);t.updateTile();
  if(popup!=null&&popup.isShowing()&&!busy){ArrayList<String> names=ProfileBridgeProvider.names(this,"cpu");int selected=names.indexOf(name);popup.getListView().clearChoices();if(selected>=0)popup.getListView().setItemChecked(selected,true);}
 }
 @Override public void onTileAdded(){update();probeMode();}
 @Override public void onStartListening(){if(!listening){listening=true;getContentResolver().registerContentObserver(Settings.System.getUriFor("performance_mode"),false,observer);getSharedPreferences("profile",0).registerOnSharedPreferenceChangeListener(changed);}modeChanged();ui.removeCallbacks(poll);poll.run();}
 @Override public void onStopListening(){ui.removeCallbacks(poll);if(listening){listening=false;getContentResolver().unregisterContentObserver(observer);getSharedPreferences("profile",0).unregisterOnSharedPreferenceChangeListener(changed);}}
 @Override public void onClick(){if(!Build.MODEL.equals("Odin3"))return;if(isLocked())unlockAndRun(this::open);else open();}
 private void open(){if(ShadeProfileBridge.open(this,"cpu"))return;fallback();}
 private void fallback(){
  if(popup!=null&&popup.isShowing())return;
  boolean light=getSharedPreferences("profile",0).getString("appearanceMode","dark").equals("light");
  ContextThemeWrapper theme=new ContextThemeWrapper(this,getResources().getIdentifier(light?"AppThemeLight":"AppTheme","style",getPackageName()));
  ArrayList<String> names=ProfileBridgeProvider.names(this,"cpu");
  popup=new AlertDialog.Builder(theme).setTitle("Raven CPU").setSingleChoiceItems(names.toArray(new String[0]),names.indexOf(current()),(dialog,index)->{
   if(busy)return;busy=true;popup.getListView().setEnabled(false);
   worker.execute(()->{String error=null;try{if(!getSharedPreferences("profile",0).getBoolean("setupCompleteV2",false))throw new IllegalStateException("Complete Guided Setup in Raven System Control first.");ProfileBridgeProvider.applyCpu(this,names.get(index));}catch(Exception e){error=e.getMessage()==null?"Could not apply CPU profile":e.getMessage();}final String result=error;ui.post(()->{busy=false;if(destroyed)return;if(result!=null)Toast.makeText(this,result,Toast.LENGTH_LONG).show();if(popup!=null&&popup.isShowing())popup.getListView().setEnabled(true);modeChanged();});});
  }).create();
  popup.setCanceledOnTouchOutside(true);
  popup.setOnShowListener(d->{popup.getWindow().setBackgroundDrawable(new ColorDrawable(light?0xffFFFFFF:0xff303034));popup.getWindow().setLayout(Math.min(Math.round(400*getResources().getDisplayMetrics().density),getResources().getDisplayMetrics().widthPixels-Math.round(32*getResources().getDisplayMetrics().density)),-2);});
  showDialog(popup);
 }
 @Override public void onDestroy(){destroyed=true;onStopListening();if(popup!=null)popup.dismiss();ui.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
}
