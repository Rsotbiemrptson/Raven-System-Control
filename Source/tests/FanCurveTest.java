package dev.odin.controls;
public final class FanCurveTest {
 static void expect(int actual,int wanted){if(actual!=wanted)throw new AssertionError(actual+" != "+wanted);}
 static void invalid(Runnable action){try{action.run();}catch(IllegalArgumentException good){return;}throw new AssertionError("Invalid policy accepted");}
 public static void main(String[] args){
  expect(FanCurve.percent(49,false,45,50,75,50,70),0);
  expect(FanCurve.percent(49,true,45,50,75,50,70),50);
  expect(FanCurve.percent(45,true,45,50,75,50,70),0);
  expect(FanCurve.percent(50,false,45,50,75,50,70),50);
  expect(FanCurve.percent(62.5,true,45,50,75,50,70),60);
  expect(FanCurve.percent(75,true,45,50,75,50,70),70);
  expect(FanCurve.percent(80,true,45,50,75,50,70),-1);
  expect(FanCurve.percent(Double.NaN,true,45,50,75,50,70),-1);
  expect(FanCurve.percent(-1,true,45,50,75,50,70),-1);
  expect(FanCurve.percent(0,true,45,50,75,50,70),-1);
  invalid(()->FanCurve.validate(50,45,75,50,70));
  invalid(()->FanCurve.validate(45,50,85,50,70));
  invalid(()->FanCurve.validate(45,50,75,70,50));
  invalid(()->FanCurve.manual(0));invalid(()->FanCurve.manual(71));
  for(int value=10;value<=100;value++)expect(FanCurve.manual(value,true),value);
  for(int value=0;value<10;value++){final int bad=value;invalid(()->FanCurve.manual(bad,true));}
  invalid(()->FanCurve.manual(-1,true));invalid(()->FanCurve.manual(101,true));
  System.out.println("PASS fan policy: thresholds, hysteresis, interpolation, fallback and validation");
 }
}
