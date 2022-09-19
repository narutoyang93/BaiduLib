package com.naruto.baidulib.example;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import com.naruto.lib.common.base.ForegroundService;
import com.naruto.lib.common.utils.LifecycleUtil;
import com.naruto.lib.common.utils.LogUtils;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;


/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2022/7/22 0022
 * @Note
 */
public class DeathlessService extends ForegroundService {
    public static final int NOTIFICATION_ID_DEATHLESS = 666;
    private PowerManager.WakeLock wl = null;

    @Override
    protected int getNotificationId() {
        return NOTIFICATION_ID_DEATHLESS;
    }

    @Override
    protected PendingIntent getPendingIntent() {
        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (wl == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getClass().getCanonicalName());
            wl.acquire();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (wl != null) wl.release();
        super.onDestroy();
    }

    /**
     * @param activity
     * @param lifecycleOwner
     */
    public static void launch(Activity activity, LifecycleOwner lifecycleOwner, Runnable onServiceLaunched) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//添加电源白名单
            String packageName = activity.getPackageName();
            PowerManager pm = (PowerManager) activity.getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName) && lifecycleOwner != null) {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setTitle("温馨提示")//设置对话框的标题
                        .setMessage("检测到您使用的是高版本系统，建议您在电池设置中解除" + activity.getString(R.string.app_name) + "的后台限制，以防止任务执行中应用被意外清除的问题。")//设置对话框的内容
                        //设置对话框的按钮
                        .setNegativeButton("以后再说", (dialog1, which) -> {
                            dialog1.dismiss();
                            launchWithIgnoringBattery(activity);
                            onServiceLaunched.run();
                        })
                        .setPositiveButton("前往设置", new DialogInterface.OnClickListener() {
                            Function0<Unit> runnable = null;//移除监听

                            @Override
                            public void onClick(DialogInterface dialog1, int which) {
                                dialog1.dismiss();
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + packageName));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                activity.startActivity(intent);
                                runnable = LifecycleUtil.INSTANCE.addObserver(lifecycleOwner, activity, Lifecycle.Event.ON_RESUME, a -> {
                                    if (runnable == null) return null;
                                    launch(a, null, onServiceLaunched);
                                    runnable.invoke();//移除监听
                                    runnable = null;
                                    return null;
                                });
                            }
                        }).create();
                dialog.show();
                return;
            }
        }
        launchWithIgnoringBattery(activity);
        onServiceLaunched.run();
    }

    public static void launchWithIgnoringBattery(Context context) {
        launch(context, DeathlessService.class);
    }

    /**
     * 启动前台服务
     *
     * @param context
     * @param serviceClass
     */
    public static <T extends ForegroundService> void launch(Context context, Class<T> serviceClass) {
        Intent intent = new Intent(context, serviceClass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, DeathlessService.class));
    }

    public interface CallBack {
        void onCallBack(boolean waitingForResume);
    }
}
