package dev.odin.controls;

/** Vendor mode and a confirmed Raven selection are distinct from the OEM settings mirror. */
final class CpuTileStatus {
 static int mode(String value){return "0".equals(value)?0:"1".equals(value)?1:"2".equals(value)?2:-1;}
 static String label(int actual,boolean owned,int selected,String name){
  if(owned&&selected>=0&&selected<3&&name!=null&&!name.isEmpty()&&(actual<0||actual==selected))return name;
  return actual>=0&&actual<3?PerformanceProfile.STOCK_NAMES[actual]:"CPU profiles";
 }
}
