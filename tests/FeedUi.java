package tv.vela.launcher;

/** Host feed tests use the real catalogue selector without loading Android fonts. */
final class Ui {
    static String apiLanguage(){return L10n.locale().toLanguageTag();}
    static String problem(String english,String spanish){return L10n.tr(english,english,spanish);}
}
