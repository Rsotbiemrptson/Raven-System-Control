package dev.odin.controls;

/** Custom policy: verified OEM manual range, temperature ordering and monotonic output. */
final class CurvePoints {
 static String defaults(){return "35:0,45:0,50:50,60:58,70:66,78:70";}
 static int[][] parse(String value){
  String[] parts=value.split(",",-1);if(parts.length<3||parts.length>8)throw new IllegalArgumentException("Use 3–8 curve points");
  int[][] out=new int[parts.length][2];int previous=34,speed=0;
  try{for(int i=0;i<parts.length;i++){if(!parts[i].matches("[0-9]+:[0-9]+"))throw new IllegalArgumentException();String[] p=parts[i].split(":",-1);if(p.length!=2)throw new IllegalArgumentException();int t=Integer.parseInt(p[0]),s=Integer.parseInt(p[1]);
   if(t<35||t>78||t<previous+1||s<speed||(s!=0&&(s<50||s>70)))throw new IllegalArgumentException();out[i][0]=t;out[i][1]=s;previous=t;speed=s;}}
  catch(Exception e){throw new IllegalArgumentException("Points need increasing temperatures (35–78°C), with fan off or 50–70% speed.");}
  if(speed<50)throw new IllegalArgumentException("The final point must run the fan");return out;
 }
 static String encode(int[][] p){StringBuilder s=new StringBuilder();for(int[] v:p){if(s.length()>0)s.append(',');s.append(v[0]).append(':').append(v[1]);}return s.toString();}
 static int percent(double temp,boolean wasOn,String value){
  int[][] p=parse(value);if(!Double.isFinite(temp)||temp<=0||temp>=80)return -1;
  int first=0;while(p[first][1]==0)first++;
  if(!wasOn&&temp<p[first][0])return 0;
  if(temp<=p[0][0])return p[0][1];
  for(int i=1;i<p.length;i++)if(temp<=p[i][0]){if(p[i-1][1]==0)return wasOn&&temp>p[i-1][0]?p[i][1]:temp<p[i][0]?0:p[i][1];return p[i-1][1]+(int)((p[i][1]-p[i-1][1])*(temp-p[i-1][0])/(p[i][0]-p[i-1][0]));}
  return p[p.length-1][1];
 }
}
