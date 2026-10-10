package cl.abuhelp.launcher;
import android.content.*;
public final class InstallWindowReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){InstallWindow.close(c);}
}
