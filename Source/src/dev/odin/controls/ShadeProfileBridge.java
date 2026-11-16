package dev.odin.controls;
import android.content.*;import android.content.pm.ProviderInfo;import android.net.Uri;import android.os.*;
final class ShadeProfileBridge {
 static boolean open(Context c,String kind){if(!Build.MODEL.equals("Odin3"))return false;try{ProviderInfo host=c.getPackageManager().resolveContentProvider("dev.raven.systemui.profilehost",0);if(host==null||!host.packageName.equals("com.android.systemui")||!host.applicationInfo.enabled)return false;Bundle reply=c.getContentResolver().call(Uri.parse("content://dev.raven.systemui.profilehost"),"show",kind,null);return reply!=null&&reply.getBoolean("ok");}catch(Exception e){android.util.Log.w("RavenShade","Using standard tile chooser",e);return false;}}
}
