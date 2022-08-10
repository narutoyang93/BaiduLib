package com.naruto.lib.baidu

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.baidu.mapapi.search.core.SearchResult.ERRORNO
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.utils.OpenClientUtil
import com.naruto.lib.common.utils.DialogFactory
import com.naruto.lib.common.utils.DialogFactory.DialogData

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
                val dialogData = DialogData()
                dialogData.layoutResId = com.naruto.lib.common.R.layout.dialog_simple_3_button
                dialogData.content = "您尚未安装百度地图app或app版本过低，是否前往安装？"
                dialogData.confirmListener = View.OnClickListener {
                    OpenClientUtil.getLatestBaiduMapApp(activity)
                }
                val dialog = DialogFactory.makeSimpleDialog(activity, dialogData)
                { d, v ->
                    val otherBtn = v.findViewById<TextView>(com.naruto.lib.common.R.id.btn_other)
                    otherBtn.text = "使用网页版"
                    otherBtn.setOnClickListener {
                        d.dismiss()
                        runnable.run()
                    }
                }
                dialog.show()
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