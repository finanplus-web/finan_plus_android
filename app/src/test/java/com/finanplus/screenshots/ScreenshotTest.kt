// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.finanplus.MainActivity
import com.finanplus.core.ThemeId
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de tela da documentação (README e docs/screenshots), feitas com as telas reais do app
 * desenhadas pelo Robolectric, com os dados de demonstração. Não fazem parte dos testes normais:
 * rode com `./gradlew testDebugUnitTest -PfinanScreenshots=true` (ou o fluxo "Capturas de tela" do GitHub).
 *
 * Usa um Application simples: o FinanApp agendaria notificações (WorkManager) e abriria o arquivo de dados,
 * e aqui nada é gravado. Celular de referência: 412 × 915 dp, densidade xxhdpi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi-port")
@OptIn(ExperimentalRoborazziApi::class)
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val dir = File(System.getProperty("finan.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(File(dir, "$name.png").path)
    }

    private fun tab(label: String) {
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun telas() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        DevicePrefs.get(app).askedNotifications = true // sem o pedido de permissão de notificação na primeira abertura
        val demo = DemoData.state()
        Repo.showForScreenshots(demo.copy(theme = ThemeId.LIGHT))
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("inicio")
            tab("Lançamentos"); shot("lancamentos")
            tab("Relatórios"); shot("relatorios")
            tab("Ajustes"); shot("ajustes")
            compose.onNode(hasContentDescription("Novo lançamento")).performClick()
            shot("novo-lancamento")
        }
        Repo.showForScreenshots(demo.copy(theme = ThemeId.TOKYO))
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("inicio-tokyo")
            tab("Relatórios"); shot("relatorios-tokyo")
        }
    }
}
