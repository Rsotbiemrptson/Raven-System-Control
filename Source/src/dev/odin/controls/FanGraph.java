package dev.odin.controls;
import android.content.Context;import android.graphics.*;import android.view.*;

/** Draggable temperature/speed points. Changes edit a draft, never write hardware. */
final class FanGraph extends View {
 interface Changed{void set(String points);} Changed changed;
 private int[][] points;private final Paint paint=new Paint(3);private final int accent,text,grid;private final boolean extended;private int selected=-1;private int minTemp=30,maxTemp=80;private boolean enabled=true,reference=false;
 void setCurve(String data){points=CurvePoints.parse(data,true);reference=false;minTemp=30;maxTemp=80;describe();invalidate();}
 void reference(int[][] data){
  points=data;reference=true;selected=-1;
  minTemp=data[0][0]<30?0:30;
  maxTemp=Math.max(80,((data[data.length-1][0]+9)/10)*10);
  describe();invalidate();
 }
 FanGraph(Context c,String curve,int accent,int text,int grid){this(c,curve,accent,text,grid,false);}
 FanGraph(Context c,String curve,int accent,int text,int grid,boolean extended){super(c);points=CurvePoints.parse(curve,true);this.accent=accent;this.text=text;this.grid=grid;this.extended=extended;setFocusable(true);describe();}
 float dp(float n){return n*getResources().getDisplayMetrics().density;}
 float x(int t){return dp(40)+(getWidth()-dp(58))*(t-minTemp)/(float)(maxTemp-minTemp);}float y(int speed){return getHeight()-dp(30)-(getHeight()-dp(54))*speed/100f;}
 void active(boolean on){enabled=on;setAlpha(on?1:.35f);invalidate();}
 void describe(){setContentDescription((reference?"Fan output reference. ":"Fan curve. Drag points to set temperature and fan speed. ")+CurvePoints.encode(points));}
 @Override protected void onDraw(Canvas c){super.onDraw(c);paint.setTextSize(dp(10));paint.setStrokeWidth(dp(1));
  for(int speed=0;speed<=100;speed+=10){paint.setColor(grid);c.drawLine(x(minTemp),y(speed),x(maxTemp),y(speed),paint);paint.setColor(text);paint.setTextAlign(Paint.Align.RIGHT);c.drawText(speed+"%",dp(34),y(speed)+dp(3),paint);}
  for(int t=minTemp;t<=maxTemp;t+=maxTemp>80?20:10){paint.setColor(grid);c.drawLine(x(t),y(0),x(t),y(100),paint);paint.setColor(text);paint.setTextAlign(Paint.Align.CENTER);c.drawText(t+"°C",x(t),getHeight()-dp(10),paint);}
  Path line=new Path();line.moveTo(x(minTemp),y(reference?points[0][1]:0));for(int i=0;i<points.length;i++){int[] p=points[i];if(!reference&&(i==0||points[i-1][1]==0)&&p[1]>0)line.lineTo(x(p[0]),y(0));line.lineTo(x(p[0]),y(p[1]));}line.lineTo(x(maxTemp),y(points[points.length-1][1]));Path fill=new Path(line);fill.lineTo(x(maxTemp),y(0));fill.lineTo(x(minTemp),y(0));fill.close();paint.setColor(accent);paint.setAlpha(35);paint.setStyle(Paint.Style.FILL);c.drawPath(fill,paint);paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));c.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);
  for(int i=0;i<points.length;i++)c.drawCircle(x(points[i][0]),y(points[i][1]),dp(i==selected?7:5),paint);
  if(selected>=0){paint.setColor(text);paint.setTextAlign(Paint.Align.LEFT);c.drawText(points[selected][0]+"°C · "+points[selected][1]+"%",dp(42),dp(14),paint);}
 }
 void move(int t,int speed){if(selected<0||reference)return;int i=selected;t=Math.max(i==0?35:points[i-1][0]+1,Math.min(i==points.length-1?78:points[i+1][0]-1,t));speed=extended?(speed<5?0:Math.max(10,Math.min(100,speed))):speed<25?0:Math.max(50,Math.min(70,speed));speed=Math.max(i==0?0:points[i-1][1],Math.min(i==points.length-1?(extended?100:70):points[i+1][1],speed));if(i==points.length-1)speed=Math.max(extended?10:50,speed);points[i][0]=t;points[i][1]=speed;describe();invalidate();if(changed!=null)changed.set(CurvePoints.encode(points));}
 @Override public boolean onTouchEvent(MotionEvent e){if(!enabled||reference)return false;int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){float nearest=dp(36)*dp(36);selected=-1;for(int i=0;i<points.length;i++){float dx=x(points[i][0])-e.getX(),dy=y(points[i][1])-e.getY();float distance=dx*dx+dy*dy;if(distance<nearest){nearest=distance;selected=i;}}if(selected<0)return false;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}
  if(selected>=0&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)){int t=Math.round(30+(e.getX()-dp(40))*50/(getWidth()-dp(58)));int speed=Math.round((getHeight()-dp(30)-e.getY())*100/(getHeight()-dp(54)));move(t,speed);if(action==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();}return true;}if(action==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);return true;}
 @Override public boolean performClick(){super.performClick();return true;}
 @Override public boolean onKeyDown(int key,android.view.KeyEvent e){if(!enabled||reference)return false;if(selected<0)selected=0;if(key==KeyEvent.KEYCODE_DPAD_CENTER){selected=(selected+1)%points.length;invalidate();return true;}if(key>=19&&key<=22){int speed=points[selected][1],minimum=extended?10:50;move(points[selected][0]+(key==21?-1:key==22?1:0),key==19&&speed==0?minimum:key==20&&speed==minimum?0:speed+(key==19?1:key==20?-1:0));return true;}return super.onKeyDown(key,e);}
}
