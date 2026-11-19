package tv.vela.launcher;

import java.util.regex.Pattern;

/** Some XMLTV sources include Kodi colour tags around the episode number. */
final class XmlTvTitle {
    private static final Pattern COLOUR=Pattern.compile("\\[(?:/COLOR|COLOR\\s+[a-z0-9#]+)\\]",Pattern.CASE_INSENSITIVE);
    private XmlTvTitle(){}
    static String plain(String text){
        return (text.indexOf('[')<0?text:COLOUR.matcher(text).replaceAll("")).trim();
    }
}
