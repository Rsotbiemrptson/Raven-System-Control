package dev.odin.controls;
import java.util.Locale;
/** Pure color operations shared by live segment switching and profile requests. */
final class SegmentColors {
 static String rgb(String hex,int brightness,boolean enabled){
  if(hex==null||!hex.matches("[A-Fa-f0-9]{6}")||brightness<0||brightness>100)throw new IllegalArgumentException("Invalid segment color or brightness");
  int color=Integer.parseInt(hex,16),level=enabled?brightness:0;
  return String.format(Locale.ROOT,"%02X%02X%02X",((color>>16)&255)*level/100,((color>>8)&255)*level/100,(color&255)*level/100);
 }
 static String replace(String packed,int index,String hex,int brightness,boolean enabled){
  if(packed==null||!packed.matches("[A-Fa-f0-9]{48}")||index<0||index>=8)throw new IllegalArgumentException("Invalid segment position or current lighting");
  return packed.substring(0,index*6)+rgb(hex,brightness,enabled)+packed.substring(index*6+6);
 }
 static String effectiveMask(String packed,String requested){
  if(packed==null||!packed.matches("[A-Fa-f0-9]{48}")||requested==null||!requested.matches("[01],[01]"))throw new IllegalArgumentException("Invalid segment output or ring mask");
  return (requested.charAt(0)=='1'&&!packed.substring(0,24).equals("000000000000000000000000")?"1":"0")+","+(requested.charAt(2)=='1'&&!packed.substring(24).equals("000000000000000000000000")?"1":"0");
 }
}
