package cl.abuhelp.launcher;
import org.junit.Test;import static org.junit.Assert.*;import java.time.*;import java.util.UUID;
public class ReminderTimeTest{
 @Test public void exactCurrentTimeSchedulesTomorrow(){ZonedDateTime now=ZonedDateTime.of(2026,10,9,8,0,0,0,ReminderStore.ZONE);long next=ReminderStore.futureWallTime(8,0,now.toInstant().toEpochMilli());assertEquals(now.toLocalDate().plusDays(1),Instant.ofEpochMilli(next).atZone(ReminderStore.ZONE).toLocalDate());}
 @Test public void preservesLocalHourAcrossChileClockChange(){ZonedDateTime now=ZonedDateTime.of(2026,9,5,8,0,0,0,ReminderStore.ZONE);ZonedDateTime next=Instant.ofEpochMilli(ReminderStore.futureWallTime(8,0,now.toInstant().toEpochMilli())).atZone(ReminderStore.ZONE);assertEquals(8,next.getHour());assertEquals(now.toLocalDate().plusDays(1),next.toLocalDate());assertNotEquals(24*60*60*1000L,next.toInstant().toEpochMilli()-now.toInstant().toEpochMilli());}
 @Test(expected=IllegalArgumentException.class) public void rejectsInvalidHours(){new ReminderStore.Item(UUID.randomUUID().toString(),"Prueba",24,0,true,0);}
 @Test(expected=IllegalArgumentException.class) public void rejectsEmptyLabels(){new ReminderStore.Item(UUID.randomUUID().toString()," ",8,0,true,0);}
}
