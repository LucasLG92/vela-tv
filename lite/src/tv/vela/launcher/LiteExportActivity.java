package tv.vela.launcher;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import java.io.*;
import java.util.UUID;

/** One explicit, signer-checked grant to import an account-free profile into unified Vela. */
public final class LiteExportActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(!"tv.vela.launcher".equals(getCallingPackage())||getPackageManager().checkSignatures(getPackageName(),"tv.vela.launcher")!=PackageManager.SIGNATURE_MATCH){finish();return;}
        Ui.init(this);if(VelaSessions.blocked(this)){Toast.makeText(this,L10n.text("Unlock the old Lite app before importing."),Toast.LENGTH_LONG).show();finish();return;}FrameLayout root=new FrameLayout(this);root.setBackground(VelaTheme.canvas());setContentView(root);
        VelaReading.Stage stage=new VelaReading.Stage(this,1920,1080);root.addView(stage,new FrameLayout.LayoutParams(-1,-1));FrameLayout page=new FrameLayout(this);stage.addView(page);
        TextView title=text(L10n.text("Import current Lite profile"),42),detail=text(L10n.text("Your old app stays installed. Accounts and passwords are not shared."),26);put(page,title,260,250,1400,90);put(page,detail,260,380,1400,120);
        TextView allow=button(L10n.text("Import settings"),this::allow),cancel=button(L10n.text("Cancel"),this::finish);put(page,allow,260,620,680,86);put(page,cancel,980,620,680,86);cancel.requestFocus();root.post(cancel::requestFocus);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(VelaTheme.WHITE);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);v.setTypeface(Ui.medium);v.setMaxLines(3);return v;}
    private TextView button(String s,Runnable action){TextView v=text(s,26);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(VelaTheme.row(false,VelaTheme.WHITE));v.setOnFocusChangeListener((w,f)->{v.setBackground(VelaTheme.row(f,VelaTheme.WHITE));v.setTextColor(f?VelaTheme.INK:VelaTheme.WHITE);});v.setOnClickListener(w->action.run());return v;}
    private void put(FrameLayout parent,View v,int x,int y,int w,int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h);p.leftMargin=x;p.topMargin=y;parent.addView(v,p);}
    private void allow(){try{
        String token=UUID.randomUUID().toString().replace("-","");File file=new File(getCacheDir(),"unified-profile.json");
        try(OutputStream output=new FileOutputStream(file)){VelaWorkspace.write(VelaWorkspace.snapshot(this),output);}
        getSharedPreferences("vela-migration",0).edit().putString("token",token).putLong("expires",System.currentTimeMillis()+120000).commit();
        android.net.Uri uri=android.net.Uri.parse("content://"+getPackageName()+".migration/"+token);
        Intent result=new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("profileName",VelaWorkspace.profiles(this).get(VelaWorkspace.active(this)));result.setClipData(ClipData.newRawUri("Vela profile",uri));
        setResult(RESULT_OK,result);finish();
    }catch(Exception failed){Toast.makeText(this,L10n.text("Could not export settings."),Toast.LENGTH_LONG).show();}}
}
