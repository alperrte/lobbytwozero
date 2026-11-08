package com.ltz.game.application.service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bir metnin gerçekten istenen dilde olup olmadığına karar veren muhafazakâr denetim. Steam, bir dilde mağaza metni yoksa İngilizce metne
 * düşer; geliştirici bazen de "Türkçe" sekmesine İngilizce farklı bir metin girer. Bu yüzden "metin İngilizceden farklı" yetmez: metin ayrıca o dile
 * özgü kanıt taşımalıdır. Şüphede metin KABUL EDİLMEZ (İngilizce kaynaklı metin yanlışlıkla Türkçe sayılmaz); makine çevirisi veya harici hizmet yoktur.
 */
public final class DescriptionLanguage {
    /** Yalnız Türkçede bulunan harfler (ğ, ş, dotsuz ı, İ); ö/ü/ç başka dillerde de bulunduğundan tek başına kanıt değildir. */
    private static final Pattern TURKISH_LETTERS = Pattern.compile("[ğĞşŞıİ]");
    /** İngilizcede bulunmayan sık Türkçe sözcükler. */
    private static final Set<String> TURKISH_WORDS = Set.of("ve", "bir", "bu", "için", "ile", "olarak", "daha", "gibi", "çok", "ama", "veya", "kadar", "olan", "değil", "sonra", "ancak");
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+");
    /** Harf kanıtı olmayan metinde en az bu kadar Türkçe sözcük gerekir. */
    static final int MIN_TURKISH_WORDS = 3;

    private DescriptionLanguage() { }

    /** @return metin Türkçe kanıtı taşıyorsa true */
    public static boolean looksTurkish(String text) {
        if (text == null || text.isBlank()) return false;
        if (TURKISH_LETTERS.matcher(text).find()) return true;
        int words = 0;
        var matcher = WORD.matcher(text.toLowerCase(Locale.forLanguageTag("tr")));
        while (matcher.find()) {
            if (TURKISH_WORDS.contains(matcher.group())) words++;
        }
        return words >= MIN_TURKISH_WORDS;
    }

    /** İngilizce metinde sık geçen işlev sözcükleri. */
    private static final Set<String> ENGLISH_WORDS = Set.of("the", "and", "of", "to", "in", "is", "you", "your", "with", "for", "an", "on", "as", "it", "that",
            "this", "are", "be", "by", "or", "from", "at", "will", "can", "has", "have", "their", "its", "all", "new", "about", "into", "game", "games",
            "play", "world", "which", "when", "where", "who", "what", "not", "but", "out", "one", "get", "up", "they", "was", "were");
    /** Metindeki harflerin en az bu oranı ASCII Latin olmalıdır (Çince/Tayca/Kiril gibi metinler elenir). */
    static final double MIN_ASCII_LETTER_RATIO = 0.9;
    static final int MIN_ENGLISH_WORDS = 2;

    /**
     * Metin gerçekten İngilizce görünüyor mu: harflerin çoğu ASCII Latin ve en az iki sık İngilizce sözcük. Steam'in "İngilizce" alanına başka dilde
     * metin girilmiş olabilir; şüphede reddedilir ki başka dildeki metin "İngilizce" diye işaretlenmesin.
     */
    public static boolean looksEnglish(String text) {
        if (text == null || text.isBlank()) return false;
        int letters = 0;
        int ascii = 0;
        int english = 0;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (Character.isLetter(c)) {
                letters++;
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) ascii++;
            }
        }
        if (letters == 0 || (double) ascii / letters < MIN_ASCII_LETTER_RATIO) return false;
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            if (ENGLISH_WORDS.contains(matcher.group())) english++;
        }
        return english >= MIN_ENGLISH_WORDS;
    }

    /** Bu dil için dil denetimi tanımlı mı (tanımsız dil işlenmez: gereksiz istek ve yanlış etiket riski yok). */
    public static boolean supports(String language) { return "tr".equals(language); }

    /** Desteklenen dil için dil denetimi; denetimi tanımlı olmayan dil için false (güvenli taraf: kaydedilmez). */
    public static boolean accepts(String language, String text) {
        return "tr".equals(language) && looksTurkish(text);
    }
}
