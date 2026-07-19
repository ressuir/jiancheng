package com.zookie.simpleschedule

import android.app.Application
import com.zookie.simpleschedule.data.AppDatabase
import com.zookie.simpleschedule.data.ScheduleRepository

class JianChengApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val database: AppDatabase = AppDatabase.build(application)
    val repository: ScheduleRepository = ScheduleRepository(database)
}
