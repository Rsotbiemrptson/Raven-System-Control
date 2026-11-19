package dev.odin.controls;
import android.content.*;
/** Ownership is valid only while the stock settings still match Raven's last write. */
final class SegmentSession {
 static volatile boolean writing;
 static boolean owned(Context c){android.content.SharedPreferences p=c.getSharedPreferences("profile",0);return p.contains("segmentPacked")&&DeviceState.ledSignature(c).equals(p.getString("segmentSignature",""));}
 static String packed(Context c){return owned(c)?c.getSharedPreferences("profile",0).getString("segmentPacked",""):"";}
 static void remember(Context c,String packed){c.getSharedPreferences("profile",0).edit().putString("segmentPacked",packed).putString("segmentSignature",DeviceState.ledSignature(c)).commit();if(DeviceState.get(c,"joystick_light_enabled").equals("0,0"))c.stopService(new Intent(c,SegmentService.class));else c.startForegroundService(new Intent(c,SegmentService.class));}
 static void forget(Context c){android.content.SharedPreferences p=c.getSharedPreferences("profile",0);android.content.SharedPreferences.Editor edit=p.edit().remove("segmentPacked").remove("segmentSignature");if(p.contains("segmentPacked"))edit.putString("activeLED","System LEDs").putString("ledObserved",DeviceState.ledSignature(c));edit.commit();c.stopService(new Intent(c,SegmentService.class));}
}
