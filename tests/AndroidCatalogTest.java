package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Exercises bounded catalog pages and artwork recycling on the user's privately linked server. */
public class AndroidCatalogTest extends Instrumentation {
    private CinemaHomeView home;
    private Object field(Object owner,String name)throws Exception{if(owner instanceof ScreenActivity)owner=((ScreenActivity)owner).screen;for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void set(String name,Object value)throws Exception{Field f=home.getClass().getDeclaredField(name);f.setAccessible(true);f.set(home,value);}
    private void invoke(String name)throws Exception{Method m=home.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(home);}
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void key(int code){sendKeyDownUpSync(code);waitForIdleSync();}
    private List<View> cards()throws Exception{return (List<View>)field(home,"cards");}
    private List<ImageView> images()throws Exception{return (List<ImageView>)field(home,"posterImages");}
    private interface Ready{boolean get()throws Exception;}
    private void await(Ready ready,String message)throws Exception{
        long until=SystemClock.uptimeMillis()+30_000;
        while(SystemClock.uptimeMillis()<until){boolean[] done={false};ui(()->{try{done[0]=ready.get();}catch(Exception e){throw new RuntimeException(e);}});if(done[0])return;Thread.sleep(120);}
        throw new AssertionError(message);
    }
    private void browse(int index)throws Exception{View button=((List<View>)field(home,"sideButtons")).get(index);ui(()->button.performClick());await(()->!(Boolean)field(home,"catalogLoading")&&field(home,"catalogPage")!=null,"Catalog page did not load");}
    private PlexClient.LibraryPage page()throws Exception{return (PlexClient.LibraryPage)field(home,"catalogPage");}
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();check(b!=null,"Screenshot unavailable");try(OutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    private void awaitHeader()throws Exception{await(()->field(home,"heroBitmap")!=null&&("Plex"+((PlexClient.Film)field(home,"selected")).key).equals(field(home,"heroKey")),"Selected real backdrop missing");}
    public void onCreate(Bundle b){super.onCreate(b);start();}
    public void onStart(){Bundle result=new Bundle();int code=-1;List<PlexClient.Film> saved=null;SettingsActivity settings=null;
        try{
            Context c=getTargetContext();PlexClient plex=new PlexClient(c);check(plex.connected(),"Plex must be linked privately");
            List<PlexClient.Section> sections=plex.sections();check(!sections.isEmpty(),"No movie/show sections shared by server");
            int checked=0;for(PlexClient.Section section:sections){PlexClient.LibraryPage first=plex.page(section,0);check(first.films.size()<=24,"Unbounded first page");if(first.hasNext()){PlexClient.LibraryPage second=plex.page(section,first.nextOffset());Set<String> ids=new HashSet<>();for(PlexClient.Film f:first.films)ids.add(f.key);for(PlexClient.Film f:second.films)check(!ids.contains(f.key),"Server repeated page one");checked++;}}
            check(checked>0,"A real multi-page library is required");result.putInt("sections_checked",sections.size());
            HomeActivity activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));home=(CinemaHomeView)field(activity,"home");
            await(()->!(Boolean)field(home,"loading")&&!cards().isEmpty()&&field(home,"heroBitmap")!=null,"Initial real home did not load");
            int resumed=0;for(PlexClient.Film film:(List<PlexClient.Film>)field(home,"films"))if(film.continuing)resumed++;result.putInt("real_continue_watching",resumed);
            ui(()->{try{home.reset();cards().get(0).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});
            await(()->images().get(0).getDrawable()!=null,"First real home poster missing");shot("catalog-home.png");
            browse(3);check(page().offset==0&&page().films.size()<=24,"First catalog page is wrong");
            check(!((View)field(home,"pagePrevious")).isEnabled(),"Previous must be disabled on page one");
            await(()->!images().isEmpty()&&images().get(0).getDrawable()!=null,"Catalog artwork missing");awaitHeader();shot("catalog-library.png");
            PlexClient.Section section=(PlexClient.Section)field(home,"catalogSection");int total=page().total;result.putInt("first_library_total",total);
            if(page().hasNext()){
                View nextButton=(View)field(home,"pageNext");
                ui(()->{nextButton.performClick();home.pause();home.releaseImages();home.resume();});
                check(page().offset==0&&nextButton.isEnabled(),"Returning after a cancelled load left pagination disabled");
                awaitHeader();
                ImageView old=images().get(0);List<String> ids=new ArrayList<>();for(PlexClient.Film f:page().films)ids.add(f.key);
                View next=(View)field(home,"pageNext");ui(()->{next.performClick();next.performClick();});
                await(()->!(Boolean)field(home,"catalogLoading")&&page().offset==24,"Next page or duplicate-click guard failed");
                check(old.getDrawable()==null&&!old.isAttachedToWindow(),"Replaced page kept artwork or remained attached");
                for(PlexClient.Film f:page().films)check(!ids.contains(f.key),"Next page repeated titles");
                check(cards().size()<=24,"Views accumulated between pages");awaitHeader();shot("catalog-page-two.png");
                ui(()->{try{((View)field(home,"pagePrevious")).performClick();}catch(Exception e){throw new RuntimeException(e);}});
                await(()->!(Boolean)field(home,"catalogLoading")&&page().offset==0,"Previous page did not return");
                ui(()->{try{cards().get(cards().size()-1).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});key(KeyEvent.KEYCODE_DPAD_RIGHT);
                await(()->!(Boolean)field(home,"catalogLoading")&&page().offset==24,"Right arrow on last poster did not load next page");
            }
            // Check the last page directly without traversing the entire server catalog.
            if(total>0){Method load=home.getClass().getDeclaredMethod("loadCatalog",String.class,int.class,boolean.class);load.setAccessible(true);int last=(total-1)/24*24;ui(()->{try{load.invoke(home,section.key,last,true);}catch(Exception e){throw new RuntimeException(e);}});await(()->!(Boolean)field(home,"catalogLoading")&&page().offset==last,"Last page did not load");check(!page().hasNext()&&!((View)field(home,"pageNext")).isEnabled(),"Last page offered a duplicate next page");}
            for(int category:new int[]{1,2}){
                boolean available=false;for(PlexClient.Section s:sections)if(s.type.equals(category==1?"movie":"show"))available=true;
                if(available){browse(category);check(((PlexClient.Section)field(home,"catalogSection")).type.equals(category==1?"movie":"show"),"Sidebar category chose wrong library type");}
            }
            browse(3);ui(()->{try{((View)field(home,"libraryChooser")).performClick();}catch(Exception e){throw new RuntimeException(e);}});check(((View)field(home,"overlay")).getVisibility()==View.VISIBLE,"Library chooser did not open");
            ui(()->{try{FrameLayout overlay=(FrameLayout)field(home,"overlay");for(int i=0;i<overlay.getChildCount();i++)if(overlay.getChildAt(i) instanceof ScrollView){LinearLayout list=(LinearLayout)((ScrollView)overlay.getChildAt(i)).getChildAt(0);check(list.getChildCount()==sections.size(),"Chooser omitted a shared library");list.getChildAt(0).performClick();break;}}catch(Exception e){throw new RuntimeException(e);}});
            await(()->!(Boolean)field(home,"catalogLoading")&&((PlexClient.Section)field(home,"catalogSection")).key.equals(sections.get(0).key),"Choosing a different library did not load its first page");
            if(page().hasNext()){ui(()->{try{((View)field(home,"pageNext")).performClick();home.reset();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(1500);check((Integer)field(home,"filter")==0&&field(home,"catalogPage")==null,"Cancelled page changed home after returning");}
            ui(()->{try{set("filter",4);set("catalogPage",new PlexClient.LibraryPage(Collections.emptyList(),0,0,0));invoke("showFilms");check(cards().isEmpty(),"Empty page retained old cards");check(!((View)field(home,"pageNext")).isEnabled()&&!((View)field(home,"pagePrevious")).isEnabled(),"Empty catalog has an invalid page action");home.reset();}catch(Exception e){throw new RuntimeException(e);}});
            await(()->!(Boolean)field(home,"loading"),"Feed refresh pending");saved=(List<PlexClient.Film>)field(home,"films");
            List<PlexClient.Film> staged=new ArrayList<>();List<PlexClient.Film> sample=plex.page(section,0).films;
            for(int i=0;i<24;i++){PlexClient.Film f=new PlexClient.Film(),source=sample.get(i%sample.size());f.key="test-"+i;f.title=source.title;f.type=source.type;f.summary=source.summary;f.thumb=source.thumb;f.art=source.art;f.meta=source.meta;f.continuing=i<12;f.progress=i<12?.5f:-1;staged.add(f);}
            ui(()->{try{set("films",staged);home.reset();cards().get(0).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});
            check(((List)field(home,"shelves")).size()==2,"Continue watching was not separated from library");
            await(()->images().get(0).getDrawable()!=null,"Staged first row did not load real artwork");
            check(images().get(12).getDrawable()==null,"Offscreen lower row decoded artwork");
            key(KeyEvent.KEYCODE_DPAD_DOWN);await(()->cards().get(12).hasFocus(),"Down did not focus the matching poster in the next row");await(()->images().get(12).getDrawable()!=null,"Next row artwork missing");await(()->images().get(0).getDrawable()==null,"Previous row artwork retained");
            ui(()->{try{cards().get(23).requestFocus();}catch(Exception e){throw new RuntimeException(e);}});
            await(()->images().get(23).getDrawable()!=null&&images().get(12).getDrawable()==null,"Far horizontal posters were retained");
            int loaded=0;for(ImageView image:images())if(image.getDrawable()!=null)loaded++;check(loaded<=12,"Offscreen poster images accumulated");result.putInt("posters_retained_at_far_end",loaded);
            key(KeyEvent.KEYCODE_DPAD_UP);await(()->cards().get(11).hasFocus()&&images().get(11).getDrawable()!=null&&images().get(23).getDrawable()==null,"Up navigation or row release failed");
            settings=(SettingsActivity)startActivitySync(new Intent(c,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            await(()->{if(!(Boolean)field(home,"artReleased")||field(home,"heroBitmap")!=null)return false;for(ImageView image:images())if(image.getDrawable()!=null)return false;return true;},"Stopped home retained artwork");
            key(KeyEvent.KEYCODE_BACK);settings=null;await(()->field(home,"heroBitmap")!=null,"Home artwork did not return after settings");
            result.putString("result","PASS real multi-section Plex pages, next/previous/last and remote paging, no duplicate-click skipping, category filters, chooser, stale cancellation, separate continue watching, horizontal/vertical offscreen recycling and stop/resume artwork release");
        }catch(Throwable e){code=1;result.putString("error",android.util.Log.getStackTraceString(e));}
        finally{if(home!=null){final List<PlexClient.Film> restore=saved;ui(()->{try{if(restore!=null)set("films",restore);home.reset();}catch(Exception ignored){}});}if(settings!=null){SettingsActivity old=settings;ui(()->old.finish());}}
        finish(code,result);
    }
}
