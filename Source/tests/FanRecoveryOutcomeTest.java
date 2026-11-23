package dev.odin.controls;
public final class FanRecoveryOutcomeTest {
 static void require(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  for(String reason:new String[]{"",null,"stopped","system-control-changed","temperature-fallback","sensor-unavailable","app-lease-expired","lease-invalid","controller-error","invalid-policy","invalid-output","pwm-period-changed","status-write-failed","user-changed"}){
   require(!FanRecoveryOutcome.needsAlert(true,reason));
   require(FanRecoveryOutcome.needsAlert(false,reason));
  }
  for(String reason:new String[]{"restore-failed","restore-and-automatic-failed","temperature-fallback-speed-restore-failed","sensor-unavailable-smart-switch-failed","temperature-fallback-automatic-failed","system-control-changed-restore-failed"})require(FanRecoveryOutcome.needsAlert(true,reason));
  require(FanRecoveryOutcome.successStatus("stop","temperature-fallback").equals("Smart cooling resumed"));
  require(FanRecoveryOutcome.successStatus("stop","system-control-changed").equals("System fan control resumed"));
  require(FanRecoveryOutcome.successStatus("stop","stopped").equals("Previous fan settings restored"));
  require(FanRecoveryOutcome.successStatus("stock-fan 4","stopped").equals("Smart cooling active"));
  require(!FanRecoveryOutcome.failureStatus().contains("ERROR"));
  System.out.println("PASS silent confirmed handoffs/fallbacks, retained failure alerts, unconfirmed explicit-stop alerts and readable status");
 }
}
