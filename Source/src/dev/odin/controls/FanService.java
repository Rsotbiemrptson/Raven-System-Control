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
  PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,FanService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,"fan").setSmallIcon(getResources().getIdentifier("ic_notification","drawable",getPackageName())).setContentTitle("Raven System Control · fan session")
   .setContentText(status).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop & restore",stop).build()).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int id){
  startForeground(1,notice());
  if(intent==null||"stop".equals(intent.getAction())){finishSession();return START_NOT_STICKY;}
  if(closing)return START_NOT_STICKY;
  if(running||token!=null)return START_NOT_STICKY;
  if(!notificationsAvailable(this)){status="Fan session not started: enable fan control notifications";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
  int off=intent.getIntExtra("off",45),on=intent.getIntExtra("on",50),full=intent.getIntExtra("full",75);
  int low=intent.getIntExtra("low",50),high=intent.getIntExtra("high",70),manual=intent.getIntExtra("manual",55),zone=intent.getIntExtra("zone",-1);
  String kind="manual".equals(intent.getStringExtra("kind"))?"manual":"curve";
  try{FanCurve.validate(off,on,full,low,high);FanCurve.manual(manual);if(zone<0||zone>999)throw new IllegalArgumentException("Choose a checked temperature sensor");}
  catch(Exception e){status=e.getMessage();stopSelf();return START_NOT_STICKY;}
  token=UUID.randomUUID().toString().replace("-","");
  final String args="start "+token+" "+kind+" "+zone+" "+off+" "+on+" "+full+" "+low+" "+high+" "+manual;
  status="Starting fan session…";
  worker.execute(()->{
   try{
    Root.run(this,"probe");
    if(closing)return;
    status=Root.run(this,args).substring(3);running=true;
    getSharedPreferences("state",0).edit().putString("session",token).apply();
    ticker=worker.scheduleWithFixedDelay(()->{
     if(closing)return;
     try{status=Root.run(this,"lease "+token).substring(3);getSystemService(NotificationManager.class).notify(1,notice());}
     catch(Exception e){failure="Session ended: "+e.getMessage();status=failure;finishSession();}
    },1,3,TimeUnit.SECONDS);
   }catch(Exception e){failure="Could not start: "+e.getMessage();status=failure;finishSession();}
  });
  return START_NOT_STICKY;
 }
 private synchronized void finishSession(){
  if(closing)return;closing=true;running=false;
  if(ticker!=null)ticker.cancel(false);
  worker.execute(()->{
   try{String stopped=Root.run(this,"stop").substring(3);status=failure.isEmpty()?stopped:failure+" · "+stopped;}
   catch(Exception e){status="Stop could not be confirmed. Root lease fallback: "+e.getMessage();}
   getSharedPreferences("state",0).edit().remove("session").apply();
   stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();worker.shutdown();
  });
 }
 @Override public void onDestroy(){if(!closing)finishSession();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
