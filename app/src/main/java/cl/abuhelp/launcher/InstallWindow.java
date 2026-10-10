package cl.abuhelp.launcher;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;

/** Short, caregiver-initiated Play Store window. Unknown-source restrictions stay in force. */
public final class InstallWindow {
 private InstallWindow(){}
 private static SharedPreferences state(Context c){return c.createDeviceProtectedStorageContext().getSharedPreferences("install_window",Context.MODE_PRIVATE);}
 private static ComponentName admin(Context c){return new ComponentName(c,FamilyAdminReceiver.class);}
 private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,7801,new Intent(c,InstallWindowReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 public static boolean pending(Context c){return state(c).getBoolean("pending",false);}
 public static void close(Context c){
  if(!pending(c))return;
  DevicePolicyManager d=c.getSystemService(DevicePolicyManager.class);
  if(d.isDeviceOwnerApp(c.getPackageName()))d.addUserRestriction(admin(c),UserManager.DISALLOW_INSTALL_APPS);
  if(!state(c).edit().putBoolean("pending",false).commit())throw new IllegalStateException("No se pudo cerrar el permiso de instalación.");
  c.getSystemService(AlarmManager.class).cancel(alarm(c));
 }
 public static void open(Activity a){
  if(!CaregiverGate.isUnlocked())throw new SecurityException("Introduce el PIN familiar.");
  if(!DeviceProtection.owner(a)||!DeviceProtection.active(a))throw new IllegalStateException("Activa primero la protección del teléfono.");
  Intent launch=a.getPackageManager().getLaunchIntentForPackage("com.android.vending");
  if(launch==null)throw new IllegalStateException("Google Play Store no está disponible.");
  try{
   ApplicationInfo info=a.getPackageManager().getApplicationInfo("com.android.vending",0);
   if((info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)throw new IllegalStateException("No se encontró la Play Store del sistema.");
  }catch(PackageManager.NameNotFoundException e){throw new IllegalStateException("Google Play Store no está disponible.");}
  AlarmManager alarms=a.getSystemService(AlarmManager.class);
  if(Build.VERSION.SDK_INT>=31&&!alarms.canScheduleExactAlarms())throw new IllegalStateException("Un familiar debe permitir Alarmas y recordatorios de ABUHELP para cerrar automáticamente la instalación.");
  close(a);
  if(!state(a).edit().putBoolean("pending",true).commit())throw new IllegalStateException("No se pudo preparar la autorización.");
  try{
   alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+300000,alarm(a));
   DevicePolicyManager d=a.getSystemService(DevicePolicyManager.class);
   d.addUserRestriction(admin(a),UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
   if(Build.VERSION.SDK_INT>=29)d.addUserRestriction(admin(a),UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY);
   d.clearUserRestriction(admin(a),UserManager.DISALLOW_INSTALL_APPS);
   CaregiverGate.lock();
   a.startActivity(launch);
  }catch(RuntimeException e){close(a);throw e;}
 }
}
