package cl.abuhelp.launcher;
import org.junit.Test;import static org.junit.Assert.*;
public class VoiceCommandTest{
 @Test public void timeAcceptsAccentsAndPunctuation(){assertEquals("time",VoiceCommand.parse("¿Qué hora es?").action);}
 @Test public void doesNotExecuteNegatedOrUnknownCommands(){assertEquals("unknown",VoiceCommand.parse("No abras YouTube").action);assertEquals("unknown",VoiceCommand.parse("borra todos mis contactos").action);}
 @Test public void retainsReminderForExplicitReview(){VoiceCommand c=VoiceCommand.parse("Recuérdame el remedio a las ocho");assertEquals("reminder",c.action);assertEquals("Recuérdame el remedio a las ocho",c.value);}
 @Test public void contactIntentDoesNotPlaceCall(){VoiceCommand c=VoiceCommand.parse("Llama a José");assertEquals("call",c.action);assertEquals("jose",c.value);}
 @Test public void acceptsYoutubeAliases(){assertEquals("youtube",VoiceCommand.parse("Abrir You Tube").action);}
 @Test public void handlesEmptyInput(){assertEquals("unknown",VoiceCommand.parse(null).action);}
}
