package dev.odin.controls;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.UUID;
import java.util.concurrent.*;

/** User-started session. The root-side lease returns to OEM automatic control on loss. */
public final class FanService extends Service {
 public static volatile String status="No fan session running";
 public static volatile boolean running=false;
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private volatile boolean closing=false;
 private String token;
 private String failure="";
 private ScheduledFuture<?> ticker;
 static boolean notificationsAvailable(Context c){
  NotificationManager nm=c.getSystemService(NotificationManager.class);
  NotificationChannel channel=nm.getNotificationChannel("fan");
  return nm.areNotificationsEnabled()&&channel!=null&&channel.getImportance()!=NotificationManager.IMPORTANCE_NONE;
 }
 @Override public void onCreate(){
  super.onCreate();
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("fan","Fan control",NotificationManager.IMPORTANCE_LOW));
 }
 private Notification notice(){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FanService.class).setAction("stock").putExtra("mode",4),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,"fan").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("Raven System Control · fan session")
   .setContentText(status).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Use Smart",stop).build()).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  startForeground(1,notice());
  if(intent==null||"stop".equals(intent.getAction())){finishSession();return START_NOT_STICKY;}
  if("stock".equals(intent.getAction())){finishSession("stock-fan "+intent.getIntExtra("mode",4));return START_NOT_STICKY;}
  if(closing)return START_NOT_STICKY;
  if(!getSharedPreferences("profile",0).getBoolean("fanControlEnabled",false)){status="Enable fan control in Guided Setup first";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
  if(!notificationsAvailable(this)){status="Fan profile not applied: enable fan control notifications";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
  int manual=intent.getIntExtra("manual",55),zone=0;
  String kind="manual".equals(intent.getStringExtra("kind"))?"manual":"curve",points=intent.getStringExtra("points");
  final String name=intent.getStringExtra("profileName")==null?"Custom fan":intent.getStringExtra("profileName");
  try{CurvePoints.parse(points);FanCurve.manual(manual);if(zone<0||zone>999)throw new IllegalArgumentException("Choose a checked temperature sensor");}
  catch(Exception e){status=e.getMessage();if(!running)stopSelf();return START_NOT_STICKY;}

  final boolean fromTile=intent.getBooleanExtra("fromTile",false);
  worker.execute(()->{
   try{
    if(closing)return;
    Root.run(this,"probe");
    String source=Root.run(this,"oem-sensor").substring(3).trim();if(!source.matches("[0-9]{1,3}"))throw new IllegalArgumentException("OEM temperature sensor unavailable");
    final String configuration=kind+" "+source+" 45 50 75 50 70 "+manual+" "+points+"\n";
    try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null),"curve.cfg"))){out.write(configuration.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    boolean update=token!=null&&running;if(!update)token=UUID.randomUUID().toString().replace("-","");
    status=Root.run(this,(update?"update2 ":"start2 ")+token).substring(3);running=true;
    getSharedPreferences("profile",0).edit().putString("activeFan",name).putString("lastFanProfile",name).apply();if(fromTile)getSharedPreferences("profile",0).edit().putString("tileFanSelection",name).apply();DeviceState.refreshTiles(this);
    getSharedPreferences("state",0).edit().putString("session",token).apply();
    if(ticker==null)ticker=worker.scheduleWithFixedDelay(()->{
     if(closing)return;
     try{status=Root.run(this,"lease "+token).substring(3);getSystemService(NotificationManager.class).notify(1,notice());}
     catch(Exception e){failure="System cooling resumed or session ended: "+e.getMessage();status=failure;finishSession();}
    },1,3,TimeUnit.SECONDS);
   }catch(Exception e){failure="Could not apply: "+e.getMessage();status=failure;finishSession();}
  });
  return START_NOT_STICKY;
 }
 private void finishSession(){finishSession("stop");}
 private synchronized void finishSession(String request){
  if(closing)return;closing=true;running=false;
  if(ticker!=null)ticker.cancel(false);
  worker.execute(()->{
   try{String stopped=Root.run(this,request).substring(3);status=failure.isEmpty()?stopped:failure+" · "+stopped;if(request.equals("stock-fan 1")||request.equals("stock-fan 4")||request.equals("stock-fan 5")){String mode=request.substring(request.length()-1);getSharedPreferences("profile",0).edit().putString("lastFanProfile",DeviceState.stockName(mode)+" · stock").apply();}}
   catch(Exception e){status="Stop could not be confirmed. Root lease fallback: "+e.getMessage();}
   getSharedPreferences("state",0).edit().remove("session").apply();
   getSharedPreferences("profile",0).edit().remove("activeFan").apply();DeviceState.refreshTiles(this);
   stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();worker.shutdown();
  });
 }
 @Override public void onDestroy(){if(!closing)finishSession();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
