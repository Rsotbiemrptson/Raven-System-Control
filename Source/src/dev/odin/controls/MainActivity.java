package dev.odin.controls;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private int BG,SURFACE,CARD,TEXT,MUTED;private boolean lightTheme;private static final int DEFAULT_ACCENT=0xffff8000;
 // Other Raven apps: Backup, App Control, Gamepad; System Control orange uses Default.
 private static final int[] COLOR_PRESETS={0xff64c895,0xffff0000,0xff0070cc,0xff8000ff,0xffffff00,0xffffffff};
 private int accent=DEFAULT_ACCENT;
 private String points=CurvePoints.defaults(),fanChoice="Custom curve",ledChoice="Current LEDs",ledEditingName="";private int selectedStock=-1;private boolean editingLight=false;private String lastLED="",lastFan="";
 private String settingsReturnTab="Fan",backupSummary="Setup creates a protected recovery backup automatically.";
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private Spinner ledProfilePicker;
 private PerformancePanel performance;
 private LinearLayout root,body,nav,actions;private TextView session,lightingStatusView,fanTemperatureView;
 private AlertDialog ledEditor;
 private RingPreview activeRingPreview;
 private String lightingStatus="",fanTemperature="Fan temperature: Reading...";
 private boolean temperaturePending=false,resumed=false;
 private android.content.SharedPreferences prefs;
 private boolean busy=false,checked=false;
 private boolean firmwareRecoveryPending=false,firmwareNoticeShown=false;
 private String sideLights="keep",sideLeft="FF8000",sideRight="FF8000";
 private int selectedLight=0;
 private String lightMask="";
 private final int[] lightBrightness={50,50,50,50};
 private boolean segmentMode;private JSONArray segmentData=new JSONArray();
 private final SegmentSwitches segmentSwitches=new SegmentSwitches();
 private String tab="Fan",report="Hardware has not been checked. Profiles can be edited without a device.",zone="";
 private ArrayList<String> sensors=new ArrayList<>(),sensorNames=new ArrayList<>();
 private int off=45,on=50,full=75,low=50,high=70,manual=55,brightness=50;
 private String left="FF8000",right="FF8000",kind="curve";
 private Handler timer=new Handler(Looper.getMainLooper());
 private final Runnable tick=new Runnable(){public void run(){syncLEDs();syncFan();refreshFanTemperature();if(performanceVisible())performance.refresh();timer.postDelayed(this,3000);}};
 @Override public void onCreate(Bundle state){
  prefs=getSharedPreferences("profile",0);loadAppearance();super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(lightTheme?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);if(!prefs.getBoolean("orangeBrandingV1",false)){android.content.SharedPreferences.Editor update=prefs.edit().putBoolean("orangeBrandingV1",true);if(prefs.getInt("accentColor",DEFAULT_ACCENT)==0xff15a5fc)update.remove("accentColor");update.apply();}accent=prefs.getInt("accentColor",DEFAULT_ACCENT);backupSummary=prefs.getString("recoveryBackupSummary",backupSummary);
  performance=new PerformancePanel(this,prefs,io);
  try{load(new JSONObject(prefs.getString("draft","{}")));}catch(Exception ignored){}
  if(state!=null){tab=state.getString("tab","Fan");settingsReturnTab=state.getString("settingsReturn","Fan");}routeTilePreferences();checked=false;migrateProfiles();lastLED=prefs.getString("ledObserved",prefs.contains("draft")?DeviceState.ledSignature(this):"");lastFan=DeviceState.get(this,"fan_mode");draw();if(!prefs.getBoolean("setupCompleteV2",false))timer.post(()->{if(!isFinishing()&&!isDestroyed())guidedSetup();});else io.execute(()->{try{readHardware();}catch(Exception ignored){}checkRecoveryFirmware();runOnUiThread(()->{if(!isFinishing()){syncLEDs();if(firmwareRecoveryPending)showFirmwareRecoveryNotice();else openQuickProfile();}});});
 }
 @Override public void onResume(){super.onResume();SegmentSession.ensureService(this);resumed=true;timer.removeCallbacks(tick);timer.post(tick);getWindow().getDecorView().post(()->{android.view.WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null){int flags=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;bars.setSystemBarsAppearance(lightTheme?flags:0,flags);}});}
 @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(routeTilePreferences())draw();else openQuickProfile();}
 @Override public void onPause(){if(ledEditor!=null&&!busy)ledEditor.dismiss();resumed=false;timer.removeCallbacks(tick);saveDraft();super.onPause();}
 @Override public void onDestroy(){if(activeRingPreview!=null)activeRingPreview.close();if(ledEditor!=null)ledEditor.dismiss();io.shutdown();super.onDestroy();}
 @Override public void onSaveInstanceState(Bundle out){saveDraft();out.putString("tab",tab);out.putString("settingsReturn",settingsReturnTab);super.onSaveInstanceState(out);}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);draw();}
 void checkRecoveryFirmware(){
  String verified=prefs.getString("recoveryVerifiedFirmware","");
  if(verified.isEmpty())try{verified=Root.run(this,"backup-firmware").substring(3).trim();}catch(Exception ignored){}
  firmwareRecoveryPending=!Build.DISPLAY.equals(verified);
 }
 void showFirmwareRecoveryNotice(){
  if(firmwareNoticeShown||isFinishing()||isDestroyed())return;
  firmwareNoticeShown=true;
  dialogBuilder().setTitle("Firmware changed").setMessage("Your device firmware has changed since its recovery backup was checked. Rerun Guided Setup to verify the fan and LED interfaces and your recovery backup.\n\nYour original backup and saved profiles are preserved. An active custom profile will never replace the original recovery settings.").setPositiveButton("Rerun Guided Setup",(d,w)->guidedSetup()).setNegativeButton("Later",(d,w)->openQuickProfile()).show();
 }
 int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(6));return t;}
 GradientDrawable box(int color){return box(color,18);}
 GradientDrawable box(int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 LinearLayout card(String title,String detail){LinearLayout c=column();c.setPadding(dp(18),dp(12),dp(18),dp(14));c.setBackground(box(CARD));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(8));body.addView(c,lp);TextView heading=text(title,18,TEXT);heading.setTypeface(null,Typeface.BOLD);c.addView(heading);if(!detail.isEmpty())c.addView(text(detail,12,MUTED));return c;}
 Button control(String label,Runnable action,boolean primary){
  Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?(AppearanceColors.ink(accent)):TEXT);b.setBackground(box(primary?accent:CARD,24));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->{if(!busy)action.run();});return b;
 }
 // Draw a larger color sample inside the existing 42dp preset row.
 Button colorPreset(int color,Runnable action){
  Button preset=new Button(this){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setColor(color);float radius=Math.min(dp(16),Math.min(getWidth(),getHeight())/2f-dp(4));canvas.drawCircle(getWidth()/2f,getHeight()/2f,Math.max(0,radius),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(lightTheme?0xffa9b0bc:0xff59606a);canvas.drawCircle(getWidth()/2f,getHeight()/2f,Math.max(0,radius),paint);paint.setStyle(Paint.Style.FILL);}};
  preset.setText("");preset.setBackground(box(CARD,24));preset.setStateListAnimator(null);preset.setElevation(0);preset.setMinHeight(0);preset.setMinimumHeight(0);preset.setMinWidth(0);preset.setMinimumWidth(0);preset.setPadding(0,0,0,0);preset.setOnClickListener(v->{if(!busy)action.run();});return preset;
 }
 void button(LinearLayout parent,String label,Runnable action,boolean primary){
  Button b=control(label,action,primary);if(!primary)b.setBackground(box(SURFACE,24));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(8),0,0);parent.addView(b,lp);
 }
 void action(String label,Runnable run,boolean primary){Button b=control(label,run,primary);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);if(actions.getChildCount()>0)lp.setMargins(dp(8),0,0,0);actions.addView(b,lp);actions.setVisibility(View.VISIBLE);}
 interface Change{void set(int n);}
 void slider(LinearLayout c,String label,int min,int max,int value,Change change){
  TextView caption=text(label+": "+value,15,TEXT);c.addView(caption);SeekBar bar=new SeekBar(this);tintSlider(bar);bar.setMax(max-min);bar.setProgress(value-min);bar.setContentDescription(label);c.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean user){if(user){change.set(v+min);caption.setText(label+": "+(v+min));saveDraft();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
 }
 boolean routeTilePreferences(){if(getIntent().getStringExtra("openTab")!=null){tab=getIntent().getStringExtra("openTab");getIntent().removeExtra("openTab");return true;}if(getIntent().getBooleanExtra("performanceSafety",false)){tab="Performance";getIntent().removeExtra("performanceSafety");return true;}if(!android.service.quicksettings.TileService.ACTION_QS_TILE_PREFERENCES.equals(getIntent().getAction()))return false;android.content.ComponentName component=getIntent().getParcelableExtra(Intent.EXTRA_COMPONENT_NAME);tab=component!=null&&component.getClassName().endsWith("LedTile")?"LEDs":"Fan";getIntent().setAction(Intent.ACTION_MAIN);return true;}
 void loadAppearance(){lightTheme=prefs.getString("appearanceMode","dark").equals("light");setTheme(getResources().getIdentifier(lightTheme?"AppThemeLight":"AppTheme","style",getPackageName()));BG=Color.parseColor(lightTheme?"#F5F5F8":"#131519");SURFACE=Color.parseColor(lightTheme?"#E9EAF0":"#1F2228");CARD=Color.parseColor(lightTheme?"#FFFFFF":"#272B32");TEXT=Color.parseColor(lightTheme?"#20232B":"#F1F2F6");MUTED=Color.parseColor(lightTheme?"#586171":"#A9B0BC");}
 Button overflow(){Button b=new Button(this){final Paint paint=new Paint(3);@Override protected void onDraw(Canvas c){paint.setColor(MUTED);for(int i=-1;i<=1;i++)c.drawCircle(getWidth()/2f,getHeight()/2f+dp(i*7),dp(2),paint);}};b.setBackground(box(SURFACE,24));b.setStateListAnimator(null);b.setElevation(0);b.setContentDescription("More options");b.setOnClickListener(v->showMenu(v));return b;}
 void draw(){
  boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
  session=null;lightingStatusView=null;fanTemperatureView=null;root=new LinearLayout(this);root.setOrientation(landscape?0:1);root.setBackgroundColor(BG);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|(Build.VERSION.SDK_INT>=35?WindowInsets.Type.ime():0));v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});root.requestApplyInsets();
  nav=new LinearLayout(this);nav.setOrientation(landscape?1:0);nav.setPadding(dp(12),dp(12),dp(12),dp(12));nav.setBackgroundColor(SURFACE);
  if(landscape){LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);ImageView emblem=new ImageView(this);emblem.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));emblem.setContentDescription("Raven System Control");brand.addView(emblem,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView name=text("Raven\nSystem Control",16,TEXT);name.setTypeface(null,Typeface.BOLD);name.setPadding(dp(7),0,0,0);brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));nav.addView(brand,new LinearLayout.LayoutParams(-1,-2));nav.addView(new View(this),new LinearLayout.LayoutParams(1,dp(22)));root.addView(nav,new LinearLayout.LayoutParams(dp(156),-1));}
  LinearLayout page=column();page.setPadding(dp(22),dp(12),dp(22),dp(12));root.addView(page,new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,1));
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);LinearLayout names=column();if(!landscape)names.addView(text("Raven System Control",12,MUTED));TextView title=text(tab.equals("Fan")?"Fan control":tab.equals("LEDs")?"LEDs":tab.equals("Performance")?"Performance":tab.equals("Settings")?"Settings":"Device & recovery",25,TEXT);title.setTypeface(null,Typeface.BOLD);names.addView(title);if(tab.equals("Device")){Button back=control("Back",this::backFromSettings,false);back.setContentDescription("Back from device details");header.addView(back,new LinearLayout.LayoutParams(dp(70),dp(44)));}header.addView(names,new LinearLayout.LayoutParams(0,-2,1));header.addView(overflow(),new LinearLayout.LayoutParams(dp(48),dp(44)));page.addView(header);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(0,dp(6),0,0);scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);actions.setVisibility(View.GONE);page.addView(actions,new LinearLayout.LayoutParams(-1,-2));
  for(String nameTab:new String[]{"Fan","LEDs","Performance"}){Button b=control(nameTab,()->{saveDraft();tab=nameTab;draw();},nameTab.equals(tab));LinearLayout.LayoutParams lp=landscape?new LinearLayout.LayoutParams(-1,dp(48)):new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(landscape?0:dp(3),0,landscape?0:dp(3),landscape?dp(8):0);nav.addView(b,lp);}if(!landscape)root.addView(nav,new LinearLayout.LayoutParams(-1,dp(72)));
  if(tab.equals("Fan"))fan();else if(tab.equals("LEDs"))lights();else if(tab.equals("Performance"))performance.show(TEXT,MUTED,SURFACE,accent);else if(tab.equals("Settings"))settings();else device();
 }
 boolean performanceVisible(){return resumed&&tab.equals("Performance");}
 void performanceAction(Button button){actions.addView(button,new LinearLayout.LayoutParams(-1,dp(44)));actions.setVisibility(View.VISIBLE);}
 void performanceBusy(boolean value){busy=value;}
 void showMenu(View anchor){
  if(busy)return;PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(0,0,0,"Settings");menu.getMenu().add(0,1,1,"About");menu.getMenu().add(0,2,2,"Report an issue");menu.setOnMenuItemClickListener(item->{if(item.getItemId()==2){reportIssue();return true;}if(item.getItemId()==1){about();return true;}if(!tab.equals("Settings")&&!tab.equals("Device"))settingsReturnTab=tab;saveDraft();tab="Settings";draw();return true;});menu.show();
 }
 void reportIssue(){
  String issueUrl="https://github.com/Rsotbiemrptson/Raven-System-Control/issues/new";
  String version="";
  try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException ignored){}
  String body="### Test environment\nRaven System Control version: "+version
   +"\nDevice model: \nAndroid and firmware version: \n"
   +"\n### Steps to reproduce\n\n### Expected result\n\n### Actual result or error message\n";
  android.net.Uri issue=android.net.Uri.parse(issueUrl).buildUpon().appendQueryParameter("body",body).build();
  try{startActivity(new Intent(Intent.ACTION_VIEW,issue));}
  catch(ActivityNotFoundException unavailable){
   dialogBuilder().setTitle("Open GitHub Issues")
    .setMessage("Open this address in a browser to report an issue:\n\n"+issueUrl)
    .setPositiveButton("Close",null).show();
  }
 }
 void backFromSettings(){if(busy)return;saveDraft();tab=tab.equals("Device")?"Settings":settingsReturnTab;draw();}
 @Override public void onBackPressed(){if(tab.equals("Settings")||tab.equals("Device"))backFromSettings();else super.onBackPressed();}
 void describedButton(LinearLayout c,String label,String description,Runnable action){button(c,label,action,false);c.addView(text(description,12,MUTED));}
 void advancedPair(LinearLayout parent,String a,String ad,Runnable ar,String b,String bd,Runnable br){LinearLayout row=new LinearLayout(this);for(int n=0;n<2;n++){LinearLayout column=column();LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,-2,1);if(n==1)space.setMargins(dp(8),0,0,0);row.addView(column,space);String label=n==0?a:b;if(label!=null)describedButton(column,label,n==0?ad:bd,n==0?ar:br);}parent.addView(row);}
 LinearLayout advancedGroup(){
  LinearLayout card=card("Advanced","");TextView title=(TextView)card.getChildAt(0);card.removeViewAt(0);LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));final boolean[] opened={prefs.getBoolean("advancedExpanded",true)};View chevron=new View(this){final Paint paint=new Paint(3);@Override protected void onDraw(Canvas canvas){float x=getWidth()/2f,y=getHeight()/2f,dy=dp(opened[0]?-3:3);paint.setColor(MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();path.moveTo(x-dp(5),y-dy);path.lineTo(x,y+dy);path.lineTo(x+dp(5),y-dy);canvas.drawPath(path,paint);}};header.addView(chevron,new LinearLayout.LayoutParams(dp(32),dp(44)));card.addView(header,0);LinearLayout content=column();content.setVisibility(opened[0]?View.VISIBLE:View.GONE);card.addView(content);header.setContentDescription("Advanced settings");header.setStateDescription(opened[0]?"Expanded":"Collapsed");header.setFocusable(true);header.setOnClickListener(v->{opened[0]=!opened[0];prefs.edit().putBoolean("advancedExpanded",opened[0]).apply();content.setVisibility(opened[0]?View.VISIBLE:View.GONE);header.setStateDescription(opened[0]?"Expanded":"Collapsed");chevron.invalidate();});header.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());info.setCheckable(true);info.setChecked(opened[0]);}});return content;
 }
 void settings(){
  LinearLayout c=card("Appearance","");LinearLayout row=new LinearLayout(this);Button color=control("Accent color · "+String.format(Locale.ROOT,"#%06X",accent&0xffffff),this::chooseAccent,true);Button mode=control(lightTheme?"Light mode":"Dark mode",()->dialogBuilder().setTitle("Appearance").setItems(new String[]{"Light","Dark"},(d,n)->{saveDraft();prefs.edit().putString("appearanceMode",n==0?"light":"dark").apply();recreate();}).show(),true);row.addView(color,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,dp(44),1);space.setMargins(dp(8),0,0,0);row.addView(mode,space);c.addView(row);c.addView(text("Choose your app accent and light or dark appearance. LED colors stay unchanged.",12,MUTED));
  c=advancedGroup();if(firmwareRecoveryPending)c.addView(text("Firmware changed. Rerun Guided Setup to verify your recovery backup.",12,MUTED));
  advancedPair(c,"Rerun Guided Setup","Check your device and retain original recovery backups.",this::guidedSetup,"Device & recovery","Review compatibility and device checks.",()->{tab="Device";draw();});
  advancedPair(c,"Factory Restore Fan","Restore original fan settings. "+DeviceState.backupDate(backupSummary),()->task(this::restoreFanBackup),"Factory Restore LEDs","Restore original LED settings. "+DeviceState.backupDate(backupSummary),()->task(this::restoreLighting));
  advancedPair(c,"Fan control safety","Review safeguards or enable fan control.",this::fanWarning,null,null,null);
  Switch extended=new Switch(this);
  extended.setText("Extended fan speed range (10–100%)");
  extended.setTextColor(TEXT);
  extended.setTextSize(14);
  extended.setMinHeight(dp(48));
  extended.setSwitchPadding(dp(12));
  extended.setContentDescription("Extended fan speed range");
  tintSwitch(extended);
  extended.setChecked(extendedFanRange());
  c.addView(extended,new LinearLayout.LayoutParams(-1,-2));
  c.addView(text("Off by default. Unlock experimental speeds after reading the warning. Temperature limits and automatic Smart fallback stay enabled. Turning this off returns an active custom fan session to Smart; saved profiles are retained.",12,MUTED));
  final boolean[] changing={false};extended.setOnCheckedChangeListener((v,enabled)->{if(changing[0])return;if(busy){changing[0]=true;extended.setChecked(extendedFanRange());changing[0]=false;return;}if(enabled){changing[0]=true;extended.setChecked(false);changing[0]=false;dialogBuilder().setTitle("Extended fan speed range").setMessage("This unlocks requested fan output from 10–100%, beyond the standard 50–70% limits. Percent is a control request, not measured RPM.\n\nRunning outputs below 10% are blocked. Curves can still switch the fan off at their off-temperature points. Insufficient cooling can cause overheating, throttling, shutdown or device damage. Higher output may increase noise and power use. Brief Odin 2 checks do not verify long-term cooling or other devices.\n\nCurve temperatures remain 35–78°C. The 80°C sensor fallback, sensor-loss and app-heartbeat checks, minimum run time, and system-tile takeover stay enabled. They cannot guarantee protection.\n\nEnabling this option does not change the fan until you apply a profile.").setPositiveButton("Enable extended range",(d,w)->setExtendedFanRange(true)).setNegativeButton("Cancel",null).show();}else setExtendedFanRange(false);});

 }
 boolean extendedFanRange(){return prefs.getBoolean("extendedFanRange",false);}
 void setExtendedFanRange(boolean enabled){prefs.edit().putBoolean("extendedFanRange",enabled).apply();if(!enabled){if(FanService.running)startForegroundService(new Intent(this,FanService.class).setAction("stock").putExtra("mode",4));boolean reset;try{reset=CurvePoints.needsExtended(points,manual);}catch(Exception e){reset=true;}if(reset){points=CurvePoints.defaults();manual=55;kind="curve";selectedStock=-1;fanChoice="Custom curve";}}saveDraft();draw();}
 TextView aboutText(String value,int size,int color){TextView view=text(value,size,color);view.setPadding(0,0,0,0);return view;}
 void aboutGap(LinearLayout content,int height){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
 void about(){
  LinearLayout content=column();content.setPadding(dp(24),dp(8),dp(24),dp(4));
  ImageView icon=new ImageView(this);icon.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));icon.setContentDescription("Raven System Control icon");LinearLayout.LayoutParams iconLayout=new LinearLayout.LayoutParams(dp(72),dp(72));iconLayout.gravity=Gravity.CENTER_HORIZONTAL;content.addView(icon,iconLayout);aboutGap(content,8);
  TextView name=aboutText("Raven System Control",22,TEXT);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER);content.addView(name,new LinearLayout.LayoutParams(-1,-2));
  String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
  TextView release=aboutText("Version "+version,13,AppearanceColors.readable(accent,CARD));release.setGravity(Gravity.CENTER);content.addView(release,new LinearLayout.LayoutParams(-1,-2));aboutGap(content,8);
  TextView slogan=aboutText("Your system. Your way.",16,AppearanceColors.readable(accent,CARD));slogan.setGravity(Gravity.START);content.addView(slogan,new LinearLayout.LayoutParams(-1,-2));aboutGap(content,8);
  content.addView(aboutText("Tune your cooling, choose your lighting, and keep recovery close at hand.",14,TEXT));aboutGap(content,8);
  content.addView(aboutText("Build date: "+BuildInfo.BUILD_DATE+"\nCreator: Rsotbiemrptson",13,MUTED));aboutGap(content,8);
  content.addView(aboutText("Open source · GNU GPL v3 only",13,MUTED));
  TextView license=aboutText("License",14,AppearanceColors.readable(accent,CARD));license.setPaintFlags(license.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);license.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);license.setMinHeight(dp(44));license.setFocusable(true);license.setOnClickListener(v->showLicense());content.addView(license,new LinearLayout.LayoutParams(-2,-2));
  AlertDialog popup=dialogBuilder().setTitle("About").setView(content).setPositiveButton("Close",null).show();
  int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(480));
  popup.getWindow().setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
 }
 void showLicense(){try(java.io.InputStream in=getAssets().open("LICENSE.txt")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);dialogBuilder().setTitle("GNU GPL v3 only").setMessage(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8)).setPositiveButton("Close",null).show();}catch(Exception e){message("License text could not be opened");}}
 String restoreFanBackup()throws Exception{if(FanService.running)startService(new Intent(this,FanService.class).setAction("stop"));return Root.run(this,"fan-recover");}
 void chooseAccent(){
  LinearLayout content=column();content.setPadding(dp(20),dp(8),dp(20),dp(8));boolean narrow=getResources().getConfiguration().screenWidthDp<440;
  LinearLayout controls=new LinearLayout(this);controls.setOrientation(narrow?1:0);ColorWheelView wheel=new ColorWheelView(this,accent,TEXT);controls.addView(wheel,new LinearLayout.LayoutParams(dp(176),dp(176)));LinearLayout fields=column();fields.setPadding(narrow?0:dp(20),0,0,0);controls.addView(fields,narrow?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
  TextView preview=text("Accent preview",16,AppearanceColors.ink(accent));preview.setGravity(Gravity.CENTER);preview.setBackground(box(accent,16));fields.addView(preview,new LinearLayout.LayoutParams(-1,dp(40)));
  EditText hex=new EditText(this);hex.setSingleLine();hex.setTextColor(TEXT);hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);hex.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);hex.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});hex.setContentDescription("Accent hex color");fields.addView(hex,new LinearLayout.LayoutParams(-1,dp(48)));
  content.addView(controls);
  LinearLayout presets=new LinearLayout(this);for(int color:COLOR_PRESETS){Button preset=colorPreset(color,()->wheel.setColor(color));preset.setContentDescription(String.format(Locale.ROOT,"Use #%06X",color&0xffffff));presets.addView(preset,new LinearLayout.LayoutParams(0,dp(42),1));}content.addView(presets);
  final boolean[] updating={false};wheel.changed=color->{updating[0]=true;hex.setText(String.format(Locale.ROOT,"#%06X",color&0xffffff));hex.setError(null);preview.setBackground(box(color,16));preview.setTextColor(AppearanceColors.ink(color));updating[0]=false;};
  hex.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int a,int n,int f){}public void onTextChanged(CharSequence v,int a,int before,int count){if(!updating[0]&&v.toString().matches("#[A-Fa-f0-9]{6}")){wheel.setColor(Color.parseColor(v.toString()));hex.setSelection(hex.length());}}public void afterTextChanged(android.text.Editable e){}});
  wheel.setColor(accent);
  ScrollView scroll=new ScrollView(this);scroll.addView(content);AlertDialog dialog=dialogBuilder().setTitle("Accent color").setView(scroll).setPositiveButton("Apply",null).setNeutralButton("Default",(d,w)->{accent=DEFAULT_ACCENT;prefs.edit().remove("accentColor").apply();draw();}).setNegativeButton("Cancel",null).create();content.setFocusableInTouchMode(true);content.requestFocus();dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(600)),-2);dialog.getButton(-1).setOnClickListener(v->{String text=hex.getText().toString();if(!text.matches("#[A-Fa-f0-9]{6}")){hex.setError("Use #RRGGBB");return;}accent=Color.parseColor(text);prefs.edit().putInt("accentColor",accent).apply();dialog.dismiss();draw();});
 }
 void tintSlider(SeekBar bar){android.content.res.ColorStateList tint=android.content.res.ColorStateList.valueOf(AppearanceColors.readable(accent,CARD));bar.setProgressTintList(tint);bar.setThumbTintList(tint);}
 void tintSwitch(Switch toggle){android.content.res.ColorStateList tint=new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{AppearanceColors.readable(accent,CARD),MUTED});toggle.setThumbTintList(tint);toggle.setTrackTintList(tint);}
 AlertDialog.Builder dialogBuilder(){return new AlertDialog.Builder(this){@Override public AlertDialog create(){AlertDialog dialog=super.create();dialog.setOnShowListener(d->{dialog.getWindow().setBackgroundDrawable(box(CARD,22));for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button button=dialog.getButton(which);if(button!=null)button.setTextColor(AppearanceColors.readable(accent,CARD));}});return dialog;}};}
 void guidedSetup(){if(busy)return;fanWarning();}
 void fanWarning(){
  if(busy)return;
  dialogBuilder().setTitle("1 of 3 · Fan control safety").setMessage("Manipulating fan settings may cause your device to overheat and possibly cause damage. Only enable fan control if you understand this risk.\n\nFor custom profiles, Raven monitors the selected temperature sensor. At 80°C, sensor loss, a user switch or a missed app heartbeat, it requests stock Smart cooling. Standard custom speeds stay within the tested 50–70% range while running. The experimental 10–100% option in Advanced requires a separate warning. Once running, the fan stays on for at least 20 seconds to avoid rapid cycling. These safeguards cannot guarantee protection.\n\nSelecting a mode in the system fan tile ends Raven's custom control. Skipping leaves fan control disabled; LEDs are still available.")
   .setPositiveButton("Enable Fan Control",(d,w)->{prefs.edit().putBoolean("fanControlEnabled",true).apply();setupFanNotifications();}).setNegativeButton("Skip",(d,w)->{prefs.edit().putBoolean("fanControlEnabled",false).apply();if(FanService.running)startService(new Intent(this,FanService.class).setAction("stock").putExtra("mode",4));runGuidedChecks();}).show();
 }
 private static final int SETUP_FAN_NOTIFICATIONS=6;
 private static final int SETUP_NOTIFICATION_SETTINGS=7;
 private static final String FAN_NOTIFICATION_REASON="Custom fan profiles run in the background. Allow notifications so you can see the active session and tap Use Smart to return cooling to the system.";
 private void setupFanNotifications(){
  createFanNotificationChannel();
  if(FanService.notificationsAvailable(this)){runGuidedChecks();return;}
  dialogBuilder().setTitle("Notifications for custom fan control")
   .setMessage(FAN_NOTIFICATION_REASON+" Stock fan modes and LEDs are available without this permission.")
   .setPositiveButton("Enable notifications",(d,w)->{
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
     requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},SETUP_FAN_NOTIFICATIONS);
    }else openSetupNotificationSettings();
   })
   .setNegativeButton("Continue without",(d,w)->runGuidedChecks()).show();
 }
 private void createFanNotificationChannel(){
  getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("fan","Fan control",NotificationManager.IMPORTANCE_LOW));
 }
 private void openSetupNotificationSettings(){
  startActivityForResult(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
   .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()),SETUP_NOTIFICATION_SETTINGS);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==SETUP_NOTIFICATION_SETTINGS)runGuidedChecks();
 }
 void runGuidedChecks(){
  if(busy)return;busy=true;AlertDialog progress=dialogBuilder().setTitle("2 of 3 · Checking your Odin").setMessage("Checking stock controls, reading sensors and retaining your original fan and LED recovery backups…").setCancelable(false).create();progress.show();
  io.execute(()->{String failure=null;try{readHardware();if(!checked)throw new Exception("No readable temperature sensors were found.");backupSummary=Root.run(this,"backup-create").substring(3);prefs.edit().putString("recoveryBackupSummary",backupSummary).putBoolean("setupCompleteV2",true).putString("recoveryVerifiedFirmware",Build.DISPLAY).apply();firmwareRecoveryPending=false;}catch(Exception e){checked=false;failure=e.getMessage();}final String error=failure;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;progress.dismiss();if(error!=null){report="Could not complete checks: "+error;dialogBuilder().setTitle("Checks need attention").setMessage(error).setPositiveButton("Try again",(d,w)->runGuidedChecks()).setNegativeButton("Close",null).show();return;}
   dialogBuilder().setTitle("3 of 3 · Ready to use").setMessage(Build.MODEL+" checks passed.\n\n"+DeviceState.backupDate(backupSummary)+"\n\nOriginal backups are retained outside app storage, including after uninstall/reinstall. Setup never replaces them with an applied profile. Fan control is "+(prefs.getBoolean("fanControlEnabled",false)?(FanService.notificationsAvailable(this)?"enabled":"enabled for stock modes. Allow notifications to use custom profiles"):"disabled")+". Add Raven Fan and Raven LED from Android's quick-tile editor.")
    .setPositiveButton("Finish",(d,w)->{draw();openQuickProfile();}).show();
  });});
 }
 String fanStatus(String status){return "Active: "+DeviceState.fan(this)+(FanService.running||status.startsWith("Could not")||status.startsWith("Stop could")||status.startsWith("System cooling resumed")?" · "+status:"");}
 // Read the active session's source, or resolve the same OEM source used at startup.
 // Never read hardware on the UI thread or change fan settings for this display.
 void refreshFanTemperature(){
  if(!resumed||fanTemperatureView==null)return;
  String controllerStatus=FanService.status;
  session.setText(fanStatus(controllerStatus));
  if(FanService.running){
   java.util.regex.Matcher reading=java.util.regex.Pattern.compile("^([0-9]+\\u00b0C)").matcher(controllerStatus);
   fanTemperatureView.setText(reading.find()?"Fan temperature: "+reading.group(1):"Fan temperature: Reading...");
   return;
  }
  if(temperaturePending||io.isShutdown())return;
  temperaturePending=true;
  io.execute(()->{
   String label="Fan temperature: Unavailable";
   try{
    String source=Root.run(this,"oem-sensor").substring(3).trim();
    if(!FanService.validTemperatureSource(source))throw new IllegalArgumentException("Invalid source");
    long value=Long.parseLong(Root.run(this,"fan-temperature "+source).substring(3).trim());
    if(value<=0||value>250)throw new IllegalArgumentException("Invalid temperature");
    label="Fan temperature: "+value+"\u00b0C";
   }catch(Exception ignored){}
   final String reading=label;
   runOnUiThread(()->{temperaturePending=false;if(!resumed||isFinishing()||isDestroyed()||FanService.running)return;fanTemperature=reading;if(fanTemperatureView!=null)fanTemperatureView.setText(reading);});
  });
 }
 void fan(){
  final boolean extended=extendedFanRange(),rangeLocked=!extended&&CurvePoints.needsExtended(points,manual);LinearLayout c=card("Cooling profile","");Spinner profilePicker=profileSelector(c,true);if(rangeLocked)c.addView(text("This profile needs Extended fan speed range in Settings → Advanced before editing or applying it.",12,MUTED));
  LinearLayout fanReadings=new LinearLayout(this);fanReadings.setOrientation(LinearLayout.HORIZONTAL);fanReadings.setGravity(Gravity.TOP);c.addView(fanReadings);
  fanTemperatureView=text(fanTemperature,14,AppearanceColors.readable(accent,CARD));fanReadings.addView(fanTemperatureView,new LinearLayout.LayoutParams(-2,-2));
  session=text(fanStatus(FanService.status),14,AppearanceColors.readable(accent,CARD));LinearLayout.LayoutParams activeLayout=new LinearLayout.LayoutParams(0,-2,1);activeLayout.setMargins(dp(16),0,0,0);fanReadings.addView(session,activeLayout);refreshFanTemperature();
  boolean stock=selectedStock>=0;
  TextView hint=text(stock?"Stock profile · read only. Select Custom curve to edit.":"Drag points to set temperature and fan speed. Changes stay in the draft until applied.",12,MUTED);
  c.addView(hint);
  FanGraph graph=new FanGraph(this,points,accent,TEXT,lightTheme?0xffd5d8e0:0xff48505c,extended);
  if(stock)showStockReference(graph,hint,selectedStock);
  else if(kind.equals("manual"))graph.reference(flatFanOutput(manual));
  c.addView(graph,new LinearLayout.LayoutParams(-1,dp(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?170:220)));
  graph.active(!rangeLocked);
  graph.changed=value->{
   points=value;
   if(!savedNames(true).contains(fanChoice)){fanChoice="Custom curve";profilePicker.setSelection(0);}
   saveDraft();
  };
  if(stock){action("Apply profile",this::applyFan,true);action("Use Smart",()->applyStock(4),false);return;}
  Switch manualMode=new Switch(this);
  manualMode.setText("Manual speed");
  manualMode.setTextColor(TEXT);
  manualMode.setTextSize(14);
  manualMode.setMinHeight(dp(48));
  manualMode.setSwitchPadding(dp(12));
  tintSwitch(manualMode);
  manualMode.setChecked(kind.equals("manual"));
  manualMode.setEnabled(!rangeLocked);
  c.addView(manualMode,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout manualControls=column();slider(manualControls,"Fan speed %",extended||rangeLocked?10:50,extended||rangeLocked?100:70,manual,n->{manual=n;graph.reference(flatFanOutput(n));});c.addView(manualControls);enableChildren(manualControls,kind.equals("manual")&&!rangeLocked);manualControls.setAlpha(kind.equals("manual")&&!rangeLocked?1:.35f);
  manualMode.setOnCheckedChangeListener((v,enabled)->{kind=enabled?"manual":"curve";if(selectedStock>=0||!savedNames(true).contains(fanChoice)){fanChoice="Custom curve";profilePicker.setSelection(0);}selectedStock=-1;if(enabled)graph.reference(flatFanOutput(manual));else graph.setCurve(points);hint.setText("Custom curve · changes are drafts until applied.");enableChildren(manualControls,enabled);manualControls.setAlpha(enabled?1:.35f);saveDraft();});
  profileCard(true);action("Apply profile",this::applyFan,true);action("Use Smart",()->applyStock(4),false);
 }
 private int[][] flatFanOutput(int speed){return new int[][]{{30,speed},{80,speed}};}
 private void showStockReference(FanGraph graph,TextView hint,int mode){
  if(mode!=4){graph.reference(flatFanOutput(mode==1?10:mode==5?50:0));return;}
  if(!Build.MODEL.equals("Odin3")){
   graph.reference(new int[][]{{35,16},{40,16},{50,23},{60,31},{70,38},{78,44}});
   return;
  }
  graph.reference(new int[][]{{0,0},{24,0},{25,20},{45,20},{65,45},{85,70},{105,100}});
  hint.setText("Stock Smart · read only · firmware defaults");
  io.execute(()->{
   try{
    String entry=Root.read("grep fan_temp_control_curve_point_key /data/user/0/com.odin.settings/shared_prefs/config.xml").trim();
    if(!entry.startsWith("<string"))return;
    org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
    parser.setInput(new java.io.StringReader(entry));
    parser.nextTag();
    JSONArray saved=new JSONArray(parser.nextText());
    if(saved.length()<3||saved.length()>8)return;
    ArrayList<int[]> values=new ArrayList<>();
    int previous=-1;
    for(int i=0;i<saved.length();i++){
     JSONObject point=saved.getJSONObject(i);
     int t=point.optInt("x",point.optInt("a",-1)),speed=point.optInt("y",point.optInt("b",-1));
     if(t==Integer.MAX_VALUE&&i==saved.length()-1)break;
     if(t<=previous||t>105||speed<0||speed>100)return;
     if(i==1&&t>1)values.add(new int[]{t-1,0});
     values.add(new int[]{t,i==0?0:speed});
     previous=t;
    }
    if(values.size()<3)return;
    int[][] reference=values.toArray(new int[0][]);
    runOnUiThread(()->{if(graph.isAttachedToWindow()){graph.reference(reference);hint.setText("Stock Smart · read only · current Odin settings");}});
   }catch(Exception ignored){}
  });
 }
 void enableChildren(View v,boolean enabled){v.setEnabled(enabled);if(v instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)v;for(int i=0;i<group.getChildCount();i++)enableChildren(group.getChildAt(i),enabled);}}
 void applyFan(){if(selectedStock>=0){applyStock(selectedStock);return;}startFan();}
 void applyStock(int mode){if(!prefs.getBoolean("fanControlEnabled",false)){message("Enable Fan Control in Rerun Guided Setup before applying a fan mode.");return;}startForegroundService(new Intent(this,FanService.class).setAction("stock").putExtra("mode",mode));}
 void startFan(){
  try{validate();if(!prefs.getBoolean("fanControlEnabled",false))throw new IllegalArgumentException("Enable Fan Control in Rerun Guided Setup first.");if(!checked)throw new IllegalArgumentException("Rerun Guided Setup in Settings first.");if(zone.isEmpty())throw new IllegalArgumentException("The OEM temperature source is unavailable. Rerun Guided Setup.");}catch(Exception e){message(e.getMessage());return;}
  createFanNotificationChannel();
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);return;}
  if(!FanService.notificationsAvailable(this)){dialogBuilder().setMessage("Enable fan control notifications so the current profile and Use Smart action stay visible.").setPositiveButton("Notification settings",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))).setNegativeButton("Cancel",null).show();return;}
  saveDraft();startForegroundService(new Intent(this,FanService.class).setAction("apply").putExtra("kind",kind).putExtra("points",points).putExtra("manual",manual).putExtra("profileName",fanChoice));
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==SETUP_FAN_NOTIFICATIONS){
   if(FanService.notificationsAvailable(this)){runGuidedChecks();return;}
   dialogBuilder().setTitle("Custom fan notifications are off")
    .setMessage(FAN_NOTIFICATION_REASON)
    .setPositiveButton("Notification settings",(d,w)->openSetupNotificationSettings())
    .setNegativeButton("Continue without",(d,w)->runGuidedChecks()).show();
   return;
  }
  if(request==6)message(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"Notifications enabled. Apply the Performance profile when ready.":"Performance safety notifications are required for custom profiles.");
  if(request==5)message(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"Notifications enabled. Apply the fan profile when ready.":"Enable notifications to keep fan controls visible.");}
 String lightColor(int n){if(n>=4)return segmentData.optJSONObject(n-4).optString("color","FF8000");return n==0?left:n==1?right:n==2?sideLeft:sideRight;}
 void lightColor(int n,String color){if(n>=4){try{segmentData.getJSONObject(n-4).put("color",color);}catch(Exception e){throw new IllegalArgumentException(e);}return;}if(n==0)left=color;else if(n==1)right=color;else if(n==2)sideLeft=color;else sideRight=color;}
 int lightLevel(int n){return n<4?lightBrightness[n]:segmentData.optJSONObject(n-4).optInt("brightness",50);}
 void lightLevel(int n,int level){if(n<4)lightBrightness[n]=level;else try{segmentData.getJSONObject(n-4).put("brightness",level);}catch(Exception e){throw new IllegalArgumentException(e);}}
 void loadSegments(JSONObject j){segmentMode=Build.MODEL.equals("Odin3")&&j.optJSONArray("ringSegments")!=null;try{segmentData=RingSegments.read(j);}catch(Exception e){segmentMode=false;try{JSONObject plain=new JSONObject(j.toString());plain.remove("ringSegments");segmentData=RingSegments.uniform(plain);}catch(Exception ignored){}}normalizeSegmentRingMask();}
 void normalizeSegmentRingMask(){if(!segmentMode||segmentData.length()!=8)return;for(int ring=0;ring<2;ring++){boolean any=false;for(int pos=0;pos<4;pos++)any|=segmentData.optJSONObject(ring*4+pos).optBoolean("enabled",true);if(!any)setProfileLight(ring,false);}}
 JSONObject withSegments(JSONObject j)throws JSONException{if(segmentMode)j.put("ringSegments",new JSONArray(segmentData.toString()));return j;}
 String lightLabel(int n){return n<4?new String[]{"Left stick","Right stick","Left side","Right side"}[n]:(n<8?"Left stick · ":"Right stick · ")+RingSegments.POSITIONS[(n-4)%4];}
 String colorRequest(int editing,String hex,int level,boolean every)throws Exception{
  if(!segmentMode)return every?lightingRequest(new String[]{hex,hex,hex,hex},new int[]{level,level,level,level},"on"):(Build.MODEL.equals("Odin3")?"light-ring ":"light-one ")+editing+" "+hex+" "+level+(Build.MODEL.equals("Odin2")&&DeviceState.led(this).equals("System LEDs")?" system":"");
  JSONObject candidate=ledJSON();JSONArray a=RingSegments.read(candidate);
  for(int n=0;n<8;n++)if(every||(editing>=4?n==editing-4:n/4==editing))a.getJSONObject(n).put("color",hex).put("brightness",level).put("enabled",true);
  String mask=DeviceState.get(this,"joystick_light_enabled");if(!mask.matches("[01],[01]"))mask="0,0";int ring=editing>=4?(editing-4)/4:editing;String enabled=every?"11":ring==0?"1"+mask.charAt(2):""+mask.charAt(0)+"1";
  return ProfileActions.ledRequest(candidate.put("ringSegments",a).put("lightMask",enabled+"00"),false);
 }
 void styleSegmentButton(Button button,int index){JSONObject part=segmentData.optJSONObject(index);boolean enabled=part.optBoolean("enabled",true);int color=enabled?Color.parseColor("#"+part.optString("color","FF8000")):0xff101719;button.setBackground(box(color,10));button.setTextColor(enabled&&Color.luminance(color)>.5?Color.BLACK:enabled?Color.WHITE:MUTED);}
 /** Merge quick switch gestures; apply each latest frame without changing peer sections. */
 private final class SegmentSwitches {
  volatile boolean running;
  String packed,mask,pending,before,choice,editing;long revision;
  String livePacked()throws Exception{
   String owned=SegmentSession.packed(MainActivity.this);if(!owned.isEmpty())return owned;
   String colors=DeviceState.get(MainActivity.this,"joystick_led_light_picker_color");if(colors.equals("null"))colors="#2BE0D8,#2BE0D8";
   if(!colors.matches("#[A-Fa-f0-9]{6},#[A-Fa-f0-9]{6}"))throw new Exception("Cannot read the current ring colors");
   String brightness=DeviceState.get(MainActivity.this,"led_light_brightness_percent");float level=brightness.equals("null")?.5f:Float.parseFloat(brightness);if(!Float.isFinite(level)||level<0||level>1)throw new Exception("Cannot read current LED brightness");
   StringBuilder out=new StringBuilder();for(String hex:colors.split(",")){int rgb=Integer.parseInt(hex.substring(1),16);String scaled=String.format(Locale.ROOT,"%02X%02X%02X",(int)(((rgb>>16)&255)*level),(int)(((rgb>>8)&255)*level),(int)((rgb&255)*level));for(int i=0;i<4;i++)out.append(scaled);}return out.toString();
  }
  boolean change(int index,boolean on,Switch toggle){
   boolean previous=segmentData.optJSONObject(index).optBoolean("enabled",true);if(previous==on)return false;
   if((busy&&!running)||LedProfileService.isApplying()||!prefs.getBoolean("setupCompleteV2",false)){toggle.setChecked(previous);return false;}
   try{
    synchronized(this){
     if(!running){before=ledJSON().toString();choice=ledChoice;editing=ledEditingName;packed=livePacked();mask=DeviceState.get(MainActivity.this,"joystick_light_enabled");if(!mask.matches("[01],[01]"))mask="0,0";}
     JSONObject part=segmentData.getJSONObject(index);packed=SegmentColors.replace(packed,index,part.getString("color"),part.getInt("brightness"),on);
     if(on){mask=index<4?"1,"+mask.charAt(2):mask.charAt(0)+",1";setProfileLight(index/4,true);}
     part.put("enabled",on);normalizeSegmentRingMask();markLEDsEdited();saveDraft();pending="light-segments "+packed+" "+mask;revision++;
     if(!running){running=true;busy=true;if(ledProfilePicker!=null)ledProfilePicker.setEnabled(false);io.execute(this::drain);}
    }
    return true;
   }catch(Exception e){toggle.setChecked(previous);message(e.getMessage());return false;}
  }
  void drain(){
   String error=null;long applied;
   while(true){String command;synchronized(this){if(pending==null){running=false;applied=revision;break;}command=pending;pending=null;}
    try{Root.run(MainActivity.this,command);DeviceState.ownLED(MainActivity.this,"Custom LEDs");}
    catch(Exception e){error=e.getMessage();synchronized(this){pending=null;running=false;applied=revision;}break;}
   }
   final String failure=error;final long finished=applied;
   runOnUiThread(()->{if(finished!=revision)return;busy=false;if(isFinishing()||isDestroyed())return;if(ledProfilePicker!=null)ledProfilePicker.setEnabled(true);lastLED=DeviceState.ledSignature(MainActivity.this);if(failure!=null){try{loadLED(new JSONObject(before));ledChoice=choice;ledEditingName=editing;saveDraft();}catch(Exception ignored){}lightingStatus="Segment change failed";draw();message(failure);}else{if(ledProfilePicker!=null)ledProfilePicker.setSelection(0);lightingStatus="Segment switches applied";if(lightingStatusView!=null)lightingStatusView.setText(lightingStatus);}});
  }
 }
 void lights(){
  boolean four=Build.MODEL.equals("Odin2");
  String[] labels={"Left stick","Right stick","Left side","Right side"};
  if(!four&&selectedLight>1)selectedLight=0;
  LinearLayout c=card("LED profile","");ledProfilePicker=profileSelector(c,false);
  if(Build.MODEL.equals("Odin3")){Switch sections=new Switch(this);sections.setText("Individual segments");sections.setTextColor(TEXT);tintSwitch(sections);sections.setChecked(segmentMode);sections.setContentDescription("Individual ring segments");c.addView(sections);sections.setOnCheckedChangeListener((v,on)->{if(busy){v.setChecked(segmentMode);return;}if(on&&!prefs.getBoolean("segmentsSupported",false)){v.setChecked(false);message("Run device checks to confirm compatible ring controllers.");return;}if(on&&!segmentMode){try{JSONObject plain=ledJSON();plain.remove("ringSegments");segmentData=RingSegments.uniform(plain);}catch(Exception e){message(e.getMessage());return;}}segmentMode=on;markLEDsEdited();saveDraft();draw();});}
  LinearLayout targets=new LinearLayout(this);
  String mask=profileLightMask();
  for(int target:four?new int[]{2,0,1,3}:new int[]{0,1}){
   LinearLayout light=column();
   light.setGravity(Gravity.CENTER_HORIZONTAL);
   light.addView(new LEDTarget(target,labels[target]),new LinearLayout.LayoutParams(-1,dp(82)));
   Switch enabled=new Switch(this);
   tintSwitch(enabled);
   enabled.setContentDescription(labels[target]+" enabled in profile");
   enabled.setChecked(mask.charAt(target)=='1');
   light.addView(enabled,new LinearLayout.LayoutParams(-2,dp(48)));
   if(segmentMode&&target<2){LinearLayout sections=new LinearLayout(this);for(int pos=0;pos<4;pos++){final int index=target*4+pos,edit=index+4;LinearLayout section=column();Button pick=control(RingSegments.POSITIONS[pos],()->{if(!busy)editLight(edit);},false);pick.setTextSize(11);pick.setContentDescription(lightLabel(edit));GradientDrawable chip=box(Color.parseColor("#"+lightColor(edit)),10);pick.setBackground(chip);pick.setTextColor(Color.luminance(Color.parseColor("#"+lightColor(edit)))>.5?Color.BLACK:Color.WHITE);styleSegmentButton(pick,index);section.addView(pick,new LinearLayout.LayoutParams(-1,dp(48)));Switch active=new Switch(this);tintSwitch(active);active.setChecked(segmentData.optJSONObject(index).optBoolean("enabled",true));active.setContentDescription(lightLabel(edit)+" enabled in profile");section.addView(active,new LinearLayout.LayoutParams(-2,dp(40)));active.setOnCheckedChangeListener((v,on)->{if(segmentSwitches.change(index,on,active)){styleSegmentButton(pick,index);light.getChildAt(0).invalidate();enabled.setChecked(profileLightMask().charAt(target)=='1');}});sections.addView(section,new LinearLayout.LayoutParams(0,-2,1));}light.addView(sections);}
   enabled.setOnCheckedChangeListener((view,on)->{
    if(busy||LedProfileService.isApplying()){
     boolean previous=profileLightMask().charAt(target)=='1';
     if(on!=previous)view.setChecked(previous);
     return;
    }
    setProfileLight(target,on);
    markLEDsEdited();
    ledProfilePicker.setSelection(0);
    saveDraft();
   });
   targets.addView(light,new LinearLayout.LayoutParams(0,-2,1));
  }
  c.addView(targets);
  c.addView(text(segmentMode?"Segment switches apply immediately and retain each section’s color and brightness. Save a profile to keep your choices.":"Use the switches to choose which LEDs the profile turns on. Tap a light to edit its color. Saving a color turns that light on.",12,MUTED));
  lightingStatusView=text(lightingStatus,13,MUTED);c.addView(lightingStatusView);
  profileCard(false);
  action("Turn on LEDs",this::turnOnLEDs,true);
  action("Turn off LEDs",()->lightingTask(()->{String before=DeviceState.led(this);if(!before.equals("Off"))prefs.edit().putString("lastLEDProfile",before).apply();String result=Root.run(this,"light-off");return result;},"LEDs off"),false);

 }
 String profileLightMask(){
  if(!lightMask.isEmpty())return lightMask;
  String sides=sideLights.equals("keep")?readLightFlag("left_handle_light_enabled")+readLightFlag("right_handle_light_enabled"):sideLights.equals("on")?"11":"00";
  return "11"+sides;
 }
 String readLightFlag(String key){return DeviceState.get(this,key).equals("1")?"1":"0";}
 void setProfileLight(int target,boolean enabled){
  StringBuilder mask=new StringBuilder(profileLightMask());
  mask.setCharAt(target,enabled?'1':'0');
  lightMask=mask.toString();
 }
 void turnOnLEDs(){
  if(!prefs.getBoolean("setupCompleteV2",false)){guidedSetup();return;}
  try{
   String request=ProfileActions.ledRequest(ledJSON(),Build.MODEL.equals("Odin2"));
   saveDraft();
   lightingTask(()->Root.run(this,request),"LED profile applied");
  }catch(Exception e){message(e.getMessage());}
 }
 void editLight(int editing){
  editingLight=true;
  String[] labels={"Left stick","Right stick","Left side","Right side"};LinearLayout editor=column();editor.setPadding(dp(24),dp(8),dp(24),dp(8));boolean narrow=getResources().getConfiguration().screenWidthDp<440;
  int initial=DEFAULT_ACCENT;try{initial=Color.parseColor("#"+lightColor(editing));}catch(Exception ignored){}
  ColorWheelView ledWheel=new ColorWheelView(this,initial,TEXT);ledWheel.setContentDescription("LED hue and saturation wheel");LinearLayout controls=new LinearLayout(this);controls.setOrientation(narrow?1:0);controls.addView(ledWheel,new LinearLayout.LayoutParams(dp(176),dp(176)));LinearLayout fields=column();fields.setPadding(narrow?0:dp(20),0,0,0);controls.addView(fields,narrow?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
  TextView swatch=text("",16,TEXT);swatch.setGravity(Gravity.CENTER);swatch.setContentDescription(lightLabel(editing)+" color preview");swatch.setBackground(box(initial,16));fields.addView(swatch,new LinearLayout.LayoutParams(-1,dp(40)));
  EditText rgb=new EditText(this);rgb.setSingleLine();rgb.setTextColor(TEXT);rgb.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);rgb.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);rgb.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});rgb.setContentDescription(lightLabel(editing)+" hex color");fields.addView(rgb,new LinearLayout.LayoutParams(-1,dp(48)));
  final int[] level={lightLevel(editing)};TextView brightnessCaption=text("Brightness: "+level[0]+"%",12,MUTED);fields.addView(brightnessCaption);SeekBar dimmer=new SeekBar(this);tintSlider(dimmer);dimmer.setMax(100);dimmer.setProgress(level[0]);dimmer.setContentDescription("Light brightness");fields.addView(dimmer,new LinearLayout.LayoutParams(-1,dp(40)));editor.addView(controls);
  LinearLayout presets=new LinearLayout(this);for(int color:COLOR_PRESETS){Button preset=colorPreset(color,()->ledWheel.setColor(color));preset.setContentDescription(String.format(Locale.ROOT,"LED preset #%06X",color&0xffffff));presets.addView(preset,new LinearLayout.LayoutParams(0,dp(42),1));}editor.addView(presets);
  RingPreview preview=Build.MODEL.equals("Odin3")&&prefs.getBoolean("setupCompleteV2",false)?new RingPreview(editing,rgb):null;
  final boolean[] updating={false};
  ledWheel.changed=color->{
   String hex=String.format(Locale.ROOT,"#%06X",color&0xffffff);
   if(!updating[0]){
    updating[0]=true;
    rgb.setText(hex);
    updating[0]=false;
   }
   rgb.setError(null);
   swatch.setBackground(box(color,16));
   if(preview!=null)preview.schedule(hex,level[0]);
  };
  rgb.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence v,int a,int n,int f){}
   public void onTextChanged(CharSequence v,int a,int before,int count){}
   public void afterTextChanged(android.text.Editable v){
    if(updating[0]||!v.toString().matches("#?[A-Fa-f0-9]{6}"))return;
    updating[0]=true;
    ledWheel.setColor(Color.parseColor("#"+v.toString().replace("#","")));
    updating[0]=false;
   }
  });
  dimmer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){if(user){level[0]=value;brightnessCaption.setText("Brightness: "+value+"%");if(preview!=null)preview.schedule(rgb.getText().toString(),value);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});ledWheel.setColor(initial);
  LinearLayout footer=new LinearLayout(this);
  footer.setPadding(0,dp(8),0,0);
  Button cancel=control("Cancel",()->{},false);
  Button defaults=control("Default",()->ledWheel.setColor(DEFAULT_ACCENT),false);
  Button save=control("Save",()->{},false);
  Button applyAll=control("Apply to all lights",()->{},false);
  Button[] footerButtons={cancel,defaults,save,applyAll};
  int footerAccent=AppearanceColors.readable(accent,CARD);
  for(Button button:footerButtons){
   GradientDrawable outline=box(CARD,24);
   outline.setStroke(dp(2),footerAccent);
   button.setBackground(outline);
   button.setTextColor(footerAccent);
   button.setPadding(dp(6),dp(4),dp(6),dp(4));
   LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(44),button==applyAll?1.8f:1f);
   if(button!=cancel)size.setMarginStart(dp(6));
   footer.addView(button,size);
  }
  editor.addView(footer);
  ScrollView scroll=new ScrollView(this);scroll.addView(editor);int height=Math.min(dp(460),(int)(getResources().getDisplayMetrics().heightPixels*.55f));scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,height));
  AlertDialog dialog=dialogBuilder().setView(scroll).create();
  ledEditor=dialog;
  activeRingPreview=preview;
  dialog.setOnDismissListener(d->{
   ledEditor=null;
   activeRingPreview=null;
   if(preview!=null)preview.close();
   else editingLight=false;
  });
  dialog.setOnShowListener(d->{
   dialog.getWindow().setBackgroundDrawable(box(CARD,22));dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(600)),-2);
   if(preview!=null)preview.enabled=true;
   cancel.setOnClickListener(v->{if(!busy)dialog.dismiss();});
   java.util.function.Consumer<Boolean> apply=every->{
    if(busy)return;String hex=rgb.getText().toString().replace("#","").toUpperCase(Locale.ROOT);if(!hex.matches("[A-Fa-f0-9]{6}")){rgb.setError("Enter six RGB hex digits");return;}
    final int appliedLevel=level[0];
    Runnable commit=()->{if(segmentMode){try{for(int n=0;n<8;n++)if(every||(editing>=4?n==editing-4:n/4==editing))segmentData.getJSONObject(n).put("color",hex).put("brightness",appliedLevel).put("enabled",true);}catch(Exception e){message(e.getMessage());}}markLEDsEdited();if(every){for(int n=0;n<(Build.MODEL.equals("Odin2")?4:2);n++){lightColor(n,hex);lightBrightness[n]=appliedLevel;}lightMask="1111";}else{lightColor(editing,hex);lightLevel(editing,appliedLevel);setProfileLight(editing>=4?(editing-4)/4:editing,true);}saveDraft();dialog.dismiss();draw();};
    if(preview!=null)preview.pause();
    busy=true;dialog.setCancelable(false);for(Button button:footerButtons)button.setEnabled(false);save.setText("Saving…");
    final boolean analogBefore=ProfileActions.groupOn(this,true),sideBefore=ProfileActions.groupOn(this,false);
    final String request;try{request=colorRequest(editing,hex,appliedLevel,every);}catch(Exception e){busy=false;dialog.setCancelable(true);for(Button button:footerButtons)button.setEnabled(true);save.setText("Save");rgb.setError(e.getMessage());return;}
    io.execute(()->{String error=null;try{Root.run(this,request);if(preview!=null)preview.committed=true;if(every||Build.MODEL.equals("Odin3"))DeviceState.ownLED(this,"Custom LEDs");else DeviceState.ownSingleLED(this,editing,analogBefore,sideBefore);lastLED=DeviceState.ledSignature(this);}catch(Exception e){error=e.getMessage();}final String failure=error;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;if(failure==null){lightingStatus=every?"Lighting colors applied":lightLabel(editing)+" color applied";commit.run();}else{if(preview!=null)preview.enabled=true;dialog.setCancelable(true);for(Button button:footerButtons)button.setEnabled(true);save.setText("Save");rgb.setError(failure);}});});
   };
   save.setOnClickListener(v->apply.accept(false));applyAll.setOnClickListener(v->apply.accept(true));
  });
  dialog.show();
 }
 /** Coalesces picker gestures and restores the stock ring state when dismissed. */
 private final class RingPreview {
  final int ring;
  final EditText field;
  boolean enabled,scheduled,inFlight,closed;
  volatile boolean committed;
  String nextCommand;
  String original,previewSignature,originalSegments="",originalFallback;
  final Runnable send=this::sendPreview;
  void sendPreview(){
   scheduled=false;
   inFlight=true;
   String request=nextCommand;
   io.execute(()->{
    try{
     String current=DeviceState.ledSignature(MainActivity.this);
     if(original==null||!current.equals(previewSignature)){
      originalSegments=SegmentSession.packed(MainActivity.this);
      originalFallback=prefs.getString("segmentStockColors",null);
      original=DeviceState.get(MainActivity.this,"joystick_led_light_picker_color")+" "
       +DeviceState.get(MainActivity.this,"led_light_brightness_percent")+" "
       +DeviceState.get(MainActivity.this,"joystick_light_enabled");
     }
     try{Root.run(MainActivity.this,request);}
     finally{previewSignature=DeviceState.ledSignature(MainActivity.this);}
    }catch(Exception e){runOnUiThread(()->{if(enabled)field.setError(e.getMessage());});}
    finally{runOnUiThread(()->{
     inFlight=false;
     if(enabled&&!request.equals(nextCommand))queue();
    });}
   });
  }
  RingPreview(int ring,EditText field){this.ring=ring;this.field=field;}
  void schedule(String hex,int level){
   if(!enabled||!hex.matches("#?[A-Fa-f0-9]{6}"))return;
   try{nextCommand=colorRequest(ring,hex.replace("#",""),level,false);}catch(Exception e){field.setError(e.getMessage());return;}
   queue();
  }
  void queue(){
   if(!scheduled&&!inFlight){scheduled=true;timer.postDelayed(send,85);}
  }
  void pause(){enabled=false;scheduled=false;timer.removeCallbacks(send);}
  void close(){
   if(closed)return;
   closed=true;
   pause();
   if(committed){editingLight=false;return;}
   io.execute(()->{
    String error=null;
    try{
     if(!committed&&original!=null&&DeviceState.ledSignature(MainActivity.this).equals(previewSignature)){
      String[] state=original.split(" ",-1);
      if(state.length!=3||!state[0].matches("null|#[A-Fa-f0-9]{6},#[A-Fa-f0-9]{6}")
        ||!state[1].matches("null|[0-9]+(\\.[0-9]+)?")||!state[2].matches("null|[01],[01]"))
       throw new IllegalStateException("Invalid original ring settings");
      if(!originalSegments.isEmpty()){
       if(originalFallback!=null)prefs.edit().putString("segmentStockColors",originalFallback).commit();
       Root.run(MainActivity.this,"light-segments "+originalSegments+" "+(state[2].equals("null")?"0,0":state[2]));
      }else Root.run(MainActivity.this,"light-ring-restore "+Root.q(state[0])+" "+state[1]+" "+state[2]);
     }
    }catch(Exception e){error=e.getMessage();}
    final String failure=error;
    runOnUiThread(()->{editingLight=false;if(failure!=null&&!isDestroyed()&&!isFinishing())message(failure);});
   });
  }
 }
 String lightingRequest(String[] colors,int[] levels,String sides){
  if(Build.MODEL.equals("Odin2"))return "light-zones "+colors[0]+" "+colors[1]+" "+colors[2]+" "+colors[3]+" "+levels[0]+" "+levels[1]+" "+levels[2]+" "+levels[3]+" "+sides;
  String[] scaled=new String[2];for(int n=0;n<2;n++){int c=Color.parseColor("#"+colors[n]);scaled[n]=String.format(Locale.ROOT,"%02X%02X%02X",Color.red(c)*levels[n]/100,Color.green(c)*levels[n]/100,Color.blue(c)*levels[n]/100);}return "light "+scaled[0]+" "+scaled[1]+" 100";
 }
 void lightingTask(Work action,String success){
  if(busy)return;final String appliedChoice=ledChoice;busy=true;if(ledProfilePicker!=null)ledProfilePicker.setEnabled(false);lightingStatus="Working…";if(lightingStatusView!=null)lightingStatusView.setText(lightingStatus);
  io.execute(()->{String error=null;try{action.run();DeviceState.ownLED(this,success.equals("LEDs off")?"Off":appliedChoice.equals("Current LEDs")?"Custom LEDs":appliedChoice);lastLED=DeviceState.ledSignature(this);}catch(Exception e){error="Could not complete: "+e.getMessage();}final String failure=error;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;if(ledProfilePicker!=null)ledProfilePicker.setEnabled(true);lightingStatus=failure==null?success:"Lighting change could not be completed";if(lightingStatusView!=null)lightingStatusView.setText(lightingStatus);if(failure!=null)message(failure);});});
 }
 EditText colorField(LinearLayout c,String label,String value){c.addView(text(label,15,TEXT));EditText e=new EditText(this);e.setSingleLine();e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setTextColor(TEXT);e.setText(value);e.setSelectAllOnFocus(true);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});e.setContentDescription(label);c.addView(e);return e;}
 ArrayList<String> savedNames(boolean fan){ArrayList<String> names=new ArrayList<>();String prefix=fan?"fan:":"led:";for(String key:prefs.getAll().keySet())if(key.startsWith(prefix))names.add(key.substring(prefix.length()));Collections.sort(names,String.CASE_INSENSITIVE_ORDER);return names;}
 ArrayList<String> choices(boolean fan){ArrayList<String> names=new ArrayList<>();if(fan)Collections.addAll(names,"Custom curve","Quiet · stock","Smart · stock","Sport · stock");else names.add("Current LEDs");names.addAll(savedNames(fan));return names;}
 Spinner profileSelector(LinearLayout c,boolean fan){ArrayList<String> names=choices(fan);Spinner picker=new Spinner(this){final Paint arrow=new Paint(3);@Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);arrow.setColor(MUTED);float x=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?dp(20):getWidth()-dp(20),y=getHeight()/2f;Path path=new Path();path.moveTo(x-dp(5),y-dp(3));path.lineTo(x+dp(5),y-dp(3));path.lineTo(x,y+dp(3));path.close();canvas.drawPath(path,arrow);}};picker.setContentDescription(fan?"Fan profile":"LED profile");GradientDrawable outline=box(SURFACE,12);outline.setStroke(dp(1),MUTED);picker.setBackground(outline);picker.setPaddingRelative(dp(12),0,dp(36),0);String[] shown=new String[names.size()];for(int n=0;n<shown.length;n++)shown[n]=fan?ProfileActions.displayName(names.get(n)):names.get(n);picker.setAdapter(adapter(shown));String selection=fan?fanChoice:ledChoice;picker.setSelection(Math.max(0,names.indexOf(selection)));c.addView(picker,new LinearLayout.LayoutParams(-1,dp(48)));picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int index,long id){String name=names.get(index);if(name.equals(fan?fanChoice:ledChoice))return;if(!fan&&(busy||LedProfileService.isApplying())){picker.setSelection(Math.max(0,names.indexOf(ledChoice)));return;}try{selectProfile(fan,name);saveDraft();draw();if(!fan&&!name.equals("Current LEDs"))turnOnLEDs();}catch(Exception e){picker.setSelection(Math.max(0,names.indexOf(fan?fanChoice:ledChoice)));message("Profile could not be loaded: "+e.getMessage());}}});return picker;}
 void selectProfile(boolean fan,String name)throws Exception{if(fan){int stock=name.equals("Quiet · stock")?1:name.equals("Smart · stock")?4:name.equals("Sport · stock")?5:-1;if(stock>=0){fanChoice=name;selectedStock=stock;kind="curve";return;}if(name.equals("Custom curve")){fanChoice=name;selectedStock=-1;kind="curve";return;}loadFan(new JSONObject(prefs.getString("fan:"+name,"{}")));fanChoice=name;}else{ledChoice=name;ledEditingName=name.equals("Current LEDs")?"":name;if(!name.equals("Current LEDs"))loadLED(new JSONObject(prefs.getString("led:"+name,"{}")));}}
 JSONObject fanJSON()throws JSONException{return new JSONObject().put("points",points).put("kind",kind).put("manual",manual).put("zone",zone).put("stockMode",selectedStock);}
 JSONObject ledJSON()throws JSONException{return withSegments(new JSONObject().put("left",left).put("right",right).put("sideLeft",sideLeft).put("sideRight",sideRight).put("sideLights",sideLights).put("lightMask",lightMask).put("lightingZones",1).put("lightBrightness",new JSONArray().put(lightBrightness[0]).put(lightBrightness[1]).put(lightBrightness[2]).put(lightBrightness[3])));}
 void loadFan(JSONObject j){String candidate=j.optString("points",CurvePoints.defaults());CurvePoints.parse(candidate,true);int requested=FanCurve.manual(j.optInt("manual",55),true);String requestedKind=j.optString("kind","curve").equals("manual")?"manual":"curve";points=candidate;kind=requestedKind;manual=requested;zone=prefs.getString("oemZone",zone);selectedStock=-1;}
 void loadLED(JSONObject j){if(RingSegments.present(j)&&!Build.MODEL.equals("Odin3"))throw new IllegalArgumentException("This profile uses Odin 3 ring segments.");left=j.optString("left","FF8000");right=j.optString("right",left);sideLeft=j.optString("sideLeft",left);sideRight=j.optString("sideRight",right);lightMask=ProfileActions.lightMask(j);sideLights=j.optString("sideLights","keep");if(!sideLights.equals("on")&&!sideLights.equals("off"))sideLights="keep";JSONArray levels=j.optJSONArray("lightBrightness");for(int n=0;n<4;n++)lightBrightness[n]=Math.max(0,Math.min(100,levels==null?j.optInt("brightness",50):levels.optInt(n,50)));loadSegments(j);}
 void profileCard(boolean fan){LinearLayout c=card("Save profile","");EditText name=new EditText(this);name.setSingleLine();name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);name.setTextColor(TEXT);name.setHintTextColor(MUTED);name.setHint(fan?"Fan profile name":"LED profile name");name.setContentDescription(fan?"Fan profile name":"LED profile name");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});String current=fan?fanChoice:ledChoice;if(savedNames(fan).contains(current))name.setText(current);else if(!fan&&savedNames(false).contains(ledEditingName))name.setText(ledEditingName);c.addView(name);
  button(c,fan?"Save & apply fan profile":"Save LED profile",()->{try{String key=name.getText().toString().trim();if(fan&&key.matches("(?i)(quiet|smart|sport)"))throw new IllegalArgumentException("Stock profiles are read-only. Choose a different name for your custom profile.");if(key.isEmpty())throw new IllegalArgumentException("Enter a profile name");if(key.equals("Custom curve")||key.equals("Current LEDs")||key.endsWith(" · stock"))throw new IllegalArgumentException("Choose a different profile name");if(fan){validate();prefs.edit().putString("fan:"+key,fanJSON().toString()).apply();fanChoice=key;}else{for(int n=0;n<4;n++)if(!lightColor(n).matches("[A-Fa-f0-9]{6}"))throw new IllegalArgumentException("Use valid LED colors");lightMask=profileLightMask();prefs.edit().putString("led:"+key,ledJSON().toString()).apply();ledChoice=key;ledEditingName=key;}saveDraft();draw();DeviceState.refreshTiles(this);if(fan)applyFan();else lightingStatus="LED profile saved";}catch(Exception e){message(e.getMessage());}},true);
  c.addView(text(fan?"Save the temperature curve, selected sensor and optional manual speed as a fan profile. Saving also applies it; editing an active profile updates it without a restart.":"Save each LED's on/off state, color and brightness separately from fan settings. Selecting a saved LED profile above applies it immediately.",12,MUTED));
  String selected=fan?fanChoice:ledChoice;if(savedNames(fan).contains(selected))describedButton(c,"Delete profile","Remove this saved "+(fan?"fan":"LED")+" profile. The current hardware settings stay as they are.",()->dialogBuilder().setTitle("Delete "+selected+"?").setPositiveButton("Delete",(d,w)->{prefs.edit().remove((fan?"fan:":"led:")+selected).apply();if(fan)fanChoice="Custom curve";else ledChoice="Current LEDs";saveDraft();draw();}).setNegativeButton("Cancel",null).show());
 }
 void migrateProfiles(){if(prefs.getBoolean("separateProfilesV1",false))return;android.content.SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith("saved:")){try{JSONObject j=new JSONObject(prefs.getString(key,"{}"));JSONObject f=new JSONObject();for(String field:new String[]{"kind","manual","zone"})if(j.has(field))f.put(field,j.get(field));f.put("points",legacyPoints(j));edit.putString("fan:"+key.substring(6),f.toString());JSONObject led=new JSONObject();for(String field:new String[]{"left","right","sideLeft","sideRight","sideLights","brightness","lightBrightness","lightingZones"})if(j.has(field))led.put(field,j.get(field));edit.putString("led:"+key.substring(6),led.toString());}catch(Exception ignored){}}edit.putBoolean("separateProfilesV1",true).apply();}
 void markLEDsEdited(){if(savedNames(false).contains(ledChoice))ledEditingName=ledChoice;ledChoice="Current LEDs";}
 void normalizeLEDChoice(){if(ledChoice.equals("Current LEDs"))return;try{String saved=prefs.getString("led:"+ledChoice,null);if(saved==null){markLEDsEdited();return;}JSONObject j=new JSONObject(saved);String[] values={j.optString("left","FF8000"),j.optString("right",j.optString("left","FF8000")),j.optString("sideLeft",j.optString("left","FF8000")),j.optString("sideRight",j.optString("right","FF8000"))};JSONArray b=j.optJSONArray("lightBrightness");boolean same=lightMask.equals(ProfileActions.lightMask(j))&&sideLights.equals(j.optString("sideLights","keep"));for(int n=0;n<4;n++)same&=lightColor(n).equalsIgnoreCase(values[n])&&lightBrightness[n]==(b==null?j.optInt("brightness",50):b.optInt(n,50));same&=segmentMode==(j.optJSONArray("ringSegments")!=null);if(segmentMode)same&=segmentData.toString().equals(RingSegments.read(j).toString());if(!same)markLEDsEdited();}catch(Exception e){markLEDsEdited();}}
 String legacyPoints(JSONObject j){try{int a=j.optInt("off",45),b=j.optInt("on",50),c=j.optInt("full",75),lo=j.optInt("low",50),hi=j.optInt("high",70);FanCurve.validate(a,b,c,lo,hi);String value=a+":0,"+b+":"+lo+","+c+":"+hi;CurvePoints.parse(value);return value;}catch(Exception e){return CurvePoints.defaults();}}
 void openQuickProfile(){String type=getIntent().getStringExtra("quickProfile");if(type==null||!prefs.getBoolean("setupCompleteV2",false))return;getIntent().removeExtra("quickProfile");if(!java.util.Arrays.asList("led","fan","cpu").contains(type)){message("Unknown profile picker.");return;}if(type.equals("cpu")){tab="Performance";draw();performance.quickProfiles();return;}boolean fan=type.equals("fan");tab=fan?"Fan":"LEDs";draw();ArrayList<String> list=fan?choices(true):savedNames(false);if(fan)list.remove("Custom curve");if(list.isEmpty()){message("Save an LED profile in the LEDs tab to use it from Raven LED.");return;}dialogBuilder().setTitle(fan?"Raven Fan · "+DeviceState.fan(this):"Raven LED · "+DeviceState.led(this)).setItems(list.toArray(new String[0]),(d,n)->{try{selectProfile(fan,list.get(n));saveDraft();draw();if(fan)applyFan();else turnOnLEDs();}catch(Exception e){message(e.getMessage());}}).setNegativeButton("Cancel",null).show();}
 void syncFan(){String selected=prefs.getString("tileFanSelection","");if(!selected.isEmpty()){prefs.edit().remove("tileFanSelection").apply();try{loadFan(new JSONObject(prefs.getString("fan:"+selected,"{}")));fanChoice=selected;saveDraft();if(tab.equals("Fan")&&!busy&&!editingLight)draw();}catch(Exception ignored){}}String mode=DeviceState.get(this,"fan_mode");if(FanService.running){lastFan="";return;}if(mode.equals(lastFan))return;lastFan=mode;String name=DeviceState.stockName(mode);if(name.equals("Quiet")||name.equals("Smart")||name.equals("Sport")){fanChoice=name+" · stock";selectedStock=Integer.parseInt(mode);kind="curve";saveDraft();if(tab.equals("Fan")&&!busy&&!editingLight)draw();}}
 void syncLEDs(){if(prefs==null||busy||editingLight||LedProfileService.isApplying())return;String chosen=prefs.getString("tileLEDSelection","");if(!chosen.isEmpty()){prefs.edit().remove("tileLEDSelection").apply();try{loadLED(new JSONObject(prefs.getString("tileLEDConfig",prefs.getString("led:"+chosen,"{}"))));prefs.edit().remove("tileLEDConfig").apply();ledChoice=chosen;if(savedNames(false).contains(chosen))ledEditingName=chosen;lastLED=DeviceState.ledSignature(this);saveDraft();if(tab.equals("LEDs"))draw();}catch(Exception ignored){}return;}String signature=DeviceState.ledSignature(this);if(signature.equals(lastLED))return;if(SegmentSession.owned(this)){lastLED=signature;prefs.edit().putString("ledObserved",signature).apply();return;}lastLED=signature;prefs.edit().putString("ledObserved",signature).putString("activeLED","System LEDs").apply();ledChoice="Current LEDs";ledEditingName="";try{if(Build.MODEL.equals("Odin2")){String raw=DeviceState.get(this,"led_light_picker_color");if(!raw.equals("null")){String color=String.format(Locale.ROOT,"%06X",Long.parseLong(raw)&0xffffff);left=right=sideLeft=sideRight=color;}String level=DeviceState.get(this,"led_light_brightness_percent");if(!level.equals("null"))Arrays.fill(lightBrightness,Math.max(0,Math.min(100,Math.round(Float.parseFloat(level)*100))));lightMask=readLightFlag("left_joystick_light_enabled")+readLightFlag("right_joystick_light_enabled")+readLightFlag("left_handle_light_enabled")+readLightFlag("right_handle_light_enabled");}else{String rawColors=DeviceState.get(this,"joystick_led_light_picker_color");String[] colors=(rawColors.equals("null")?"#2BE0D8,#2BE0D8":rawColors).split(",");if(colors.length==2){left=colors[0].replace("#","");right=colors[1].replace("#","");}String rings=DeviceState.get(this,"joystick_light_enabled");if(rings.matches("[01],[01]"))lightMask=rings.replace(",","")+"00";}segmentMode=false;saveDraft();lightingStatus="System LED settings updated";DeviceState.refreshTiles(this);if(tab.equals("LEDs"))draw();}catch(Exception ignored){}}
 void device(){
  LinearLayout c=card("Device checks","");
  c.addView(text(Build.MANUFACTURER+" · "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\n"+Build.DISPLAY,14,MUTED));
  describedButton(c,"Run device checks","Read stock controls, fan interfaces and temperature sensors without changing them.",this::checkHardware);describedButton(c,"Read fan status","Read the current fan controller status.",()->task(()->Root.run(this,"status")));
  TextView result=text(report,14,TEXT);result.setTextIsSelectable(true);c.addView(result);

 }
 void checkHardware(){task(this::readHardware);}
 String readHardware()throws Exception{
  if(Build.MODEL.equals("Odin3")){boolean compatible=false;try{compatible=Root.run(this,"light-segments-probe").startsWith("OK");}catch(Exception ignored){}prefs.edit().putBoolean("segmentsSupported",compatible).apply();}
  checked=false;String probe=Root.run(this,"probe");
  String oem=Root.run(this,"oem-sensor").substring(3).trim();if(!FanService.validTemperatureSource(oem))throw new Exception("OEM sensor unavailable");zone=oem;prefs.edit().putString("oemZone",zone).apply();
  String sensorText=Root.read("for z in /sys/class/thermal/thermal_zone*; do echo ${z##*zone}:$(cat $z/type):$(cat $z/temp 2>/dev/null); done");
  ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();
  for(String line:sensorText.split("\n")){String[] p=line.trim().split(":",3);if(p.length!=3||!p[0].matches("[0-9]{1,3}"))continue;
   try{double n=Double.parseDouble(p[2]);if(n>=1000)n/=1000;if(n>0&&n<100){ids.add(p[0]);names.add(sensorLabel(p[1])+" · "+String.format(Locale.US,"%.1f°C",n)+" · zone "+p[0]);}}catch(Exception ignored){}
  }
  synchronized(this){sensors=ids;sensorNames=names;checked=!ids.isEmpty();report=probe+"\n\n"+ids.size()+" readable temperature sensors. Fan temperature source: "+("cpu-average".equals(zone)?"CPU thermal sensor average":"thermal_zone"+zone)+". Checks do not change your fan or lights.";}
  return report;
 }
 String sensorLabel(String name){String lower=name.toLowerCase(Locale.ROOT);if(lower.contains("pwr"))return "Device temperature ("+name+")";if(lower.contains("cpu")||lower.contains("tsens"))return "Processor sensor ("+name+")";if(lower.contains("battery"))return "Battery sensor";return name;}
 String restoreLighting()throws Exception{String result=Root.run(this,"restore-light");lightingStatus="Original LEDs restored";DeviceState.ownLED(this,"Factory LEDs");lastLED=DeviceState.ledSignature(this);return result;}
 interface Work{String run()throws Exception;}
 void task(Work action){if(busy)return;busy=true;AlertDialog progress=dialogBuilder().setMessage("Working…").setCancelable(false).create();progress.show();io.execute(()->{String s;try{s=action.run();}catch(Exception e){s="Could not complete: "+e.getMessage();}final String result=DeviceState.backupDate(s);runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;progress.dismiss();message(result);if(lightingStatusView!=null)lightingStatusView.setText(lightingStatus);if(tab.equals("Device")){if(!checked)report=result;draw();}});});}
 void message(String s){dialogBuilder().setMessage(DeviceState.backupDate(s)).setPositiveButton("OK",null).show();}
 android.widget.ArrayAdapter<String> adapter(String[] items){android.widget.ArrayAdapter<String> a=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);return a;}
 void validate(){if(!extendedFanRange()&&CurvePoints.needsExtended(points,manual))throw new IllegalArgumentException("Enable Extended fan speed range in Settings → Advanced to apply this profile.");CurvePoints.parse(points,extendedFanRange());FanCurve.manual(manual,extendedFanRange());}
 JSONObject json()throws JSONException{return withSegments(new JSONObject().put("points",points).put("stockMode",selectedStock).put("fanChoice",fanChoice).put("ledChoice",ledChoice).put("ledEditingName",ledEditingName).put("off",off).put("on",on).put("full",full).put("low",low).put("high",high).put("manual",manual).put("brightness",lightBrightness[0]).put("lightBrightness",new JSONArray().put(lightBrightness[0]).put(lightBrightness[1]).put(lightBrightness[2]).put(lightBrightness[3])).put("left",left).put("right",right).put("kind",kind).put("zone",zone).put("sideLights",sideLights).put("lightMask",lightMask).put("lightingZones",1).put("sideLeft",sideLeft).put("sideRight",sideRight));}
 void load(JSONObject j){points=j.optString("points",legacyPoints(j));try{CurvePoints.parse(points,true);}catch(Exception ignored){points=CurvePoints.defaults();}selectedStock=j.optInt("stockMode",-1);fanChoice=j.optString("fanChoice","Custom curve");ledChoice=j.optString("ledChoice","Current LEDs");ledEditingName=j.optString("ledEditingName","");off=j.optInt("off",45);on=j.optInt("on",50);full=j.optInt("full",75);low=j.optInt("low",50);high=j.optInt("high",70);manual=j.optInt("manual",55);try{FanCurve.manual(manual,true);}catch(Exception e){manual=55;}brightness=j.optInt("brightness",50);left=j.optString("left","FF8000");right=j.optString("right","FF8000");kind=j.optString("kind","curve");zone=j.optString("zone","");lightMask=ProfileActions.lightMask(j);sideLights=j.optString("sideLights","keep");if(!sideLights.equals("on")&&!sideLights.equals("off"))sideLights="keep";if(j.optInt("lightingZones",0)==0&&Build.MODEL.equals("Odin2"))right=left;sideLeft=j.optString("sideLeft",left);sideRight=j.optString("sideRight",right);JSONArray levels=j.optJSONArray("lightBrightness");for(int n=0;n<4;n++)lightBrightness[n]=Math.max(0,Math.min(100,levels==null?brightness:levels.optInt(n,brightness)));loadSegments(j);normalizeLEDChoice();}
 void saveDraft(){try{prefs.edit().putString("draft",json().toString()).apply();}catch(Exception ignored){}}
 final class LEDTarget extends View {
  final Paint paint=new Paint(3);final int target;final String label;
  LEDTarget(int target,String label){super(MainActivity.this);this.target=target;this.label=label;setContentDescription("Select "+label);setFocusable(true);setClickable(true);setOnClickListener(v->{if(!busy){selectedLight=target;editLight(target);}});}
  @Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=dp(29),radius=dp(18);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff101719);if(target<2)c.drawCircle(x,y,radius+dp(6),paint);try{paint.setColor(Color.parseColor("#"+lightColor(target)));}catch(Exception e){paint.setColor(accent);}paint.setAlpha(lightBrightness[target]*255/100);
   if(target<2){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(7));if(segmentMode){for(int pos=0;pos<4;pos++){JSONObject part=segmentData.optJSONObject(target*4+pos);paint.setColor(part.optBoolean("enabled",true)?Color.parseColor("#"+part.optString("color","FF8000")):0xff101719);paint.setAlpha(part.optBoolean("enabled",true)?part.optInt("brightness",50)*255/100:255);c.drawArc(new RectF(x-radius,y-radius,x+radius,y+radius),180+pos*90+3,84,false,paint);}}else c.drawCircle(x,y,radius,paint);}else c.drawRoundRect(x-dp(5),y-radius-dp(6),x+dp(5),y+radius+dp(6),dp(5),dp(5),paint);
   paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setColor(TEXT);paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);paint.setTextAlign(Paint.Align.CENTER);float top=y+radius+dp(15);c.drawText(label,x,top-paint.getFontMetrics().top,paint);
  }
 }
}
