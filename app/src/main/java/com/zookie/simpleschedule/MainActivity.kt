package com.zookie.simpleschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.zookie.simpleschedule.ui.AppViewModelFactory
import com.zookie.simpleschedule.ui.DataViewModel
import com.zookie.simpleschedule.ui.JianChengApp
import com.zookie.simpleschedule.ui.ScheduleViewModel
import com.zookie.simpleschedule.ui.theme.JianChengTheme

class MainActivity : ComponentActivity() {
    private val factory by lazy {
        val container = (application as JianChengApplication).container
        AppViewModelFactory(container.database, container.repository)
    }
    private val scheduleViewModel: ScheduleViewModel by viewModels { factory }
    private val dataViewModel: DataViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JianChengTheme {
                JianChengApp(scheduleViewModel, dataViewModel)
            }
        }
    }
}
