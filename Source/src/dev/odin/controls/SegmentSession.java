package dev.odin.controls;
import android.content.*;
/** Ownership is valid only while the stock settings still match Raven's last write. */
final class SegmentSession {
 static final String DARK="#000000,#000000";
 static volatile boolean writing;
 static void captureStock(Context c){android.content.SharedPreferences p=c.getSharedPreferences("profile",0);String colors=DeviceState.get(c,"joystick_led_light_picker_color");if(!p.contains("segmentStockColors")||!owned(c)&&!DARK.equals(colors))p.edit().putString("segmentStockColors",colors).commit();}
 static boolean owned(Context c){android.content.SharedPreferences p=c.getSharedPreferences("profile",0);return p.contains("segmentPacked")&&DeviceState.ledSignature(c).equals(p.getString("segmentSignature",""));}
 static String packed(Context c){return owned(c)?c.getSharedPreferences("profile",0).getString("segmentPacked",""):"";}
 static void remember(Context c,String packed){String signature=DeviceState.ledSignature(c);c.getSharedPreferences("profile",0).edit().putString("segmentPacked",packed).putString("segmentSignature",signature).putString("ledObserved",signature).commit();ControlNotifications.clear(c,ControlNotifications.LED_ALERT);ensureService(c);}
 // Cold tile binding is not always a foreground-service exemption. Retain ownership
 // on denial; retry from a user click/activity or Android's sticky service restart.
 static void ensureService(Context c){
  if(SegmentService.active||!c.getSharedPreferences("profile",0).contains("segmentPacked"))return;
  try{c.startForegroundService(new Intent(c,SegmentService.class));}
  catch(android.app.ForegroundServiceStartNotAllowedException e){android.util.Log.i("RavenSegments","LED recovery deferred until foreground-service start is allowed");}
 }
 static void forget(Context c){android.content.SharedPreferences p=c.getSharedPreferences("profile",0);android.content.SharedPreferences.Editor edit=p.edit().remove("segmentPacked").remove("segmentSignature").remove("segmentStockColors");if(p.contains("segmentPacked"))edit.putString("activeLED","System LEDs").putString("ledObserved",DeviceState.ledSignature(c));edit.commit();c.stopService(new Intent(c,SegmentService.class));}
}


