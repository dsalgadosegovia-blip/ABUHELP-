package cl.abuhelp.launcher;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;

public final class Ui {
 public static final int NAVY=Color.rgb(15,35,65), BG=Color.rgb(255,249,238), GREEN=Color.rgb(20,91,55), RED=Color.rgb(164,28,31);
 public static int dp(Context c,int v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}
 public static LinearLayout page(Activity a,String title){
  a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  ScrollView s=new ScrollView(a); s.setFillViewport(true); s.setBackgroundColor(BG);
  LinearLayout l=new LinearLayout(a); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,24));
  s.addView(l,new ScrollView.LayoutParams(-1,-2)); a.setContentView(s); l.addView(text(a,title,32,true)); return l;
 }
 public static TextView text(Context c,String value,int size,boolean bold){
  TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(NAVY);t.setPadding(0,dp(c,8),0,dp(c,8));
  if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 public static Button button(Context c,String label,int color,Runnable action){
  Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(28);b.setTextColor(Color.WHITE);b.setMinHeight(dp(c,88));
  b.setPadding(dp(c,12),dp(c,14),dp(c,12),dp(c,14));GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,16));b.setBackground(d);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,7),0,dp(c,7));b.setLayoutParams(p);
  final long[] last={-1000};b.setOnClickListener(v->{long now=SystemClock.elapsedRealtime();if(now-last[0]<600)return;last[0]=now;action.run();});return b;
 }
 public static EditText input(Context c,String hint,int type){
  EditText e=new EditText(c);e.setTextSize(28);e.setTextColor(NAVY);e.setHint(hint);e.setInputType(type);e.setMinHeight(dp(c,72));return e;
 }
 public static void message(Context c,String title,String text){new AlertDialog.Builder(c).setTitle(title).setMessage(text).setPositiveButton("Entendido",null).show();}
 public static void open(Context c,Intent i){
  try{c.startActivity(i);}catch(ActivityNotFoundException|SecurityException e){message(c,"No se pudo abrir","Pide a un familiar que revise esta aplicación en Acceso familiar.");}
 }
 public static void back(Activity a,LinearLayout l){l.addView(button(a,"Volver",NAVY,a::finish));}
 private Ui(){}
}
