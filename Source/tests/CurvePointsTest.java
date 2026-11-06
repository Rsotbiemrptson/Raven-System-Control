package dev.odin.controls;
public final class CurvePointsTest {
 static void equal(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
 static void invalid(String s){try{CurvePoints.parse(s);}catch(IllegalArgumentException good){return;}throw new AssertionError("Invalid curve accepted: "+s);}
 public static void main(String[] args){String p=CurvePoints.defaults();equal(CurvePoints.percent(49,false,p),0);equal(CurvePoints.percent(50,false,p),50);equal(CurvePoints.percent(49,true,p),50);equal(CurvePoints.percent(45,true,p),0);equal(CurvePoints.percent(55,true,p),54);equal(CurvePoints.percent(78,true,p),70);equal(CurvePoints.percent(80,true,p),-1);equal(CurvePoints.percent(Double.NaN,true,p),-1);
  invalid("35:0,40:20,78:70");invalid("35:0,40:70,78:50");invalid("35:0,35:50,78:70");invalid("35:0,50:50,79:70");invalid("35:0,50:50,78:100");invalid("35:0,50:0,78:0");invalid("35:0,50:50,78:70,");
  String encoded=CurvePoints.encode(CurvePoints.parse(p));if(!p.equals(encoded))throw new AssertionError("Curve serialization changed");
  for(int t=1;t<80;t++){int speed=CurvePoints.percent(t,true,p);if(speed!=0&&(speed<50||speed>70))throw new AssertionError("Output outside verified range");}
  String wide="35:0,50:10,60:50,78:100";CurvePoints.parse(wide,true);
  equal(CurvePoints.percent(49,false,wide,true),0);equal(CurvePoints.percent(50,false,wide,true),10);equal(CurvePoints.percent(60,true,wide,true),50);equal(CurvePoints.percent(78,true,wide,true),100);equal(CurvePoints.percent(80,true,wide,true),-1);equal(CurvePoints.percent(Double.NaN,true,wide,true),-1);
  if(!CurvePoints.needsExtended(wide,55)||!CurvePoints.needsExtended(p,10)||CurvePoints.needsExtended(p,55))throw new AssertionError("Extended-range detection failed");
  for(String bad:new String[]{"35:0,50:1,78:100","35:0,50:9,78:100","34:0,50:10,78:100","35:0,50:10,79:100","35:0,50:101,78:101","35:0,50:20,78:10","35:0,50:0,78:0"}){boolean rejected=false;try{CurvePoints.parse(bad,true);}catch(IllegalArgumentException expected){rejected=true;}if(!rejected)throw new AssertionError("Extended mode loosened temperature/order/output guards: "+bad);}
  System.out.println("PASS editable curve interpolation, ordering, serialization, range limits and fallback");
 }
}
