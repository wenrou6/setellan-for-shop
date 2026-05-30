package com.qiutool.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.qiutool.app.core.PermissionMethod
import com.qiutool.app.ui.AppTheme
import com.qiutool.app.ui.DISCLAIMER_VERSION
import com.qiutool.app.ui.DisclaimerScreen
import com.qiutool.app.ui.MainScreen
import com.qiutool.app.ui.PermissionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("qiutool_settings", Context.MODE_PRIVATE)

        setContent {
            AppTheme {
                var disclaimerOk by remember {
                    mutableStateOf(prefs.getInt("disclaimer_accepted_version", 0) >= DISCLAIMER_VERSION)
                }
                var permissionMethod by remember {
                    mutableStateOf(prefs.getString("permission_method", "") ?: "")
                }

                when {
                    !disclaimerOk -> DisclaimerScreen(
                        onAccept = {
                            prefs.edit()
                                .putInt("disclaimer_accepted_version", DISCLAIMER_VERSION)
                                .apply()
                            disclaimerOk = true
                        },
                        onReject = { finish() },
                    )
                    permissionMethod.isEmpty() -> PermissionScreen(
                        current = permissionMethod,
                        onConfirm = { chosen ->
                            prefs.edit().putString("permission_method", chosen).apply()
                            permissionMethod = chosen
                        },
                    )
                    else -> MainScreen()
                }
            }
        }
    }
}
