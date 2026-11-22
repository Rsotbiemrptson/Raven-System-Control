package dev.odin.controls;
import org.json.*;
/** Profile order is physical top-left, top-right, bottom-right, bottom-left for each ring. */
final class RingSegments {
 static final String[] POSITIONS={"Top left","Top right","Bottom right","Bottom left"};
 static boolean present(JSONObject j){return j.has("ringSegments");}
 static JSONArray uniform(JSONObject j)throws Exception{
  JSONArray out=new JSONArray(),levels=j.optJSONArray("lightBrightness");
  for(int n=0;n<8;n++){int ring=n/4;out.put(new JSONObject().put("color",j.optString(ring==0?"left":"right","FF8000")).put("brightness",levels==null?j.optInt("brightness",50):levels.optInt(ring,50)).put("enabled",true));}return out;
 }
 static JSONArray read(JSONObject j)throws Exception{
  JSONArray a=j.has("ringSegments")?j.getJSONArray("ringSegments"):uniform(j);
  if(a.length()!=8)throw new IllegalArgumentException("A segment profile needs eight sections");
  for(int n=0;n<8;n++){JSONObject s=a.getJSONObject(n);if(s.length()!=3||!(s.get("color") instanceof String)||!s.getString("color").matches("[A-Fa-f0-9]{6}")||!(s.get("brightness") instanceof Number)||s.getDouble("brightness")!=s.getInt("brightness")||s.getInt("brightness")<0||s.getInt("brightness")>100||!(s.get("enabled") instanceof Boolean))throw new IllegalArgumentException("Invalid ring segment");}
  return new JSONArray(a.toString());
 }
 static String packed(JSONObject j)throws Exception{
  JSONArray a=read(j);StringBuilder out=new StringBuilder();
  for(int n=0;n<8;n++){JSONObject part=a.getJSONObject(n);out.append(SegmentColors.rgb(part.getString("color"),part.getInt("brightness"),part.getBoolean("enabled")));}return out.toString();
 }
}


