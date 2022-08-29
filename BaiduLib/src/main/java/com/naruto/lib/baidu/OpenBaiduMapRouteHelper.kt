package com.naruto.lib.baidu

import android.text.TextUtils
import com.baidu.location.BDLocation
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.utils.DistanceUtil
import com.baidu.mapapi.utils.OpenClientUtil
import com.baidu.mapapi.utils.route.BaiduMapRoutePlan
import com.baidu.mapapi.utils.route.RouteParaOption
import com.naruto.lib.common.MultiTaskFinishListener
import com.naruto.lib.common.base.BaseActivity
import com.naruto.lib.common.utils.LifecycleUtil
import com.naruto.lib.common.utils.DialogFactory
import com.naruto.lib.common.utils.LogUtils

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/27 0027
 * @Note
 */
class OpenBaiduMapRouteHelper(private val activity: BaseActivity) {
    private val locationHelper = LocationHelper(activity)// TODO: 申请权限
    private val locationCallback = object : LocationHelper.LocationCallback {
        override val requestPermissionReason: String = "导航功能需要定位权限"
        override val needGps: Boolean = true

        override fun onFinish(bdLocation: BDLocation?) {
            if (bdLocation == null) {
                DialogFactory.showHintDialog(activity, "定位失败，无法开启导航")
            } else {
                currentPosition = LatLng(bdLocation.latitude, bdLocation.longitude)
            }
            mTaskFinishListener.finish("locating")
        }

        override fun onStart() {
            activity.showLoadingDialog()
        }

        override fun onPermissionDenied(): Boolean {
            mTaskFinishListener.finish("locating")
            return super.onPermissionDenied()
        }
    }

    private val mTaskFinishListener = object : MultiTaskFinishListener() {
        override fun onAllTasksFinished() {
            activity.dismissLoadingDialog()
            openBaiduMapRoute()
        }
    }

    private val geoCoder: GeoCoder by lazy {
        GeoCoder.newInstance().apply {
            setOnGetGeoCodeResultListener(BaiduMapUtil.createOnGetGeoCoderResultListener { data: GeoCodeResult?, throwable: Throwable? ->
                if (throwable != null) DialogFactory.showHintDialog(activity, throwable.message)
                else {
                    destination = data!!.location
                    latLngMap[data.address] = destination!!
                }
            })
        }

    }

    private val destroyCallback =
        LifecycleUtil.addDestroyObserver(activity.lifecycleOwner, this) { destroy() }

    private var currentPosition: LatLng? = null//当前位置经纬度
    private var destination //目的地经纬度
            : LatLng? = null
    private var destinationName //目的地名称
            : String? = null
    private val latLngMap = mutableMapOf<String, LatLng>()
    private var destinationAddress //目的地地址
            : String? = null
    private var isOnGoing = false


    /**
     * 打开百度地图规划路线
     *
     * @param address 目的地地址
     */
    fun openBaiduMapRoute(address: String?) {
        BaiduMapUtil.checkBaiduMapClient(activity) {
            if (isOnGoing) return@checkBaiduMapClient
            if (TextUtils.isEmpty(address)) {
                DialogFactory.showHintDialog(activity, null, R.string.address_error_navigate).show()
                return@checkBaiduMapClient
            }
            isOnGoing = true
            destination = latLngMap[address]
            if (destination == null) getLatLngByAddress(address!!)
            locating()
        }
    }

    /**
     * 打开百度地图规划路线
     *
     * @param destination 目的地经纬度
     */
    fun openBaiduMapRoute(destination: LatLng?) {
        BaiduMapUtil.checkBaiduMapClient(activity) {
            if (isOnGoing) return@checkBaiduMapClient
            if (destination == null) {
                DialogFactory.showHintDialog(activity, null, R.string.address_error_navigate).show()
                return@checkBaiduMapClient
            }
            isOnGoing = true
            this.destination = destination
            locating()
        }
    }

    /**
     * 获取当前位置经纬度
     */
    private fun locating() {
        currentPosition = null
        mTaskFinishListener.start("locating")
        LocationHelper.clearLastLocation()
        locationHelper.startLocating(locationCallback)
    }

    /**
     * 打开百度地图规划路线
     */
    private fun openBaiduMapRoute() {
        if (currentPosition == null) {
            isOnGoing = false
            LogUtils.d("---> return")
            return
        }
        // 构建 route搜索参数
        val routeParaOption = RouteParaOption()
            .startPoint(currentPosition).startName("我的位置").endName(destinationName)
        val distance: Double = if (destination == null) Int.MAX_VALUE.toDouble()//无法获取目的地经纬度
        else {
            routeParaOption.endPoint(destination)
            DistanceUtil.getDistance(currentPosition, destination)
        }
        LogUtils.i("---> distance=$distance")

        //启动百度地图路线规划
        val routeFunc: () -> Unit = if (distance > 1000) { //路程太远，使用公共交通
            routeParaOption.busStrategyType(RouteParaOption.EBusStrategyType.bus_recommend_way);
            { BaiduMapRoutePlan.openBaiduMapTransitRoute(routeParaOption, activity) }
        } else { //步行
            { BaiduMapRoutePlan.openBaiduMapWalkingRoute(routeParaOption, activity) }
        }
        runCatching(routeFunc).onFailure { e ->
            e.printStackTrace()
            //提示未安装百度地图app或app版本过低
            DialogFactory.makeSimpleDialog(
                activity, content = "您尚未安装百度地图app或app版本过低，点击确认安装？",
                confirmListener = { OpenClientUtil.getLatestBaiduMapApp(activity) }
            ).show()
        }

        isOnGoing = false
    }

    /**
     * 根据地址获取经纬度
     *
     * @param address
     */
    private fun getLatLngByAddress(address: String) {
        mTaskFinishListener.start("geoCoder")
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
}