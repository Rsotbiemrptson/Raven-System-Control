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
 private static final int BG=0xff131519,SURFACE=0xff1f2228,CARD=0xff272b32,TEXT=0xfff1f2f6,MUTED=0xffa9b0bc,ACCENT=0xff15a5fc;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private LinearLayout root,body,nav,actions;private TextView session;
 private android.content.SharedPreferences prefs;
 private boolean busy=false,checked=false;
 private String tab="Fan",report="Hardware has not been checked. Profiles can be edited without a device.",zone="";
 private ArrayList<String> sensors=new ArrayList<>(),sensorNames=new ArrayList<>();
 private int off=45,on=50,full=75,low=50,high=70,manual=55,brightness=50;
 private String left="2BE0D8",right="B68CFF",kind="curve";
 private Handler timer=new Handler(Looper.getMainLooper());
 private final Runnable tick=new Runnable(){public void run(){if(session!=null)session.setText(FanService.status);timer.postDelayed(this,2000);}};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);prefs=getSharedPreferences("profile",0);
  try{load(new JSONObject(prefs.getString("draft","{}")));}catch(Exception ignored){}
  if(state!=null)tab=state.getString("tab","Fan");checked=false;draw();
 }
 @Override public void onResume(){super.onResume();timer.post(tick);}
 @Override public void onPause(){timer.removeCallbacks(tick);saveDraft();super.onPause();}
 @Override public void onDestroy(){io.shutdown();super.onDestroy();}
 @Override public void onSaveInstanceState(Bundle out){saveDraft();out.putString("tab",tab);super.onSaveInstanceState(out);}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);draw();}
 int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(6));return t;}
 GradientDrawable box(int color){return box(color,18);}
 GradientDrawable box(int color,int radius){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(radius));return b;}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 LinearLayout card(String title,String detail){LinearLayout c=column();c.setPadding(dp(18),dp(12),dp(18),dp(14));c.setBackground(box(CARD));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(8));body.addView(c,lp);TextView heading=text(title,18,TEXT);heading.setTypeface(null,Typeface.BOLD);c.addView(heading);if(!detail.isEmpty())c.addView(text(detail,12,MUTED));return c;}
 Button control(String label,Runnable action,boolean primary){
  Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?BG:TEXT);b.setBackground(box(primary?ACCENT:CARD,24));b.setStateListAnimator(null);b.setElevation(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->{if(!busy)action.run();});return b;
 }
 void button(LinearLayout parent,String label,Runnable action,boolean primary){
  Button b=control(label,action,primary);if(!primary)b.setBackground(box(SURFACE,24));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(8),0,0);parent.addView(b,lp);
 }
 void action(String label,Runnable run,boolean primary){Button b=control(label,run,primary);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);if(actions.getChildCount()>0)lp.setMargins(dp(8),0,0,0);actions.addView(b,lp);actions.setVisibility(View.VISIBLE);}
 interface Change{void set(int n);}
 void slider(LinearLayout c,String label,int min,int max,int value,Change change){
  TextView caption=text(label+": "+value,15,TEXT);c.addView(caption);SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(value-min);bar.setContentDescription(label);c.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));
  bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int v,boolean user){if(user){change.set(v+min);caption.setText(label+": "+(v+min));saveDraft();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
 }
 void draw(){
  boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
  session=null;root=new LinearLayout(this);root.setOrientation(landscape?0:1);root.setBackgroundColor(BG);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|(Build.VERSION.SDK_INT>=35?WindowInsets.Type.ime():0));v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});root.requestApplyInsets();
  nav=new LinearLayout(this);nav.setOrientation(landscape?1:0);nav.setPadding(dp(12),dp(12),dp(12),dp(12));nav.setBackgroundColor(SURFACE);
  if(landscape){
   LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);ImageView emblem=new ImageView(this);emblem.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));emblem.setContentDescription("Raven System Control");brand.addView(emblem,new LinearLayout.LayoutParams(dp(40),dp(40)));TextView name=text("Raven\nSystem Control",16,TEXT);name.setTypeface(null,Typeface.BOLD);name.setPadding(dp(7),0,0,0);brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));nav.addView(brand,new LinearLayout.LayoutParams(-1,-2));View gap=new View(this);nav.addView(gap,new LinearLayout.LayoutParams(1,dp(22)));root.addView(nav,new LinearLayout.LayoutParams(dp(156),-1));
  }
  LinearLayout page=column();page.setPadding(dp(22),dp(12),dp(22),dp(12));root.addView(page,new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,1));
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);LinearLayout names=column();if(!landscape)names.addView(text("Raven System Control",12,MUTED));TextView title=text(tab.equals("Fan")?"Fan control":tab.equals("Lighting")?"Stick lighting":tab.equals("Profiles")?"Saved profiles":"Device & recovery",25,TEXT);title.setTypeface(null,Typeface.BOLD);names.addView(title);names.addView(text(tab.equals("Fan")?"Tune cooling and review your session":tab.equals("Lighting")?"Preview colors before applying":tab.equals("Profiles")?"Save a draft or load your preferences":"Compatibility checks and stock restoration",12,MUTED));header.addView(names,new LinearLayout.LayoutParams(0,-2,1));
  Button menu=control("⋮",()->{},false);menu.setTextSize(26);menu.setContentDescription("More options");menu.setOnClickListener(v->showMenu(v));header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(44)));page.addView(header);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=column();body.setPadding(0,dp(6),0,0);scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  actions=new LinearLayout(this);actions.setPadding(0,dp(8),0,0);actions.setVisibility(View.GONE);page.addView(actions,new LinearLayout.LayoutParams(-1,-2));
  TextView foot=text("PREVIEW 0.1.6 · "+Build.MODEL+" · Changes apply when you choose Start or Apply",11,MUTED);foot.setMaxLines(2);page.addView(foot);
  for(String nameTab:new String[]{"Fan","Lighting","Profiles","Device"}){Button b=control(nameTab,()->{saveDraft();tab=nameTab;draw();},nameTab.equals(tab));LinearLayout.LayoutParams lp=landscape?new LinearLayout.LayoutParams(-1,dp(48)):new LinearLayout.LayoutParams(0,dp(48),1);lp.setMargins(landscape?0:dp(3),0,landscape?0:dp(3),landscape?dp(8):0);nav.addView(b,lp);}
  if(landscape){nav.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));nav.addView(text("Odin 2 / Odin 3\nStock controls",11,MUTED));}else root.addView(nav,new LinearLayout.LayoutParams(-1,dp(72)));
  if(tab.equals("Fan"))fan();else if(tab.equals("Lighting"))lights();else if(tab.equals("Profiles"))profiles();else device();
 }
 void showMenu(View anchor){
  if(busy)return;PopupMenu menu=new PopupMenu(this,anchor);String[] labels={"Hardware checks","Stop fan & restore","Restore ring settings","About"};for(int i=0;i<labels.length;i++)menu.getMenu().add(0,i,0,labels[i]);menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){case 0:saveDraft();tab="Device";draw();checkHardware();break;case 1:stopFan();break;case 2:task(()->Root.run(this,"restore-light"));break;default:message("Raven System Control 0.1.6-preview\n\nFan curves, stick lighting and local profiles for stock Odin systems.\n\nOdin 2 firmware 400 uses one shared ring color. Odin 3 hardware behavior still needs testing.");}return true;});menu.show();
 }
 void fan(){
  LinearLayout c=card("Cooling session", "Settings below are drafts until you start a session. No automatic startup after reboot.");
  session=text(FanService.status,16,ACCENT);c.addView(session);
  Spinner mode=new Spinner(this);mode.setContentDescription("Fan control mode");mode.setAdapter(adapter(new String[]{"Temperature curve","Manual speed"}));mode.setSelection(kind.equals("manual")?1:0);c.addView(mode);mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String next=pos==1?"manual":"curve";if(!kind.equals(next)){kind=next;saveDraft();draw();}}public void onNothingSelected(AdapterView<?> p){}});
  button(c,"Choose temperature sensor",this::chooseSensor,false);c.addView(text(zone.isEmpty()?"No sensor selected":selectedSensor(),14,MUTED));
  if(kind.equals("curve")){c=card("Temperature curve","Fan turns on at the upper threshold and off at the lower threshold. Minimum running time: 20 seconds.");
  slider(c,"Turn off at °C",35,65,off,n->off=n);slider(c,"Turn on at °C",40,70,on,n->on=n);slider(c,"Reach upper speed at °C",50,78,full,n->full=n);
  slider(c,"Starting PWM %",50,70,low,n->low=n);slider(c,"Upper PWM %",50,70,high,n->high=n);
  button(c,"Preview curve",()->{try{validate();curvePreview();}catch(Exception e){message(e.getMessage());}},false);
  }else{c=card("Manual speed","Stock manual range: 50–70% PWM. PWM is a requested duty, not an RPM measurement.");slider(c,"Manual PWM %",50,70,manual,n->manual=n);}
  card("Session safeguards","Start requires successful hardware checks and a selected sensor. Sensor loss, ≥80°C, a user switch or a missed heartbeat requests stock automatic cooling.");
  action("Start fan session",this::startFan,true);
  action("Stop & restore",this::stopFan,false);
 }
 void chooseSensor(){if(!checked||sensors.isEmpty()){message("Run hardware checks in Device first. Then select the sensor appropriate for your temperature curve.");return;}
  new AlertDialog.Builder(this).setTitle("Temperature sensor").setItems(sensorNames.toArray(new String[0]),(d,n)->{zone=sensors.get(n);saveDraft();draw();}).setNegativeButton("Cancel",null).show();
 }
 String selectedSensor(){int i=sensors.indexOf(zone);return i>=0?sensorNames.get(i):"Previously selected thermal zone "+zone+" · recheck before use";}
 void startFan(){
  try{validate();if(!checked)throw new IllegalArgumentException("Run hardware checks in Device first");if(!sensors.contains(zone))throw new IllegalArgumentException("Choose a sensor from the current hardware check");if(FanService.running)throw new IllegalArgumentException("Stop the current session before applying a new profile");}
  catch(Exception e){message(e.getMessage());return;}
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("fan","Fan control",NotificationManager.IMPORTANCE_LOW));
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},5);return;}
  if(!FanService.notificationsAvailable(this)){
   new AlertDialog.Builder(this).setMessage("Enable fan control notifications so the session and Stop action stay visible. Then return and press Start again.")
    .setPositiveButton("Notification settings",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())))
    .setNegativeButton("Cancel",null).show();return;
  }
  new AlertDialog.Builder(this).setTitle("Start this fan profile?").setMessage("Sensor: "+selectedSensor()+"\n\nThis is a hardware-untested preview. The selected sensor determines the curve. The visible session stops with the notification or Stop & restore.").setPositiveButton("Start",(d,w)->{
   Intent i=new Intent(this,FanService.class).putExtra("kind",kind).putExtra("zone",Integer.parseInt(zone)).putExtra("off",off).putExtra("on",on).putExtra("full",full).putExtra("low",low).putExtra("high",high).putExtra("manual",manual);
   startForegroundService(i);message("Starting… status appears above.");
  }).setNegativeButton("Cancel",null).show();
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
  super.onRequestPermissionsResult(request,permissions,results);
  if(request==5)message(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?"Notifications enabled. Press Start fan session again when ready.":"Fan session was not started. Enable notifications in Android app settings to keep the session and Stop action visible.");
 }
 void stopFan(){startService(new Intent(this,FanService.class).setAction("stop"));message("Stopping… the session status will confirm the result.");}
 void curvePreview(){
  LinearLayout view=column();view.setPadding(dp(20),dp(10),dp(20),dp(10));TextView value=text("",18,TEXT);view.addView(value);final boolean[] was={false};
  slider(view,"Simulated temperature °C",25,85,25,n->{int p=FanCurve.percent(n,was[0],off,on,full,low,high);was[0]=p>0;value.setText(p<0?"Request stock automatic cooling":p==0?"Fan off":p+"% PWM requested");});
  value.setText("Fan off · slide up and down to see hysteresis");
  view.addView(text("Shows temperature thresholds and hysteresis. The live session also holds the fan on for at least 20 seconds; elapsed time is not simulated here.",14,MUTED));
  new AlertDialog.Builder(this).setTitle("Curve preview · no device writes").setView(view).setPositiveButton("Done",null).show();
 }
 void lights(){
  boolean shared=Build.MODEL.equals("Odin2");
  LinearLayout c=card("Stick lighting",shared?"Odin 2 firmware 400 uses one shared stock color for both rings. Shared color and brightness also affect any enabled handle lights. Changes apply only when you press Apply.":"Separate RGB colors for the left and right rings. Changes apply only when you press Apply.");
  RingPreview rings=new RingPreview(this);c.addView(rings,new LinearLayout.LayoutParams(-1,dp(140)));
  EditText l=colorField(c,shared?"Both rings · RGB":"Left ring · RGB",left),r=shared?null:colorField(c,"Right ring · RGB",right);
  android.text.TextWatcher watch=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int n,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){left=l.getText().toString().replace("#","").toUpperCase(Locale.ROOT);if(r!=null)right=r.getText().toString().replace("#","").toUpperCase(Locale.ROOT);rings.invalidate();}public void afterTextChanged(android.text.Editable e){}};l.addTextChangedListener(watch);if(r!=null)r.addTextChangedListener(watch);
  slider(c,"Brightness %",0,100,brightness,n->{brightness=n;rings.invalidate();});
  action("Apply ring colors",()->{if(!checked){message("Run hardware checks in Device first");return;}if(!left.matches("[A-Fa-f0-9]{6}")||(!shared&&!right.matches("[A-Fa-f0-9]{6}"))){message("Enter six RGB hex digits for each ring");return;}saveDraft();task(()->Root.run(this,"light "+left+" "+(shared?left:right)+" "+brightness));},true);
  action("Restore ring settings",()->task(()->Root.run(this,"restore-light")),false);
  card("Individual LED colors","Eight independent colors are not verified. This preview uses the stock left/right ring settings and sends no undocumented LED commands.");
 }
 EditText colorField(LinearLayout c,String label,String value){c.addView(text(label,15,TEXT));EditText e=new EditText(this);e.setSingleLine();e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);e.setTextColor(TEXT);e.setText(value);e.setSelectAllOnFocus(true);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(7)});e.setContentDescription(label);c.addView(e);return e;}
 void profiles(){
  LinearLayout c=card("Saved profiles","Profiles contain your fan curve, manual speed and ring colors. Loading changes the draft; it does not apply hardware settings.");
  EditText name=new EditText(this);name.setSingleLine();name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);name.setTextColor(TEXT);name.setHint("Profile name");name.setHintTextColor(MUTED);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});c.addView(name);
  button(c,"Save current draft",()->{try{validate();String key=name.getText().toString().trim();if(key.isEmpty())throw new IllegalArgumentException("Enter a profile name");prefs.edit().putString("saved:"+key,json().toString()).apply();message("Profile saved");draw();}catch(Exception e){message(e.getMessage());}},true);
  ArrayList<String> keys=new ArrayList<>();for(String k:prefs.getAll().keySet())if(k.startsWith("saved:"))keys.add(k);Collections.sort(keys);
  for(String k:keys){LinearLayout row=card(k.substring(6),"");button(row,"Load draft",()->{try{load(new JSONObject(prefs.getString(k,"{}")));saveDraft();message("Loaded · apply from Fan or Lighting");draw();}catch(Exception e){message("Profile could not be read");}},false);button(row,"Delete profile",()->new AlertDialog.Builder(this).setTitle("Delete "+k.substring(6)+"?").setPositiveButton("Delete",(d,w)->{prefs.edit().remove(k).apply();draw();}).setNegativeButton("Cancel",null).show(),false);}
  if(keys.isEmpty())c.addView(text("No saved profiles yet",14,MUTED));
 }
 void device(){
  LinearLayout c=card("Device checks","Checks read the stock service, fan interface and temperature sensors. They do not change hardware settings.");
  c.addView(text(Build.MANUFACTURER+" · "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\n"+Build.DISPLAY,14,MUTED));
  button(c,"Run hardware checks",this::checkHardware,true);button(c,"Read session status",()->task(()->Root.run(this,"status")),false);
  TextView result=text(report,14,TEXT);result.setTextIsSelectable(true);c.addView(result);
  c=card("Stock OS approach","Raven System Control makes no firmware-image, fingerprint, app-removal, bootloader or SELinux changes. The OEM privileged service must be accessible; unsupported devices stay in profile-editing mode.");
  c.addView(text("Before uninstalling: stop the fan session and restore the original ring settings. Root session/snapshot files live outside app storage. Reboot ends the temporary fan controller; there are no boot hooks.",14,MUTED));
  button(c,"Stop fan & restore ring settings",()->{startService(new Intent(this,FanService.class).setAction("stop"));task(()->Root.run(this,"restore-light"));},false);
 }
 void checkHardware(){task(()->{
  checked=false;String probe=Root.run(this,"probe");
  String sensorText=Root.read("for z in /sys/class/thermal/thermal_zone*; do echo ${z##*zone}:$(cat $z/type):$(cat $z/temp 2>/dev/null); done");
  ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();
  for(String line:sensorText.split("\n")){String[] p=line.trim().split(":",3);if(p.length!=3||!p[0].matches("[0-9]{1,3}"))continue;
   try{double n=Double.parseDouble(p[2]);if(n>=1000)n/=1000;if(n>0&&n<100){ids.add(p[0]);names.add(p[1]+" · "+String.format(Locale.US,"%.1f°C",n)+" · zone "+p[0]);}}catch(Exception ignored){}
  }
  synchronized(this){sensors=ids;sensorNames=names;checked=!ids.isEmpty();report=probe+"\n\n"+ids.size()+" readable temperature sensors. Select a suitable sensor on the Fan tab. Hardware behavior remains untested.";}
  return report;
 });}
 interface Work{String run()throws Exception;}
 void task(Work action){if(busy)return;busy=true;AlertDialog progress=new AlertDialog.Builder(this).setMessage("Working…").setCancelable(false).create();progress.show();io.execute(()->{String s;try{s=action.run();}catch(Exception e){s="Could not complete: "+e.getMessage();}final String result=s;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;progress.dismiss();message(result);if(tab.equals("Device")){if(!checked)report=result;draw();}});});}
 void message(String s){new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();}
 android.widget.ArrayAdapter<String> adapter(String[] items){android.widget.ArrayAdapter<String> a=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);return a;}
 void validate(){FanCurve.validate(off,on,full,low,high);FanCurve.manual(manual);}
 JSONObject json()throws JSONException{return new JSONObject().put("off",off).put("on",on).put("full",full).put("low",low).put("high",high).put("manual",manual).put("brightness",brightness).put("left",left).put("right",right).put("kind",kind).put("zone",zone);}
 void load(JSONObject j){off=j.optInt("off",45);on=j.optInt("on",50);full=j.optInt("full",75);low=j.optInt("low",50);high=j.optInt("high",70);manual=j.optInt("manual",55);brightness=j.optInt("brightness",50);left=j.optString("left","2BE0D8");right=j.optString("right","B68CFF");kind=j.optString("kind","curve");zone=j.optString("zone","");}
 void saveDraft(){try{prefs.edit().putString("draft",json().toString()).apply();}catch(Exception ignored){}}
 final class RingPreview extends View {
  final Paint paint=new Paint(3);RingPreview(Context c){super(c);setContentDescription("Left and right ring color preview");}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float y=getHeight()*.46f,r=dp(38);String[] colors={left,Build.MODEL.equals("Odin2")?left:right};for(int i=0;i<2;i++){float x=getWidth()*(i==0?.28f:.72f);paint.setColor(0xff101719);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r+dp(9),paint);int color=ACCENT;try{color=Color.parseColor("#"+colors[i]);}catch(Exception ignored){}paint.setColor(color);paint.setAlpha(40+brightness*215/100);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(8));c.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setTextSize(dp(13));paint.setColor(MUTED);paint.setTextAlign(Paint.Align.CENTER);c.drawText(i==0?"LEFT":"RIGHT",x,y+r+dp(27),paint);}}
 }
}
