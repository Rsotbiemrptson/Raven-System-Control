package dev.odin.controls;

import android.app.Application;

public final class RavenApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  ControlNotifications.ensure(this);
  TileSyncJob.ensureScheduled(this);
  if(android.os.Build.MODEL.equals("Odin2"))ZoneSession.ensure(this);
  if(android.os.Build.MODEL.equals("Odin3")){
   new Thread(()->{
    try{Root.run(this,"performance-bootstrap");SegmentSession.ensureService(this);}
    catch(Exception error){android.util.Log.e("RavenPerformance","Performance recovery check failed",error);}
   },"PerformanceRecoveryCheck").start();
  }
 }
}

