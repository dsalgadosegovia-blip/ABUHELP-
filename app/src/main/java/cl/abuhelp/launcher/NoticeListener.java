package cl.abuhelp.launcher;
import android.app.*;
import android.content.*;
import android.service.notification.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/** Reads current notifications on this device only; never stores or sends their contents. */
public final class NoticeListener extends NotificationListenerService {
 private static volatile NoticeListener connected;
 private static final Set<Runnable> observers=new CopyOnWriteArraySet<>();
 public static boolean permitted(Context c){return android.os.Build.VERSION.SDK_INT>=27&&c.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(c,NoticeListener.class));}
 public static boolean ready(Context c){return permitted(c)&&connected!=null;}
 public static void addObserver(Runnable r){observers.add(r);}
 public static void removeObserver(Runnable r){observers.remove(r);}
 private static void changed(){for(Runnable r:observers)r.run();}
 @Override public void onListenerConnected(){connected=this;changed();}
 @Override public void onListenerDisconnected(){if(connected==this)connected=null;changed();}
 @Override public void onDestroy(){if(connected==this)connected=null;changed();super.onDestroy();}
 @Override public void onNotificationPosted(StatusBarNotification n){changed();}
 @Override public void onNotificationRemoved(StatusBarNotification n){changed();}
 public static List<StatusBarNotification> current(Context c){
  List<StatusBarNotification> result=new ArrayList<>();
  if(c.getSystemService(KeyguardManager.class).isKeyguardLocked()||!permitted(c))return result;
  NoticeListener service=connected;if(service==null)return result;
  try{
   StatusBarNotification[] all=service.getActiveNotifications();
   if(all!=null)for(StatusBarNotification n:all){
    if(!n.getUser().equals(android.os.Process.myUserHandle()))continue;
    if((n.getNotification().flags&Notification.FLAG_GROUP_SUMMARY)==0)result.add(n);
   }
  }catch(RuntimeException ignored){return result;}
  result.sort((a,b)->Long.compare(b.getPostTime(),a.getPostTime()));
  return result;
 }
}
