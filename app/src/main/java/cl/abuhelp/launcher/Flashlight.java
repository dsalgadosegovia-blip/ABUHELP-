package cl.abuhelp.launcher;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.camera2.*;
import android.os.*;
import android.widget.Button;

/** Controls only the torch; never opens a camera or captures images. */
public final class Flashlight {
 public static final int PERMISSION=402;
 private final Activity activity;private final Button button;private final CameraManager manager;
 private String camera;private boolean registered,known,on,available,busy;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final Runnable timeout=()->{busy=false;label();};
 private final CameraManager.TorchCallback callback=new CameraManager.TorchCallback(){
  @Override public void onTorchModeChanged(String id,boolean enabled){if(id.equals(camera)){known=true;available=true;on=enabled;busy=false;handler.removeCallbacks(timeout);label();}}
  @Override public void onTorchModeUnavailable(String id){if(id.equals(camera)){known=true;available=false;on=false;busy=false;handler.removeCallbacks(timeout);label();}}
 };
 public Flashlight(Activity a,Button b){activity=a;button=b;manager=a.getSystemService(CameraManager.class);}
 public void start(){
  if(registered)return;
  if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){label();return;}
  camera=null;known=false;available=false;
  try{
   if(manager!=null)for(String id:manager.getCameraIdList()){
    CameraCharacteristics info=manager.getCameraCharacteristics(id);
    if(Boolean.TRUE.equals(info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))){
     if(camera==null)camera=id;
     if(Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK).equals(info.get(CameraCharacteristics.LENS_FACING))){camera=id;break;}
    }
   }
   if(camera!=null){manager.registerTorchCallback(callback,handler);registered=true;}
  }catch(CameraAccessException|RuntimeException ignored){camera=null;}
  label();
 }
 public void stop(){if(registered){manager.unregisterTorchCallback(callback);registered=false;}handler.removeCallbacksAndMessages(null);busy=false;}
 private void label(){
  button.setText(on?"Linterna\nApagar":camera==null||!known?"Linterna":available?"Linterna\nEncender":"Linterna\nNo disponible");
 }
 public void toggle(){
  if(activity.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
   activity.requestPermissions(new String[]{Manifest.permission.CAMERA},PERMISSION);return;
  }
  start();
  if(camera==null){Ui.message(activity,"Linterna","Este dispositivo no ofrece una linterna disponible.");return;}
  if(!known){Ui.message(activity,"Linterna","Espera un momento y vuelve a tocar Linterna.");return;}
  if(!available){Ui.message(activity,"Linterna","La cámara está ocupada. Cierra la cámara o videollamada y vuelve a intentarlo.");return;}
  if(busy)return;
  try{busy=true;manager.setTorchMode(camera,!on);handler.postDelayed(timeout,2000);}
  catch(CameraAccessException|RuntimeException e){busy=false;Ui.message(activity,"Linterna","No se pudo cambiar la linterna. Puede estar ocupada por otra aplicación.");}
 }
}
