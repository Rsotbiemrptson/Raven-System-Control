package dev.odin.controls;

import android.app.Application;

public final class RavenApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  TileSyncJob.ensureScheduled(this);
  if(android.os.Build.MODEL.equals("Odin3")){
   new Thread(()->{
    try{Root.run(this,"performance-bootstrap");}
    catch(Exception error){android.util.Log.e("RavenPerformance","Performance recovery check failed",error);}
   },"PerformanceRecoveryCheck").start();
  }
 }
}
