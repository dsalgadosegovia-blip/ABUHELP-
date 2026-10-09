package cl.abuhelp.launcher;
import android.content.*;import org.json.*;
public final class AppPrefs{
 public static SharedPreferences prefs(Context c){return c.getSharedPreferences("abuhelp",Context.MODE_PRIVATE);}
 public static JSONArray contacts(Context c){try{return new JSONArray(prefs(c).getString("contacts","[]"));}catch(JSONException e){return new JSONArray();}}
 public static void saveContacts(Context c,JSONArray a){prefs(c).edit().putString("contacts",a.toString()).apply();}
 public static boolean hasPin(Context c){return prefs(c).contains("pin_hash");}
 private AppPrefs(){}
}
