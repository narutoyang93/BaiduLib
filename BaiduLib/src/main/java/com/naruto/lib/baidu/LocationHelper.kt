package com.naruto.lib.baidu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.util.Pair
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.naruto.lib.common.Global
import com.naruto.lib.common.base.BaseActivity
import com.naruto.lib.common.helper.PermissionHelper
import com.naruto.lib.common.utils.DialogFactory
import com.naruto.lib.common.utils.LogUtils
import com.naruto.lib.common.utils.NotificationUtil
import com.naruto.lib.common.utils.showWithoutActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.schedule

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/1/20 0020
 * @Note
 */
/*
option.locationMode = LocationClientOption.LocationMode.Battery_Saving; //可选，设置定位模式，默认高精度。LocationMode.Hight_Accuracy：高精度；LocationMode. Battery_Saving：低功耗；LocationMode. Device_Sensors：仅使用设备。
option.coorType="bd09ll"//可选，设置返回经纬度坐标类型，默认GCJ02。//GCJ02：国测局坐标；BD09ll：百度经纬度坐标；BD09：百度墨卡托坐标。海外地区定位，无需设置坐标类型，统一返回WGS84类型坐标
option.scanSpan=1000 //可选，设置发起定位请求的间隔，int类型，单位ms，默认为0。如果设置为0，则代表单次定位，即仅定位一次；如果设置非0，需设置1000ms以上才有效
option.isLocationNotify = true //可选，设置是否当GPS有效时按照1S/1次频率输出GPS结果，默认false
option.isIgnoreKillProcess=false //可选，定位SDK内部是一个service，并放到了独立进程。设置是否在stop的时候杀死这个进程，默认（建议）不杀死，即setIgnoreKillProcess(true)
option.isIgnoreCacheException=true//可选，设置是否收集Crash信息，默认收集，即参数为false
option.wifiCacheTimeOut=5*60*1000//可选，V7.2版本新增能力。如果设置了该接口，首次启动定位时，会先判断当前Wi-Fi是否超出有效期，若超出有效期，会先重新扫描Wi-Fi，然后定位
更多LocationClientOption的配置，请参照类参考中LocationClientOption类的详细说明
*/
private const val LAST_LOCATION_EXPIRY_TIME = 5 * 60 * 1000L//上一次定位结果失效时间（毫秒）
private const val DEF_TIMEOUT = 5000L //5s超时
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

class LocationHelper(
    /**
     * 可以使用NormalActivityPermissionHelper或LegacyActivityPermissionHelper，也可以直接用PermissionHelper.getDefault(activity)
     */
    private val permissionHelper: PermissionHelper,
    optionConfig: LocationClientOption.() -> Unit = {},
    private val cacheExpiryTime: Long = LAST_LOCATION_EXPIRY_TIME,//缓存结果有效时间（在此期间不再执行定位，而是拿缓存数据返回），若<=0则不缓存，单位：毫秒
    needFineLocation: Boolean = true,//是否需要确切位置
    private val needForegroundService: Boolean = false,//是否需要前台服务（无需外部创建服务）
) {
    constructor(
        activity: BaseActivity,
        optionConfig: LocationClientOption.() -> Unit = {},
        cacheExpiryTime: Long = LAST_LOCATION_EXPIRY_TIME,//缓存结果有效时间（在此期间不再执行定位，而是拿缓存数据返回），若<=0则不缓存，单位：毫秒
        needFineLocation: Boolean = true,//是否需要确切位置
        needForegroundService: Boolean = false//是否需要前台服务（无需外部创建服务）
    ) : this(
        activity.permissionHelper,
        optionConfig, cacheExpiryTime, needFineLocation, needForegroundService
    )

    companion object {
        //全局记录定位结果，cacheExpiryTime内不再执行定位，除非执行了clearLastLocation()
        var lastLocation: Pair<Long, BDLocation>? = null
            private set

        fun clearLastLocation() {
            lastLocation = null
        }
    }

    private val client = LocationClient(Global.getMainModuleContext())
    private lateinit var locationCallback: LocationCallback
    private var timeoutTimer: Timer? = null
    private val permissions =
        if (needFineLocation) LOCATION_PERMISSIONS else arrayOf(LOCATION_PERMISSIONS[0])
    var timeOut = DEF_TIMEOUT

    @Volatile
    var isLocating = false //正在定位中
        private set

    init {
        val option = LocationClientOption()
        optionConfig.invoke(option)
        client.locOption = option

        client.registerLocationListener(object : BDAbstractLocationListener() {
            @Volatile//是否已经回调（防止重复回调，因为有时候会只执行onLocDiagnosticMessage，有时候会同时执行onLocDiagnosticMessage和onReceiveLocation）
            private var hasBeenCalledBack = false

            override fun onReceiveLocation(result: BDLocation) {
                if (isNeedAutoStop()) stopLocating()
                if (cacheExpiryTime > 0) lastLocation = Pair(System.currentTimeMillis(), result)
                callback(result)
            }

            override fun onLocDiagnosticMessage(p0: Int, p1: Int, p2: String?) {
                super.onLocDiagnosticMessage(p0, p1, p2)
                if (isNeedAutoStop()) stopLocating()
                LogUtils.e("--->locType=$p0;diagnosticType=$p1;diagnosticMessage=$p2")
                callback(null)
            }

            private fun callback(result: BDLocation?) {
                if (hasBeenCalledBack) return
                hasBeenCalledBack = true
                Timer().schedule(1000) { hasBeenCalledBack = false }//1s内防止重复回调
                locationCallback.onFinish(result)
            }
        })
    }

    /**
     * 执行定位
     */
    @Synchronized
    fun startLocating(callback: LocationCallback) {
        if (cacheExpiryTime > 0) lastLocation?.run {
            if (System.currentTimeMillis() - first < cacheExpiryTime && isNeedAutoStop()) {//上次定位结果还没有过期，使用该结果，不执行定位
                LogUtils.i("--->使用上次定位结果")
                callback.onFinish(second)
                return
            }
        }

        if (isLocating) {
            LogUtils.w("--->Locating task is running.")
            return
        }

        if (!isGpsOpen()) {
            if (callback.needGps) { //GPS没有打开，提示用户打开GPS重新定位
                MainScope().launch {
                    DialogFactory.createGoSettingDialog(permissionHelper, "定位服务未开启",
                        "请开启定位服务以" + callback.locatingPurpose,
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                        { callback.onUserDoNotOpenGps() } //用户不前往设置
                    ) {
                        if (isGpsOpen()) checkPermissionAndLocating(callback)
                        else callback.onUserDoNotOpenGps()//用户依旧没开启GPS
                    }.showWithoutActivity()
                }
            } else {
                LogUtils.w("--->GPS is not opened.")
                callback.onFinish(null)
            }
            return
        }

        checkPermissionAndLocating(callback)
    }


    /**
     * 检查权限
     * @param callback LocationCallback
     */
    private fun checkPermissionAndLocating(callback: LocationCallback) {
        if (callback.locatingPurpose == null) doLocating(callback)
        else {
            val reason = "拒绝此权限将无法${callback.locatingPurpose}"
            permissionHelper.doWithPermission(
                object : PermissionHelper.RequestPermissionsCallback(Pair(reason, permissions)) {
                    override fun onGranted() {//先申请前台权限，再申请后台权限，因为不能同时申请
                        if (needForegroundService || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                            doLocating(callback)
                        else permissionHelper.doWithPermission(
                            object : PermissionHelper.RequestPermissionsCallback(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    .let { Pair(reason, it) }
                            ) {
                                override fun onGranted() {
                                    doLocating(callback)
                                }
                            })
                    }

                    override fun onDenied(
                        context: Context?, deniedPermissions: MutableList<String>
                    ) {
                        if (!callback.onPermissionDenied())
                            super.onDenied(context, deniedPermissions)
                    }
                })
        }
    }

    /**
     * 执行定位
     * @param callback LocationCallback
     */
    private fun doLocating(callback: LocationCallback) {
        locationCallback = callback
        client.start()
        if (needForegroundService) client.enableLocInForeground()
        locationCallback.onStart()
        isLocating = true

        if (isNeedAutoStop())
            timeoutTimer = Timer().apply {
                schedule(timeOut) {
                    if (isLocating) {
                        timeoutTimer = null
                        stopLocating()
                        MainScope().launch {
                            Global.toast("定位超时")
                            LogUtils.w("--->定位超时")
                            locationCallback.onFinish(null)
                        }
                    }
                }
            }
    }

    @Synchronized
    fun stopLocating() {
        if (needForegroundService) client.disableLocInForeground(true)
        client.stop()
        timeoutTimer?.cancel()
        isLocating = false
    }

    private fun isNeedAutoStop(): Boolean {
        return client.locOption.scanSpan < 1000
    }

    private fun LocationClient.enableLocInForeground() {
        val notification =
            NotificationUtil.createNotificationBuilder(permissionHelper.context).build()
        enableLocInForeground(this@LocationHelper.hashCode(), notification)
    }

    fun destroy() {
        stopLocating()
    }

    /**
     * GPS是否已打开
     *
     * @return
     */
    private fun isGpsOpen(): Boolean {
        val lm =
            permissionHelper.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }


    /**
     * @Description
     * @Author Naruto Yang
     * @CreateDate 2022/7/27 0027
     * @Note
     */
    interface LocationCallback {
        val locatingPurpose: String?//执行定位的目的（例如“获取周边店铺信息”），若为null，则不申请权限，有权限就定位，无权限直接返回定位失败
        var needGps: Boolean//是否需要开启GPS，若为false，则不要求用户开启GPS，已开启就定位，未开启直接返回定位失败
        fun onFinish(bdLocation: BDLocation?)
        fun onStart() {}

        /**
         * 权限被拒绝时
         * @return Boolean 是否已弹窗或Toast提醒了。若返回false，则会弹出默认toast
         */
        fun onPermissionDenied(): Boolean {
            return false
        }

        fun onUserDoNotOpenGps() {
            onFinish(null)
        }
    }
}