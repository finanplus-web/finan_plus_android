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
import com.finanplus.core.AppState
import com.finanplus.core.ThemeId
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
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

    /** Seletor de ícones usa descrição acessível e mantém o modo ao trocar de aba. */
    @Test
    fun seletorListaCalendarioMantemEstadoELabelsAcessiveis() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        DevicePrefs.get(app).askedNotifications = true
        Repo.showForScreenshots(DemoData.state().copy(theme = ThemeId.LIGHT))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Lançamentos")
            val list = hasContentDescription("Visualização em lista") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            val calendar = hasContentDescription("Visualização em calendário") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
            val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
            assertEquals(1, compose.onAllNodes(list and selected).fetchSemanticsNodes().size)
            compose.onNode(calendar).performClick()
            assertEquals(1, compose.onAllNodes(calendar and selected).fetchSemanticsNodes().size)
            tab("Meu dinheiro")
            tab("Lançamentos")
            assertEquals(1, compose.onAllNodes(calendar and selected).fetchSemanticsNodes().size)
            compose.onNode(list).performClick()
            assertEquals(1, compose.onAllNodes(list and selected).fetchSemanticsNodes().size)
        }
    }

    /** Regressão de texto e acessibilidade na conta inicial (sem cartões ou metas). */
    @Test
    fun meuDinheiroComContaZeradaMantemAtalhosVisiveis() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        DevicePrefs.get(app).askedNotifications = true
        Repo.showForScreenshots(AppState(theme = ThemeId.LIGHT))
        ActivityScenario.launch(MainActivity::class.java).use {
            tab("Meu dinheiro")
            for (label in listOf(
                "Conta principal · Saldo: R$ 0,00",
                "Adicione um cartão para acompanhar faturas e vencimentos.",
                "Adicionar conta",
                "Adicionar cartão",
            )) {
                assertTrue(
                    "Texto ou ação ausente em Meu dinheiro: $label",
                    compose.onAllNodes(hasText(label, substring = true)).fetchSemanticsNodes().isNotEmpty(),
                )
            }
        }
    }

    private fun view(description: String) {
        compose.onNode(
            hasContentDescription(description) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
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
            view("Visualização em calendário"); shot("calendario")
            view("Visualização em lista")
            tab("Meu dinheiro"); shot("contas")
            tab("Resumo"); shot("relatorios")
            tab("Ajustes"); shot("ajustes")
            tab("Início")
            compose.onNode(hasContentDescription("Adicionar movimentação")).performClick()
            shot("adicionar-opcoes")
            compose.onNode(hasText("Despesa")).performClick()
            shot("novo-lancamento")
        }
        Repo.showForScreenshots(demo.copy(theme = ThemeId.TOKYO))
        ActivityScenario.launch(MainActivity::class.java).use {
            shot("inicio-tokyo")
            tab("Resumo"); shot("relatorios-tokyo")
        }
    }
}
