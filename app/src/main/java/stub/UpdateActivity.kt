package stub;

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateActivity : Activity() {

    private val updateUrl = "https://raw.githubusercontent.com/JohnnySun/ClashForAndroid-Geoip/release_info/version_info.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        checkForUpdate()
    }

    private fun checkForUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                val url = URL(updateUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val inputStream = connection.inputStream

                val updateJson = JSONObject(inputStream.bufferedReader().use { it.readText() })
                val newVersion = updateJson.getInt("newVersion")
                val downloadUrl = updateJson.getString("downloadUrl")

                if (newVersion > BuildConfig.VERSION_CODE) {
                    downloadAndInstallApk(downloadUrl)
                    "New Version Found"
                } else {
                    "Already Latest"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                "Failed"
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    "New Version Found" -> Toast.makeText(this@UpdateActivity, "Update new version geoip database", Toast.LENGTH_LONG).show()
                    "Already Latest" -> Toast.makeText(this@UpdateActivity, "Update check completed. No new updates founded.", Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(this@UpdateActivity, "Failed to check for updates.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadAndInstallApk(downloadUrl: String) {
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Downloading Update")
                .setDescription("Downloading a new update for the app.")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "new_app_update.apk")

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val downloadFileUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (downloadFileUri != null) {
                    val intentInstall = Intent(Intent.ACTION_VIEW).apply {
                        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // For Android N and above, use FileProvider to get the URI
                            val contentUri = FileProvider.getUriForFile(
                                    this@UpdateActivity,
                                    "${applicationContext.packageName}.provider",
                                    File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "new_app_update.apk")
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            contentUri
                        } else {
                            // For older versions, use the Uri from the DownloadManager
                            Uri.fromFile(File(downloadManager.getUriForDownloadedFile(downloadId).path))
                        }

                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    startActivity(intentInstall)

                }

                unregisterReceiver(this)
                finish()
            }
        }

        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}
