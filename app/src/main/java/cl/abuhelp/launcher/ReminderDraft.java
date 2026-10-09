package cl.abuhelp.launcher;
import java.util.*;import java.util.regex.*;
/** Converts supported speech into an uncommitted draft. Ambiguous hours stay unset. */
public final class ReminderDraft {
 public final String label;public final int hour,minute;public final boolean daily;public final String explanation;
 private ReminderDraft(String l,int h,int m,boolean d,String e){label=l;hour=h;minute=m;daily=d;explanation=e;}
 public static ReminderDraft parse(String raw){
  String s=VoiceCommand.normalize(raw);boolean daily=s.matches(".* (todos los dias|cada dia|diariamente)$");
  s=s.replaceFirst(" (todos los dias|cada dia|diariamente)$","");
  Matcher m=Pattern.compile("^(?:recuerdame|recordar|programa un recordatorio(?: para)?) (.+?) a las? (.+)$").matcher(s);
  if(!m.matches())return new ReminderDraft("",-1,-1,daily,"Revisa el nombre y escribe la hora.");
  String label=m.group(1),t=m.group(2);Matcher digital=Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$").matcher(t);
  if(digital.matches())return new ReminderDraft(label,Integer.parseInt(digital.group(1)),Integer.parseInt(digital.group(2)),daily,"");
  boolean am=t.endsWith(" de la manana")||t.endsWith(" de la madrugada");
  boolean pm=t.endsWith(" de la tarde")||t.endsWith(" de la noche");
  t=t.replaceFirst(" de la (manana|madrugada|tarde|noche)$","");
  int minute=0;if(t.endsWith(" y media")){minute=30;t=t.substring(0,t.length()-8);}else if(t.endsWith(" y cuarto")){minute=15;t=t.substring(0,t.length()-9);}
  String[] hours={"cero","una","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte","veintiuna","veintidos","veintitres"};
  int h=-1;for(int i=0;i<hours.length;i++)if(t.equals(hours[i]))h=i;
  if(t.equals("uno"))h=1;if(t.matches("[0-9]{1,2}"))h=Integer.parseInt(t);
  if(h<0||h>23||(h>12&&(am||pm)))return new ReminderDraft(label,-1,-1,daily,"No se reconoció una hora válida. Escríbela en formato 24 horas.");
  if(!am&&!pm&&h>=1&&h<=12)return new ReminderDraft(label,-1,-1,daily,"Falta precisar si es por la mañana o por la tarde. No se eligió una hora.");
  if(am&&h==12)h=0;else if(pm&&h<12)h+=12;else if(pm&&h==12&&VoiceCommand.normalize(raw).contains("de la noche"))h=0;
  return new ReminderDraft(label,h,minute,daily,"");
 }
}
