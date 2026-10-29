package dev.odin.controls;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.util.concurrent.*;

/** Keeps a tile-initiated LED write alive until the privileged helper returns. */
public final class LedProfileService extends Service {
 private static final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
 private static final java.util.concurrent.atomic.AtomicInteger profilePending=new java.util.concurrent.atomic.AtomicInteger();
 static boolean isApplying(){return pending.get()>0;}
 static boolean isProfileApplying(){return profilePending.get()>0;}
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 @Override public void onCreate(){super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("led-apply","LED profile changes",NotificationManager.IMPORTANCE_LOW));}
 @Override public int onStartCommand(Intent i,int flags,int id){
  startForeground(2,new Notification.Builder(this,"led-apply").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("Raven LED").setContentText("Applying profile…").build());
  if(i==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(id);return START_NOT_STICKY;}
  final String name=i.getStringExtra("profileName"),power=i.getStringExtra("power"),group=i.getStringExtra("group");final boolean enabled=i.getBooleanExtra("enabled",false);pending.incrementAndGet();if(group==null)profilePending.incrementAndGet();worker.execute(()->{String error=null;try{android.content.SharedPreferences p=getSharedPreferences("profile",0);
   if(group!=null){applyGroup(p,group,enabled);}
   else if("off".equals(power)){boolean retainGroup=DeviceState.ledSignature(this).equals(p.getString("ledGroupObserved",""));String before=DeviceState.led(this);if(!before.equals("Off"))p.edit().putString("lastLEDProfile",before).apply();Root.run(this,"light-off");DeviceState.ownLED(this,"Off");if(retainGroup)p.edit().putString("ledGroupObserved",DeviceState.ledSignature(this)).apply();}
   else{String chosen=name;if("on".equals(power))chosen=p.getString("lastLEDProfile",DeviceState.led(this));String saved=p.getString("led:"+chosen,null);String selected=chosen;if(saved==null&&"on".equals(power)){saved=DeviceState.ledSignature(this).equals(p.getString("ledGroupObserved",""))?p.getString("ledGroupConfig",p.getString("draft","{}")):p.getString("draft","{}");selected="Current LEDs";}if(saved==null)throw new Exception("LED profile is no longer available");Root.run(this,ProfileActions.ledRequest(new JSONObject(saved),Build.MODEL.equals("Odin2")));DeviceState.ownLED(this,selected.equals("Current LEDs")?"Custom LEDs":selected);p.edit().putString("tileLEDSelection",selected).putString("tileLEDConfig",saved).apply();}
  }catch(Exception e){error=e.getMessage();}finally{pending.decrementAndGet();if(group==null)profilePending.decrementAndGet();}final String message=error;if(message!=null)new Handler(Looper.getMainLooper()).post(()->android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show());if(stopSelfResult(id))stopForeground(STOP_FOREGROUND_REMOVE);});return START_NOT_STICKY;

 }
 private void applyGroup(android.content.SharedPreferences p,String group,boolean enabled)throws Exception{
  if(!Build.MODEL.equals("Odin2")||!(group.equals("analog")||group.equals("side")))throw new IllegalArgumentException("Unsupported LED group");
  boolean analog=ProfileActions.groupOn(this,true),side=ProfileActions.groupOn(this,false);
  if(group.equals("analog"))analog=enabled;else side=enabled;
  String before=DeviceState.led(this),signature=DeviceState.ledSignature(this);
  String selected=before.equals("Off")?p.getString("lastLEDProfile","Current LEDs"):before;
  String saved=p.getString("led:"+selected,null);
  if(saved==null&&signature.equals(p.getString("ledGroupObserved","")))saved=p.getString("ledGroupConfig",null);
  if(saved==null)saved=p.getString("draft","{}");
  JSONObject config=new JSONObject(saved);
  if(before.equals("System LEDs")){String color=DeviceState.get(this,"led_light_picker_color"),level=DeviceState.get(this,"led_light_brightness_percent");if(!color.equals("null")&&!level.equals("null")){String hex=String.format(java.util.Locale.ROOT,"%06X",Long.parseLong(color)&0xffffff);int percent=Math.max(0,Math.min(100,Math.round(Float.parseFloat(level)*100)));for(String key:new String[]{"left","right","sideLeft","sideRight"})config.put(key,hex);config.put("lightBrightness",new JSONArray(new int[]{percent,percent,percent,percent}));}}
  config.put("sideLights",side?"on":"off");
  long started=SystemClock.elapsedRealtime();Root.run(this,ProfileActions.ledGroupRequest(config,analog));
  android.util.Log.d("RavenLED","Direct group command completed in "+(SystemClock.elapsedRealtime()-started)+" ms");
  DeviceState.ownLEDGroups(this,analog,side);
  p.edit().putString("ledGroupObserved",DeviceState.ledSignature(this)).putString("ledGroupConfig",config.toString())
   .putString("tileLEDSelection","Current LEDs").putString("tileLEDConfig",config.toString()).apply();
 }
 @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
