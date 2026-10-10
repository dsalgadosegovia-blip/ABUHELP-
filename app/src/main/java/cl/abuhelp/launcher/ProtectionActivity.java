package cl.abuhelp.launcher;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import java.util.concurrent.*;
public final class ProtectionActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private boolean busy;
 @Override public void onCreate(Bundle b){super.onCreate(b);}
 @Override public void onResume(){super.onResume();render();}
 private void render(){
  if(!CaregiverGate.isUnlocked()){finish();return;}
  LinearLayout page=Ui.page(this,"Protección del teléfono");
  boolean owner=DeviceProtection.owner(this),active=DeviceProtection.active(this);
  page.addView(Ui.text(this,!owner?"Preparación pendiente":active?(DeviceProtection.complete(this)?"Protección activa":"Revisar protección incompleta"):"Administración lista; protección desactivada",26,true));
  page.addView(Ui.text(this,"Bloquea instalaciones de aplicaciones y APK. Para instalar usa Play Store desde Acceso familiar. Bloquea cambios del Wi-Fi, modo avión, brillo, redes móviles, fecha y controles de aplicaciones. Fija el brillo al 63% y el botón Inicio en ABUHELP. Bloquea el panel superior con el teléfono desbloqueado. Los avisos se leen desde la campana de Inicio. Conserva los botones de volumen.",22,false));
  page.addView(Ui.text(this,"En la pantalla bloqueada Android no aplica el bloqueo del panel: un familiar debe desactivar ese acceso en los ajustes de HyperOS y probarlo. Verifica llamadas de teléfono y WhatsApp antes de usarlo a diario.",22,false));
  if(!owner){page.addView(Ui.text(this,"Un familiar debe preparar este teléfono nuevo por USB como dispositivo administrado. Instalar la APK o aceptar un permiso común no basta. Consulta la guía PREPARAR_TELEFONO del repositorio.",22,false));}
  else{
   page.addView(Ui.button(this,"Activar protección",Ui.GREEN,()->confirm("Activar protección","Configura primero Wi-Fi, cuentas, llamadas, recordatorios y lectura de notificaciones. Después se bloquearán sus ajustes. Guarda el PIN familiar.",()->DeviceProtection.enable(this))));
   page.addView(Ui.button(this,"Desactivar protección",Ui.NAVY,()->run(()->DeviceProtection.disable(this))));
   page.addView(Ui.button(this,"Retirar administración",Ui.RED,()->confirm("Retirar administración","Se quitarán los bloqueos de ABUHELP y su administración. Las fotos y aplicaciones se conservan. Para activarla otra vez será necesaria una nueva preparación.",()->DeviceProtection.removeAdministration(this))));
  }
  Ui.back(this,page);
 }
 private void confirm(String title,String message,Runnable action){if(!CaregiverGate.isUnlocked()){finish();return;}new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancelar",null).setPositiveButton("Confirmar",(d,w)->run(action)).show();}
 private void run(Runnable action){
  if(busy)return;if(!CaregiverGate.isUnlocked()){finish();return;}busy=true;
  worker.execute(()->{String problem=null;try{action.run();}catch(RuntimeException e){problem=e.getMessage();}final String result=problem;runOnUiThread(()->{busy=false;if(isDestroyed())return;render();if(!isFinishing())Ui.message(this,"Protección",result==null?"Cambio aplicado. Comprueba ahora los controles del teléfono.":result);});});
 }
 @Override public void onDestroy(){worker.shutdown();super.onDestroy();}
}
