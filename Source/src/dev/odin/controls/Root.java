package dev.odin.controls;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Only the observed OEM protocol. No global SELinux changes or filesystem remounts. */
final class Root {
 static synchronized String call(String command)throws Exception {
  if(command.getBytes(StandardCharsets.UTF_8).length>192)throw new IOException("Request too long");
  Class<?> sm=Class.forName("android.os.ServiceManager");
  IBinder binder=(IBinder)sm.getMethod("getService",String.class).invoke(null,"PServerBinder");
  if(binder==null)throw new IOException("AYN privileged service is unavailable to this app");
  Parcel in=Parcel.obtain(),out=Parcel.obtain();
  try {in.writeStringArray(new String[]{command,"1"});
   if(!binder.transact(0,in,out,0))throw new IOException("OEM service rejected the request");
   byte[] bytes=out.createByteArray();if(bytes==null)throw new IOException("OEM service returned no result");
   return new String(bytes,StandardCharsets.UTF_8).trim();
  }finally{in.recycle();out.recycle();}
 }
 static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
 static synchronized File helper(Context c)throws Exception {
  File dir=c.getExternalFilesDir(null);
  if(dir==null)throw new IOException("App storage unavailable");
  for(String name:new String[]{"performance-guard.sh","performance.sh","hardware.sh","ring-segments","odin2-zones"}){
   File file=new File(dir,name),temp=new File(dir,name+".tmp");
   try(InputStream in=c.getAssets().open(name);OutputStream out=new FileOutputStream(temp)){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
   }
   if(!temp.renameTo(file))throw new IOException("Could not stage hardware helper");
  }
  return new File(dir,"hardware.sh");
 }
 static synchronized String run(Context c,String args)throws Exception {
  if(ZoneSession.handles(c,args))return ZoneSession.run(c,args);
  return runDirect(c,args);
 }
 static synchronized String runDirect(Context c,String args)throws Exception {
  String retained=SegmentSession.packed(c);
  if(!retained.isEmpty()&&args.startsWith("light-ring-enable ")){
   String[] fields=args.split(" ");String mask=DeviceState.get(c,"joystick_light_enabled");
   if(fields.length!=3||!fields[1].matches("left|right|both")||!fields[2].matches("[01]")||!mask.matches("[01],[01]"))throw new IOException("Invalid ring power request");
   mask=fields[1].equals("left")?fields[2]+","+mask.charAt(2):fields[1].equals("right")?mask.charAt(0)+","+fields[2]:fields[2]+","+fields[2];
   args="light-segments "+retained+" "+mask;
  }
  if(args.startsWith("light-segments ")){String[] fields=args.split(" ");if(fields.length!=3)throw new IOException("Invalid segment request");args="light-segments "+fields[1]+" "+SegmentColors.effectiveMask(fields[1],fields[2]);}
  File h=helper(c);
  if(args.startsWith("light-ring "))restoreStockColors(c,h);
  if(args.startsWith("light-segments "))SegmentSession.captureStock(c);
  String command="sh "+q(h.getPath())+" "+args;
  // The OEM command bridge returns only one stdout line. Encode the structured
  // performance readout through the existing read path to retain every field.
    String previous=SegmentSession.packed(c);
  String previousMask=DeviceState.get(c,"joystick_light_enabled");
  boolean led=args.startsWith("light")||args.equals("restore-light");
  if(led)SegmentSession.writing=true;
  String result;
  try{
   result=args.equals("performance-status")?read(command):call(command);
   if(result.startsWith("OK")&&args.startsWith("light-ring-restore "))SystemClock.sleep(300);
   if(result.startsWith("OK")&&led){
    if(args.startsWith("light-segments "))SegmentSession.remember(c,args.split(" ")[1]);
    else if(!previous.isEmpty()&&(args.startsWith("light-ring-enable ")||args.equals("light-off"))){
     String mask=DeviceState.get(c,"joystick_light_enabled");
     if(!mask.equals(previousMask)&&!mask.equals("0,0"))SystemClock.sleep(250);
     String replay=call("sh "+q(h.getPath())+" light-segments "+previous+" "+mask);
     if(!replay.startsWith("OK"))throw new IOException(replay);
     SegmentSession.remember(c,previous);
    }else if(!args.equals("light-segments-probe")&&!args.equals("light-segments-sleep"))SegmentSession.forget(c);
   }
  }catch(Exception e){
   if(args.startsWith("light-segments ")){try{restoreStockColors(c,h);SegmentSession.forget(c);}catch(Exception recovery){e.addSuppressed(recovery);}}
   throw e;
  }finally{if(led)SegmentSession.writing=false;}
  if(!result.startsWith("OK")&&args.startsWith("light-segments ")){restoreStockColors(c,h);SegmentSession.forget(c);}
  if(!result.startsWith("OK"))throw new IOException(result.isEmpty()?"No helper response; privileged access may be restricted":result);
  return result;
 }
 // Restore only our dark fallback; never replace an explicit stock color change.
 static void restoreStockColors(Context c,File h)throws Exception {
  String colors=c.getSharedPreferences("profile",0).getString("segmentStockColors",null);
  if(colors==null)return;
  String result=call("sh "+q(h.getPath())+" light-stock-colors "+q(colors));
  if(!result.startsWith("OK"))throw new IOException(result);
 }
 static synchronized void releaseSegments(Context c){
  if(SegmentSession.owned(c))return;
  SegmentSession.writing=true;
  try{restoreStockColors(c,helper(c));SegmentSession.forget(c);DeviceState.refreshTiles(c);}
  catch(Exception e){android.util.Log.e("RavenSegments","Cannot restore stock color; original retained",e);ControlNotifications.recovery(c,ControlNotifications.LED_ALERT,"Raven · LED recovery","Stock lighting could not be restored. The original settings are retained; check LEDs.");}
  finally{SegmentSession.writing=false;}
 }
 static synchronized void resumeSegments(Context c)throws Exception {
  if(!c.getSystemService(PowerManager.class).isInteractive())return;
  String packed=SegmentSession.packed(c),mask=DeviceState.get(c,"joystick_light_enabled");
  if(!packed.isEmpty()&&mask.matches("[01],[01]")&&!mask.equals("0,0"))run(c,"light-segments "+packed+" "+mask);
 }
 static synchronized void sleepSegments(Context c)throws Exception {
  if(!c.getSystemService(PowerManager.class).isInteractive()&&SegmentSession.owned(c))run(c,"light-segments-sleep");
 }
 static String read(String command)throws Exception {
  String s=call("("+command+") 2>&1 | head -c 24000 | base64 -w 0");
  return new String(android.util.Base64.decode(s,android.util.Base64.DEFAULT),StandardCharsets.UTF_8);
 }
}



