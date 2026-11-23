package dev.odin.controls;

/** A completed safety fallback is a handoff; failed or unconfirmed recovery needs attention. */
final class FanRecoveryOutcome {
 static boolean needsAlert(boolean stopConfirmed,String reason){
  if(!stopConfirmed)return true;
  String value=reason==null?"":reason;
  return value.contains("restore-failed")||value.contains("automatic-failed")||value.contains("smart-switch-failed");
 }
 static String failureStatus(){return "Fan recovery could not be confirmed. Open Fan settings to retry.";}
 static String successStatus(String request,String reason){
  if("stock-fan 0".equals(request))return "Fan is off";
  if("stock-fan 1".equals(request))return "Quiet cooling active";
  if("stock-fan 4".equals(request))return "Smart cooling active";
  if("stock-fan 5".equals(request))return "Sport cooling active";
  if("system-control-changed".equals(reason))return "System fan control resumed";
  if("stopped".equals(reason))return "Previous fan settings restored";
  if(reason==null||reason.isEmpty())return "No fan session running";
  return "Smart cooling resumed";
 }
}
