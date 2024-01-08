package stub

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import stub.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        BarUtils.setNavBarVisibility(this, window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)


        binding.root.setOnClickListener {
            finish()
        }

        binding.msgTv.text = "${getString(R.string.package_label)} \nversion ${getString(R.string.geoip_version)}"
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 4000L)
    }

    override fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {
        super.overridePendingTransition(0, 0)
    }

}