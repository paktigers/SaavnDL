package com.saavndl.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object I18n {
    var lang by mutableStateOf("en")
}

private val UR = mapOf(
    "Discover" to "دریافت", "Library" to "لائبریری", "Downloads" to "ڈاؤن لوڈز", "Settings" to "سیٹنگز",
    "Songs" to "گانے", "Albums" to "البمز", "Playlists" to "پلے لسٹس", "Artists" to "فنکار",
    "Search, preview and download — with full tags" to "تلاش کریں، سنیں اور مکمل ٹیگز کے ساتھ ڈاؤن لوڈ کریں",
    "Songs, albums, artists or paste a link" to "گانے، البم، فنکار یا لنک پیسٹ کریں",
    "Recent searches" to "حالیہ تلاشیں", "Clear" to "صاف کریں", "Open a JioSaavn link" to "JioSaavn لنک کھولیں",
    "No results" to "کوئی نتیجہ نہیں", "Download all" to "سب ڈاؤن لوڈ کریں", "Play" to "چلائیں",
    "Downloaded" to "ڈاؤن لوڈ شدہ", "Favorites" to "پسندیدہ", "Filter downloads" to "ڈاؤن لوڈز فلٹر کریں",
    "Download format" to "ڈاؤن لوڈ فارمیٹ", "Audio quality" to "آڈیو کوالٹی", "Appearance" to "ظاہری شکل",
    "Language" to "زبان", "Save" to "محفوظ کریں", "Test connection" to "کنکشن ٹیسٹ کریں",
    "Cancel all" to "سب منسوخ کریں", "Retry failed" to "ناکام دوبارہ کریں", "Clear finished" to "مکمل شدہ صاف کریں",
    "Pause all" to "سب روکیں", "Resume" to "جاری رکھیں", "Queue is empty" to "قطار خالی ہے",
    "No favorites yet" to "ابھی کوئی پسندیدہ نہیں", "Nothing here yet" to "یہاں ابھی کچھ نہیں",
    "Song info" to "گانے کی معلومات", "Similar songs" to "ملتے جلتے گانے", "Share link" to "لنک شیئر کریں",
    "Copy link" to "لنک کاپی کریں", "Add to playlist" to "پلے لسٹ میں شامل کریں", "Play next" to "اگلا چلائیں",
    "Add to queue" to "قطار میں شامل کریں", "Now playing" to "اب چل رہا ہے", "Lyrics" to "بول",
    "Sleep timer" to "سلیپ ٹائمر", "Close" to "بند کریں", "Cancel" to "منسوخ", "Delete" to "حذف کریں",
    "Create" to "بنائیں", "New playlist" to "نئی پلے لسٹ", "Batch import" to "بیچ امپورٹ", "Storage" to "اسٹوریج",
    "Backup and restore" to "بیک اپ اور بحالی", "Updates" to "اپ ڈیٹس", "API server" to "API سرور",
    "About" to "کے بارے میں", "Downloading" to "ڈاؤن لوڈ", "For you" to "آپ کے لیے", "Edit tags" to "ٹیگز میں ترمیم"
)

private val HI = mapOf(
    "Discover" to "खोजें", "Library" to "लाइब्रेरी", "Downloads" to "डाउनलोड", "Settings" to "सेटिंग्स",
    "Songs" to "गाने", "Albums" to "एल्बम", "Playlists" to "प्लेलिस्ट", "Artists" to "कलाकार",
    "Search, preview and download — with full tags" to "खोजें, सुनें और पूरे टैग के साथ डाउनलोड करें",
    "Songs, albums, artists or paste a link" to "गाने, एल्बम, कलाकार या लिंक पेस्ट करें",
    "Recent searches" to "हाल की खोज", "Clear" to "साफ़ करें", "Open a JioSaavn link" to "JioSaavn लिंक खोलें",
    "No results" to "कोई परिणाम नहीं", "Download all" to "सभी डाउनलोड करें", "Play" to "चलाएँ",
    "Downloaded" to "डाउनलोड किए गए", "Favorites" to "पसंदीदा", "Filter downloads" to "डाउनलोड फ़िल्टर करें",
    "Download format" to "डाउनलोड फ़ॉर्मेट", "Audio quality" to "ऑडियो क्वालिटी", "Appearance" to "रूप-रंग",
    "Language" to "भाषा", "Save" to "सहेजें", "Test connection" to "कनेक्शन जाँचें",
    "Cancel all" to "सभी रद्द करें", "Retry failed" to "विफल दोबारा करें", "Clear finished" to "पूर्ण साफ़ करें",
    "Pause all" to "सभी रोकें", "Resume" to "जारी रखें", "Queue is empty" to "कतार खाली है",
    "No favorites yet" to "अभी कोई पसंदीदा नहीं", "Nothing here yet" to "यहाँ अभी कुछ नहीं",
    "Song info" to "गाने की जानकारी", "Similar songs" to "मिलते-जुलते गाने", "Share link" to "लिंक शेयर करें",
    "Copy link" to "लिंक कॉपी करें", "Add to playlist" to "प्लेलिस्ट में जोड़ें", "Play next" to "अगला चलाएँ",
    "Add to queue" to "कतार में जोड़ें", "Now playing" to "अभी चल रहा है", "Lyrics" to "बोल",
    "Sleep timer" to "स्लीप टाइमर", "Close" to "बंद करें", "Cancel" to "रद्द करें", "Delete" to "हटाएँ",
    "Create" to "बनाएँ", "New playlist" to "नई प्लेलिस्ट", "Batch import" to "बैच इम्पोर्ट", "Storage" to "स्टोरेज",
    "Backup and restore" to "बैकअप और रीस्टोर", "Updates" to "अपडेट", "API server" to "API सर्वर",
    "About" to "जानकारी", "Downloading" to "डाउनलोड", "For you" to "आपके लिए", "Edit tags" to "टैग बदलें"
)

/** Looks up a UI string in the selected language (falls back to English). */
fun tr(s: String): String = when (I18n.lang) {
    "ur" -> UR[s] ?: s
    "hi" -> HI[s] ?: s
    else -> s
}
