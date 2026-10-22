package tv.vela.launcher;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** A small device-wrapped data key keeps catalog encryption fast, without storing a plaintext key. */
final class TvPrivateData {
    private static final String ALIAS="vela.tv.cache.wrap.v1";
    private final Context context;
    private final SecureRandom random=new SecureRandom();
    private SecretKey dataKey;
    TvPrivateData(Context c){context=c.getApplicationContext();}
    private synchronized SecretKey key()throws Exception{
        if(dataKey!=null)return dataKey;
        synchronized(TvPrivateData.class){
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(!store.containsAlias(ALIAS)){KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
            SecretKey wrapping=(SecretKey)store.getKey(ALIAS,null);SharedPreferences p=context.getSharedPreferences("tv-private",0);String encoded=p.getString("wrapped-key","");byte[] raw;
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            if(encoded.isEmpty()){raw=new byte[32];new SecureRandom().nextBytes(raw);cipher.init(Cipher.ENCRYPT_MODE,wrapping);byte[] value=pack(cipher.getIV(),cipher.doFinal(raw));if(!p.edit().putString("wrapped-key",Base64.encodeToString(value,Base64.NO_WRAP)).commit())throw new java.io.IOException("Cannot protect catalog");}
            else{byte[] value=Base64.decode(encoded,Base64.NO_WRAP);if(value.length!=60)throw new java.io.IOException("Catalog key unavailable");cipher.init(Cipher.DECRYPT_MODE,wrapping,new GCMParameterSpec(128,value,0,12));raw=cipher.doFinal(value,12,value.length-12);}
            dataKey=new SecretKeySpec(raw,"AES");java.util.Arrays.fill(raw,(byte)0);return dataKey;
        }
    }
    private static byte[] pack(byte[] iv,byte[] encrypted){byte[] out=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(encrypted,0,out,iv.length,encrypted.length);return out;}
    byte[] encrypt(String text)throws Exception{byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));return pack(iv,cipher.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    String decrypt(byte[] value)throws Exception{if(value==null||value.length<28||value.length>128*1024)throw new java.io.IOException("Invalid private data");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,value,0,12));return new String(cipher.doFinal(value,12,value.length-12),java.nio.charset.StandardCharsets.UTF_8);}
    String encode(String value)throws Exception{return Base64.encodeToString(encrypt(value),Base64.NO_WRAP);}
    String decode(String value)throws Exception{return decrypt(Base64.decode(value,Base64.NO_WRAP));}
}
