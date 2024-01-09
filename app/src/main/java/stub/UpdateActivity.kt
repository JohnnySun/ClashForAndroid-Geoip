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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import stub.databinding.ActivityMainBinding
import stub.databinding.ActivityUpdateBinding
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateBinding
    private val updateUrl = "https://raw.githubusercontent.com/JohnnySun/ClashForAndroid-Geoip/release_info/version_info.json"
    private var downloadUrl: String = ""
    private var newVersion: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)
        title = "IPinfo GEOIP MMDB Updater"
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        ActivityStatusCheckUpdate("Checking Update...")
        checkForUpdate()
    }


    private fun ActivityStatusCheckUpdate(message: String) {
        Toast.makeText(this@UpdateActivity, message, Toast.LENGTH_LONG).show()
        binding.textView.text = message
        binding.updateBtn.text = "Check Update"
        binding.updateBtn.setOnClickListener {
            binding.textView.text = "Checking Update..."
            binding.updateBtn.isClickable = false
            checkForUpdate()
        }
        binding.updateBtn.isClickable = true
    }

    private fun ActivityStatusInstallUpdate() {
        Toast.makeText(this@UpdateActivity, "New version: $newVersion founded",
            Toast.LENGTH_LONG).show()
        binding.textView.text = "New version: $newVersion founded"
        binding.updateBtn.isClickable = true
        binding.updateBtn.text = "Download New Version"
        binding.updateBtn.setOnClickListener{
            binding.updateBtn.isClickable = false
            downloadAndInstallApk(downloadUrl)
        }
        binding.updateBtn.isClickable = true
    }



    private fun checkForUpdate() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = try {
                val url = URL(updateUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                val inputStream = connection.inputStream

                val updateJson = JSONObject(inputStream.bufferedReader().use { it.readText() })
                newVersion = updateJson.getInt("newVersion")
                downloadUrl = updateJson.getString("downloadUrl")

                if (newVersion > BuildConfig.VERSION_CODE) {
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
                    "New Version Found" -> ActivityStatusInstallUpdate()
                    "Already Latest" -> ActivityStatusCheckUpdate(
                            "Update check completed. \nNo new updates found. \nCurrent Version: ${BuildConfig.VERSION_CODE}")
                    else -> ActivityStatusCheckUpdate("Failed to check for update.")
                }
            }
        }
    }

    private fun downloadAndInstallApk(downloadUrl: String) {
        // Define the file name and the download directory
        val fileName = "app_update.apk"
        val downloadDirectory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        // Delete the old APK file if it exists
        val oldApkFile = File(downloadDirectory, fileName)
        if (oldApkFile.exists()) {
            oldApkFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Downloading Update")
                .setDescription("Downloading a new update for the app.")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Start a coroutine to update the progress
        CoroutineScope(Dispatchers.IO).launch {
            var downloading = true
            while (downloading) {
                val q = DownloadManager.Query()
                q.setFilterById(downloadId)

                val cursor = downloadManager.query(q)
                if (cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

                    if (bytesDownloadedIndex >= 0 && bytesTotalIndex >= 0 && statusIndex >= 0) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val bytesTotal = cursor.getInt(bytesTotalIndex)
                        val status = cursor.getInt(statusIndex)

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                        }

                        if (bytesTotal > 0) {
                            val progress = (bytesDownloaded * 100L / bytesTotal).toInt()
                            withContext(Dispatchers.Main) {
                                binding.progressBar.progress = progress
                            }
                        }
                    }
                }
                cursor.close()
            }
        }

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
                                    File(downloadDirectory, fileName)
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
                    ActivityStatusInstallUpdate()
                }

                unregisterReceiver(this)
                finish()
            }
        }

        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}
