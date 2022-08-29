package com.naruto.lib.baidu

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer
import com.naruto.lib.common.Global.commonLibInit
import com.naruto.lib.common.utils.LogUtils

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/21 0021
 * @Note
 */
class BaiduSdkInitializer {
    companion object {
        private var isBaiduInitialized: Boolean? = null
        private val taskQueue: MutableList<(Boolean) -> Unit> = mutableListOf()

        fun doAfterInitialized(block: (Boolean) -> Unit) {
            isBaiduInitialized?.run(block) ?: taskQueue.add(block)
        }
    }

    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/8/27 0027
     * @Note
     */
    internal class BaiduInitializerReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            LogUtils.i("--->action=${intent.action}")
            isBaiduInitialized =
                intent.action == SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK
            while (taskQueue.isNotEmpty()) {
                taskQueue.removeAt(0).invoke(isBaiduInitialized!!)
            }
            unRegister(context)
        }

        fun register(context: Context) {
            val intentFilter = IntentFilter().apply {
                addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR)
                addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_OK)
            }
            context.registerReceiver(this, intentFilter)
        }

        fun unRegister(context: Context) {
            context.unregisterReceiver(this)
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
    BaiduSdkInitializer.BaiduInitializerReceiver().register(this)
    SDKInitializer.setAgreePrivacy(applicationContext, true)
    SDKInitializer.initialize(applicationContext)
    LocationClient.setAgreePrivacy(true)
    commonLibInit()
}

private var hasInitialized = false