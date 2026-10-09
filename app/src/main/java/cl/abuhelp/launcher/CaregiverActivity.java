package cl.abuhelp.launcher;
import android.Manifest;import android.app.*;import android.app.role.RoleManager;import android.content.*;
import android.content.pm.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
import android.provider.*;import android.text.InputType;import android.widget.*;
import org.json.*;import java.util.*;import java.util.concurrent.*;
public class CaregiverActivity extends Activity{
 private LinearLayout page;private boolean busy=false;private final ExecutorService worker=Executors.newSingleThreadExecutor();
 @Override public void onCreate(Bundle b){super.onCreate(b);}
 @Override public void onResume(){super.onResume();render();}
 private boolean guard(){if(CaregiverGate.isUnlocked())return true;render();return false;}
 private void render(){
  if(isFinishing())return;
  if(!CaregiverGate.isUnlocked()){showPin();return;}
  if("reminders".equals(getIntent().getStringExtra("destination"))){
   getIntent().removeExtra("destination");Intent i=new Intent(this,RemindersActivity.class);
   if(getIntent().hasExtra("draft"))i.putExtra("draft",getIntent().getStringExtra("draft"));startActivity(i);return;
  }
  page=Ui.page(this,"Acceso familiar");
  page.addView(Ui.text(this,"Sesión de configuración: hasta 5 minutos. Al salir de ABUHELP se vuelve a bloquear.",20,false));
  page.addView(Ui.button(this,"Protección del teléfono",Ui.NAVY,()->{if(guard())startActivity(new Intent(this,ProtectionActivity.class));}));
  page.addView(Ui.button(this,"Permitir lectura de notificaciones",Ui.NAVY,()->{if(guard())new AlertDialog.Builder(this).setTitle("Avisos en ABUHELP").setMessage("Android dará acceso al contenido de las notificaciones, incluidos mensajes. ABUHELP los muestra solo en este teléfono, sin guardarlos ni enviarlos. En la siguiente pantalla activa Avisos de ABUHELP.").setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{if(guard())Ui.open(this,new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}).show();}));
  page.addView(Ui.button(this,"Añadir contacto",Ui.NAVY,()->{if(guard())addContact();}));
  page.addView(Ui.button(this,"Importar contactos del teléfono",Ui.NAVY,()->{if(guard())importContacts();}));
  page.addView(Ui.button(this,"Elegir aplicación Mahjong",Ui.NAVY,()->{if(guard())chooseMahjong();}));
  page.addView(Ui.button(this,"Configurar recordatorios",Ui.NAVY,()->{if(guard())startActivity(new Intent(this,RemindersActivity.class));}));
  page.addView(Ui.button(this,"Permitir avisos",Ui.NAVY,()->{if(guard())notifications();}));
  page.addView(Ui.button(this,"Permitir pantalla de llamadas",Ui.NAVY,()->{if(guard())fullScreen();}));
  page.addView(Ui.button(this,"Usar ABUHELP como teléfono",Ui.GREEN,()->{if(guard())dialer();}));
  page.addView(Ui.button(this,"Usar ABUHELP como inicio",Ui.GREEN,()->{if(guard())home();}));
  page.addView(Ui.button(this,"Ajustes de batería de la app",Ui.NAVY,()->{if(guard())Ui.open(this,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}));
  page.addView(Ui.text(this,"La voz usa el servicio de reconocimiento configurado en Android; puede requerir internet y enviar audio a su proveedor. ABUHELP no guarda grabaciones. Las llamadas personales de WhatsApp conservan su interfaz.",20,false));
  page.addView(Ui.text(this,"Contactos visibles",28,true));
  JSONArray cs=AppPrefs.contacts(this);
  for(int n=0;n<cs.length();n++){JSONObject c=cs.optJSONObject(n);if(c==null)continue;final int index=n;
   page.addView(Ui.button(this,"Quitar: "+c.optString("name"),Ui.RED,()->{if(!guard())return;new AlertDialog.Builder(this).setMessage("¿Quitar este acceso de ABUHELP? El contacto original se conserva.").setNegativeButton("Cancelar",null).setPositiveButton("Quitar",(d,w)->{if(guard()){JSONArray list=AppPrefs.contacts(this);list.remove(index);AppPrefs.saveContacts(this,list);render();}}).show();}));
  }
  page.addView(Ui.button(this,"Cambiar PIN",Ui.NAVY,()->{if(guard())setNewPin();}));
  page.addView(Ui.button(this,"Terminar y bloquear",Ui.NAVY,()->{CaregiverGate.lock();finish();}));
 }
 private void showPin(){
  boolean initial=!AppPrefs.hasPin(this);page=Ui.page(this,initial?"Configuración inicial":"Acceso familiar");
  page.addView(Ui.text(this,initial?"Un familiar debe crear un PIN de seis números. Guárdalo: no existe un PIN universal de recuperación.":"Introduce el PIN de seis números.",24,false));
  EditText pin=Ui.input(this,"PIN",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);page.addView(pin);
  EditText repeat=Ui.input(this,"Repite el PIN",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);if(initial)page.addView(repeat);
  page.addView(Ui.button(this,initial?"Crear PIN":"Entrar",Ui.GREEN,()->{
   if(busy)return;String p=pin.getText().toString();
   if(!p.matches("[0-9]{6}")||(initial&&!p.equals(repeat.getText().toString()))){Ui.message(this,"Revisa el PIN","Introduce seis números iguales en ambos campos si estás creando el PIN.");return;}
   long wait=CaregiverGate.waitSeconds(this);if(wait>0){Ui.message(this,"Espera","Inténtalo en "+wait+" segundos.");return;}
   busy=true;worker.execute(()->{String error=null;try{if(initial)CaregiverGate.setPin(this,p);else if(!CaregiverGate.verify(this,p))error="PIN incorrecto.";}catch(Exception ex){error="No se pudo validar el PIN.";}
    final String result=error;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed())return;if(result==null)render();else Ui.message(this,"Acceso familiar",result);});
   });
  }));Ui.back(this,page);
 }
 private void setNewPin(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
  EditText a=Ui.input(this,"Nuevo PIN: seis números",18),b=Ui.input(this,"Repite el PIN",18);box.addView(a);box.addView(b);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Cambiar PIN").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{if(!guard()){dialog.dismiss();return;}String p=a.getText().toString();if(!p.matches("[0-9]{6}")||!p.equals(b.getText().toString())){a.setError("Seis números iguales en ambos campos");return;}
   if(busy)return;busy=true;worker.execute(()->{boolean ok=false;try{CaregiverGate.setPin(this,p);ok=true;}catch(Exception ignored){} final boolean success=ok;
    runOnUiThread(()->{busy=false;if(isDestroyed())return;if(success)dialog.dismiss();else Ui.message(this,"PIN","No se pudo guardar; vuelve a entrar al acceso familiar.");});
   });
  }));dialog.show();
 }
 private void addContact(){
  LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,8,24,8);
  EditText name=Ui.input(this,"Nombre visible",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS),phone=Ui.input(this,"Teléfono con código de país",InputType.TYPE_CLASS_PHONE);box.addView(name);box.addView(phone);
  AlertDialog d=new AlertDialog.Builder(this).setTitle("Nuevo contacto").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
  d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{if(!guard()){d.dismiss();return;}String nm=name.getText().toString().trim(),ph=phone.getText().toString().trim();
   if(nm.isEmpty()||ph.replaceAll("[^0-9]","").length()<3){name.setError("Escribe un nombre y teléfono válidos");return;}
   JSONArray cs=AppPrefs.contacts(this);try{cs.put(new JSONObject().put("name",nm).put("phone",ph));AppPrefs.saveContacts(this,cs);}catch(JSONException ignored){}d.dismiss();render();
  }));d.show();
 }
 private void importContacts(){
  if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},201);return;}
  if(busy)return;busy=true;worker.execute(()->{
   ArrayList<String> names=new ArrayList<>(),numbers=new ArrayList<>();String failure=null;
   try(Cursor c=getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")){
    HashSet<String> seen=new HashSet<>();if(c!=null)while(c.moveToNext()&&names.size()<500){String n=c.getString(0),p=c.getString(1);if(p==null)continue;String key=(n==null?"":n)+"|"+p.replaceAll("[^0-9+]","");if(seen.add(key)){names.add(n==null?p:n);numbers.add(p);}}
   }catch(Exception ex){failure="No se pudieron leer los contactos.";}final String err=failure;
   runOnUiThread(()->{busy=false;if(isDestroyed()||!guard())return;if(err!=null){Ui.message(this,"Contactos",err);return;}
    String[] labels=new String[names.size()];boolean[] selected=new boolean[names.size()];for(int i=0;i<labels.length;i++)labels[i]=names.get(i)+" — "+numbers.get(i);
    new AlertDialog.Builder(this).setTitle("Elige los contactos visibles").setMultiChoiceItems(labels,selected,(d,idx,on)->selected[idx]=on).setNegativeButton("Cancelar",null).setPositiveButton("Añadir",(d,w)->{if(!guard())return;JSONArray cs=AppPrefs.contacts(this);HashSet<String> existing=new HashSet<>();for(int j=0;j<cs.length();j++)existing.add(cs.optJSONObject(j).optString("phone").replaceAll("[^0-9+]",""));
     for(int i=0;i<selected.length;i++)if(selected[i]&&existing.add(numbers.get(i).replaceAll("[^0-9+]","")))try{cs.put(new JSONObject().put("name",names.get(i)).put("phone",numbers.get(i)));}catch(JSONException ignored){}
     AppPrefs.saveContacts(this,cs);render();
    }).show();
   });
  });
 }
 private void chooseMahjong(){
  Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  List<ResolveInfo> apps=getPackageManager().queryIntentActivities(launch,0);apps.sort((a,b)->a.loadLabel(getPackageManager()).toString().compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
  ArrayList<String> labels=new ArrayList<>(),pkgs=new ArrayList<>();for(ResolveInfo r:apps){String p=r.activityInfo.packageName;if(p.equals(getPackageName())||pkgs.contains(p))continue;pkgs.add(p);labels.add(r.loadLabel(getPackageManager()).toString());}
  new AlertDialog.Builder(this).setTitle("Selecciona el Mahjong instalado").setItems(labels.toArray(new String[0]),(d,n)->{if(guard()){AppPrefs.prefs(this).edit().putString("mahjong",pkgs.get(n)).apply();Ui.message(this,"Mahjong","Aplicación seleccionada: "+labels.get(n));}}).setNegativeButton("Cancelar",null).show();
 }
 private void notifications(){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},202);
  else Ui.open(this,new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
 }
 private void fullScreen(){
  if(Build.VERSION.SDK_INT>=34)Ui.open(this,new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));
  else Ui.message(this,"Pantalla de llamadas","Prueba una llamada con el teléfono bloqueado después de configurar ABUHELP como teléfono.");
 }
 private void dialer(){
  if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE,Manifest.permission.CALL_PHONE},203);Ui.message(this,"Permisos de teléfono","Después de concederlos, vuelve a pulsar Usar ABUHELP como teléfono.");return;}

  NotificationManager nm=getSystemService(NotificationManager.class);
  if(!nm.areNotificationsEnabled()){Ui.message(this,"Activa los avisos","Pulsa primero Permitir avisos para recibir llamadas visibles.");return;}
  if(Build.VERSION.SDK_INT>=34&&!nm.canUseFullScreenIntent()){Ui.message(this,"Pantalla de llamadas","Activa primero Permitir pantalla de llamadas y vuelve aquí.");return;}
  if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm.isRoleAvailable(RoleManager.ROLE_DIALER))startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER),210);}
  else startActivityForResult(new Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,getPackageName()),210);
 }
 private void home(){
  if(Build.VERSION.SDK_INT>=29){RoleManager rm=getSystemService(RoleManager.class);if(rm.isRoleAvailable(RoleManager.ROLE_HOME)){startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),211);return;}}
  Ui.open(this,new Intent(Settings.ACTION_HOME_SETTINGS));
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==201&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED&&guard())importContacts();}
 @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
