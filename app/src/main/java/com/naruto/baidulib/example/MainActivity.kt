package com.naruto.baidulib.example

import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.lifecycle.Lifecycle
import com.baidu.location.BDLocation
import com.baidu.location.LocationClientOption
import com.naruto.lib.baidu.LocationHelper
import com.naruto.lib.common.base.BaseActivity
import com.naruto.lib.common.utils.LifecycleUtil
import com.naruto.lib.common.utils.LogUtils
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {
    private val SDF_TIME = SimpleDateFormat("HH:mm:ss")
    private val handler by lazy { android.os.Handler(Looper.myLooper()!!) }
    private val btnStart by lazy { findViewById<Button>(R.id.btn_start) }
    private val btnStop by lazy { findViewById<Button>(R.id.btn_stop) }
    private val adapter by
    lazy { ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>()) }
    private val locationHelper by lazy {
        LocationHelper(permissionHelper , cacheExpiryTime = 0, optionConfig = {
            locationMode = LocationClientOption.LocationMode.Battery_Saving
            scanSpan = 30 * 1000
        })
    }
    private val locationCallback by lazy {
        object : LocationHelper.LocationCallback {
            override val locatingPurpose = "测试定位功能"
            override var needGps = true

            override fun onStart() {
                super.onStart()
                btnStart.isEnabled = false
                btnStop.isEnabled = true
            }

            override fun onFinish(bdLocation: BDLocation?) {
                val msg =
                    bdLocation?.apply { LogUtils.i("adCode=$adCode;locType=$locType;locTypeDescription=${locTypeDescription}") }
                        ?.takeIf { it.locType == 61 || it.locType == 161 }
                        ?.run { "定位成功latitude=$latitude;longitude=$longitude" } ?: "定位失败"
                //Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                bdLocation?: LogUtils.e("--->",Exception("bdLocation==null"))
                adapter.add("${SDF_TIME.format(Date())}->$msg")
            }
        }
    }

    override fun init() {
        btnStart.setOnClickListener {
            DeathlessService.launch(this, this) { startLocating() }
        }
        btnStop.setOnClickListener {
            locationHelper.stopLocating()
            DeathlessService.stop(this)
            it.isEnabled = false
            btnStart.isEnabled = true
        }

        findViewById<ListView>(R.id.lv).adapter = adapter
    }

    private fun startLocating() {
        handler.postDelayed({ locationHelper.startLocating(locationCallback) }, 1000)
    }

    override fun getLayoutRes() = R.layout.activity_main

}