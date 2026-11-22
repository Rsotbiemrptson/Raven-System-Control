package dev.odin.controls;
import android.app.PendingIntent;import android.content.Intent;import android.database.ContentObserver;import android.os.*;import android.service.quicksettings.*;

public class FanTile extends TileService {
 protected boolean leds(){return false;}
 private android.app.AlertDialog popup;private RingControls ringControls;private android.widget.Switch analogToggle,sideToggle;private boolean refreshing;private long waitUntil,pendingUntil;private Boolean pendingAnalog,pendingSide;
 private final Runnable popupTick=new Runnable(){public void run(){refreshPopup();if(popup!=null&&popup.isShowing())handler.postDelayed(this,250);}};
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ContentObserver observer=new ContentObserver(handler){@Override public void onChange(boolean self){update();}};
 private final Runnable tick=new Runnable(){public void run(){update();handler.postDelayed(this,3000);}};
 void update(){Tile tile=getQsTile();if(tile==null)return;String status=leds()?DeviceState.ledTileStatus(this):DeviceState.fan(this);tile.setIcon(android.graphics.drawable.Icon.createWithResource(this,getResources().getIdentifier(leds()?"ic_tile_led":"ic_tile_fan","drawable",getPackageName())));tile.setLabel(leds()?"Raven LED":"Raven Fan");tile.setSubtitle(status);tile.setState(("Off".equals(status))?Tile.STATE_INACTIVE:Tile.STATE_ACTIVE);tile.setContentDescription((leds()?"Raven LED: ":"Raven Fan: ")+status);tile.updateTile();}
 @Override public void onTileAdded(){super.onTileAdded();update();}
 @Override public void onStartListening(){getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI,true,observer);handler.removeCallbacks(tick);update();handler.postDelayed(tick,3000);}
 @Override public void onStopListening(){getContentResolver().unregisterContentObserver(observer);handler.removeCallbacks(tick);}
 @Override public void onDestroy(){handler.removeCallbacks(popupTick);if(popup!=null)popup.dismiss();handler.removeCallbacks(tick);getContentResolver().unregisterContentObserver(observer);super.onDestroy();}
 private android.widget.Switch groupToggle(android.content.Context c,String label,boolean analog){android.widget.Switch toggle=new android.widget.Switch(c);toggle.setText(label);toggle.setContentDescription(label);toggle.setTextSize(16);int accent=getSharedPreferences("profile",0).getInt("accentColor",0xffff8000);boolean light=getSharedPreferences("profile",0).getString("appearanceMode","dark").equals("light");toggle.setTextColor(light?0xff202124:0xffF2F2F2);toggle.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,0xff999999}));toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{(accent&0xffffff)|0x66000000,0x66999999}));toggle.setMinimumHeight((int)(48*getResources().getDisplayMetrics().density));toggle.setChecked(ProfileActions.groupOn(this,analog));toggle.setOnCheckedChangeListener((b,on)->{if(refreshing)return;try{if(analog)pendingAnalog=on;else pendingSide=on;pendingUntil=SystemClock.uptimeMillis()+8000;waitUntil=0;ProfileActions.groups(this,analog,on);}catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),1).show();}refreshPopup();});return toggle;}
 private void refreshPopup(){if(popup==null||!popup.isShowing())return;boolean applying=LedProfileService.isApplying();long now=SystemClock.uptimeMillis();if(!applying){if(pendingAnalog!=null&&(ProfileActions.groupOn(this,true)==pendingAnalog||now>=pendingUntil))pendingAnalog=null;if(pendingSide!=null&&(ProfileActions.groupOn(this,false)==pendingSide||now>=pendingUntil))pendingSide=null;}boolean busy=leds()&&(applying||now<waitUntil||pendingAnalog!=null||pendingSide!=null);if(popup.getListView()!=null)popup.getListView().setEnabled(!busy);if(ringControls!=null)ringControls.refresh(busy);for(int which:new int[]{-1,-3})if(popup.getButton(which)!=null)popup.getButton(which).setEnabled(!busy);if(analogToggle!=null){boolean profileBusy=LedProfileService.isProfileApplying()||now<waitUntil;analogToggle.setEnabled(!profileBusy&&pendingAnalog==null);sideToggle.setEnabled(!profileBusy&&pendingSide==null);if(!busy){refreshing=true;boolean a=ProfileActions.groupOn(this,true),s=ProfileActions.groupOn(this,false);if(analogToggle.isChecked()!=a)analogToggle.setChecked(a);if(sideToggle.isChecked()!=s)sideToggle.setChecked(s);refreshing=false;}}if(!busy&&popup.getListView()!=null){java.util.ArrayList<String> names=ProfileActions.names(this,!leds());String current=leds()?DeviceState.led(this):DeviceState.fan(this);if(!leds()&&(current.equals("Quiet")||current.equals("Smart")||current.equals("Sport")))current+=" · stock";int index=names.indexOf(current);if(popup.getListView().getCheckedItemPosition()!=index){popup.getListView().clearChoices();if(index>=0)popup.getListView().setItemChecked(index,true);}}}
 @Override public void onClick(){
  if(isLocked())unlockAndRun(this::openChooser);
  else openChooser();
 }
 private void openChooser(){
  if(leds())SegmentSession.ensureService(this);
  if(ShadeProfileBridge.open(this,leds()?"led":"fan")){update();return;}
  if(TilePopupService.supported(Build.MODEL,Build.VERSION.SDK_INT)){
   TilePopupService.open(this,!leds(),this::standardPopup);
  }else standardPopup();
  update();
 }
 private void standardPopup(){
  boolean light=getSharedPreferences("profile",0).getString("appearanceMode","dark").equals("light");android.content.Context themed=new android.view.ContextThemeWrapper(this,getResources().getIdentifier(light?"AppThemeLight":"AppTheme","style",getPackageName()));java.util.ArrayList<String> names=ProfileActions.names(this,!leds());
  String current=leds()?DeviceState.led(this):DeviceState.fan(this);if(!leds()&&(current.equals("Quiet")||current.equals("Smart")||current.equals("Sport")))current+=" · stock";final int selected=names.indexOf(current);
  int accent=getSharedPreferences("profile",0).getInt("accentColor",0xffff8000);int ink=AppearanceColors.readable(accent,light?0xffFFFFFF:0xff303034);android.widget.TextView title=new android.widget.TextView(themed);title.setText(leds()?"Raven LED":"Raven Fan");title.setTextSize(20);title.setTextColor(light?0xff202124:0xffF2F2F2);int padTitle=(int)(24*getResources().getDisplayMetrics().density);title.setPadding(padTitle,padTitle,padTitle,padTitle/2);
  android.widget.ArrayAdapter<String> list=new android.widget.ArrayAdapter<String>(themed,android.R.layout.simple_list_item_single_choice,names){@Override public android.view.View getView(int position,android.view.View recycled,android.view.ViewGroup parent){android.widget.CheckedTextView text=(android.widget.CheckedTextView)super.getView(position,recycled,parent);text.setText(ProfileActions.displayName(names.get(position)));text.setTextColor(light?0xff202124:0xffF2F2F2);text.setCheckMarkTintList(android.content.res.ColorStateList.valueOf(ink));return text;}};
  android.app.AlertDialog.Builder builder=new android.app.AlertDialog.Builder(themed).setCustomTitle(title);
  if(names.isEmpty())builder.setMessage("Save an LED profile in Raven System Control to use it here.");else builder.setSingleChoiceItems(list,selected,(d,n)->{try{ProfileActions.apply(this,!leds(),names.get(n));waitUntil=android.os.SystemClock.uptimeMillis()+700;}catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}});
  analogToggle=null;sideToggle=null;ringControls=null;
  if(leds()&&Build.MODEL.equals("Odin2")){
   android.widget.LinearLayout controls=new android.widget.LinearLayout(themed);controls.setOrientation(1);int pad=(int)(24*getResources().getDisplayMetrics().density);controls.setPadding(pad,0,pad,0);
   analogToggle=groupToggle(themed,"Analog lights",true);sideToggle=groupToggle(themed,"Side lights",false);controls.addView(analogToggle);controls.addView(sideToggle);builder.setView(controls);
  }else if(leds()&&Build.MODEL.equals("Odin3")){
   ringControls=new RingControls(themed,accent,light);
   builder.setView(ringControls.row);
  }
  android.app.AlertDialog dialog=builder.create();dialog.setOnShowListener(d->{dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(light?0xffFFFFFF:0xff303034));int width=Math.min((int)(400*getResources().getDisplayMetrics().density),getResources().getDisplayMetrics().widthPixels-(int)(32*getResources().getDisplayMetrics().density));dialog.getWindow().setLayout(width,android.view.ViewGroup.LayoutParams.WRAP_CONTENT);});dialog.setCanceledOnTouchOutside(true);dialog.setOnDismissListener(d->{handler.removeCallbacks(popupTick);if(popup==dialog)popup=null;});popup=dialog;showDialog(dialog);handler.post(popupTick);
 }
 private final class RingControls {
  final android.widget.LinearLayout row;
  final android.widget.Switch power,left,right;
  final int accent,ink;
  boolean syncing;
  RingControls(android.content.Context context,int accent,boolean light){
   this.accent=accent;
   ink=AppearanceColors.readable(accent,light?0xffffffff:0xff303034);
   row=new android.widget.LinearLayout(context);
   row.setGravity(android.view.Gravity.CENTER_VERTICAL);
   row.setPadding(dp(16),dp(4),dp(16),dp(4));
   power=ringSwitch(context,"Power","both",light);
   left=ringSwitch(context,"Left","left",light);
   right=ringSwitch(context,"Right","right",light);
   add(power);add(left);add(right);
   refresh(false);
  }
  int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  void add(android.view.View control){
   android.widget.LinearLayout.LayoutParams size=new android.widget.LinearLayout.LayoutParams(control==power?-2:0,dp(48),control==power?0:1);
   if(control==power)size.setMarginEnd(dp(12));
   if(row.getChildCount()>0)size.setMarginStart(dp(8));
   row.addView(control,size);
  }
  android.widget.Switch ringSwitch(android.content.Context context,String label,String ring,boolean light){
   android.widget.Switch toggle=new android.widget.Switch(context);
   toggle.setText(label);
   toggle.setTextColor(light?0xff202124:0xffF2F2F2);
   toggle.setTextSize(14);
   toggle.setSwitchPadding(dp(4));
   int[][] states={{android.R.attr.state_checked},{}};
   toggle.setThumbTintList(new android.content.res.ColorStateList(states,new int[]{ink,0xff999999}));
   toggle.setTrackTintList(new android.content.res.ColorStateList(states,new int[]{(accent&0xffffff)|0x66000000,0x66999999}));
   toggle.setOnCheckedChangeListener((v,on)->{if(!syncing)request(ring,on);});
   return toggle;
  }
  void request(String ring,boolean enabled){
   try{
    ProfileActions.ringPower(FanTile.this,ring,enabled);
    waitUntil=SystemClock.uptimeMillis()+350;
   }catch(Exception e){android.widget.Toast.makeText(FanTile.this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
   refreshPopup();
  }
  void refresh(boolean busy){
   String mask=DeviceState.get(FanTile.this,"joystick_light_enabled");
   boolean leftOn=mask.startsWith("1,"),rightOn=mask.endsWith(",1");
   syncing=true;
   if(!busy){
    left.setChecked(leftOn);
    right.setChecked(rightOn);
    power.setChecked(leftOn||rightOn);
   }
   syncing=false;
   power.setEnabled(!busy);
   left.setEnabled(!busy);
   right.setEnabled(!busy);
  }
 }

}

