package dev.odin.controls;
import android.content.Context;import android.provider.Settings;import java.io.IOException;
/** Boot-scoped CPU ownership; automatic OEM writes must not undo a selected Raven profile or recovery. */
final class PerformanceOwnership {
 static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
 static boolean active(Context c){android.content.SharedPreferences p=c.getSharedPreferences("profile",0);int b=boot(c);return b>=0&&p.getBoolean("performanceOwned",false)&&p.getInt("performanceOwnedBoot",-2)==b;}
 static void begin(Context c)throws IOException{int b=boot(c);if(b<0)throw new IOException("Cannot identify this boot for CPU control");if(!c.getSharedPreferences("profile",0).edit().putBoolean("performanceOwned",true).putInt("performanceOwnedBoot",b).commit())throw new IOException("Could not retain CPU ownership");}
 static void stock(Context c,int mode)throws Exception{if(mode<0||mode>2)throw new IllegalArgumentException("Invalid stock CPU mode");if(active(c)){Root.run(c,"performance-stock "+mode);c.getSystemService(android.app.NotificationManager.class).cancel(3);ControlNotifications.clear(c,ControlNotifications.CPU_ALERT);}if(!c.getSharedPreferences("profile",0).edit().putBoolean("performanceOwned",false).remove("performanceOwnedBoot").remove("performanceActiveName").remove("performanceActiveConfig").commit())throw new IOException("Could not release CPU ownership");CpuTile.refresh(c);}
}
