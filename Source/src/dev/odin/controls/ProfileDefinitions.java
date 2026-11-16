package dev.odin.controls;
import android.content.*;import org.json.*;import java.util.*;import dev.raven.profileformat.PortableProfiles;
final class ProfileDefinitions {
 static String prefix(String kind){return kind.equals("cpu")?"performance:":kind+":";}
 static JSONObject export(Context c)throws Exception{JSONObject result=PortableProfiles.empty();for(java.util.Map.Entry<String,?> e:c.getSharedPreferences("profile",0).getAll().entrySet())for(String kind:PortableProfiles.KINDS)if(e.getKey().startsWith(prefix(kind)))result.getJSONObject(kind).put(e.getKey().substring(prefix(kind).length()),new JSONObject((String)e.getValue()));PortableProfiles.validate(result);return result;}
 static void restore(Context c,JSONObject data,boolean replace)throws Exception{PortableProfiles.validate(data);SharedPreferences p=c.getSharedPreferences("profile",0);JSONObject merged=PortableProfiles.merge(export(c),data,replace);SharedPreferences.Editor edit=p.edit();if(replace)for(String key:p.getAll().keySet())for(String kind:PortableProfiles.KINDS)if(key.startsWith(prefix(kind)))edit.remove(key);for(String kind:PortableProfiles.KINDS){JSONObject all=merged.getJSONObject(kind);for(Iterator<String> i=all.keys();i.hasNext();){String n=i.next();edit.putString(prefix(kind)+n,all.getJSONObject(n).toString());}}if(!edit.commit())throw new Exception("Could not restore Raven profile definitions");}
}
