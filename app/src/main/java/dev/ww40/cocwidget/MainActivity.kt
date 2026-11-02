package dev.ww40.cocwidget

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import dev.ww40.cocwidget.data.PlayerNameStore
import dev.ww40.cocwidget.sync.SyncActivity
import dev.ww40.cocwidget.widget.WidgetRefresher
import kotlinx.coroutines.launch

/** Setup screen: explains the sync flow, stores the player name and asks for notification permission. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.sync_button).setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
        }
        setUpPlayerName()
        requestNotificationPermission()
    }

    private fun setUpPlayerName() {
        val store = PlayerNameStore(this)
        val nameField = findViewById<EditText>(R.id.player_name)
        nameField.setText(store.load().orEmpty())
        findViewById<Button>(R.id.save_name_button).setOnClickListener {
            store.save(nameField.text.toString())
            val appContext = applicationContext
            WidgetRefresher.scope.launch { WidgetRefresher.refreshAll(appContext) }
            Toast.makeText(this, R.string.player_name_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 1
    }
}
