package com.abletonremote

import android.app.Application
import androidx.room.Room
import com.abletonremote.data.db.AppDatabase
import com.abletonremote.osc.OscConnectionManager
import com.abletonremote.repository.AbletonRepository

class AbletonRemoteApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var connectionManager: OscConnectionManager
        private set

    lateinit var repository: AbletonRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            this, AppDatabase::class.java, "ableton_remote.db"
        ).build()

        connectionManager = OscConnectionManager()
        repository = AbletonRepository(connectionManager)
    }

    override fun onTerminate() {
        connectionManager.disconnectAll()
        super.onTerminate()
    }
}
