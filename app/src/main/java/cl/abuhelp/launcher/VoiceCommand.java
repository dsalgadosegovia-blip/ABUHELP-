package cl.abuhelp.launcher;
import java.text.Normalizer;import java.util.Locale;
public final class VoiceCommand{
 public final String action,value;
 private VoiceCommand(String a,String v){action=a;value=v;}
 public static String normalize(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[¿?¡!.,]"," ").replaceAll("\\s+"," ").trim();}
 public static VoiceCommand parse(String text){
  String s=normalize(text);
  if(s.matches("(que hora es|dime la hora|hora|la hora)"))return new VoiceCommand("time","");
  if(s.matches("((abre|abrir|quiero abrir|quiero ver|ver) )?(youtube|you tube)"))return new VoiceCommand("youtube","");
  if(s.matches("((abre|abrir|quiero ver|ver) )?(las )?(noticias|noticias de chile|google noticias)"))return new VoiceCommand("news","");
  if(s.matches("((abre|abrir|quiero ver|ver) )?(el )?(clima|tiempo)"))return new VoiceCommand("weather","");
  if(s.matches("((abre|abrir|quiero jugar|jugar) )?(mahjong|mah jong|maihong|mayong)"))return new VoiceCommand("mahjong","");
  if(s.matches("((abre|abrir|ver) )?(los )?(contactos|telefono|llamadas)"))return new VoiceCommand("contacts","");
  if(s.startsWith("llama a ")||s.startsWith("llamar a ")){String n=s.substring(s.indexOf(" a ")+3).trim();return new VoiceCommand(n.isEmpty()?"unknown":"call",n);}
  if(s.startsWith("recuerdame ")||s.startsWith("programa un recordatorio ")||s.startsWith("recordar "))return new VoiceCommand("reminder",text.trim());
  if(s.matches("((abre|abrir|ver) )?(mis )?(remedios|recordatorios)"))return new VoiceCommand("reminder","");
  return new VoiceCommand("unknown",text==null?"":text);
 }
}
