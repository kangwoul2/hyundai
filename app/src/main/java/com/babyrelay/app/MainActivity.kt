package com.babyrelay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.babyrelay.app.app.MainViewModel
import com.babyrelay.app.ui.BabyRelayRoot

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 0=시스템, 1=다크, 2=라이트 (개발 패널에서 변경)
            var themeMode by rememberSaveable { mutableStateOf(0) }
            BabyRelayRoot(
                vm = vm,
                darkOverride = when (themeMode) { 1 -> true; 2 -> false; else -> null },
                onDarkOverride = { themeMode = when (it) { true -> 1; false -> 2; null -> 0 } },
            )
        }
    }
}
