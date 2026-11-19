package dev.odin.controls;

import android.app.Application;

public final class RavenApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  TileSyncJob.ensureScheduled(this);
  if(android.os.Build.MODEL.equals("Odin3")){
   new Thread(()->{
    try{Root.run(this,"performance-bootstrap");String packed=SegmentSession.packed(this);String mask=DeviceState.get(this,"joystick_light_enabled");if(!packed.isEmpty()&&mask.matches("[01],[01]")&&!mask.equals("0,0"))Root.run(this,"light-segments "+packed+" "+mask);}
    catch(Exception error){android.util.Log.e("RavenPerformance","Performance recovery check failed",error);}
   },"PerformanceRecoveryCheck").start();
  }
 }
}
