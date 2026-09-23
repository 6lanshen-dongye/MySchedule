package com.myschedule.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.myschedule.app.data.AppStore
import com.myschedule.app.ui.MyScheduleApp

class MainActivity : ComponentActivity() {

    private val store by lazy { AppStore.get(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyScheduleApp(store)
        }
    }
}
