package com.myagent.app

import android.app.Application
import com.myagent.app.data.db.AppDatabase
import com.myagent.app.data.prefs.SettingsDataStore

class MyAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.init(this)
        SettingsDataStore.init(this)
    }
}
