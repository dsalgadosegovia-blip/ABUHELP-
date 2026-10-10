package cl.abuhelp.launcher;
import org.junit.Test;
import static org.junit.Assert.*;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
public class ProximityPolicyTest {
 @Test public void handsetCallsUseProximity(){
  for(int state:new int[]{Call.STATE_ACTIVE,Call.STATE_DIALING,Call.STATE_CONNECTING})
   assertTrue(ProximityPolicy.atEar(state,CallAudioState.ROUTE_EARPIECE,0));
 }
 @Test public void incomingHeldAndFinishedCallsLeaveControlsVisible(){
  for(int state:new int[]{Call.STATE_RINGING,Call.STATE_HOLDING,Call.STATE_DISCONNECTED,Call.STATE_DISCONNECTING,Call.STATE_SELECT_PHONE_ACCOUNT,-1})
   assertFalse(ProximityPolicy.atEar(state,CallAudioState.ROUTE_EARPIECE,0));
 }
 @Test public void speakerAndExternalAudioLeaveScreenAvailable(){
  for(int route:new int[]{CallAudioState.ROUTE_SPEAKER,CallAudioState.ROUTE_BLUETOOTH,CallAudioState.ROUTE_WIRED_HEADSET,0})
   assertFalse(ProximityPolicy.atEar(Call.STATE_ACTIVE,route,0));
 }
 @Test public void videoCallsLeaveScreenAvailable(){
  for(int video:new int[]{VideoProfile.STATE_TX_ENABLED,VideoProfile.STATE_RX_ENABLED,VideoProfile.STATE_BIDIRECTIONAL})
   assertFalse(ProximityPolicy.atEar(Call.STATE_ACTIVE,CallAudioState.ROUTE_EARPIECE,video));
 }
}
