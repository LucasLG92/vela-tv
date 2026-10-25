package tv.vela.launcher;

import android.content.Context;import android.graphics.drawable.GradientDrawable;import android.graphics.drawable.Drawable;import android.widget.ImageView;import java.util.*;

/** Brand paths are native vectors. No photo decoding, cache, worker or network request. */
final class DeviceIcons {
    private final Context context;private final Set<ImageView> bound=new HashSet<>();boolean closed;
    DeviceIcons(Context c){context=c;}
    private int resource(String model){
        switch(model){
            case "ps1":case "playstation":return R.drawable.device_playstation;
            case "ps2":return R.drawable.device_playstation2;case "ps3":return R.drawable.device_playstation3;case "ps4":return R.drawable.device_playstation4;case "ps5":return R.drawable.device_playstation5;
            case "switch":case "switch2":case "switcholed":return R.drawable.device_nintendoswitch;
            case "gamecube":return R.drawable.device_nintendogamecube;case "wii":return R.drawable.device_wii;case "wiiu":return R.drawable.device_wiiu;
            case "nes":case "snes":case "n64":return R.drawable.device_nintendo;
            case "saturn":case "dreamcast":case "megadrive":return R.drawable.device_sega;
            case "deck":return R.drawable.device_steamdeck;case "rogally":return R.drawable.device_asus;
            case "mac":return R.drawable.device_apple;case "appletv":return R.drawable.device_appletv;
            case "firetv":return R.drawable.device_amazonfiretv;case "shield":return R.drawable.device_nvidia;
            case "roku":return R.drawable.device_roku;case "androidtv":case "mecool":return R.drawable.device_android;
            case "chromecast":case "googletv":return R.drawable.device_google;
            case "atari":return R.drawable.device_atari;
            default:return model.startsWith("xbox")?R.drawable.device_xbox:0;
        }
    }
    private int brandColor(int resource){switch(resource){
        case R.drawable.device_nintendoswitch:return 0xFFE60012;
        case R.drawable.device_nintendo:return 0xFF8F8F8F;
        case R.drawable.device_nintendogamecube:return 0xFF6A5FBB;
        case R.drawable.device_wii:return 0xFF8B8B8B;
        case R.drawable.device_wiiu:return 0xFF8B8B8B;
        case R.drawable.device_playstation:return 0xFF003791;
        case R.drawable.device_playstation2:return 0xFF003791;
        case R.drawable.device_playstation3:return 0xFF003791;
        case R.drawable.device_playstation4:return 0xFF003791;
        case R.drawable.device_playstation5:return 0xFF003791;
        case R.drawable.device_xbox:return 0xFF107C10;
        case R.drawable.device_sega:return 0xFF0089CF;
        case R.drawable.device_steamdeck:return 0xFF1A9FFF;
        case R.drawable.device_steam:return 0xFF000000;
        case R.drawable.device_asus:return 0xFF000000;
        case R.drawable.device_windows:return 0xFF0078D4;
        case R.drawable.device_apple:return 0xFF000000;
        case R.drawable.device_appletv:return 0xFF000000;
        case R.drawable.device_amazonfiretv:return 0xFFFC4C02;
        case R.drawable.device_google:return 0xFF4285F4;
        case R.drawable.device_roku:return 0xFF662D91;
        case R.drawable.device_android:return 0xFF3DDC84;
        case R.drawable.device_nvidia:return 0xFF76B900;
        case R.drawable.device_atari:return 0xFFE4202E;
        default:return VelaTheme.CARD;}}
    private static int mix(int a,int b,float t){int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),bl=(int)((a&255)*(1-t)+(b&255)*t);return 0xFF000000|(r<<16)|(g<<8)|bl;}
    GradientDrawable surface(String model,boolean focus,int radius){int color=brandColor(resource(model));GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{mix(color,0xFFFFFFFF,focus?.18f:.07f),mix(color,0xFF000000,.22f)});d.setCornerRadius(radius);d.setStroke(focus?2:1,focus?Appearance.accent(context):0x20FFFFFF);return d;}
    void load(String model,ImageView view){load(model,view,false);}
    void load(String model,ImageView view,boolean hero){if(closed)return;int id=resource(model);Drawable drawable=null;try{if(id!=0)drawable=context.getDrawable(id);}catch(android.content.res.Resources.NotFoundException unavailable){}view.setImageDrawable(drawable);view.setTag(model);bound.add(view);}
    void refreshVisible(){}
    void clearViews(){for(ImageView view:bound)view.setImageDrawable(null);bound.clear();}
    void close(){closed=true;clearViews();}
}
