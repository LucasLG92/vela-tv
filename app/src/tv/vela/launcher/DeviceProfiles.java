package tv.vela.launcher;

import java.util.*;
import java.util.regex.Pattern;

/** Matches only explicit TV labels. Unknown generations remain unknown; adapters need a manual profile. */
final class DeviceProfiles {
    private DeviceProfiles(){}
    private static final String[][] DATA={
        {"switch2","Nintendo Switch 2","Nintendo","nintendoswitch2|switch2"},
        {"switcholed","Nintendo Switch OLED","Nintendo","nintendoswitcholed|switcholed"},
        {"switch","Nintendo Switch","Nintendo","nintendoswitch|switch"},
        {"wiiu","Nintendo Wii U","Nintendo","nintendowiiu|wiiu"},
        {"wii","Nintendo Wii","Nintendo","nintendowii|wii"},
        {"gamecube","Nintendo GameCube","Nintendo","nintendogamecube|gamecube"},
        {"n64","Nintendo 64","Nintendo","nintendo64|n64"},
        {"snes","Super Nintendo","Nintendo","supernintendo|snes"},
        {"nes","Nintendo NES","Nintendo","nintendonES|nes|nintendoentertainmentsystem"},
        {"ps5","PlayStation 5","PlayStation","(?:sony)?(?:playstation|ps)5(?:pro|slim|digital|console)?"},
        {"ps4","PlayStation 4","PlayStation","(?:sony)?(?:playstation|ps)4(?:pro|slim|console)?"},
        {"ps3","PlayStation 3","PlayStation","(?:sony)?(?:playstation|ps)3(?:superslim|slim|console)?"},
        {"ps2","PlayStation 2","PlayStation","(?:sony)?(?:playstation|ps)2(?:slim|console)?"},
        {"ps1","PlayStation","PlayStation","(?:sony)?(?:playstation|ps)1|psone"},
        {"playstation","PlayStation","PlayStation","(?:sony)?playstation"},
        {"xboxseriesx","Xbox Series X","Xbox","(?:microsoft)?xboxseriesx"},
        {"xboxseriess","Xbox Series S","Xbox","(?:microsoft)?xboxseriess"},
        {"xboxonex","Xbox One X","Xbox","(?:microsoft)?xboxonex"},
        {"xboxones","Xbox One S","Xbox","(?:microsoft)?xboxones"},
        {"xboxone","Xbox One","Xbox","(?:microsoft)?xboxone"},
        {"xbox360","Xbox 360","Xbox","(?:microsoft)?xbox360(?:s|e|elite)?"},
        {"xbox","Xbox","Xbox","(?:microsoft)?xbox"},
        {"dreamcast","Sega Dreamcast","Sega","(?:sega)?dreamcast"},
        {"saturn","Sega Saturn","Sega","(?:sega)?saturn"},
        {"megadrive","Sega Mega Drive / Genesis","Sega","(?:sega)?(?:megadrive|genesis)(?:mini|mini2|2)?"},
        {"deck","Steam Deck","PC / Gaming","(?:valve)?steamdeck(?:oled)?"},
        {"rogally","ASUS ROG Ally","PC / Gaming","(?:asus)?rogally(?:x)?"},
        {"pc","PC","PC / Gaming","pc|computer|windows|desktop|laptop"},
        {"mac","Mac","PC / Gaming","(?:apple)?mac(?:mini|book|bookpro|bookair|studio)?"},
        {"appletv","Apple TV","Streaming","appletv(?:4k|hd)?"},
        {"shield","NVIDIA Shield TV","Streaming","(?:nvidia)?shield(?:tv|tvpro|pro)?"},
        {"firetv","Amazon Fire TV","Streaming","(?:amazon)?firetv(?:stick|stick4k|stick4kmax|cube)?"},
        {"chromecast","Google Chromecast","Streaming","(?:google)?chromecast(?:ultra|googleTV|4k|hd)?"},
        {"googletv","Google TV Streamer","Streaming","(?:google)?tvstreamer(?:4k)?"},
        {"roku","Roku","Streaming","roku(?:ultra|express|premiere|streamingstick)?"},
        {"androidtv","Android TV","Streaming","androidtv|tvbox|xiaomibox|mibox|miboxs"},
        {"bluray","Blu-ray","Vídeo y audio","bluray|blurayplayer|bdplayer"},
        {"dvd","DVD","Vídeo y audio","dvd|dvdplayer"},
        {"decoder","Decodificador","Vídeo y audio","settopbox|decoder|decodificador|cablebox|satellite"},
        {"soundbar","Barra de sonido","Vídeo y audio","soundbar|barradesonido"},
        {"receiver","Receptor AV","Vídeo y audio","avreceiver|receiver|receptorav|hometheater"},
        {"other","Otro dispositivo HDMI","Otros",""},
    };
    private static final Pattern CLEAN=Pattern.compile("[^a-z0-9]");
    private static final Pattern[] MATCHERS=new Pattern[DATA.length];
    static {for(int i=0;i<DATA.length;i++)MATCHERS[i]=DATA[i][3].isEmpty()?null:Pattern.compile(DATA[i][3].toLowerCase(Locale.ROOT));}
    static String[] presets(){String[] values=new String[DATA.length+1];values[0]="auto";for(int i=0;i<DATA.length;i++)values[i+1]=DATA[i][0];return values;}
    static String name(String id){if(id.equals("auto"))return Ui.tr("Automático","Automatic","Automático");if(id.equals("other"))return Ui.tr("Outro dispositivo HDMI","Other HDMI device","Otro dispositivo HDMI");if(id.equals("decoder"))return Ui.tr("Decodificador","Set-top box","Decodificador");if(id.equals("soundbar"))return Ui.tr("Barra de som","Soundbar","Barra de sonido");if(id.equals("receiver"))return Ui.tr("Receptor AV","AV receiver","Receptor AV");for(String[] row:DATA)if(row[0].equals(id))return row[1];return "HDMI";}
    static String group(String id){for(String[] row:DATA)if(row[0].equals(id))return row[2].equals("Vídeo y audio")?Ui.tr("Vídeo e áudio","Video and audio","Vídeo y audio"):row[2].equals("Otros")?Ui.tr("Outros","Other","Otros"):row[2];return "";}
    static String detect(String label){String value=CLEAN.matcher(label.toLowerCase(Locale.ROOT)).replaceAll("");for(int i=0;i<DATA.length;i++)if(MATCHERS[i]!=null&&MATCHERS[i].matcher(value).matches())return DATA[i][0];return "other";}
}
