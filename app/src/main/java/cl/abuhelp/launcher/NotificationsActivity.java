package cl.abuhelp.launcher;
import android.app.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.widget.*;
import java.util.*;

/** Read-only tray: no notification intents/actions that might open system settings. */
public final class NotificationsActivity extends Activity {
 private LinearLayout rows;private ScrollView scroll;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable redraw=this::refresh;
 private final Runnable observer=()->{handler.removeCallbacks(redraw);handler.postDelayed(redraw,200);};
 @Override public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout page=Ui.page(this,"Notificaciones");
  page.addView(Ui.button(this,"Volver a Inicio",Ui.NAVY,this::finish));
  page.addView(Ui.text(this,"Lee tus avisos aquí. Para responder, abre la aplicación desde Inicio.",22,false));
  rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);page.addView(rows);
  scroll=(ScrollView)page.getParent();
 }
 @Override protected void onResume(){super.onResume();NoticeListener.addObserver(observer);refresh();}
 @Override protected void onPause(){NoticeListener.removeObserver(observer);handler.removeCallbacksAndMessages(null);rows.removeAllViews();super.onPause();}
 private static String value(Bundle extras,String key){
  try{CharSequence s=extras.getCharSequence(key);if(s==null)return "";String text=s.toString();return text.length()>2000?text.substring(0,2000)+"…":text;}catch(RuntimeException e){return "";}
 }
 private void refresh(){
  if(isFinishing()||isDestroyed())return;int y=scroll.getScrollY();rows.removeAllViews();
  if(getSystemService(KeyguardManager.class).isKeyguardLocked()){rows.addView(Ui.text(this,"Desbloquea el teléfono para leer tus avisos.",26,true));return;}
  if(!NoticeListener.ready(this)){rows.addView(Ui.text(this,"Los avisos no están disponibles. Pide a un familiar que revise «Permitir lectura de notificaciones» en Acceso familiar.",26,true));return;}
  List<StatusBarNotification> notices=NoticeListener.current(this);
  if(notices.isEmpty())rows.addView(Ui.text(this,"No hay notificaciones pendientes.",28,true));
  int count=0;
  for(StatusBarNotification item:notices){
   if(count++>=100){rows.addView(Ui.text(this,"Se muestran los 100 avisos más recientes.",22,false));break;}
   Notification n=item.getNotification();String app=item.getPackageName();
   try{app=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(app,0)).toString();}catch(Exception ignored){}
   String title=value(n.extras,Notification.EXTRA_TITLE),body=value(n.extras,Notification.EXTRA_BIG_TEXT);
   if(body.isEmpty())body=value(n.extras,Notification.EXTRA_TEXT);
   if(body.isEmpty())try{CharSequence[] lines=n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null){StringBuilder text=new StringBuilder();for(CharSequence line:lines){if(text.length()>2000)break;if(line!=null)text.append(line).append("\n");}body=text.toString();}}catch(RuntimeException ignored){}
   if(body.length()>2000)body=body.substring(0,2000)+"…";
   rows.addView(Ui.text(this,app+" · "+android.text.format.DateFormat.getTimeFormat(this).format(new Date(item.getPostTime())),22,true));
   if(!title.isEmpty())rows.addView(Ui.text(this,title,28,true));
   rows.addView(Ui.text(this,body.isEmpty()?"Sin texto disponible. Revisa la aplicación.":body,26,false));
   android.view.View line=new android.view.View(this);line.setBackgroundColor(Ui.NAVY);rows.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(this,1)));
  }
  scroll.post(()->scroll.scrollTo(0,y));
 }
}
