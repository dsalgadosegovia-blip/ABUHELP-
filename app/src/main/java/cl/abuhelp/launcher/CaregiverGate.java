package cl.abuhelp.launcher;
import android.content.*;import android.os.SystemClock;import android.util.Base64;
import java.security.*;import javax.crypto.*;import javax.crypto.spec.PBEKeySpec;import java.util.Arrays;
public final class CaregiverGate{
 private static volatile long until=0;private static volatile int generation=0;
 public static boolean isUnlocked(){return SystemClock.elapsedRealtime()<until;}
 public static synchronized void lock(){until=0;generation++;}
 public static long waitSeconds(Context c){return Math.max(0,(AppPrefs.prefs(c).getLong("pin_wait",0)-System.currentTimeMillis()+999)/1000);}
 private static byte[] derive(String pin,byte[] salt)throws Exception{
  PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,120000,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
 }
 public static void setPin(Context c,String pin)throws Exception{
  if(AppPrefs.hasPin(c)&&!isUnlocked())throw new SecurityException("Acceso familiar requerido");
  if(!pin.matches("[0-9]{6}"))throw new IllegalArgumentException("Usa seis números");
  int token=generation;byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] hash=derive(pin,salt);
  synchronized(CaregiverGate.class){if(token!=generation)throw new SecurityException("La sesión fue cerrada");
  if(!AppPrefs.prefs(c).edit().putString("pin_salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("pin_hash",Base64.encodeToString(hash,Base64.NO_WRAP)).putInt("pin_tries",0).putLong("pin_wait",0).commit())throw new IllegalStateException("No se pudo guardar el PIN");
  Arrays.fill(hash,(byte)0);until=SystemClock.elapsedRealtime()+300000;}
 }
 public static boolean verify(Context c,String pin)throws Exception{
  int token=generation;if(waitSeconds(c)>0)return false;SharedPreferences p=AppPrefs.prefs(c);
  byte[] salt=Base64.decode(p.getString("pin_salt",""),Base64.NO_WRAP), expected=Base64.decode(p.getString("pin_hash",""),Base64.NO_WRAP);
  byte[] actual=derive(pin,salt);boolean ok=MessageDigest.isEqual(expected,actual);Arrays.fill(actual,(byte)0);
  synchronized(CaregiverGate.class){if(token!=generation)return false;
  if(ok){p.edit().putInt("pin_tries",0).putLong("pin_wait",0).apply();until=SystemClock.elapsedRealtime()+300000;return true;}
  int tries=p.getInt("pin_tries",0)+1;long delay=tries<5?0:Math.min(600000L,30000L*(tries-4));
  p.edit().putInt("pin_tries",tries).putLong("pin_wait",System.currentTimeMillis()+delay).apply();return false;}
 }
 private CaregiverGate(){}
}
