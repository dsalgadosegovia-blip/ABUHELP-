package cl.abuhelp.launcher;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.net.Uri;
import android.os.*;import android.widget.*;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;import org.json.*;
public class MainActivity extends Activity{
 private TextView clock,date,status;private Button missed,notices,ongoing;private VoiceAssistant voice;private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable tick=new Runnable(){public void run(){updateTime();h.postDelayed(this,1000);}};
 private final Runnable noticeChanged=()->h.post(this::updateNotices);
 private final Runnable calls=()->runOnUiThread(()->{if(voice!=null&&AbuInCallService.hasCalls())voice.stop();updateOngoing();});
 @Override public void onCreate(Bundle b){super.onCreate(b);render();handleDialIntent(getIntent());}
 private void render(){
  LinearLayout page=Ui.page(this,"ABUHELP");
  clock=Ui.text(this,"",52,true);date=Ui.text(this,"",24,false);page.addView(clock);page.addView(date);updateTime();
  ongoing=Ui.button(this,"Volver a la llamada",Ui.GREEN,()->startActivity(new Intent(this,CallActivity.class)));page.addView(ongoing);updateOngoing();
  notices=Ui.button(this,"Notificaciones",Ui.NAVY,()->startActivity(new Intent(this,NotificationsActivity.class)));
  notices.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_notifications,0,0,0);page.addView(notices);updateNotices();
  missed=Ui.button(this,"Llamadas perdidas",Ui.RED,()->startActivity(new Intent(this,ContactsActivity.class)));page.addView(missed);updateMissed();
  String[] labels={"Llamar","YouTube","Noticias","Clima\nSan Miguel","Mahjong","Remedios"};
  Runnable[] actions={()->startActivity(new Intent(this,ContactsActivity.class)),()->launch("com.google.android.youtube","https://www.youtube.com"),()->launch("com.google.android.apps.magazines","https://news.google.com/topstories?hl=es-419&gl=CL&ceid=CL:es-419"),()->openWeather(),()->mahjong(),()->startActivity(new Intent(this,RemindersActivity.class))};
  boolean two=getResources().getConfiguration().screenWidthDp>=360&&getResources().getConfiguration().fontScale<=1.3f;
  if(two){for(int row=0;row<3;row++){LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);
   for(int col=0;col<2;col++){int n=row*2+col;Button btn=Ui.button(this,labels[n],n==0?Ui.GREEN:n==1?Ui.RED:Ui.NAVY,actions[n]);
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(col==0?0:Ui.dp(this,6),Ui.dp(this,6),col==0?Ui.dp(this,6):0,Ui.dp(this,6));
    line.addView(btn,p);}page.addView(line,new LinearLayout.LayoutParams(-1,-2));}}
  else for(int n=0;n<labels.length;n++)page.addView(Ui.button(this,labels[n],n==0?Ui.GREEN:Ui.NAVY,actions[n]));
  status=Ui.text(this,"Toca Hablar y dime qué necesitas.",24,false);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
  voice=new VoiceAssistant(this,text->status.setText(text),this::command);
  page.addView(Ui.button(this,"Hablar",Ui.NAVY,voice::start));page.addView(status);
  page.addView(Ui.button(this,"Acceso familiar",Ui.NAVY,()->startActivity(new Intent(this,CaregiverActivity.class))));
  if(!AppPrefs.hasPin(this))page.addView(Ui.text(this,"Un familiar debe completar la configuración inicial.",22,false));
 }
 private void updateOngoing(){if(ongoing!=null)ongoing.setVisibility(AbuInCallService.hasCalls()?android.view.View.VISIBLE:android.view.View.GONE);}
 private void updateNotices(){if(notices==null)return;int n=NoticeListener.current(this).size();notices.setText(n>0?"Notificaciones ("+n+")":"Notificaciones");}
 private void updateMissed(){if(missed==null)return;int count=AppPrefs.prefs(this).getInt("missed_count",0);missed.setVisibility(count>0?android.view.View.VISIBLE:android.view.View.GONE);if(count>0)missed.setText(count+(count==1?" llamada perdida":" llamadas perdidas")+"\n"+AppPrefs.prefs(this).getString("last_missed_name",""));}
 private void updateTime(){if(clock==null)return;updateMissed();ZonedDateTime now=ZonedDateTime.now();String time=now.format(DateTimeFormatter.ofPattern("HH:mm"));String day=now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM",new Locale("es","CL")));if(!time.contentEquals(clock.getText()))clock.setText(time);if(!day.contentEquals(date.getText()))date.setText(day);}
 private void handleDialIntent(Intent intent){
  if(Intent.ACTION_DIAL.equals(intent.getAction())){Intent c=new Intent(this,ContactsActivity.class);Uri data=intent.getData();if(data!=null)c.putExtra("dialNumber",data.getSchemeSpecificPart());startActivity(c);setIntent(new Intent(Intent.ACTION_MAIN));}
 }
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleDialIntent(intent);}
 private void launch(String pkg,String fallback){Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){Ui.open(this,i);return;}Ui.open(this,new Intent(Intent.ACTION_VIEW,Uri.parse(fallback)));}
 private void openWeather(){Ui.open(this,new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q=clima+San+Miguel+Santiago+Chile")));}
 private void mahjong(){String pkg=AppPrefs.prefs(this).getString("mahjong","");Intent i=pkg.isEmpty()?null:getPackageManager().getLaunchIntentForPackage(pkg);if(i==null){Ui.message(this,"Mahjong","Un familiar debe seleccionar el juego instalado desde Acceso familiar.");return;}Ui.open(this,i);}
 private void command(VoiceCommand c){
  switch(c.action){
   case "time":ZonedDateTime now=ZonedDateTime.now();voice.say("Son las "+now.getHour()+" horas y "+now.getMinute()+" minutos.");break;
   case "youtube":launch("com.google.android.youtube","https://www.youtube.com");break;
   case "news":launch("com.google.android.apps.magazines","https://news.google.com/topstories?hl=es-419&gl=CL&ceid=CL:es-419");break;
   case "weather":openWeather();break;case "mahjong":mahjong();break;
   case "contacts":startActivity(new Intent(this,ContactsActivity.class));break;
   case "call":
    int matches=0;JSONArray cs=AppPrefs.contacts(this);for(int i=0;i<cs.length();i++){JSONObject contact=cs.optJSONObject(i);if(contact!=null&&VoiceCommand.normalize(contact.optString("name")).equals(c.value))matches++;}
    if(matches==1)startActivity(new Intent(this,ContactsActivity.class).putExtra("contact",c.value));
    else{voice.say("Elige a la persona en tus contactos.");startActivity(new Intent(this,ContactsActivity.class));}break;
   case "reminder":startActivity(new Intent(this,RemindersActivity.class).putExtra("draft",c.value));break;
   default:voice.say("Puedes decir: abre YouTube, qué hora es, contactos, noticias, clima o recuérdame un remedio.");break;
  }
 }
 @Override protected void onResume(){super.onResume();NoticeListener.addObserver(noticeChanged);updateNotices();updateOngoing();h.removeCallbacks(tick);tick.run();AbuInCallService.addListener(calls);try{ReminderStore.rescheduleAll(this);}catch(Exception ignored){}}
 @Override protected void onPause(){NoticeListener.removeObserver(noticeChanged);AbuInCallService.removeListener(calls);h.removeCallbacks(tick);if(voice!=null)voice.stop();super.onPause();}
 @Override protected void onDestroy(){if(voice!=null)voice.destroy();h.removeCallbacksAndMessages(null);super.onDestroy();}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==401){status.setText(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED?"Micrófono habilitado. Toca Hablar.":"Puedes seguir usando los botones sin micrófono.");}}
 @Override public void onBackPressed(){if(voice!=null)voice.stop();}
}
