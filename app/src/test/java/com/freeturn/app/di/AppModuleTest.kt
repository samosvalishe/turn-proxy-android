package com.freeturn.app.di

import android.content.Context
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import java.io.File

/**
 * Рефлексией проходит конструкторы всех определений и падает, если чья-то зависимость
 * в модуле не объявлена. Без этого такая ошибка - `NoDefinitionFoundException` на
 * открытии экрана, то есть только на устройстве и только если до экрана дошли.
 *
 * Ничего не инстанцирует: поднимать здесь DataStore и gomobile-ядро нечем.
 *
 * [extraTypes] - то, что приходит не из модуля: `Context` даёт `androidContext()`,
 * а `String`/`File` - литералы внутри самих определений (stateDir ядра, каталог логов).
 * Определения, привязанные к интерфейсу (`single<ProxyServiceLauncher>`), проверка
 * пропускает - у интерфейса нет конструктора.
 */
@OptIn(KoinExperimentalAPI::class)
class AppModuleTest {

    @Test
    fun `граф модуля резолвится целиком`() {
        appModule.verify(extraTypes = listOf(Context::class, String::class, File::class))
    }
}
