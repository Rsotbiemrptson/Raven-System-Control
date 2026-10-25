package dev.odin.controls;

/** Pure policy shared by the preview and host checks. Values are Celsius / PWM percent. */
public final class FanCurve {
 public static void validate(int off,int on,int full,int low,int high) {
  if(off<35||on<40||off>=on||on>=full||full>78||low<50||high>70||low>high)
   throw new IllegalArgumentException("Use off < on < full (35–78°C), and speeds from 50–70%.");
 }
 public static int percent(double temp,boolean wasOn,int off,int on,int full,int low,int high) {
  validate(off,on,full,low,high);
  if(!Double.isFinite(temp)||temp<=0||temp>=80) return -1;
  if(temp<=off||(!wasOn&&temp<on)) return 0;
  if(temp<=on)return low;
  if(temp>=full)return high;
  return low+(int)((high-low)*(temp-on)/(full-on));
 }
 public static int manual(int value) {
  if(value<50||value>70)throw new IllegalArgumentException("Preview manual range is 50–70%.");
  return value;
 }
}
