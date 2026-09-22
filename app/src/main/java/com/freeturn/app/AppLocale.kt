package com.freeturn.app

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Язык интерфейса поверх системного. API 33+ - системный per-app locale (виден и в
 * настройках Android), ниже - свой тег и обёртка контекста в attachBaseContext.
 * Пустой тег - язык системы.
 */
object AppLocale {

    val SUPPORTED = listOf("ru", "en")

    // SharedPreferences, не DataStore: тег нужен синхронно в attachBaseContext, до Koin.
    private const val PREFS = "app_locale"
    private const val KEY_TAG = "tag"

    fun current(context: Context): String {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.localeManager().applicationLocales.get(0)?.language.orEmpty()
        } else {
            storedTag(context)
        }
        return tag.takeIf { it in SUPPORTED }.orEmpty()
    }

    fun set(activity: Activity, tag: String) {
        if (tag == current(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            // Система сама пересоздаёт активити и обновляет ресурсы процесса.
            activity.localeManager().applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return
        }
        activity.prefs().edit().putString(KEY_TAG, tag).commit()
        // Уведомления, виджет и VM берут строки из application-контекста - его тоже.
        applyTo(activity.application)
        activity.recreate()
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val locale = targetLocale(storedTag(base)) ?: return base
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration(base.resources.configuration).withLocale(locale))
    }

    @Suppress("DEPRECATION") // createConfigurationContext не обновляет уже живой Application
    private fun applyTo(app: Application) {
        val locale = targetLocale(storedTag(app)) ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(locale)
        val res = app.resources
        res.updateConfiguration(Configuration(res.configuration).withLocale(locale), res.displayMetrics)
    }

    private fun targetLocale(tag: String): Locale? =
        tag.takeIf { it in SUPPORTED }?.let(Locale::forLanguageTag)

    private fun Configuration.withLocale(locale: Locale) = apply { setLocales(LocaleList(locale)) }

    private fun storedTag(context: Context): String = context.prefs().getString(KEY_TAG, null).orEmpty()

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @RequiresApi(33)
    private fun Context.localeManager() = getSystemService(LocaleManager::class.java)
}
