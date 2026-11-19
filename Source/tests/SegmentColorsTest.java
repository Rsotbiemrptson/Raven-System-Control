package dev.odin.controls;
public final class SegmentColorsTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  String original="7F0000007F0000007F7F4000FFFFFF101010202020303030";
  for(int index=0;index<8;index++){
   String off=SegmentColors.replace(original,index,"ABCDEF",75,false);
   check(off.substring(index*6,index*6+6).equals("000000"));
   check(off.substring(0,index*6).equals(original.substring(0,index*6)));
   check(off.substring(index*6+6).equals(original.substring(index*6+6)));
   check(SegmentColors.replace(off,index,"ABCDEF",75,true).substring(index*6,index*6+6).equals("8099B3"));
  }
  String rapid=original;for(int i=0;i<8;i++)rapid=SegmentColors.replace(rapid,i,"FFFFFF",50,false);
  check(rapid.equals("000000000000000000000000000000000000000000000000"));
  check(SegmentColors.rgb("FF0000",0,true).equals("000000"));
  for(int index:new int[]{-1,8}){boolean failed=false;try{SegmentColors.replace(original,index,"FFFFFF",50,true);}catch(IllegalArgumentException e){failed=true;}check(failed);}
  System.out.println("PASS segment off/on, retained color/brightness, peer preservation, rapid accumulated edits and invalid positions");
 }
}
