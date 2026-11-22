package dev.odin.controls;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.service.quicksettings.TileService;

/** Android watches the stock settings even when Raven is not in memory. */
public final class TileSyncJob extends JobService {
 private static final int JOB_ID=147;
 private static final Uri FAN_MODE=Settings.System.getUriFor("fan_mode");
 private static final Uri CPU_MODE=Settings.System.getUriFor("performance_mode");

 static void ensureScheduled(Context context){
  JobScheduler scheduler=context.getSystemService(JobScheduler.class);
  JobInfo previous=scheduler.getPendingJob(JOB_ID);
  if(previous!=null&&previous.getTriggerContentUris()!=null)for(JobInfo.TriggerContentUri uri:previous.getTriggerContentUris())if(CPU_MODE.equals(uri.getUri()))return;
  schedule(context,scheduler);
 }

 private static boolean schedule(Context context,JobScheduler scheduler){
  JobInfo.Builder job=new JobInfo.Builder(JOB_ID,new ComponentName(context,TileSyncJob.class))
   .addTriggerContentUri(new JobInfo.TriggerContentUri(FAN_MODE,0))
   .addTriggerContentUri(new JobInfo.TriggerContentUri(CPU_MODE,0))
   .setTriggerContentUpdateDelay(500)
   .setTriggerContentMaxDelay(1000);
  for(String key:DeviceState.LED_KEYS){
   job.addTriggerContentUri(new JobInfo.TriggerContentUri(Settings.System.getUriFor(key),0));
  }
  return scheduler.schedule(job.build())==JobScheduler.RESULT_SUCCESS;
 }

 @Override public boolean onStartJob(JobParameters params){
  Uri[] changed=params.getTriggeredContentUris();
  boolean fan=changed==null,led=changed==null,cpu=changed==null;
  if(changed!=null){
   for(Uri uri:changed){
    if(FAN_MODE.equals(uri))fan=true;
    else if(CPU_MODE.equals(uri))cpu=true;
    else led=true;
   }
  }
  if(fan)TileService.requestListeningState(this,new ComponentName(this,FanTile.class));
  if(led)TileService.requestListeningState(this,new ComponentName(this,LedTile.class));
  if(cpu)TileService.requestListeningState(this,new ComponentName(this,CpuTile.class));
  // Replace the running job to carry forward changes that arrived during it.
  return schedule(this,getSystemService(JobScheduler.class));
 }

 @Override public boolean onStopJob(JobParameters params){return true;}
}
