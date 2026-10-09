package cl.abuhelp.launcher;
import android.Manifest;import android.app.Activity;import android.content.*;import android.content.pm.PackageManager;
import android.media.AudioManager;import android.os.*;import android.speech.*;import android.speech.tts.TextToSpeech;
import java.util.*;import java.util.function.Consumer;
public final class VoiceAssistant implements RecognitionListener{
 private final Activity activity;private final Consumer<String> status;private final Consumer<VoiceCommand> commands;
 private final Handler handler=new Handler(Looper.getMainLooper());private SpeechRecognizer recognizer;private TextToSpeech tts;
 private int generation=0;private boolean useLocal=true;private boolean listening=false,ready=false,closed=false;private final Runnable timeout=()->{stop();status.accept("No te escuché. Puedes tocar Hablar otra vez.");};
 public VoiceAssistant(Activity a,Consumer<String> s,Consumer<VoiceCommand> c){
  activity=a;status=s;commands=c;
  tts=new TextToSpeech(a,result->{if(result==TextToSpeech.SUCCESS&&!closed){int lang=tts.setLanguage(new Locale("es","CL"));if(lang<0)lang=tts.setLanguage(new Locale("es","ES"));ready=lang>=0;tts.setSpeechRate(0.85f);}});
 }
 public boolean inCall(){try{if(activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED&&activity.getSystemService(android.telecom.TelecomManager.class).isInCall())return true;}catch(RuntimeException ignored){}AudioManager am=activity.getSystemService(AudioManager.class);return AbuInCallService.hasCalls()||(am!=null&&(am.getMode()==AudioManager.MODE_IN_CALL||am.getMode()==AudioManager.MODE_IN_COMMUNICATION));}
 public void start(){
  if(closed)return;if(inCall()){status.accept("Termina la llamada antes de usar Hablar.");return;}
  if(listening){stop();status.accept("Escucha cancelada.");return;}
  if(activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},401);return;}
  if(!SpeechRecognizer.isRecognitionAvailable(activity)){status.accept("La voz no está disponible. Puedes usar los botones.");return;}
  if(tts!=null)tts.stop();
  try{
   recognizer=useLocal&&Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)?SpeechRecognizer.createOnDeviceSpeechRecognizer(activity):SpeechRecognizer.createSpeechRecognizer(activity);
   final int token=++generation;recognizer.setRecognitionListener(new RecognitionListener(){
 public void onReadyForSpeech(Bundle p){if(token==generation)VoiceAssistant.this.onReadyForSpeech(p);}
 public void onBeginningOfSpeech(){}public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}
 public void onEndOfSpeech(){if(token==generation)VoiceAssistant.this.onEndOfSpeech();}
 public void onError(int e){if(token==generation)VoiceAssistant.this.onError(e);}
 public void onResults(Bundle b){if(token==generation)VoiceAssistant.this.onResults(b);}
 public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}
 });Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
   i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
   i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-CL");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
   listening=true;status.accept("Te escucho… Toca Hablar para cancelar.");recognizer.startListening(i);handler.postDelayed(timeout,20000);
  }catch(Exception e){stop();status.accept("No se pudo iniciar la voz. Usa los botones o inténtalo otra vez.");}
 }
 public void say(String text){status.accept(text);if(ready&&!closed&&!inCall())tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"abuhelp");}
 public void stop(){generation++;handler.removeCallbacks(timeout);listening=false;if(recognizer!=null){SpeechRecognizer r=recognizer;recognizer=null;r.cancel();r.destroy();}if(tts!=null)tts.stop();}
 public void destroy(){closed=true;stop();if(tts!=null){tts.shutdown();tts=null;}}
 @Override public void onReadyForSpeech(Bundle p){if(listening)status.accept("Te escucho…");}
 @Override public void onBeginningOfSpeech(){}@Override public void onRmsChanged(float v){}@Override public void onBufferReceived(byte[] b){}
 @Override public void onEndOfSpeech(){if(listening)status.accept("Estoy escuchando la respuesta…");}
 @Override public void onError(int error){if(!listening)return;if(Build.VERSION.SDK_INT>=31&&(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)&&useLocal){useLocal=false;stop();status.accept("Idioma local no disponible. Toca Hablar otra vez para usar el servicio de Android; puede necesitar internet.");return;}stop();status.accept(error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Activa el permiso de micrófono en Acceso familiar.":"No pude entender. Toca Hablar e inténtalo otra vez, o usa los botones.");}
 @Override public void onResults(Bundle results){if(!listening)return;ArrayList<String> phrases=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);stop();if(phrases==null||phrases.isEmpty()){status.accept("No escuché una instrucción.");return;}commands.accept(VoiceCommand.parse(phrases.get(0)));}
 @Override public void onPartialResults(Bundle b){}@Override public void onEvent(int type,Bundle b){}
}
