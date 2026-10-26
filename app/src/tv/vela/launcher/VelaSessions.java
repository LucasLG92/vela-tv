package tv.vela.launcher;

import android.content.*;
import android.os.SystemClock;
import java.io.IOException;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;

/** Local settings lock, not Android parental controls. Neither PIN material nor sessions are backed up. */
final class VelaSessions {
    private static long unlockedUntil;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("vela-lock",0);}
    static boolean configured(Context c){return prefs(c).contains("hash");}
    static boolean guest(Context c){return VelaWorkspace.active(c).equals(prefs(c).getString("guest",""));}
    static boolean blocked(Context c){return guest(c)||configured(c)&&SystemClock.elapsedRealtime()>=unlockedUntil;}
    static void lock(){unlockedUntil=0;}
    static void setPin(Context c,String pin)throws Exception{
        if(!pin.matches("[0-9]{4,8}"))throw new IOException("PIN");
        if(configured(c)&&blocked(c))throw new IOException("Locked");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        String hash=Base64.getEncoder().encodeToString(hash(pin,salt));
        if(!prefs(c).edit().putString("salt",Base64.getEncoder().encodeToString(salt)).putString("hash",hash).remove("failures").remove("retry").commit())throw new IOException("PIN write failed");
        unlockedUntil=SystemClock.elapsedRealtime()+5*60_000;
    }
    private static byte[] hash(String pin,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,65000,256);try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}}
    static boolean unlock(Context c,String pin)throws Exception{
        SharedPreferences p=prefs(c);long now=System.currentTimeMillis();if(p.getLong("retry",0)>now)return false;
        boolean ok=configured(c)&&pin.matches("[0-9]{4,8}")&&MessageDigest.isEqual(hash(pin,Base64.getDecoder().decode(p.getString("salt",""))),Base64.getDecoder().decode(p.getString("hash","")));
        if(ok){p.edit().remove("failures").remove("retry").commit();unlockedUntil=SystemClock.elapsedRealtime()+5*60_000;return true;}
        int n=p.getInt("failures",0)+1;p.edit().putInt("failures",n).putLong("retry",n>=5?now+30_000:0).commit();return false;
    }
    private static boolean nameExists(Context c,String name){for(String n:VelaWorkspace.profiles(c).values())if(VelaWorkspace.normal(n).equals(VelaWorkspace.normal(name)))return true;return false;}
    static void enterGuest(Context c)throws Exception{
        if(!configured(c)||blocked(c))throw new IOException("Unlock first");String id=prefs(c).getString("guest","");
        if(!VelaWorkspace.profiles(c).containsKey(id)){String base=L10n.text("Guest"),name=base;for(int n=2;nameExists(c,name)&&n<=VelaWorkspace.MAX_PROFILES;n++)name=base+" "+n;id=VelaWorkspace.create(c,name);if(!prefs(c).edit().putString("guest",id).commit())throw new IOException("Guest write failed");}
        if(!prefs(c).edit().putString("return",VelaWorkspace.active(c)).commit())throw new IOException("Guest write failed");VelaShows.cancel(c);VelaWorkspace.select(c,id);VelaShows.schedule(c);lock();
    }
    static void exitGuest(Context c)throws Exception{if(SystemClock.elapsedRealtime()>=unlockedUntil)throw new IOException("Locked");String id=prefs(c).getString("return","default");VelaShows.cancel(c);VelaWorkspace.select(c,VelaWorkspace.profiles(c).containsKey(id)?id:"default");VelaShows.schedule(c);}
}
