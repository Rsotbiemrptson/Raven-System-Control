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
  for(String name:new String[]{"performance-guard.sh","performance.sh","hardware.sh"}){
   File file=new File(dir,name),temp=new File(dir,name+".tmp");
   try(InputStream in=c.getAssets().open(name);OutputStream out=new FileOutputStream(temp)){
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
   }
   if(!temp.renameTo(file))throw new IOException("Could not stage hardware helper");
  }
  return new File(dir,"hardware.sh");
 }
 static String run(Context c,String args)throws Exception {
  File h=helper(c);
  String command="sh "+q(h.getPath())+" "+args;
  // The OEM command bridge returns only one stdout line. Encode the structured
  // performance readout through the existing read path to retain every field.
  String result=args.equals("performance-status")?read(command):call(command);
  if(!result.startsWith("OK"))throw new IOException(result.isEmpty()?"No helper response; privileged access may be restricted":result);
  return result;
 }
 static String read(String command)throws Exception {
  String s=call("("+command+") 2>&1 | head -c 24000 | base64 -w 0");
  return new String(android.util.Base64.decode(s,android.util.Base64.DEFAULT),StandardCharsets.UTF_8);
 }
}
