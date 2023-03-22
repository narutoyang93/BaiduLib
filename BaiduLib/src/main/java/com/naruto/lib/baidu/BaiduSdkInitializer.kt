package com.naruto.lib.baidu

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.VersionInfo
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.weather.WeatherDataType
import com.baidu.mapapi.search.weather.WeatherSearch
import com.baidu.mapapi.search.weather.WeatherSearchOption
import com.naruto.lib.common.commonLibInit
import com.naruto.lib.common.utils.LogUtils
import java.util.*
import kotlin.concurrent.schedule

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/21 0021
 * @Note
 */
class BaiduSdkInitializer {
    companion object {
        private var isBaiduKeyValid: Boolean? = null
        private val taskQueue: MutableList<(Boolean) -> Unit> = mutableListOf()

        fun doAfterInitialized(block: (Boolean) -> Unit) {
            isBaiduKeyValid?.run(block) ?: taskQueue.add(block)
        }

        internal fun startInitializedListener(context: Context) {
            (if (VersionInfo.getApiVersion() > "7_5_3") Checker() else Receiver { context }).start()
        }
    }

    /**
     * @Description 监听鉴权广播（7.5.4及以上版本无效）
     * @Author Naruto Yang
     * @CreateDate 2022/8/27 0027
     * @Note
     */
    private class Receiver(private val context: () -> Context) :
        BroadcastReceiver(), BaiduInitializerListener {

        override fun onReceive(context: Context, intent: Intent) {
            LogUtils.i("--->action=${intent.action}")
            onFinish(intent.action == SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK)
            context.unregisterReceiver(this)
        }

        override fun start() {
            val intentFilter = IntentFilter().apply {
                addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR)
                addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK)
            }
            context().registerReceiver(this, intentFilter)
        }
    }

    /**
     * @Description 循环检查鉴权结果
     * @Author Naruto Yang
     * @CreateDate 2023/3/21 0021
     * @Note
     */
    private class Checker : BaiduInitializerListener {
        private val weatherSearch = WeatherSearch.newInstance().apply {
            setWeatherSearchResultListener { result ->
                if (result.error == SearchResult.ERRORNO.PERMISSION_UNFINISHED)
                    Timer().schedule(300) { start() }
                else (result.error == SearchResult.ERRORNO.NO_ERROR)
                        .let { onFinish(it);if (!it) LogUtils.e("--->error:${result.error}") }
            }
        }

        private val option = WeatherSearchOption()
            .apply { weatherDataType(WeatherDataType.WEATHER_DATA_TYPE_ALL).districtID("110105") }// 天安门区域ID

        override fun start() {
            weatherSearch.request(option)
        }
    }


    private interface BaiduInitializerListener {
        fun start()
        fun onFinish(isValid: Boolean) {
            isBaiduKeyValid = isValid
            while (taskQueue.isNotEmpty()) taskQueue.removeAt(0).invoke(isBaiduKeyValid!!)
        }
    }
}

/**
 * 初始化本模块
 * @receiver Application
 */
fun Application.baiduLibInit() {
    if (hasInitialized) return
    hasInitialized = true
    BaiduSdkInitializer.startInitializedListener(this)
    SDKInitializer.setAgreePrivacy(applicationContext, true)
    SDKInitializer.initialize(applicationContext)
    LocationClient.setAgreePrivacy(true)
    commonLibInit()
}

private var hasInitialized = false