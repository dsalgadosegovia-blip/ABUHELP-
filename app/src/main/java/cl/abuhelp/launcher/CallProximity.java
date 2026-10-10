package cl.abuhelp.launcher;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;
final class CallProximity {
 private PowerManager.WakeLock lock;
 CallProximity(Context context){
  try{
   PowerManager power=context.getSystemService(PowerManager.class);
   if(power!=null&&power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)){
    lock=power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,"ABUHELP:call-proximity");
    lock.setReferenceCounted(false);
   }
  }catch(RuntimeException e){Log.w("ABUHELP","Proximity screen control unavailable");}
 }
 void update(boolean atEar){
  if(lock==null)return;
  try{
   // Bound to Telecom call/audio callbacks; never expire while a call is at the ear.
   if(atEar&&!lock.isHeld())lock.acquire();
   else if(!atEar&&lock.isHeld())lock.release();
  }catch(RuntimeException e){Log.w("ABUHELP","Could not update proximity screen control");}
 }
 void close(){update(false);}
}
