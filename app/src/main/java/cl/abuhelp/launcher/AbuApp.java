package cl.abuhelp.launcher;
import android.app.*;import android.os.*;
public class AbuApp extends Application{
 private int started=0;private final Handler h=new Handler(Looper.getMainLooper());
 private final Runnable check=()->{if(started==0)CaregiverGate.lock();};
 @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
 public void onActivityStarted(Activity a){started++;h.removeCallbacks(check);}
 public void onActivityStopped(Activity a){started=Math.max(0,started-1);h.postDelayed(check,500);}
 public void onActivityCreated(Activity a,Bundle b){}public void onActivityResumed(Activity a){
 try{InstallWindow.close(a);}catch(RuntimeException e){Ui.message(a,"Revisar protección","No se pudo volver a bloquear la instalación. Entra en Acceso familiar y revisa la protección antes de entregar el teléfono.");}
}
 public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
 });}
}
