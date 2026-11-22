package dev.odin.controls;
import java.util.Locale;
/** A complete Odin 2 frame, independent of Android and the OEM shared color. */
final class ZoneFrame {
 final String[] colors;final int[] levels;final String mask;
 ZoneFrame(String[] colors,int[] levels,String mask){if(colors.length!=4||levels.length!=4||!mask.matches("[01]{4}"))throw new IllegalArgumentException("Invalid LED frame");this.colors=colors.clone();this.levels=levels.clone();this.mask=mask;for(int n=0;n<4;n++){if(!colors[n].matches("[A-Fa-f0-9]{6}")||levels[n]<0||levels[n]>100)throw new IllegalArgumentException("Invalid LED color or brightness");this.colors[n]=colors[n].toUpperCase(Locale.ROOT);}}
 static ZoneFrame parse(String command,ZoneFrame before){String[] a=command.trim().split(" +");String[] c=before.colors.clone();int[] b=before.levels.clone();String mask=before.mask;
  if(a[0].equals("light-off")){if(a.length!=1)throw new IllegalArgumentException("Invalid LED off request");return new ZoneFrame(c,b,"0000");}
  if(a[0].equals("light-one")){if(a.length!=4&&!(a.length==5&&a[4].equals("system")))throw new IllegalArgumentException("Invalid single LED request");int n=Integer.parseInt(a[1]);if(n<0||n>3)throw new IllegalArgumentException("Invalid LED target");c[n]=a[2];b[n]=Integer.parseInt(a[3]);mask=mask.substring(0,n)+"1"+mask.substring(n+1);return new ZoneFrame(c,b,mask);}
  boolean groups=a[0].equals("light-groups");if(!(groups||a[0].equals("light-zones"))||a.length<(groups?11:10)||a.length>(groups?12:11))throw new IllegalArgumentException("Invalid zone request");for(int n=0;n<4;n++){c[n]=a[n+1];b[n]=Integer.parseInt(a[n+5]);}
  if(!a[9].matches("on|off|keep")||groups&&a[9].equals("keep"))throw new IllegalArgumentException("Invalid side state");String sides=a[9].equals("keep")?mask.substring(2):a[9].equals("on")?"11":"00";String rings="11";if(groups){if(!a[10].matches("on|off"))throw new IllegalArgumentException("Invalid analog state");rings=a[10].equals("on")?"11":"00";}mask=rings+sides;if(a.length==(groups?12:11))mask=a[a.length-1];return new ZoneFrame(c,b,mask);
 }
 String request(){return String.format(Locale.ROOT,"light-zones %s %s %s %s %d %d %d %d keep %s",colors[0],colors[1],colors[2],colors[3],levels[0],levels[1],levels[2],levels[3],mask);}
}
