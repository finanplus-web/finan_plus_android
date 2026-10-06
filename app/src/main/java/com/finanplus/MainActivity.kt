// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.finanplus.data.DevicePrefs
import com.finanplus.security.AppLock
import com.finanplus.ui.FinanRoot
import kotlinx.coroutines.launch

/** FragmentActivity (subclasse de ComponentActivity) porque o BiometricPrompt exige. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = DevicePrefs.get(this)
        // Bloqueia capturas de tela e esconde o conteúdo na lista de apps recentes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                prefs.flow.collect { d ->
                    if (d.secureScreen) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent { FinanRoot(activity = this) }
    }

    /** O aparelho tem bloqueio de tela (PIN, padrão ou senha do Android). */
    fun isDeviceSecure(): Boolean = getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true

    /** Há digital/rosto cadastrado no aparelho. */
    fun canUseBiometric(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC) == BiometricManager.BIOMETRIC_SUCCESS

    /** Há digital OU bloqueio de tela (senha/padrão/PIN do Android) para usar como alternativa. */
    fun canUseDeviceAuth(): Boolean =
        BiometricManager.from(this).canAuthenticate(DEVICE_AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Pede a digital. [withPinFallback] = o app tem PIN próprio: o botão "Usar PIN" volta para o teclado.
     * Sem PIN no app, a alternativa é o bloqueio de tela do Android, para ninguém ficar trancado fora.
     */
    fun promptBiometric(
        title: String = "Desbloquear Finan+",
        withPinFallback: Boolean,
        onSuccess: () -> Unit = { AppLock.unlock() },
        onFail: (String) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) onFail(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("Use a digital ou o rosto")
        if (withPinFallback && canUseBiometric()) {
            info.setAllowedAuthenticators(BIOMETRIC).setNegativeButtonText("Usar PIN")
        } else if (canUseDeviceAuth()) {
            info.setAllowedAuthenticators(DEVICE_AUTH)
        } else { onFail("Nenhuma digital ou bloqueio de tela cadastrado no aparelho."); return }
        prompt.authenticate(info.build())
    }

    private companion object {
        /**
         * Só biometria Classe 3 (forte): a fraca (Classe 2) aceita reconhecimento facial 2D, que pode ser enganado com foto.
         * Aparelhos só com biometria fraca usam o bloqueio de tela do Android como alternativa.
         */
        const val BIOMETRIC = BiometricManager.Authenticators.BIOMETRIC_STRONG
        /** STRONG | DEVICE_CREDENTIAL só é aceito a partir do Android 11; antes, o par suportado é WEAK | DEVICE_CREDENTIAL. */
        val DEVICE_AUTH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
