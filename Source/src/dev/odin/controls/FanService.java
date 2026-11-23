package dev.odin.controls;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.UUID;
import java.util.concurrent.*;

/** User-started session. The root-side lease returns to OEM automatic control on loss. */
public final class FanService extends Service {
 static final java.util.concurrent.atomic.AtomicLong appliedRevision=new java.util.concurrent.atomic.AtomicLong();
 public static volatile String status="No fan session running";
 public static volatile boolean running=false;
 static volatile String lastExitReason="";
 static volatile FanService instance;static boolean closingNow(){return instance!=null&&instance.closing;}
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private volatile boolean closing=false;
 private String token;
 private String failure="";
 private ScheduledFuture<?> ticker;
 private boolean sessionExtended;
 static boolean validTemperatureSource(String source){
  return source.matches("[0-9]{1,3}")||("Odin3".equals(Build.MODEL)&&"cpu-average".equals(source));
 }
 @Override public void onCreate(){
  super.onCreate();instance=this;
  NotificationManager nm=getSystemService(NotificationManager.class);
  ControlNotifications.ensure(this);
 }
 private Notification notice(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FanService.class).setAction("stock").putExtra("mode",4),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return ControlNotifications.status(this,ControlNotifications.FAN,"Raven System Control · fan session",status).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Use Smart",stop).build()).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  startForeground(1,notice());
  if(intent!=null&&!intent.getBooleanExtra("gameOverride",false))GameProfileSession.globalChoice(this,"fan");
  if(intent==null||"stop".equals(intent.getAction())){finishSession();return START_NOT_STICKY;}
  if("stock".equals(intent.getAction())){finishSession("stock-fan "+intent.getIntExtra("mode",4));return START_NOT_STICKY;}
  if(closing)return START_NOT_STICKY;
  if(!getSharedPreferences("profile",0).getBoolean("fanControlEnabled",false)){status="Enable fan control in Guided Setup first";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
  int manual=intent.getIntExtra("manual",55);
  String kind="manual".equals(intent.getStringExtra("kind"))?"manual":"curve",points=intent.getStringExtra("points");
  final String name=intent.getStringExtra("profileName")==null?"Custom fan":intent.getStringExtra("profileName");
  try{boolean extended=getSharedPreferences("profile",0).getBoolean("extendedFanRange",false);CurvePoints.parse(points,extended);FanCurve.manual(manual,extended);}
  catch(Exception e){status=e.getMessage();if(!running)stopSelf();return START_NOT_STICKY;}

  final boolean fromTile=intent.getBooleanExtra("fromTile",false);
  worker.execute(()->{
   try{
    if(closing)return;
    lastExitReason="";
    Root.run(this,"probe");
    String source=Root.run(this,"oem-sensor").substring(3).trim();if(!validTemperatureSource(source))throw new IllegalArgumentException("OEM temperature sensor unavailable");
    // Recheck consent on the worker too: a queued tile action must not retain
    // a revoked extended-range preference.
    final boolean extended=getSharedPreferences("profile",0).getBoolean("extendedFanRange",false);CurvePoints.parse(points,extended);FanCurve.manual(manual,extended);
    final String configuration=kind+" "+source+" 45 50 75 50 70 "+manual+" "+points+" "+(extended?"extended":"standard")+"\n";
    try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null),"curve.cfg"))){out.write(configuration.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    boolean update=token!=null&&running;if(!update)token=UUID.randomUUID().toString().replace("-","");
    ControlNotifications.clear(this,ControlNotifications.FAN_ALERT);
    status=Root.run(this,(update?"update2 ":"start2 ")+token).substring(3);running=true;sessionExtended=extended;
    if(sessionExtended&&!getSharedPreferences("profile",0).getBoolean("extendedFanRange",false)){finishSession("stock-fan 4");return;}
    getSharedPreferences("profile",0).edit().putString("activeFan",name).putString("lastFanProfile",name).apply();if(fromTile)getSharedPreferences("profile",0).edit().putString("tileFanSelection",name).apply();DeviceState.refreshTiles(this);
    getSharedPreferences("state",0).edit().putString("session",token).apply();appliedRevision.incrementAndGet();
    if(ticker==null)ticker=worker.scheduleWithFixedDelay(this::heartbeat,1,3,TimeUnit.SECONDS);
   }catch(Exception e){failure="Could not apply: "+e.getMessage();status=failure;finishSession();}
  });
  return START_NOT_STICKY;
 }
 private void heartbeat(){

     if(closing)return;
     if(sessionExtended&&!getSharedPreferences("profile",0).getBoolean("extendedFanRange",false)){finishSession("stock-fan 4");return;}
     try{String sample=Root.run(this,"lease "+token);if(sample.length()>3)status=sample.substring(3);getSystemService(NotificationManager.class).notify(1,notice());}
     catch(Exception e){failure="System cooling resumed or session ended: "+e.getMessage();status=failure;finishSession();}
    
 }
 private void finishSession(){finishSession("stop");}
 private synchronized void finishSession(String request){
  if(closing)return;closing=true;running=false;
  if(ticker!=null)ticker.cancel(false);
  worker.execute(()->{
   boolean confirmed=false;String reason="";
   try{Root.run(this,request);confirmed=true;appliedRevision.incrementAndGet();if(request.equals("stock-fan 1")||request.equals("stock-fan 4")||request.equals("stock-fan 5")){String mode=request.substring(request.length()-1);getSharedPreferences("profile",0).edit().putString("lastFanProfile",DeviceState.stockName(mode)+" · stock").apply();}}
   catch(Exception e){android.util.Log.e("RavenFan","Fan recovery was not confirmed",e);}
   try{reason=Root.read("cat /data/local/odin-controls/last-result").trim();}catch(Exception ignored){}
   lastExitReason=token==null?"":reason;
   boolean recoveryFailed=FanRecoveryOutcome.needsAlert(confirmed,reason);
   if(recoveryFailed)status=FanRecoveryOutcome.failureStatus();
   else status=failure.startsWith("Could not apply:")?failure:FanRecoveryOutcome.successStatus(request,reason);
   if(!failure.isEmpty())android.util.Log.i("RavenFan","Session ended: "+failure+"; recovery="+reason+"; confirmed="+confirmed);
   getSharedPreferences("state",0).edit().remove("session").apply();
   getSharedPreferences("profile",0).edit().remove("activeFan").apply();DeviceState.refreshTiles(this);
   if(recoveryFailed)ControlNotifications.recovery(this,ControlNotifications.FAN_ALERT,"Raven · Fan recovery",status);
   else ControlNotifications.clear(this,ControlNotifications.FAN_ALERT);
   stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();worker.shutdown();
  });
 }
 @Override public void onDestroy(){if(instance==this)instance=null;if(!closing)finishSession();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
