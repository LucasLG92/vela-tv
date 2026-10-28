package tv.vela.launcher;

import android.app.*;
import android.content.*;
import android.database.MatrixCursor;
import android.media.tv.TvContract;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Watch Next mapping and the mixed home row, without depending on private accounts. */
public class AndroidContinueWatchingTest extends Instrumentation {
    private void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private Object field(Object o,String name)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void set(Object o,String name,Object value)throws Exception{if(o instanceof ScreenActivity)o=((ScreenActivity)o).screen;for(Class<?> type=o.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException next){}throw new NoSuchFieldException(name);}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void render(CinemaHomeView home){ui(()->{try{Method m=home.getClass().getDeclaredMethod("showFilms");m.setAccessible(true);m.invoke(home);}catch(Exception e){throw new RuntimeException(e);}});}
    private MatrixCursor row(Object... values){MatrixCursor cursor=new MatrixCursor(ContinueWatching.COLUMNS);cursor.addRow(Arrays.copyOf(values,ContinueWatching.COLUMNS.length));cursor.moveToFirst();return cursor;}
    private String text(View view){StringBuilder result=new StringBuilder();if(view instanceof TextView)result.append(((TextView)view).getText()).append(' ');if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result.append(text(((ViewGroup)view).getChildAt(i)));return result.toString();}
    private TextView label(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView match=label(((ViewGroup)view).getChildAt(i),value);if(match!=null)return match;}return null;}
    private TextView caption(View card,String title){ViewGroup group=(ViewGroup)card;for(int i=0;i<group.getChildCount();i++){View v=group.getChildAt(i);if(v instanceof TextView&&title.contentEquals(((TextView)v).getText()))return (TextView)v;}return null;}
    private int redPixel(HorizontalScrollView rail,int x)throws Exception{android.graphics.Rect bounds=new android.graphics.Rect();ui(()->rail.getGlobalVisibleRect(bounds));float scale=bounds.width()/(float)rail.getWidth();Thread.sleep(100);android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();try{return android.graphics.Color.red(shot.getPixel(bounds.left+Math.round(x*scale),bounds.top+Math.round(80*scale)));}finally{shot.recycle();}}
    private void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:values.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Set)edit.putStringSet(e.getKey(),new HashSet<>((Set<String>)v));}edit.commit();}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();HomeActivity activity=null;Context c=getTargetContext();SharedPreferences personalization=Personalization.prefs(c),visuals=Visuals.prefs(c);Map<String,?> saved=new HashMap<>(personalization.getAll()),visualSaved=new HashMap<>(visuals.getAll());
        try{
            Locale.setDefault(new Locale("es","ES"));Map<String,?> plexBefore=new HashMap<>(new PlexClient(c).prefs.getAll());String[] apps=SidebarApps.slots(c);
            PlexClient.Film video;
            try(MatrixCursor cursor=row(1,TvSuggestions.YOUTUBE,"Vídeo real","Descripción","https://i.ytimg.com/vi/example/hqdefault.jpg","","https://www.youtube.com/watch?v=example",600_000,180_000,2000,0,1,"","",TvContract.WatchNextPrograms.TYPE_CLIP)){
                video=ContinueWatching.parse(cursor);check(video!=null&&video.tvSource&&video.continuing&&video.landscape,"Continue item lost its source/layout flags");check(video.remainingMs()==420_000&&Math.abs(video.progress-.3f)<.001,"Watch Next duration/position mapping failed");
            }
            check(ContinueWatching.remaining(video).equals("Quedan 7 min"),"Watch Next remaining time is not based on the supplied progress");
            try(MatrixCursor cursor=row(2,"com.example.app","Sin tiempo","","https://example.com/a.jpg","","https://example.com/play",null,null,0,0,1,"","",0)){PlexClient.Film unknown=ContinueWatching.parse(cursor);check(unknown!=null&&unknown.remainingMs()==-1&&ContinueWatching.remaining(unknown).equals("Continuar"),"Missing position invented a remaining time");}
            try(MatrixCursor cursor=row(3,"com.example.app","Terminado","","https://example.com/a.jpg","","https://example.com/play",600,600,0,0,1,"","",0)){check(ContinueWatching.parse(cursor)==null,"Completed item was offered for resume");}
            try(MatrixCursor cursor=row(4,"com.example.app","Por ver","","https://example.com/a.jpg","","https://example.com/play",600,0,0,TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST,1,"","",0)){check(ContinueWatching.parse(cursor)==null,"Watchlist was treated as started playback");}
            try(MatrixCursor cursor=row(5,"com.example.app","Oculto","","https://example.com/a.jpg","","https://example.com/play",600,50,0,0,0,"","",0)){check(ContinueWatching.parse(cursor)==null,"Hidden item was displayed");}
            try(MatrixCursor cursor=row(11,"com.example.app","Capítulo nuevo","","https://example.com/episode.jpg","","https://example.com/play",2_700_000,null,1000,TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_NEW,1,"1","2",TvContract.WatchNextPrograms.TYPE_TV_EPISODE)){PlexClient.Film next=ContinueWatching.parse(cursor);check(next!=null&&next.nextEpisode&&next.positionMs==0&&ContinueWatching.remaining(next).equals("Siguiente episodio · 45 min"),"Shared next/new episode was not shown");}
            PlexClient.Film prime,square;
            try(MatrixCursor cursor=row(6,"com.amazon.amazonvideo.livingroom","Serie de Prime","","https://example.com/episode.jpg","","https://example.com/play",3_600_000,600_000,1000,0,1,"3","2",TvContract.WatchNextPrograms.TYPE_TV_EPISODE,TvContract.WatchNextPrograms.ASPECT_RATIO_16_9,null)){
                prime=ContinueWatching.parse(cursor);check(prime!=null&&prime.landscape&&Math.abs(prime.thumbAspect-16f/9)<.001,"Episode artwork ignored the declared panoramic format");
            }
            try(MatrixCursor cursor=row(7,"com.amazon.amazonvideo.livingroom","Cartel de Prime","","https://example.com/poster.jpg","","https://example.com/play",600_000,100_000,0,0,1,"","",0,TvContract.WatchNextPrograms.ASPECT_RATIO_2_3,null)){
                PlexClient.Film poster=ContinueWatching.parse(cursor);check(poster!=null&&!poster.landscape&&Math.abs(poster.thumbAspect-2f/3)<.001,"Provider name overrode a real portrait poster");
            }
            try(MatrixCursor cursor=row(8,"com.example.app","Otra plataforma","","","content://example/thumbnail","https://example.com/play",600_000,100_000,0,0,1,"","",0,TvContract.WatchNextPrograms.ASPECT_RATIO_2_3,TvContract.WatchNextPrograms.ASPECT_RATIO_1_1)){
                square=ContinueWatching.parse(cursor);check(square!=null&&square.thumbAspect==1,"Fallback thumbnail used the poster's different aspect ratio");
            }
            JSONObject metadata=new JSONObject().put("type","movie").put("ratingKey","local-movie").put("title","Película").put("duration",10_800_000).put("viewOffset",2_700_000).put("thumb","/poster");
            JSONObject root=new JSONObject().put("MediaContainer",new JSONObject().put("Metadata",new JSONArray().put(metadata)).put("offset",0).put("totalSize",1));
            PlexClient.Film movie=PlexClient.parsePage(root,0).films.get(0);movie.continuing=true;check(movie.remainingMs()==8_100_000&&ContinueWatching.remaining(movie).equals("Quedan 2 h 15 min"),"Plex exact duration/offset was discarded");
            metadata.remove("viewOffset");check(PlexClient.parsePage(root,0).films.get(0).remainingMs()==-1,"Plex missing position was assumed to be zero");
            PlexClient.Film systemPlex=new PlexClient.Film();systemPlex.key="watchnext:plex";systemPlex.continuing=true;
            check(ContinueWatching.merge(Collections.singletonList(movie),Arrays.asList(systemPlex,video),true).size()==2,"Linked Plex was duplicated from Watch Next");
            check(ContinueWatching.merge(Collections.emptyList(),Arrays.asList(systemPlex,video),false).size()==2,"System Plex fallback was lost without a linked server");
            video.intent="file:///private/example";check(ContinueWatching.launch(c,video)==null,"Unsafe deep link was accepted");video.intent="https://www.youtube.com/watch?v=example";
            Personalization.prepare(c);Personalization.complete(c);activity=(HomeActivity)startActivitySync(new Intent(c,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));HomeActivity a=activity;CinemaHomeView home=(CinemaHomeView)field(a,"home");
            video.provider="YouTube";video.lastPlayedMs=3000;movie.lastPlayedMs=2000;movie.thumb="";movie.art="";video.thumb="";video.art="";
            ui(()->{try{set(home,"filter",0);set(home,"films",Collections.singletonList(movie));set(home,"watchNext",Collections.singletonList(video));set(home,"watchGeneration",((Integer)field(home,"watchGeneration"))+1);}catch(Exception e){throw new RuntimeException(e);}});render(home);
            List<View> cards=(List<View>)field(home,"cards");List<PlexClient.Film> visible=(List<PlexClient.Film>)field(home,"posterFilms");check(cards.size()==2&&visible.get(0)==video,"Home did not merge and order the resume row");
            check(text(cards.get(0)).contains("Quedan 7 min")&&text(cards.get(1)).contains("Quedan 2 h 15 min"),"Visible cards do not contain provider playback times");
            check(((List<?>)field(home,"shelves")).size()==1,"Resume items were duplicated into another shelf");
            ui(()->cards.get(0).requestFocus());sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();check(a.getCurrentFocus()==cards.get(1),"Remote could not move between providers");
            check(visible.get(1).positionMs==2_700_000,"Browsing advanced the playback clock");
            prime.provider="Prime Video";prime.thumb="";prime.art="";square.provider="Otra plataforma";square.thumb="";square.art="";int cache=Visuals.cacheKb(c);
            ui(()->{try{set(home,"watchNext",Arrays.asList(video,prime,square));}catch(Exception e){throw new RuntimeException(e);}});
            for(int size=0;size<3;size++){
                int choice=size;ui(()->Appearance.set(c,"size",choice));render(home);
                List<View> mixed=(List<View>)field(home,"cards");List<ImageView> images=(List<ImageView>)field(home,"posterImages");List<PlexClient.Film> entries=(List<PlexClient.Film>)field(home,"posterFilms");
                int baseline=-1,cardWidth=-1,cardHeight=-1;
                for(int i=0;i<mixed.size();i++){
                    View card=mixed.get(i),picture=(View)images.get(i).getParent();PlexClient.Film film=entries.get(i);float ratio=(float)picture.getWidth()/picture.getHeight();
                    check(images.get(i).getScaleType()==ImageView.ScaleType.FIT_CENTER,"Artwork is cropped instead of preserving the source");
                    check(Math.abs(ratio-16f/9)<.01,"Resume frame is not panoramic for "+film.provider+" size "+size);
                    if(cardWidth<0){cardWidth=card.getWidth();cardHeight=card.getHeight();}else check(card.getWidth()==cardWidth&&card.getHeight()==cardHeight,"Resume cards have different sizes");
                    TextView remaining=label(card,ContinueWatching.remaining(film));check(remaining!=null&&remaining.getParent()==picture&&remaining.getBottom()<=picture.getHeight(),"Remaining time is outside or clipped by the artwork");if(baseline<0)baseline=remaining.getTop();else check(remaining.getTop()==baseline,"Provider times do not align across formats");
                    TextView caption=caption(card,film.title);check(caption!=null&&caption.getTop()>picture.getBottom()&&caption.getBottom()<=picture.getHeight()+60,"Resume title overlaps artwork or provider metadata");
                }
                ui(()->mixed.get(0).requestFocus());for(int i=1;i<mixed.size();i++){sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);waitForIdleSync();check(a.getCurrentFocus()==mixed.get(i),"Remote skipped a differently shaped card");}
            }
            check(Visuals.cacheKb(c)==cache,"Mixed artwork expanded the configured cache budget");
            List<PlexClient.Film> many=new ArrayList<>();for(int i=0;i<30;i++){PlexClient.Film film=new PlexClient.Film();film.key="bounded-"+i;film.title="Película "+i;film.thumb="";film.art="";film.meta="";film.summary="";film.continuing=true;many.add(film);}
            ui(()->{try{set(home,"films",many);set(home,"watchNext",Collections.emptyList());}catch(Exception e){throw new RuntimeException(e);}});render(home);check(((List<?>)field(home,"cards")).size()==12,"Resume row exceeded its artwork/card limit");
            HorizontalScrollView rail=((List<HorizontalScrollView>)field(home,"posterRails")).get(0);check(rail.isHorizontalFadingEdgeEnabled(),"Horizontal fade is disabled");
            ui(()->{try{set(home,"active",false);((View)field(home,"play")).requestFocus();for(ImageView image:(List<ImageView>)field(home,"posterImages"))image.setImageDrawable(new android.graphics.drawable.ColorDrawable(0xFFFFFFFF));rail.scrollTo(0,0);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(250);
            check(redPixel(rail,24)>230&&redPixel(rail,rail.getWidth()-8)<200,"Start of rail should show an intact first image and fade the clipped right image");
            ui(()->rail.scrollTo(112,0));check(redPixel(rail,8)<200&&redPixel(rail,rail.getWidth()-8)<200,"Middle of rail should fade both clipped ends");
            ui(()->rail.scrollTo(rail.getChildAt(0).getWidth()-rail.getWidth(),0));int endRed=redPixel(rail,rail.getWidth()-100);check(endRed>230,"Last image should not fade when there is no more content on the right: red="+endRed+", x="+rail.getScrollX()+", width="+rail.getWidth()+", content="+rail.getChildAt(0).getWidth()+", more="+rail.canScrollHorizontally(1));
            PlexClient.Film recent=new PlexClient.Film();recent.key=many.get(0).key;recent.title="Nuevo en Plex";recent.type="movie";recent.thumb="";recent.art="";recent.meta="";recent.summary="";recent.recentlyAdded=true;many.add(recent);
            ui(()->{try{set(home,"active",true);set(home,"films",many);}catch(Exception e){throw new RuntimeException(e);}});render(home);
            List<View> rowsCards=(List<View>)field(home,"cards");check(((List<?>)field(home,"shelves")).size()==2&&label((View)field(home,"shelfList"),"Últimas subidas")!=null,"Recent posters are not below Continue watching");
            View newest=rowsCards.get(rowsCards.size()-1);check(newest.getWidth()*3==newest.getHeight()*2,"Latest uploads must keep portrait posters");ui(()->newest.requestFocus());render(home);check(!((PlexClient.Film)field(home,"selected")).continuing,"Refreshing selected the resume copy instead of the recent poster");ScrollView scroll=(ScrollView)field(home,"shelvesScroll");check(scroll.getClipBounds()!=null&&scroll.getClipBounds().height()==scroll.getHeight()&&scroll.getClipChildren(),"Scrolled rows can draw over the hero");sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();check(a.getCurrentFocus()==((List<View>)field(home,"cards")).get(0),"Remote cannot return from latest uploads to resume");
            // Real down/up navigation must reveal the heading and complete text at every size.
            video.title="Un título de YouTube suficientemente largo para ocupar dos líneas completas";
            for(int size=0;size<3;size++){
                int choice=size;ui(()->Appearance.set(c,"size",choice));
                ui(()->{try{set(home,"films",Arrays.asList(movie,recent));set(home,"watchNext",Collections.singletonList(video));}catch(Exception e){throw new RuntimeException(e);}});render(home);
                List<View> current=(List<View>)field(home,"cards");ui(()->current.get(0).requestFocus());Thread.sleep(250);
                TextView caption=caption(current.get(0),video.title);check(caption.getLineCount()==2,"Fixture did not exercise a two-line caption");check(caption.getLayout().getLineBottom(1)<=caption.getHeight(),"Two-line title clipped its last glyphs at size "+size);
                TextView hero=(TextView)field(home,"title");check(hero.getLayout().getLineBottom(hero.getLineCount()-1)<=hero.getHeight(),"Hero title clipped at size "+size);
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);waitForIdleSync();Thread.sleep(250);
                check(a.getCurrentFocus()==current.get(current.size()-1),"Down skipped Latest uploads at size "+size);
                TextView heading=label((View)field(home,"shelfList"),"Últimas subidas");android.graphics.Rect visibleBox=new android.graphics.Rect();heading.getGlobalVisibleRect(visibleBox);float scale=visibleBox.width()/(float)heading.getWidth();check(visibleBox.height()>=heading.getHeight()*scale-2,"Latest heading clipped after Down at size "+size);
                check(scroll.getScrollY()==((View)heading.getParent()).getTop(),"Automatic focus scrolling hid the shelf heading");
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);waitForIdleSync();Thread.sleep(250);check(scroll.getScrollY()==0,"Up did not restore the first heading");
            }
            View art=(View)field(home,"backdrop"),veil=(View)field(home,"backdropBottom");check(art.getBottom()==veil.getBottom(),"Backdrop stops before its bottom gradient, leaving a seam");
            check(plexBefore.equals(new PlexClient(c).prefs.getAll())&&Arrays.equals(apps,SidebarApps.slots(c)),"Preview changed accounts or app shortcuts");
            result.putString("result","PASS exact Plex/Watch Next times, filtering, deduplication, safe links and twelve-card limit. Uniform panoramic resume cards in three sizes; portrait Latest uploads below, duplicate-title focus and remote row navigation. Real screenshot pixels verify right/start, both/middle and no right/end fades; full-height backdrop covers the gradient. Long two-line captions and hero titles fit, and real Down/Up preserves full headings in three sizes. Cache, playback positions, accounts and shortcuts preserved.");
        }catch(Throwable error){result.putString("error",android.util.Log.getStackTraceString(error));try{android.graphics.Bitmap shot=getUiAutomation().takeScreenshot();try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(c.getExternalFilesDir(null),"continue-failure.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}catch(Exception ignored){}}
        finally{if(activity!=null){HomeActivity a=activity;ui(()->a.finish());}restore(personalization,saved);restore(visuals,visualSaved);}
        finish(result.containsKey("error")?1:-1,result);
    }
}
