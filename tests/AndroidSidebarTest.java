package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Native picker, focus, shortcut routing and restoration; never changes home mode or launches another app. */
public class AndroidSidebarTest extends Instrumentation {
    private Object field(Object owner,String name)throws Exception{if(owner instanceof ScreenActivity)owner=((ScreenActivity)owner).screen;for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void capture(Context c,String file)throws Exception{
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Missing screenshot");
        try(OutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),file))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private List<FrameLayout> rows(SettingsActivity a)throws Exception{return (List<FrameLayout>)field(field(a,"view"),"rows");}
    private int page(SettingsActivity a)throws Exception{return (Integer)field(field(a,"view"),"page");}
    private void choose(SettingsActivity a,String name){ui(()->{try{for(FrameLayout row:rows(a))if(row.getContentDescription().toString().equals(name)||row.getContentDescription().toString().startsWith(name+",")){row.performClick();return;}throw new AssertionError("App absent from native rows: "+name);}catch(Exception e){throw new RuntimeException(e);}});}
    private void render(SettingsActivity a,int page){ui(()->{try{Object view=field(a,"view");Method method=view.getClass().getDeclaredMethod("render",int.class);method.setAccessible(true);method.invoke(view,page);}catch(Exception e){throw new RuntimeException(e);}});}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences prefs=SidebarApps.prefs(c);
        Map<String,?> saved=new HashMap<>(prefs.getAll());SettingsActivity settings=null;HomeActivity homeActivity=null;ActivityMonitor monitor=null;
        try{
            SidebarApps.reset(c);String[] defaults=SidebarApps.slots(c);
            check(defaults.length==4&&defaults[0].equals("com.plexapp.android")&&defaults[1].equals(SidebarApps.YOUTUBE),"Default access migration failed");
            prefs.edit().putString("slots",new org.json.JSONArray(Arrays.asList(defaults)).toString()).commit();
            check(Arrays.equals(defaults,SidebarApps.slots(c)),"Legacy four-slot preference did not migrate");
            SidebarApps.assign(c,0,SidebarApps.YOUTUBE);String[] swapped=SidebarApps.slots(c);
            check(swapped[0].equals(SidebarApps.YOUTUBE)&&swapped[1].equals(defaults[0])&&swapped[2].equals(defaults[2]),"Reordering duplicated or lost an app");
            SidebarApps.assign(c,1,"");check(SidebarApps.slots(c)[1].isEmpty()&&SidebarApps.slots(c)[0].equals(SidebarApps.YOUTUBE),"Hiding affected another position");
            SidebarApps.assign(c,3,"com.vela.uninstalled");check(SidebarApps.slots(c)[3].equals("com.vela.uninstalled")&&!SidebarApps.launchable(c,"com.vela.uninstalled"),"Unavailable choice lost instead of being retained");
            prefs.edit().putString("slots","bad json").commit();check(Arrays.equals(defaults,SidebarApps.slots(c)),"Corrupt preference did not recover defaults");
            prefs.edit().putString("slots","[\"com.plexapp.android\",\"com.plexapp.android\",null,\"tv.vela.launcher\"]").commit();
            String[] sanitized=SidebarApps.slots(c);check(sanitized[1].isEmpty()&&sanitized[2].isEmpty()&&sanitized[3].isEmpty(),"Invalid or duplicate shortcuts retained");
            SidebarApps.reset(c);check(SidebarApps.add(c,defaults[0])==0&&SidebarApps.slots(c).length==4,"Adding an existing app duplicated it");
            for(int i=0;i<8;i++)SidebarApps.add(c,"com.vela.fixture.app"+i);
            check(SidebarApps.slots(c).length==12&&Arrays.equals(defaults,Arrays.copyOf(SidebarApps.slots(c),4)),"Adding beyond four lost existing choices");
            SidebarApps.remove(c,5);check(SidebarApps.slots(c).length==11&&SidebarApps.slots(c)[5].equals("com.vela.fixture.app2"),"Removal did not preserve remaining order");
            while(SidebarApps.slots(c).length>0)SidebarApps.remove(c,0);check(SidebarApps.slots(c).length==0,"Empty list was reset unexpectedly");
            SidebarApps.reset(c);List<SidebarApps.Choice> choices=SidebarApps.choices(c);Set<String> distinct=new HashSet<>();SidebarApps.Choice alternate=null;
            for(SidebarApps.Choice choice:choices){check(distinct.add(choice.pkg)&&!choice.pkg.equals(c.getPackageName())&&SidebarApps.launchable(c,choice.pkg),"Picker has duplicate, own or unavailable app");if(!Arrays.asList(defaults).contains(choice.pkg))alternate=choice;}
            check(alternate!=null,"Device needs one additional launchable app for routing test");final SidebarApps.Choice app=alternate;
            homeActivity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final CinemaHomeView home=(CinemaHomeView)field(homeActivity,"home");
            settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final SettingsActivity a=settings;
            ui(()->{try{rows(a).get(5).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();
            check(page(a)==11&&rows(a).size()==6,"Main settings did not show access slots and Add action");
            capture(c,"sidebar-settings.png");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);waitForIdleSync();check(page(a)==12,"Slot did not open app picker");
            List<FrameLayout> options=rows(a);check(options.size()==choices.size()+2,"Picker omits installed apps");
            final FrameLayout last=options.get(options.size()-1);ui(()->last.requestFocus());
            check(last.hasFocus(),"Picker last app cannot receive focus");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();check(options.get(options.size()-2).hasFocus(),"Remote navigation across scrolled apps failed");
            ui(()->options.get(0).requestFocus());capture(c,"sidebar-picker.png");
            ui(()->{for(FrameLayout row:options)if(row.getContentDescription().toString().equals(app.label)||row.getContentDescription().toString().startsWith(app.label+",")){row.performClick();return;}throw new AssertionError("Installed app absent from native rows");});
            check(page(a)==11&&SidebarApps.slots(c)[0].equals(app.pkg),"Native choice not saved");
            String[] beforeAdd=SidebarApps.slots(c);
            ui(()->{try{rows(a).get(beforeAdd.length).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            check(page(a)==12,"Add shortcut did not open picker");
            for(FrameLayout option:rows(a))check(!option.getContentDescription().toString().startsWith(app.label+",")&&!option.getContentDescription().toString().equals(app.label),"Add picker offered an existing shortcut");
            choose(a,"Plex");check(SidebarApps.slots(c).length==5&&Arrays.equals(beforeAdd,Arrays.copyOf(SidebarApps.slots(c),4)),"Native Add did not preserve first four positions");
            ui(()->{try{rows(a).get(4).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            ui(()->{try{rows(a).get(1).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            check(page(a)==11&&Arrays.equals(beforeAdd,SidebarApps.slots(c)),"Native removal changed other accesses");
            ui(()->{try{rows(a).get(4).performClick();}catch(Exception e){throw new RuntimeException(e);}});choose(a,"Plex");
            for(SidebarApps.Choice choice:choices){if(SidebarApps.slots(c).length>=10)break;SidebarApps.add(c,choice.pkg);}
            check(SidebarApps.slots(c).length>4,"Device needs more than four installed apps");
            render(a,11);capture(c,"sidebar-many-settings.png");
            ui(()->a.finish());Thread.sleep(250);waitForIdleSync();
            ui(()->{try{
                check(((String[])field(home,"sidePackages"))[0].equals(app.pkg),"Existing home did not refresh shortcut on return");
                ImageView icon=((List<ImageView>)field(home,"appIcons")).get(0);Bitmap bitmap=((BitmapDrawable)icon.getDrawable()).getBitmap();check(bitmap.getWidth()<=64&&bitmap.getHeight()<=64,"Installed icon is not bounded");
            }catch(Exception e){throw new RuntimeException(e);}});
            final List<FrameLayout> appButtons=(List<FrameLayout>)field(home,"appButtons"),fixed=(List<FrameLayout>)field(home,"sideButtons");
            check(appButtons.size()==SidebarApps.slots(c).length,"Home truncated accesses after four");
            ui(()->fixed.get(3).requestFocus());sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();check(appButtons.get(0).hasFocus(),"Library did not enter first shortcut");
            for(int i=1;i<appButtons.size();i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();check(appButtons.get(i).hasFocus(),"Remote skipped shortcut "+i);}
            Thread.sleep(350);check(((ScrollView)field(home,"appScroll")).getScrollY()>0,"Extra shortcuts did not scroll");
            Rect visible=new Rect();check(appButtons.get(appButtons.size()-1).getGlobalVisibleRect(visible)&&visible.height()>=50,"Last shortcut is clipped or invisible");
            capture(c,"sidebar-many-home.png");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();check(fixed.get(4).hasFocus(),"Last shortcut did not reach All Apps");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();check(appButtons.get(appButtons.size()-1).hasFocus(),"All Apps did not return to last shortcut");
            for(int i=appButtons.size()-1;i>=0;i--)sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            waitForIdleSync();check(fixed.get(3).hasFocus(),"First shortcut did not return to Library");
            Intent launch=HomeApps.launchIntent(c.getPackageManager(),app.pkg);check(launch!=null&&launch.getComponent()!=null,"Expected explicit TV launch intent");
            monitor=addMonitor(launch.getComponent().getClassName(),null,true);final ActivityMonitor blocked=monitor;
            ui(()->{try{((List<FrameLayout>)field(home,"appButtons")).get(0).performClick();}catch(Exception e){throw new RuntimeException(e);}});
            check(blocked.getHits()==1,"Sidebar opened a hard-coded app instead of chosen app");removeMonitor(monitor);monitor=null;
            ui(()->{SidebarApps.assign(c,0,SidebarApps.YOUTUBE);home.resume();try{((List<FrameLayout>)field(home,"appButtons")).get(0).performClick();check((Integer)field(home,"filter")==3,"Moved YouTube lost its content route");}catch(Exception e){throw new RuntimeException(e);}});
            ui(()->{for(int i=0;i<SidebarApps.slots(c).length;i++)SidebarApps.assign(c,i,"");home.resume();try{
                for(FrameLayout button:(List<FrameLayout>)field(home,"appButtons"))check(button.getVisibility()==View.GONE,"Hidden access remains focusable");check(((List<FrameLayout>)field(home,"sideButtons")).get(7).getVisibility()==View.VISIBLE,"Hiding apps hid settings");check((Integer)field(home,"filter")==0,"Removed YouTube left an orphaned selection");
            }catch(Exception e){throw new RuntimeException(e);}});
            ui(()->{while(SidebarApps.slots(c).length>0)SidebarApps.remove(c,0);home.resume();fixed.get(3).requestFocus();});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();check(fixed.get(4).hasFocus(),"Empty shortcut list trapped remote focus");
            SidebarApps.reset(c);check(Arrays.equals(defaults,SidebarApps.slots(c)),"Reset did not recover original four apps");
            result.putString("result","PASS legacy migration, 12 saved positions, duplicate prevention, removal and empty list, native Add and Remove preserving previous choices, 10 installed shortcuts, real remote scrolling in both directions and footer boundaries, original bounded icons, selected-app routing, YouTube after moving, hidden/empty navigation, reset. Original preferences restored.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));}
        finally{
            if(monitor!=null)removeMonitor(monitor);
            SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet())if(entry.getValue() instanceof String)edit.putString(entry.getKey(),(String)entry.getValue());edit.commit();
            if(settings!=null){SettingsActivity a=settings;ui(()->a.finish());}
            if(homeActivity!=null){HomeActivity a=homeActivity;ui(()->a.finish());}
        }
        finish(result.containsKey("error")?1:-1,result);
    }
}
