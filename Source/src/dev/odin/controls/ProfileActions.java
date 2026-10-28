package dev.odin.controls;
import android.content.*;import org.json.*;import java.util.*;

/** Profile selection shared by the panel popup and its background application. */
final class ProfileActions {
 static String displayName(String name){return name.equals("Quiet · stock")?"Quiet":name.equals("Smart · stock")?"Smart":name.equals("Sport · stock")?"Sport":name;}
 static ArrayList<String> names(Context c,boolean fan){ArrayList<String> out=new ArrayList<>();if(fan)Collections.addAll(out,"Quiet · stock","Smart · stock","Sport · stock");ArrayList<String> custom=new ArrayList<>();for(String key:c.getSharedPreferences("profile",0).getAll().keySet())if(key.startsWith(fan?"fan:":"led:"))custom.add(key.substring(4));Collections.sort(custom,String.CASE_INSENSITIVE_ORDER);out.addAll(custom);return out;}
 static void apply(Context c,boolean fan,String name)throws Exception{
  android.content.SharedPreferences p=c.getSharedPreferences("profile",0);
  if(!p.getBoolean("setupCompleteV2",false))throw new IllegalArgumentException("Run Guided Setup in Raven System Control first.");
  if(!fan){c.startForegroundService(new Intent(c,LedProfileService.class).putExtra("profileName",name));return;}
  if(!p.getBoolean("fanControlEnabled",false))throw new IllegalArgumentException("Enable Fan Control in Guided Setup first.");
  int stock=name.equals("Quiet · stock")?1:name.equals("Smart · stock")?4:name.equals("Sport · stock")?5:-1;
  JSONObject j=stock<0?new JSONObject(p.getString("fan:"+name,"{}")):new JSONObject();if(stock<0)stock=j.optInt("stockMode",-1);
  Intent i=new Intent(c,FanService.class).putExtra("fromTile",true);if(stock==0||stock==1||stock==4||stock==5)i.setAction("stock").putExtra("mode",stock);
  else{android.app.NotificationManager nm=c.getSystemService(android.app.NotificationManager.class);nm.createNotificationChannel(new android.app.NotificationChannel("fan","Fan control",android.app.NotificationManager.IMPORTANCE_LOW));if(!FanService.notificationsAvailable(c))throw new IllegalArgumentException("Enable fan notifications in Raven System Control first.");String points=j.getString("points");CurvePoints.parse(points);int speed=j.optInt("manual",55);FanCurve.manual(speed);i.setAction("apply").putExtra("points",points).putExtra("kind",j.optString("kind","curve")).putExtra("manual",speed).putExtra("zone",0).putExtra("profileName",name);}
  c.startForegroundService(i);
 }
 static String currentFanProfile(Context c){String mode=DeviceState.get(c,"fan_mode");if(mode.equals("1")||mode.equals("4")||mode.equals("5"))return DeviceState.stockName(mode)+" · stock";return DeviceState.fan(c);}
 static void power(Context c,boolean fan,boolean on)throws Exception{android.content.SharedPreferences p=c.getSharedPreferences("profile",0);if(!p.getBoolean("setupCompleteV2",false))throw new IllegalArgumentException("Run Guided Setup first.");if(fan){String current=currentFanProfile(c);ArrayList<String> available=names(c,true);if(on){String name=available.contains(current)?current:p.getString("lastFanProfile","Smart · stock");if(!available.contains(name))name="Smart · stock";apply(c,true,name);}else{if(!p.getBoolean("fanControlEnabled",false))throw new IllegalArgumentException("Enable Fan Control in Guided Setup first.");if(available.contains(current))p.edit().putString("lastFanProfile",current).apply();c.startForegroundService(new Intent(c,FanService.class).setAction("stock").putExtra("mode",0));}}else c.startForegroundService(new Intent(c,LedProfileService.class).putExtra("power",on?"on":"off"));}
 static void groups(Context c,boolean analog,boolean on)throws Exception{
  if(!android.os.Build.MODEL.equals("Odin2"))throw new IllegalArgumentException("Separate side lights require Odin 2.");
  if(!c.getSharedPreferences("profile",0).getBoolean("setupCompleteV2",false))throw new IllegalArgumentException("Run Guided Setup first.");
  c.startForegroundService(new Intent(c,LedProfileService.class).putExtra("group",analog?"analog":"side").putExtra("enabled",on));
 }
 static boolean groupOn(Context c,boolean analog){return DeviceState.groupOn(c,analog);}
 static String ledRequest(JSONObject j,boolean four)throws Exception{String[] colors={j.getString("left"),j.getString("right"),j.optString("sideLeft",j.getString("left")),j.optString("sideRight",j.getString("right"))};JSONArray b=j.optJSONArray("lightBrightness");int[] levels=new int[4];for(int n=0;n<4;n++){if(!colors[n].matches("[a-fA-F0-9]{6}"))throw new IllegalArgumentException("Invalid LED profile color");levels[n]=b==null?50:b.optInt(n,50);if(levels[n]<0||levels[n]>100)throw new IllegalArgumentException("Invalid LED brightness");}String side=j.optString("sideLights","keep");if(!side.equals("on")&&!side.equals("off")&&!side.equals("keep"))throw new IllegalArgumentException("Invalid side-light setting");
  if(four)return "light-zones "+colors[0]+" "+colors[1]+" "+colors[2]+" "+colors[3]+" "+levels[0]+" "+levels[1]+" "+levels[2]+" "+levels[3]+" "+side;
  String[] scaled=new String[2];for(int n=0;n<2;n++){int color=Integer.parseInt(colors[n],16);scaled[n]=String.format(Locale.ROOT,"%02X%02X%02X",((color>>16)&255)*levels[n]/100,((color>>8)&255)*levels[n]/100,(color&255)*levels[n]/100);}return "light "+scaled[0]+" "+scaled[1]+" 100";
 }
}
