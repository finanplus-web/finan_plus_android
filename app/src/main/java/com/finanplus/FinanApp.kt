// Finan+ — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.finanplus

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.finanplus.data.DevicePrefs
import com.finanplus.data.Repo
import com.finanplus.notify.Reminders
import com.finanplus.security.AppLock

class FinanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLock.init(this)
        Repo.init(this)
        val prefs = DevicePrefs.get(this)
        prefs.migrate(Repo.state.value.autoLock)
        if (prefs.value.lockEnabled) AppLock.lockNow() // processo novo com PIN/digital: começa bloqueado
        Reminders.createChannel(this)
        Reminders.schedule(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLock.onForeground(prefs.value.lockEnabled, prefs.value.autoLock)
                Repo.runRecurring() // virou o mês com o app aberto em segundo plano
            }
            override fun onStop(owner: LifecycleOwner) { AppLock.onBackground() }
        })
    }
}
