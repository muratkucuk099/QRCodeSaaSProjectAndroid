package murat.com.saasproject.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tarih ve sayı biçimlendirme. iOS'taki `DateFormatter`/`NumberFormatter` ayarlarıyla
 * aynı çıktıyı üretir, böylece iki platformda aynı veri aynı görünür.
 *
 * Locale sabit `tr_TR`: uygulama şu an yalnızca Türkçe. İngilizce eklendiğinde bu sınıf
 * cihaz locale'ini kullanacak şekilde güncellenmeli.
 */
object Formatters {

    val TURKISH: Locale = Locale("tr", "TR")

    /** iOS `.medium` tarih + `.short` saat — ör. "12 Eyl 2026 14:30". */
    fun dateTime(date: Date): String =
        SimpleDateFormat("d MMM yyyy HH:mm", TURKISH).format(date)

    /** iOS abonelik bitiş tarihi biçimi — ör. "12 Eylül 2026". */
    fun longDate(date: Date): String =
        SimpleDateFormat("d MMMM yyyy", TURKISH).format(date)

    /** iOS `monthSummaryTitle` içindeki ay adı — ör. "Eylül". */
    fun monthName(date: Date = Date()): String =
        SimpleDateFormat("MMMM", TURKISH).format(date)
            .replaceFirstChar { it.titlecase(TURKISH) }

    /** iOS `NumberFormatter` (grouping separator ".") — ör. 12345 -> "12.345". */
    fun groupedNumber(value: Int): String {
        val symbols = DecimalFormatSymbols(TURKISH).apply { groupingSeparator = '.' }
        return DecimalFormat("#,###", symbols).format(value.toLong())
    }
}
