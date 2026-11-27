package tv.vela.launcher;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Programme start and stop times. XMLTV assumes UTC when the offset is omitted. */
final class XmlTvTime {
    private XmlTvTime() {}

    static long parse(String value) {
        if (value == null || value.length() > 40) return 0;
        String text = value.trim();
        int digits = 0;
        while (digits < text.length() && text.charAt(digits) >= '0' && text.charAt(digits) <= '9') digits++;
        if (digits < 12 || digits > 14) return 0;

        int second = digits > 12 ? (text.charAt(12) - '0') * 10 : 0;
        if (digits == 14) second += text.charAt(13) - '0';
        try {
            LocalDateTime time = LocalDateTime.of(number(text, 0, 4), number(text, 4, 2),
                    number(text, 6, 2), number(text, 8, 2), number(text, 10, 2), second);
            String zone = text.substring(digits).trim();
            return time.toEpochSecond(offset(zone));
        } catch (DateTimeException invalid) {
            return 0;
        }
    }

    private static ZoneOffset offset(String zone) {
        switch (zone) {
            case "": case "Z": case "GMT": case "UTC": return ZoneOffset.UTC;
            case "BST": case "CET": return ZoneOffset.ofHours(1);
            case "CEST": return ZoneOffset.ofHours(2);
            default:
                if (zone.length() != 5 || (zone.charAt(0) != '+' && zone.charAt(0) != '-'))
                    throw new DateTimeException("Invalid XMLTV offset");
                return ZoneOffset.of(zone);
        }
    }

    private static int number(String text, int from, int length) {
        int value = 0;
        for (int i = from; i < from + length; i++) value = value * 10 + (text.charAt(i) - '0');
        return value;
    }
}
