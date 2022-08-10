package com.naruto.lib.baidu

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.SDKInitializer

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/21 0021
 * @Note
 */
class Bridge {
}

/**
 * 初始化本模块
 * @receiver Application
 */
fun Application.baiduLibInit() {
    SDKInitializer.setAgreePrivacy(applicationContext, true)
    SDKInitializer.initialize(applicationContext)
    LocationClient.setAgreePrivacy(true)
}