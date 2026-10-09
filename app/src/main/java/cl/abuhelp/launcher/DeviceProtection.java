package cl.abuhelp.launcher;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.telecom.TelecomManager;
public final class DeviceProtection {
 private DeviceProtection(){}
 private static DevicePolicyManager manager(Context c){return c.getSystemService(DevicePolicyManager.class);}
 private static ComponentName admin(Context c){return new ComponentName(c,FamilyAdminReceiver.class);}
 public static boolean owner(Context c){return manager(c).isDeviceOwnerApp(c.getPackageName());}
 public static boolean active(Context c){return AppPrefs.prefs(c).getBoolean("protection_active",false);}
 public static boolean complete(Context c){if(Build.VERSION.SDK_INT<33||!owner(c)||!active(c)||!AppPrefs.prefs(c).getBoolean("status_bar_blocked",false)||!NoticeListener.ready(c))return false;try{Bundle b=manager(c).getUserRestrictions(admin(c));for(String k:restrictions())if(!b.getBoolean(k))return false;return true;}catch(RuntimeException e){return false;}}
 private static void guard(Context c){if(!CaregiverGate.isUnlocked())throw new SecurityException("Introduce de nuevo el PIN familiar.");if(!owner(c))throw new IllegalStateException("Falta preparar ABUHELP como administrador del dispositivo.");if(Build.VERSION.SDK_INT<33)throw new IllegalStateException("La protección completa requiere Android 13 o posterior.");}
 @android.annotation.TargetApi(33)
 private static String[] restrictions(){return new String[]{UserManager.DISALLOW_CHANGE_WIFI_STATE,UserManager.DISALLOW_CONFIG_WIFI,UserManager.DISALLOW_AIRPLANE_MODE,UserManager.DISALLOW_CONFIG_BRIGHTNESS,UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,UserManager.DISALLOW_NETWORK_RESET,UserManager.DISALLOW_CONFIG_DATE_TIME,UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT,UserManager.DISALLOW_APPS_CONTROL};}
 public static void enable(Context c){
  if(Build.VERSION.SDK_INT<33)throw new IllegalStateException("Requiere Android 13 o posterior.");
  guard(c);if(active(c))throw new IllegalStateException("Desactiva primero la protección existente para revisarla.");
  WifiManager wifi=c.getApplicationContext().getSystemService(WifiManager.class);
  if(wifi==null||!wifi.isWifiEnabled())throw new IllegalStateException("Activa y configura primero el Wi-Fi.");
  if(Settings.Global.getInt(c.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0)throw new IllegalStateException("Desactiva primero el modo avión.");
  if(!NoticeListener.ready(c))throw new IllegalStateException("Activa primero Permitir lectura de notificaciones en Acceso familiar y espera unos segundos.");
  TelecomManager telecom=c.getSystemService(TelecomManager.class);
  if(telecom==null||!c.getPackageName().equals(telecom.getDefaultDialerPackage())||!AbuInCallService.notificationsReady(c)||!AbuInCallService.fullscreenReady(c))throw new IllegalStateException("Configura primero ABUHELP como teléfono y permite avisos y pantalla de llamadas.");
  SharedPreferences p=AppPrefs.prefs(c);
  if(!p.edit().putInt("previous_brightness",Settings.System.getInt(c.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,160)).putInt("previous_brightness_mode",Settings.System.getInt(c.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0)).putBoolean("protection_active",true).commit())throw new IllegalStateException("No se pudo guardar el estado.");
  try{
   DevicePolicyManager d=manager(c);ComponentName a=admin(c);
   d.setSystemSetting(a,Settings.System.SCREEN_BRIGHTNESS_MODE,"0");
   d.setSystemSetting(a,Settings.System.SCREEN_BRIGHTNESS,"160");
   IntentFilter home=new IntentFilter(Intent.ACTION_MAIN);home.addCategory(Intent.CATEGORY_HOME);home.addCategory(Intent.CATEGORY_DEFAULT);
   d.addPersistentPreferredActivity(a,home,new ComponentName(c,MainActivity.class));
   d.setUninstallBlocked(a,c.getPackageName(),true);
   for(String key:restrictions())d.addUserRestriction(a,key);
   if(!d.setStatusBarDisabled(a,true))throw new IllegalStateException("Android no permitió bloquear el panel superior.");
   if(!p.edit().putBoolean("status_bar_blocked",true).commit())throw new IllegalStateException("No se pudo guardar el bloqueo del panel.");
   Bundle applied=d.getUserRestrictions(a);
   for(String key:restrictions())if(!applied.getBoolean(key))throw new IllegalStateException("Android no confirmó el bloqueo: "+key);
  }catch(RuntimeException error){
   try{disable(c);}catch(RuntimeException recovery){throw new IllegalStateException("Protección incompleta. Pulsa Desactivar protección antes de entregar el teléfono.",error);}
   throw error;
  }
 }
 public static void disable(Context c){
  if(Build.VERSION.SDK_INT<33)throw new IllegalStateException("Requiere Android 13 o posterior.");
  guard(c);DevicePolicyManager d=manager(c);ComponentName a=admin(c);
  // Every step is attempted so a partial enable can always be recovered.
  RuntimeException failure=null;
  try{if(!d.setStatusBarDisabled(a,false))throw new IllegalStateException("No se pudo restaurar el panel superior.");}catch(RuntimeException e){failure=e;}
  for(String key:restrictions())try{d.clearUserRestriction(a,key);}catch(RuntimeException e){failure=e;}
  try{d.setUninstallBlocked(a,c.getPackageName(),false);}catch(RuntimeException e){failure=e;}
  try{d.clearPackagePersistentPreferredActivities(a,c.getPackageName());}catch(RuntimeException e){failure=e;}
  SharedPreferences p=AppPrefs.prefs(c);
  if(p.contains("previous_brightness")){
   try{d.setSystemSetting(a,Settings.System.SCREEN_BRIGHTNESS,Integer.toString(p.getInt("previous_brightness",160)));d.setSystemSetting(a,Settings.System.SCREEN_BRIGHTNESS_MODE,Integer.toString(p.getInt("previous_brightness_mode",0)));}catch(RuntimeException e){failure=e;}
  }
  if(failure!=null)throw new IllegalStateException("No se retiraron todos los bloqueos. Reintenta y no entregues aún el teléfono.",failure);
  if(!p.edit().putBoolean("protection_active",false).putBoolean("status_bar_blocked",false).remove("previous_brightness").remove("previous_brightness_mode").commit())throw new IllegalStateException("Revisa de nuevo el estado de protección.");
 }
 @SuppressWarnings("deprecation")
 public static void removeAdministration(Context c){guard(c);disable(c);manager(c).clearDeviceOwnerApp(c.getPackageName());if(owner(c))throw new IllegalStateException("Android no confirmó la salida de administración.");}
}
