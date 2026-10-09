package cl.abuhelp.launcher;
import org.junit.Test;import static org.junit.Assert.*;
public class ReminderDraftTest{
 @Test public void eveningAndDailyRemainDraft(){ReminderDraft d=ReminderDraft.parse("Recuérdame mi remedio a las ocho de la noche todos los días");assertEquals(20,d.hour);assertEquals(0,d.minute);assertTrue(d.daily);assertEquals("mi remedio",d.label);}
 @Test public void ambiguousEightNeverAssumesMorning(){ReminderDraft d=ReminderDraft.parse("Recuérdame mi remedio a las ocho");assertEquals(-1,d.hour);assertFalse(d.explanation.isEmpty());}
 @Test public void parsesHalfPast(){ReminderDraft d=ReminderDraft.parse("Recuérdame la prueba a las ocho y media de la mañana");assertEquals(8,d.hour);assertEquals(30,d.minute);}
 @Test public void supportsExplicit24HourTime(){ReminderDraft d=ReminderDraft.parse("Recuérdame prueba a las 20:45");assertEquals(20,d.hour);assertEquals(45,d.minute);}
 @Test public void rejectsInvalidTime(){assertEquals(-1,ReminderDraft.parse("Recuérdame prueba a las 99:00").hour);}
}
