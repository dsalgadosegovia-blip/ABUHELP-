package cl.abuhelp.launcher;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
final class ProximityPolicy {
 private ProximityPolicy(){}
 static boolean atEar(int state,int route,int videoState){
  return route==CallAudioState.ROUTE_EARPIECE
   && (videoState & VideoProfile.STATE_BIDIRECTIONAL)==0
   && (state==Call.STATE_ACTIVE||state==Call.STATE_DIALING||state==Call.STATE_CONNECTING);
 }
}
