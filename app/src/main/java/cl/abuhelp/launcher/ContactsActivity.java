package cl.abuhelp.launcher;
import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;
import android.net.Uri;import android.os.*;import android.telecom.TelecomManager;import android.text.InputType;
import android.widget.*;import org.json.*;import java.util.Locale;

public class ContactsActivity extends Activity{
 private String pendingNumber;private LinearLayout page;
 @Override public void onCreate(Bundle b){super.onCreate(b);showContacts();
  String number=getIntent().getStringExtra("dialNumber"),match=getIntent().getStringExtra("contact");
  if(number!=null&&!number.isEmpty())showPerson("Número de teléfono",number);
  else if(match!=null){JSONArray cs=AppPrefs.contacts(this);for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);if(c!=null&&normalize(c.optString("name")).equals(normalize(match))){showPerson(c.optString("name"),c.optString("phone"));break;}}}
 }
 private String normalize(String s){return java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").trim();}
 private void showContacts(){
  AppPrefs.prefs(this).edit().putInt("missed_count",0).apply();getSystemService(NotificationManager.class).cancel(4002);
  page=Ui.page(this,"¿A quién quieres llamar?");JSONArray contacts=AppPrefs.contacts(this);
  if(contacts.length()==0)page.addView(Ui.text(this,"Un familiar puede añadir aquí tus contactos desde Acceso familiar.",26,false));
  for(int i=0;i<contacts.length();i++){JSONObject c=contacts.optJSONObject(i);if(c==null)continue;String name=c.optString("name"),phone=c.optString("phone");
   Button b=Ui.button(this,name,Ui.NAVY,()->showPerson(name,phone));b.setTextSize(32);b.setMinHeight(Ui.dp(this,104));page.addView(b);
  }
  page.addView(Ui.button(this,"Marcar un número",Ui.NAVY,this::dialpad));Ui.back(this,page);
 }
 private void dialpad(){
  page=Ui.page(this,"Marcar número");
  EditText number=Ui.input(this,"Número de teléfono",InputType.TYPE_CLASS_PHONE);page.addView(number);
  page.addView(Ui.button(this,"Revisar número",Ui.GREEN,()->showPerson("Número de teléfono",number.getText().toString())));
  page.addView(Ui.button(this,"Volver a contactos",Ui.NAVY,this::showContacts));
 }
 private void showPerson(String name,String phone){
  page=Ui.page(this,name);page.addView(Ui.text(this,phone,28,false));
  page.addView(Ui.button(this,"Llamar por teléfono",Ui.GREEN,()->placeCall(phone)));
  page.addView(Ui.button(this,"Abrir WhatsApp",Ui.GREEN,()->openWhatsApp(phone)));
  page.addView(Ui.text(this,"WhatsApp abrirá su propia pantalla. Para llamar allí, usa su botón de llamada.",22,false));
  page.addView(Ui.button(this,"Volver a contactos",Ui.NAVY,this::showContacts));
 }
 private void placeCall(String raw){
  String number=raw.replaceAll("[^0-9+*#]","");
  if(number.isEmpty()){Ui.message(this,"Falta el número","Pide a un familiar que revise este contacto.");return;}
  if(AbuInCallService.hasCalls()){Ui.open(this,new Intent(this,CallActivity.class));return;}
  if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){pendingNumber=number;requestPermissions(new String[]{Manifest.permission.CALL_PHONE},301);return;}
  try{getSystemService(TelecomManager.class).placeCall(Uri.fromParts("tel",number,null),new Bundle());}
  catch(SecurityException|IllegalArgumentException|IllegalStateException e){Ui.message(this,"No se pudo llamar","Revisa el permiso de llamadas, la SIM y la selección de ABUHELP como teléfono en Acceso familiar.");}
 }
 private void openWhatsApp(String raw){
  String digits=raw.replaceAll("[^0-9]","");
  if(!raw.trim().startsWith("+")&&!digits.startsWith("56")){Ui.message(this,"Revisar número para WhatsApp","El contacto necesita el código de país. Para Chile se escribe +56 seguido del número.");return;}
  Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+digits)).setPackage("com.whatsapp");
  Ui.open(this,i);
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
  super.onRequestPermissionsResult(r,p,g);if(r==301){String n=pendingNumber;pendingNumber=null;
   if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED&&n!=null){
    new AlertDialog.Builder(this).setTitle("Permiso activado").setMessage("¿Llamar ahora a "+n+"?").setNegativeButton("Cancelar",null).setPositiveButton("Llamar",(d,w)->placeCall(n)).show();
   }else Ui.message(this,"Llamadas desactivadas","Un familiar puede habilitar el permiso desde los ajustes de ABUHELP.");
  }
 }
}
