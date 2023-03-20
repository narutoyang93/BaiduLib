package com.naruto.lib.baidu

import android.app.Activity
import com.baidu.mapapi.search.core.SearchResult.ERRORNO
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.utils.OpenClientUtil
import com.naruto.lib.common.utils.DialogFactory
import com.naruto.lib.common.utils.DialogFactory.ActionDialogOption

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/27 0027
 * @Note
 */
object BaiduMapUtil {
    /**
     * 检查百度地图客户端版本
     *
     * @param runnable 检查通过后将执行的操作
     */
    fun checkBaiduMapClient(activity: Activity, runnable: Runnable): Boolean {
        return (OpenClientUtil.getBaiduMapVersion(activity) < 810).also {
            if (it) { //创建弹窗提示未安装百度地图app或app版本过低
                val dialogOption = ActionDialogOption(
                    content = "您尚未安装百度地图app或app版本过低，是否前往安装？",
                    confirmListener = { _, _ -> OpenClientUtil.getLatestBaiduMapApp(activity) },
                    neutralText = "使用网页版",
                    neutralListener = { _, _ -> runnable.run() }
                )
                DialogFactory.createActionDialog(activity, dialogOption).show()
            } else runnable.run()
        }
    }

    /**
     * 创建地理编码回调
     *
     * @param callback
     * @return
     */
    fun createOnGetGeoCoderResultListener(callback: (GeoCodeResult?, Throwable?) -> Unit): OnGetGeoCoderResultListener {
        return object : OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult?) {
                val msg = geoCodeResult?.run {
                    when (error) {
                        ERRORNO.NO_ERROR -> if (location != null) {
                            callback(this, null)
                            return
                        } else "经纬度为空"
                        ERRORNO.NETWORK_ERROR, ERRORNO.NETWORK_TIME_OUT -> "网络异常"
                        else -> "地址解析异常"
                    }
                }
                callback(geoCodeResult, Throwable(msg))
            }

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult) {}
        }
    }
}