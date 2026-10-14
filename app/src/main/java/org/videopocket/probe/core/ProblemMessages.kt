package org.videopocket.probe.core

fun problemMessage(code: String?, arabic: Boolean): String {
    val text = when (code) {
        "INVALID_URL" -> "Enter a valid public HTTP or HTTPS link." to "أدخل رابط HTTP أو HTTPS عامًا وصالحًا."
        "UNSUPPORTED" -> "This source or selected format is not available." to "المصدر أو التنسيق المحدد غير متاح."
        "LOGIN_REQUIRED" -> "This item requires an account and cannot be downloaded here." to "يتطلب هذا المحتوى حسابًا ولا يمكن تنزيله هنا."
        "REMOVED" -> "The source could not find this item." to "تعذر العثور على المحتوى في المصدر."
        "RESTRICTED" -> "The source restricted this request. Try again later." to "قيّد المصدر هذا الطلب. حاول لاحقًا."
        "LIVE_OR_PLAYLIST" -> "Live or upcoming media is not supported." to "البث المباشر أو المرتقب غير مدعوم."
        "SPACE" -> "Not enough free storage. Free some space and retry." to "المساحة غير كافية. حرر مساحة ثم أعد المحاولة."
        "NETWORK" -> "Connection failed or timed out. Check your network and retry." to "تعذر الاتصال أو انتهت المهلة. تحقق من الشبكة ثم أعد المحاولة."
        "CONVERSION" -> "The output could not be processed or verified. Try another format." to "تعذرت معالجة الناتج أو التحقق منه. جرّب تنسيقًا آخر."
        "CANCELED", "CANCELLED" -> "Cancelled." to "تم الإلغاء."
        "INTERRUPTED" -> "The app stopped during this task. Retry to restart it." to "توقف التطبيق أثناء المهمة. أعد المحاولة للبدء من جديد."
        else -> "The media engine could not complete this operation." to "تعذر على محرك الوسائط إكمال العملية."
    }
    return if (arabic) text.second else text.first
}
