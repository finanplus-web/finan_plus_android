// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
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
import kotlin.test.assertTrue

/**
 * Ajustes › Sobre › licenças: abre o app real, chega aos botões de licença e confere que os textos
 * (GPL, avisos de terceiros e Apache 2.0) aparecem inteiros, lidos dos assets, sem rede.
 * Roda junto com as capturas (`-PfinanScreenshots=true`), que já usam o Robolectric. A imagem fica em
 * build/ (não vai para docs/screenshots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class LicensesScreenTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** a lista vertical da aba Ajustes: a rolagem vertical mais interna que contém "Aparência" */
    private fun settingsList() = compose.onAllNodes(
        hasScrollToNodeAction() and hasAnyDescendant(hasText("Aparência")),
    ).onLast()

    /** item da lista ainda não montado (a seção "Sobre"): rola a lista até ele e toca */
    private fun open(label: String) {
        val list = settingsList()
        list.performScrollToNode(hasText(label))
        tap(label)
    }

    /**
     * Botão já montado (dentro de uma seção aberta): rola até ele e aciona o clique pela acessibilidade.
     * Um toque na tela poderia cair na barra de abas, que fica por cima do fim da lista.
     */
    private fun tap(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun has(fragment: String) = compose.onAllNodes(hasText(fragment, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun assertShown(fragment: String) {
        assertTrue(
            has(fragment),
            "não apareceu na tela: \"$fragment\" (ainda em Ajustes: ${has("Aparência")}; " +
                "botão virou Ocultar: ${has("Ocultar avisos de terceiros")}; texto de reserva: ${has("Texto da licença:")})",
        )
    }

    @Test
    fun licencasAbremDentroDoApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        DevicePrefs.get(app).askedNotifications = true
        Repo.showForScreenshots(DemoData.state().copy(theme = ThemeId.LIGHT))
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNode(hasText("Ajustes") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
            compose.waitForIdle()

            open("Sobre")
            assertShown("Componentes de terceiros")

            tap("Ver avisos de terceiros")
            assertShown("AVISOS DE TERCEIROS")
            // começo, meio e fim do arquivo: o texto chegou inteiro
            for (s in listOf("AndroidX (Jetpack)", "Bouncy Castle", "Liberation Sans", "Material Symbols", "Helvetica Bold")) assertShown(s)
            assertShown("Ocultar avisos de terceiros")

            tap("Ver licença Apache 2.0")
            assertShown("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION")

            tap("Ver licença completa (GNU GPL v3)")
            assertShown("GNU GENERAL PUBLIC LICENSE")

            compose.onNode(hasText("Ocultar avisos de terceiros") and hasClickAction()).performScrollTo()
            compose.waitForIdle()
            captureScreenRoboImage(File("build/screenshots-verificacao/licencas.png").apply { parentFile?.mkdirs() }.path)
        }
    }

}
