// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus.screenshots

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.finanplus.MainActivity
import com.finanplus.core.ThemeId
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Trocar de aba deslizando muito rápido (bug relatado em 09/10/2026): a tela ficava numa aba e a barra
 * inferior marcava outra. Aqui o app real recebe vários deslizes seguidos, sem esperar um terminar para
 * começar o próximo, e depois confere se a aba marcada na barra é a mesma que aparece na tela.
 * Roda junto com as capturas (`-PfinanScreenshots=true`), que já usam o Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-port-xxhdpi")
class TabSwipeTest {
    @get:Rule val compose = createEmptyComposeRule()

    /** texto que só existe na tela de cada aba */
    private val marker = mapOf(
        "Início" to "Saldo atual", "Lançamentos" to "Calendário",
        "Relatórios" to "Exportar relatório em PDF", "Ajustes" to "Aparência",
    )

    private fun selectedTab(): String = compose.onAllNodes(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
    ).fetchSemanticsNodes().mapNotNull { n -> n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") }
        .single { it in marker.keys }

    private fun assertInSync() {
        compose.waitForIdle()
        val tab = selectedTab()
        val shown = marker.filterValues { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }.keys
        assertEquals(setOf(tab), shown, "barra marca \"$tab\", mas a tela mostra $shown")
    }

    /** vários deslizes rápidos: o próximo começa 40 ms depois do anterior, antes de a página assentar */
    private fun rapid(vararg left: Boolean) {
        compose.mainClock.autoAdvance = false
        for (l in left) {
            compose.onRoot().performTouchInput { if (l) swipeLeft(durationMillis = 60) else swipeRight(durationMillis = 60) }
            compose.mainClock.advanceTimeBy(40)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun deslizesRapidosMantemBarraETelaIguais() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        DevicePrefs.get(app).askedNotifications = true
        Repo.showForScreenshots(DemoData.state().copy(theme = ThemeId.LIGHT))
        ActivityScenario.launch(MainActivity::class.java).use {
            assertInSync()
            rapid(true, true, true); assertInSync()          // Início → … → Ajustes
            rapid(false, false, false); assertInSync()       // e de volta
            rapid(true, false, true, true, false); assertInSync()
            rapid(true, true, true, true, true, true); assertInSync() // além da última aba
        }
    }
}
