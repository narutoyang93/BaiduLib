package com.naruto.lib.baidu

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.utils.route.BaiduMapRoutePlan
import com.baidu.mapapi.utils.route.RouteParaOption
import com.naruto.lib.common.TopFunction.getResString
import com.naruto.lib.common.list.OnItemClickListener
import com.naruto.lib.common.utils.DialogFactory
import com.naruto.lib.common.utils.LifecycleUtil
import com.naruto.lib.common.utils.PopupWindowFactory

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/27 0027
 * @Note
 */
private const val TAG = "OpenBaiduMapRouteHelper"

class OpenMapRouteHelper(
    private val activity: AppCompatActivity,
    private val loadingDialog: AlertDialog
    = DialogFactory.createLoadingDialog(activity).also { it.setCancelable(false) }
) {
    private val rootView = activity.window.decorView.findViewById(android.R.id.content) as ViewGroup
    private val geoCoder: GeoCoder by lazy {
        GeoCoder.newInstance().apply {
            setOnGetGeoCodeResultListener(BaiduMapUtil.createOnGetGeoCoderResultListener { data: GeoCodeResult?, throwable: Throwable? ->
                loadingDialog.dismiss()
                if (throwable != null)
                    DialogFactory.showHintDialog(activity, throwable.message!!)
                else {
                    destination = data!!.location
                    latLngMap[data.address] = destination!!
                    showSelectTransportTypeDialog()
                }
            })
        }
    }

    private val transportTypePopupWindow by lazy { createSelectTransportPopupWindow() }

    private val destroyCallback =
        LifecycleUtil.addDestroyObserver(activity, this) { destroy() }

    private var destination //目的地经纬度
            : LatLng? = null
    private var destinationName //目的地名称
            : String? = null
    private val latLngMap = mutableMapOf<String, LatLng>()
    private var destinationAddress //目的地地址
            : String? = null


    /**
     * 打开百度地图规划路线
     *
     * @param address 目的地地址
     */
    fun openBaiduMapRoute(address: String?) {
        if (TextUtils.isEmpty(address)) {
            DialogFactory.showHintDialog(activity, getResString(R.string.address_error_navigate))
            return
        }
        loadingDialog.show()
        BaiduMapUtil.checkBaiduMapClient(activity) {
            destination = latLngMap[address]
            if (destination == null) getLatLngByAddress(address!!)
            else {
                loadingDialog.dismiss()
                showSelectTransportTypeDialog()
            }
        }
    }

    /**
     * 打开百度地图规划路线
     *
     * @param destination 目的地经纬度
     */
    fun openBaiduMapRoute(destination: LatLng?) {
        loadingDialog.show()
        BaiduMapUtil.checkBaiduMapClient(activity) {
            loadingDialog.dismiss()
            if (destination == null) {
                DialogFactory
                    .showHintDialog(activity, getResString(R.string.address_error_navigate))
                return@checkBaiduMapClient
            }
            this.destination = destination
            showSelectTransportTypeDialog()
        }
    }

    /**
     * 显示选择交通方式弹窗
     */
    private fun showSelectTransportTypeDialog() {
        transportTypePopupWindow.showAtLocation(rootView, Gravity.BOTTOM, 0, 0)
    }

    /**
     * 创建选择交通方式弹窗
     * @return PopupWindow
     */
    private fun createSelectTransportPopupWindow(): PopupWindow {
        return PopupWindowFactory.createSelectPopupWindow(
            "选择交通方式", rootView, TransportType.values().asList(), { it.text },
            object : OnItemClickListener<TransportType> {
                override fun onClick(data: TransportType, position: Int, view: View) {
                    openBaiduMapRoute(data)
                }
            })
    }

    /**
     * 打开百度地图规划路线
     */
    private fun openBaiduMapRoute(transportType: TransportType) {
        // 构建 route搜索参数
        val routeParaOption = RouteParaOption()
            .startName("我的位置")
            .endName(destinationName)
            .endPoint(destination)

        //启动百度地图路线规划
        runCatching {
            when (transportType) {
                TransportType.Walking ->
                    BaiduMapRoutePlan.openBaiduMapWalkingRoute(routeParaOption, activity)

                TransportType.Transit -> {
                    routeParaOption.busStrategyType(RouteParaOption.EBusStrategyType.bus_recommend_way)
                    BaiduMapRoutePlan.openBaiduMapTransitRoute(routeParaOption, activity)
                }

                TransportType.Driving ->
                    BaiduMapRoutePlan.openBaiduMapDrivingRoute(routeParaOption, activity)

                TransportType.NewEnergy ->
                    BaiduMapRoutePlan.openBaiduMapNewEnergyRoute(routeParaOption, activity)

                TransportType.Truck ->
                    BaiduMapRoutePlan.openBaiduMapTruckRoute(routeParaOption, activity)
            }
        }.onFailure { e ->
            e.printStackTrace()
            DialogFactory.showHintDialog(activity, e.message ?: "未知异常", "调起导航失败")
        }
    }

    /**
     * 根据地址获取经纬度
     *
     * @param address
     */
    private fun getLatLngByAddress(address: String) {
        destinationAddress = address
        destinationName = destinationAddress
        destinationAddress!!.let { s ->
            var city = ""
            s.indexOf("市").takeIf { it > 0 }?.let {
                city = s.substring(0, it)
                destinationName = s.substring(it + 1)
            }
            geoCoder.geocode(GeoCodeOption().city(city).address(s)) // 地址
        }
    }

    private fun destroy() {
        geoCoder.destroy()
        destroyCallback()
        BaiduMapRoutePlan.finish(activity)
    }

    private enum class TransportType(val text: String) {
        Walking("步行"), Transit("公共交通"), Driving("驾车"), NewEnergy("新能源"), Truck("货车")
    }
}