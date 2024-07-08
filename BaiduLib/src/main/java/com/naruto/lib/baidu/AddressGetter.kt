package com.naruto.lib.baidu

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.baidu.location.Address
import com.baidu.location.BDLocation
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.naruto.lib.common.base.BaseActivity
import com.naruto.lib.common.helper.PermissionHelper
import java.lang.ref.WeakReference

/**
 * @Description 用于获取当前地址
 * @Author Naruto Yang
 * @CreateDate 2023/8/28 0028
 * @Note
 */
private const val TAG = "AddressGetter"

open class AddressGetter(
    permissionHelper: PermissionHelper,
    optionConfig: LocationClientOption.() -> Unit = {}
) {
    private val permissionHelperWF = WeakReference(permissionHelper)
    private val locationHelper by lazy {
        LocationHelper(permissionHelper, optionConfig, 0, true, true)
            .also { addOnActivityDestroyListener { it.destroy() } }
    }

    private lateinit var geoCoderCallback: (address: ReverseGeoCodeResult.AddressComponent?) -> Unit

    private val geoCoder by lazy {
        GeoCoder.newInstance().also {
            it.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(p0: GeoCodeResult?) {}

                override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                    p0?.run {
                        if (error == SearchResult.ERRORNO.NO_ERROR) {
                            Log.i(TAG, "--->address=$address")
                            geoCoderCallback.invoke(addressDetail)
                        } else {
                            Log.e(TAG, "--->error=$error")
                            geoCoderCallback.invoke(null)
                        }
                    }
                }
            })
            addOnActivityDestroyListener { it.destroy() }
        }
    }

    protected fun getActivity(): BaseActivity? =
        permissionHelperWF.get()?.context as BaseActivity?

    /**
     * 执行定位
     *
     * @param callback
     */
    fun startLocating(locatingPurpose: String, callback: (BDLocation?) -> Unit) {
        locationHelper.startLocating(object : LocationHelper.LocationCallback {
            override var needGps = true
            override val locatingPurpose: String
                get() = locatingPurpose

            override fun onFinish(bdLocation: BDLocation?) {
                if (bdLocation != null && bdLocation.addrStr == null) {
                    if (bdLocation.locType == 61 || bdLocation.locType == 161) {
                        geoCoderCallback = { addrData ->
                            if (addrData == null) callback.invoke(null)
                            else {
//                                callback.invoke(bdLocation.apply { addrStr = it })
                                //不能直接用setAddrStr，因为getAddrStr取值与setAddrStr存值不是同一个成员变量
                                val address = addrData.run {
                                    Address.Builder()
                                        .province(province).city(city).district(district)
                                        .town(town).street(street).streetNumber(streetNumber)
                                }.build()
                                callback.invoke(bdLocation.apply { setAddr(address) })
                            }
                        }
                        val latLng = bdLocation.run { LatLng(latitude, longitude) }
                        geoCoder.reverseGeoCode(ReverseGeoCodeOption().location(latLng))
                    } else callback.invoke(null)
                } else callback.invoke(bdLocation)
            }
        })
    }

    private fun addOnActivityDestroyListener(callback: () -> Unit) {
        getActivity()?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                callback.invoke()
                super.onDestroy(owner)
            }
        })
    }
}