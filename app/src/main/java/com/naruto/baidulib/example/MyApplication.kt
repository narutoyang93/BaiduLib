package com.naruto.baidulib.example

import android.app.Application
import com.naruto.lib.baidu.baiduLibInit

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/8/19 0019
 * @Note
 */
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        baiduLibInit()//初始化百度SDK
    }
}