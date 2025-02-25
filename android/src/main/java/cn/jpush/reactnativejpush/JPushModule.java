package cn.jpush.reactnativejpush;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;


import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.jpush.android.api.BasicPushNotificationBuilder;
import cn.jpush.android.api.CustomPushNotificationBuilder;
import cn.jpush.android.api.JPushInterface;
import cn.jpush.android.api.JPushMessage;
import cn.jpush.android.data.JPushLocalNotification;
import cn.jpush.android.service.JPushMessageReceiver;

import static android.content.Context.MODE_PRIVATE;

public class JPushModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private static String TAG = "JPushModule";
    private Context mContext;
    private static String mEvent;
    private static String mRidEvent;
    private static String mConnectEvent;
    private static Bundle mCachedBundle;
    private static Bundle mRidBundle;
    private static Bundle mConnectCachedBundle;
    private static ReactApplicationContext mRAC;

    private final static String RECEIVE_NOTIFICATION = "receiveNotification";
    private final static String RECEIVE_CUSTOM_MESSAGE = "receivePushMsg";
    private final static String OPEN_NOTIFICATION = "openNotification";
    private final static String RECEIVE_REGISTRATION_ID = "getRegistrationId";
    private final static String CONNECTION_CHANGE = "connectionChange";

    private static SparseArray<Callback> sCacheMap;
    private static Callback mGetRidCallback;

    public JPushModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public boolean canOverrideExistingModule() {
        return true;
    }

    @Override
    public String getName() {
        return "JPushModule";
    }

    @Override
    public void initialize() {
        super.initialize();
        sCacheMap = new SparseArray<>();
    }

    @Override
    public void onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy();
        Logger.i(TAG, "onCatalystInstanceDestroy");
        mCachedBundle = null;
        mRidBundle = null;
        mConnectCachedBundle = null;
        if (null != sCacheMap) {
            sCacheMap.clear();
        }
        mEvent = null;
        mRidEvent = null;
        mConnectEvent = null;
        mGetRidCallback = null;
        mRAC = null;
    }

    @ReactMethod
    public void initPush() {
        mContext = getReactApplicationContext();
        JPushInterface.init(getReactApplicationContext());
        Logger.toast(mContext, "Init push success");
        Logger.i(TAG, "init Success!");
    }

    @ReactMethod
    public void getInfo(Callback successCallback) {
        WritableMap map = Arguments.createMap();
        String appKey = "AppKey:" + ExampleUtil.getAppKey(getReactApplicationContext());
        map.putString("myAppKey", appKey);
        String imei = "IMEI: " + ExampleUtil.getImei(getReactApplicationContext(), "");
        map.putString("myImei", imei);
        String packageName = "PackageName: " + getReactApplicationContext().getPackageName();
        map.putString("myPackageName", packageName);
        String deviceId = "DeviceId: " + ExampleUtil.getDeviceId(getReactApplicationContext());
        map.putString("myDeviceId", deviceId);
        String version = "Version: " + ExampleUtil.GetVersion(getReactApplicationContext());
        map.putString("myVersion", version);
        successCallback.invoke(map);
    }


    @ReactMethod
    public void stopPush() {
        mContext = getReactApplicationContext();
        JPushInterface.stopPush(getReactApplicationContext());
        Logger.i(TAG, "Stop push");
        Logger.toast(mContext, "Stop push success");
    }

    @ReactMethod
    public void hasPermission(Callback callback) {
        callback.invoke(hasPermission("OP_POST_NOTIFICATION"));
    }



    @ReactMethod
    public void resumePush() {
        mContext = getReactApplicationContext();
        JPushInterface.resumePush(getReactApplicationContext());
        Logger.i(TAG, "Resume push");
        Logger.toast(mContext, "Resume push success");
    }

    @ReactMethod
    public void crashLogOFF() {
        JPushInterface.stopCrashHandler(getReactApplicationContext());
    }

    @ReactMethod
    public void crashLogON() {
        JPushInterface.initCrashHandler(getReactApplicationContext());
    }

    @ReactMethod
    public void notifyJSDidLoad(Callback callback) {
        // send cached event
        if (getReactApplicationContext().hasActiveCatalystInstance()) {
            mRAC = getReactApplicationContext();
            sendEvent();
            callback.invoke(0);
        }
    }

    private static void sendEvent() {
        if (mEvent != null) {
            Logger.i(TAG, "Sending event : " + mEvent);
            switch (mEvent) {
                case RECEIVE_CUSTOM_MESSAGE:
                    WritableMap map = Arguments.createMap();
                    map.putInt("id", mCachedBundle.getInt(JPushInterface.EXTRA_NOTIFICATION_ID));
                    map.putString("message", mCachedBundle.getString(JPushInterface.EXTRA_MESSAGE));
                    map.putString("content", mCachedBundle.getString(JPushInterface.EXTRA_MESSAGE));
                    map.putString("content_type", mCachedBundle.getString(JPushInterface.EXTRA_CONTENT_TYPE));
                    map.putString("title", mCachedBundle.getString(JPushInterface.EXTRA_TITLE));
                    map.putString("extras", mCachedBundle.getString(JPushInterface.EXTRA_EXTRA));
                    mRAC.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit(mEvent, map);
                    break;
                case RECEIVE_NOTIFICATION:
                case OPEN_NOTIFICATION:
                    map = Arguments.createMap();
                    map.putInt("id", mCachedBundle.getInt(JPushInterface.EXTRA_NOTIFICATION_ID));
                    map.putString("alertContent", mCachedBundle.getString(JPushInterface.EXTRA_ALERT));
                    map.putString("extras", mCachedBundle.getString(JPushInterface.EXTRA_EXTRA));
                    mRAC.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit(mEvent, map);
                    break;
            }

            mEvent = null;
            mCachedBundle = null;
        }

        if (mRidEvent != null) {
            Logger.i(TAG, "Sending ridevent : " + mRidEvent);
            if (mGetRidCallback != null) {
                mGetRidCallback.invoke(mRidBundle.getString(JPushInterface.EXTRA_REGISTRATION_ID));
                mGetRidCallback = null;
            }
            mRAC.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(mRidEvent, mRidBundle.getString(JPushInterface.EXTRA_REGISTRATION_ID));
            mRidEvent = null;
            mRidBundle = null;
        }

        if (mConnectEvent != null) {
            Logger.i(TAG, "Sending connectevent : " + mConnectEvent);
            if (mConnectCachedBundle != null) {
                mRAC.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(mConnectEvent,
                        mConnectCachedBundle.getBoolean(JPushInterface.EXTRA_CONNECTION_CHANGE, false));
            }
            mConnectEvent = null;
            mConnectCachedBundle = null;
        }
    }

    /**
     * JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Set tags
     * @param tags tags array
     * @param callback callback
     */
    @ReactMethod
    public void setTags(final ReadableArray tags, final Callback callback) {
        int sequence = getSequence();
        Logger.i(TAG, "sequence: " + sequence);
        sCacheMap.put(sequence, callback);
        Logger.i(TAG, "tag: " + tags.toString());
        Set<String> tagSet = getSet(tags);
        JPushInterface.setTags(getReactApplicationContext(), sequence, tagSet);
    }

    private int getSequence() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
        String date = sdf.format(new Date());
        return Integer.valueOf(date);
    }
    /**
     * JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * @param tags tags to be added
     * @param callback callback
     */
    @ReactMethod
    public void addTags(ReadableArray tags, Callback callback) {
        int sequence = getSequence();
        Logger.i(TAG, "tags to be added: " + tags.toString() + " sequence: " + sequence);
        sCacheMap.put(sequence, callback);
        Set<String> tagSet = getSet(tags);
        JPushInterface.addTags(getReactApplicationContext(), sequence, tagSet);
    }

    /**
     * JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * @param tags tags to be deleted
     * @param callback callback
     */
    @ReactMethod
    public void deleteTags(ReadableArray tags, Callback callback) {
        int sequence = getSequence();
        Logger.i(TAG, "tags to be deleted: " + tags.toString() + " sequence: " + sequence);
        sCacheMap.put(sequence, callback);
        Set<String> tagSet = getSet(tags);
        JPushInterface.deleteTags(getReactApplicationContext(), sequence, tagSet);
    }

    /**
     *  JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Clean all tags
     * @param callback callback
     */
    @ReactMethod
    public void cleanTags(Callback callback) {
        int sequence = getSequence();
        sCacheMap.put(sequence, callback);
        Logger.i(TAG, "Will clean all tags, sequence: " + sequence);
        JPushInterface.cleanTags(getReactApplicationContext(), sequence);
    }

    /**
     *  JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Get all tags
     * @param callback callback
     */
    @ReactMethod
    public void getAllTags(Callback callback) {
        int sequence = getSequence();
        sCacheMap.put(sequence, callback);
        Logger.i(TAG, "Get all tags, sequence: " + sequence);
        JPushInterface.getAllTags(getReactApplicationContext(), sequence);
    }

    private Set<String> getSet(ReadableArray strArray) {
        Set<String> tagSet = new LinkedHashSet<>();
        for (int i = 0; i < strArray.size(); i++) {
            if (!ExampleUtil.isValidTagAndAlias(strArray.getString(i))) {
                Logger.toast(getReactApplicationContext(), "Invalid tag !");
            }
            tagSet.add(strArray.getString(i));
        }
        return tagSet;
    }

    /**
     *  JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Check tag bind state
     * @param tag Tag to be checked
     * @param callback callback
     */
    @ReactMethod
    public void checkTagBindState(String tag, Callback callback) {
        int sequence = getSequence();
        sCacheMap.put(sequence, callback);
        Logger.i(TAG, "Checking tag bind state, tag: " + tag + " sequence: " + sequence);
        JPushInterface.checkTagBindState(getReactApplicationContext(), sequence, tag);
    }

    /**
     *  JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Set alias
     * @param alias alias to be set
     */
    @ReactMethod
    public void setAlias(String alias, Callback callback) {
        int sequence = getSequence();
        Logger.i(TAG, "Set alias, sequence: " + sequence);
        sCacheMap.put(sequence, callback);
        JPushInterface.setAlias(getReactApplicationContext(), sequence, alias);
    }

    /**
     *  JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Delete alias
     * @param callback callback
     */
    @ReactMethod
    public void deleteAlias(Callback callback) {
        int sequence = getSequence();
        Logger.i(TAG,"Delete alias, sequence: " + sequence);
        sCacheMap.put(sequence, callback);
        JPushInterface.deleteAlias(getReactApplicationContext(), sequence);
    }

    /**
     *  JPush v3.0.7 Add this API
     * See document https://docs.jiguang.cn/jpush/client/Android/android_api/#aliastag for detail
     * Get alias
     * @param callback callback
     */
    @ReactMethod
    public void getAlias(Callback callback) {
        int sequence = getSequence();
        Logger.i(TAG,"Get alias, sequence: " + sequence);
        sCacheMap.put(sequence, callback);
        JPushInterface.getAlias(getReactApplicationContext(), sequence);
    }


    /**
     * 设置通知提示方式 - 基础属性
     */
    @ReactMethod
    public void setStyleBasic() {
        mContext = getCurrentActivity();
        if (mContext != null) {
            BasicPushNotificationBuilder builder = new BasicPushNotificationBuilder(mContext);
            builder.statusBarDrawable = IdHelper.getDrawable(mContext, "ic_launcher");
            builder.notificationFlags = Notification.FLAG_AUTO_CANCEL;  //设置为点击后自动消失
            builder.notificationDefaults = Notification.DEFAULT_SOUND;  //设置为铃声（ Notification.DEFAULT_SOUND）或者震动（ Notification.DEFAULT_VIBRATE）
            JPushInterface.setPushNotificationBuilder(1, builder);
            Logger.toast(mContext, "Basic Builder - 1");
        } else {
            Logger.d(TAG, "Current activity is null, discard event");
        }
    }

    /**
     * 设置通知栏样式 - 定义通知栏Layout
     */
    @ReactMethod
    public void setStyleCustom() {
        mContext = getCurrentActivity();
        CustomPushNotificationBuilder builder = new CustomPushNotificationBuilder(mContext
                , IdHelper.getLayout(mContext, "customer_notification_layout"),
                IdHelper.getViewID(mContext, "icon"), IdHelper.getViewID(mContext, "title"),
                IdHelper.getViewID(mContext, "text"));
        builder.layoutIconDrawable = IdHelper.getDrawable(mContext, "ic_launcher");
        builder.developerArg0 = "developerArg2";
        JPushInterface.setPushNotificationBuilder(2, builder);
        Logger.toast(mContext, "Custom Builder - 2");
    }

    /**
     * Get registration id, different from JPushModule.addGetRegistrationIdListener, this
     * method has no calling limits.
     *
     * @param callback callback with registrationId
     */
    @ReactMethod
    public void getRegistrationID(Callback callback) {
        try {
            String id = JPushInterface.getRegistrationID(getReactApplicationContext());
            if (id != null) {
                callback.invoke(id);
            } else {
                mGetRidCallback = callback;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void getConnectionState(Callback callback) {
        callback.invoke(JPushInterface.getConnectionState(getReactApplicationContext()));
    }

    /**
     * Clear all notifications, suggest invoke this method while exiting app.
     */
    @ReactMethod
    public void clearAllNotifications() {
        JPushInterface.clearAllNotifications(getReactApplicationContext());
    }

    /**
     * Clear specified notification
     *
     * @param id the notification id
     */
    @ReactMethod
    public void clearNotificationById(int id) {
        try {
            mContext = getReactApplicationContext();
            JPushInterface.clearNotificationById(mContext, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void setLatestNotificationNumber(int number) {
        try {
            mContext = getReactApplicationContext();
            JPushInterface.setLatestNotificationNumber(mContext, number);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void setPushTime(ReadableMap map) {
        try {
            mContext = getReactApplicationContext();
            ReadableArray array = map.getArray("days");
            Set<Integer> days = new HashSet<Integer>();
            for (int i=0; i < array.size(); i++) {
                days.add(array.getInt(i));
            }
            int startHour = map.getInt("startHour");
            int endHour = map.getInt("endHour");
            JPushInterface.setPushTime(mContext, days, startHour, endHour);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Set silent push time
     * @param map must includes startTime and endTime property
     */
    @ReactMethod
    public void setSilenceTime(ReadableMap map) {
        try {
            mContext = getReactApplicationContext();
            String starTime = map.getString("startTime");
            String endTime = map.getString("endTime");
            String[] sTime = starTime.split(":");
            String[] eTime = endTime.split(":");
            JPushInterface.setSilenceTime(mContext, Integer.valueOf(sTime[0]), Integer.valueOf(sTime[1]),
                    Integer.valueOf(eTime[0]), Integer.valueOf(eTime[1]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @ReactMethod
    public void setGeofenceInterval(double interval) {
        try {
            mContext = getReactApplicationContext();
            JPushInterface.setGeofenceInterval(mContext, (long)interval);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @ReactMethod
    public void setMaxGeofenceNumber(int maxNumber) {
        try {
            mContext = getReactApplicationContext();
            JPushInterface.setMaxGeofenceNumber(mContext, maxNumber);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void sendLocalNotification(ReadableMap map) {
        try {
            JPushLocalNotification ln = new JPushLocalNotification();
            ln.setBuilderId(map.getInt("buildId"));
            ln.setNotificationId(map.getInt("id"));
            ln.setTitle(map.getString("title"));
            ln.setContent(map.getString("content"));
            ReadableMap extra = map.getMap("extra");
            JSONObject json = new JSONObject();
            ReadableMapKeySetIterator iterator = extra.keySetIterator();
            while (iterator.hasNextKey()) {
                String key = iterator.nextKey();
                json.put(key, extra.getString(key));
            }
            ln.setExtras(json.toString());
            if (map.hasKey("fireTime")) {
                long date = (long) map.getDouble("fireTime");
                ln.setBroadcastTime(date);
            }
            JPushInterface.addLocalNotification(getReactApplicationContext(), ln);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void removeLocalNotification(int id) {
        try {
            Logger.d(TAG, "removeLocalNotification："+id);
            JPushInterface.removeLocalNotification(getReactApplicationContext(), id);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @ReactMethod
    public void clearLocalNotifications() {
        try {
            Logger.d(TAG, "clearLocalNotifications");
            JPushInterface.clearLocalNotifications(getReactApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onHostResume() {
        Logger.d(TAG, "onHostResume");
    }

    @Override
    public void onHostPause() {
        Logger.d(TAG, "onHostPause");
    }

    @Override
    public void onHostDestroy() {
        Logger.d(TAG, "onHostDestroy");
        mRAC = null;
    }

    /**
     * 接收自定义消息,通知,通知点击事件等事件的广播
     * 文档链接:http://docs.jiguang.cn/client/android_api/
     */
    public static class JPushReceiver extends BroadcastReceiver {

        public JPushReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent data) {

            if (JPushInterface.ACTION_MESSAGE_RECEIVED.equals(data.getAction())) {
                mCachedBundle = data.getExtras();
                try {
                    String message = data.getStringExtra(JPushInterface.EXTRA_MESSAGE);
                    Logger.i(TAG, "收到自定义消息: " + message);
                    mEvent = RECEIVE_CUSTOM_MESSAGE;
                    if (mRAC != null) {
                        sendEvent();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (JPushInterface.ACTION_NOTIFICATION_RECEIVED.equals(data.getAction())) {
                mCachedBundle = data.getExtras();
                try {
                    // 通知内容
                    String alertContent = mCachedBundle.getString(JPushInterface.EXTRA_ALERT);
                    // extra 字段的 json 字符串
                    String extras = mCachedBundle.getString(JPushInterface.EXTRA_EXTRA);
                    Logger.i(TAG, "收到推送下来的通知: " + alertContent);
                    Logger.i(TAG, "extras: " + extras);
                    mEvent = RECEIVE_NOTIFICATION;
                    if (mRAC != null) {
                        sendEvent();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (JPushInterface.ACTION_NOTIFICATION_OPENED.equals(data.getAction())) {
                mCachedBundle = data.getExtras();
                try {
                    Logger.d(TAG, "用户点击打开了通知");
                    // 通知内容
                    String alertContent = mCachedBundle.getString(JPushInterface.EXTRA_ALERT);
                    // extra 字段的 json 字符串
                    String extras = mCachedBundle.getString(JPushInterface.EXTRA_EXTRA);
                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtras(mCachedBundle);
                    context.startActivity(intent);
                    mEvent = OPEN_NOTIFICATION;
                    SharedPreferences sp = context.getSharedPreferences("setting",MODE_PRIVATE);
                    SharedPreferences.Editor editor = sp.edit();
                    editor.putString("extras",extras);
                    editor.commit();
                    if (mRAC != null) {
                        sendEvent();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Logger.i(TAG, "Shouldn't access here");
                }
                // 应用注册完成后会发送广播，在 JS 中 JPushModule.addGetRegistrationIdListener 接口可以第一时间得到 registrationId
                // After JPush finished registering, will send this broadcast, use JPushModule.addGetRegistrationIdListener
                // to get registrationId in the first instance.
            } else if (JPushInterface.ACTION_REGISTRATION_ID.equals(data.getAction())) {
                mRidBundle = data.getExtras();
                try {
                    mRidEvent = RECEIVE_REGISTRATION_ID;
                    if (mRAC != null) {
                        sendEvent();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (JPushInterface.ACTION_CONNECTION_CHANGE.equals(data.getAction())) {
                mConnectCachedBundle = data.getExtras();
                try {
                    mConnectEvent = CONNECTION_CHANGE;
                    if (mRAC != null) {
                        sendEvent();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static class MyJPushMessageReceiver extends JPushMessageReceiver {

        @Override
        public void onTagOperatorResult(Context context,JPushMessage jPushMessage) {
            String log = "action - onTagOperatorResult, sequence:" + jPushMessage.getSequence()
                    + ", tags: " + jPushMessage.getTags();
            Logger.i(TAG, log);
            Logger.toast(context, log);
            Logger.i(TAG,"tags size:"+jPushMessage.getTags().size());
            Callback callback = sCacheMap.get(jPushMessage.getSequence());
            if (null != callback) {
                WritableMap map = Arguments.createMap();
                WritableArray array = Arguments.createArray();
                Set<String> tags = jPushMessage.getTags();
                for (String str : tags) {
                    array.pushString(str);
                }
                map.putArray("tags", array);
                map.putInt("errorCode", jPushMessage.getErrorCode());
                callback.invoke(map);
                sCacheMap.remove(jPushMessage.getSequence());
            } else {
                Logger.i(TAG, "Unexpected error, null callback!");
            }
            super.onTagOperatorResult(context, jPushMessage);
        }
        @Override
        public void onCheckTagOperatorResult(Context context,JPushMessage jPushMessage){
            String log = "action - onCheckTagOperatorResult, sequence:" + jPushMessage.getSequence()
                    + ", checktag: " + jPushMessage.getCheckTag();
            Logger.i(TAG, log);
            Logger.toast(context, log);
            Callback callback = sCacheMap.get(jPushMessage.getSequence());
            if (null != callback) {
                WritableMap map = Arguments.createMap();
                map.putInt("errorCode", jPushMessage.getErrorCode());
                map.putString("tag", jPushMessage.getCheckTag());
                map.putBoolean("bindState", jPushMessage.getTagCheckStateResult());
                callback.invoke(map);
                sCacheMap.remove(jPushMessage.getSequence());
            } else {
                Logger.i(TAG, "Unexpected error, null callback!");
            }
            super.onCheckTagOperatorResult(context, jPushMessage);
        }
        @Override
        public void onAliasOperatorResult(Context context, JPushMessage jPushMessage) {
            String log = "action - onAliasOperatorResult, sequence:" + jPushMessage.getSequence()
                    + ", alias: " + jPushMessage.getAlias();
            Logger.i(TAG, log);
            Logger.toast(context, log);
            Callback callback = sCacheMap.get(jPushMessage.getSequence());
            if (null != callback) {
                WritableMap map = Arguments.createMap();
                map.putString("alias", jPushMessage.getAlias());
                map.putInt("errorCode", jPushMessage.getErrorCode());
                callback.invoke(map);
                sCacheMap.remove(jPushMessage.getSequence());
            } else {
                Logger.i(TAG, "Unexpected error, null callback!");
            }
            super.onAliasOperatorResult(context, jPushMessage);
        }
    }

    private static boolean isApplicationRunningBackground(final Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            ComponentName topActivity = tasks.get(0).topActivity;
            if (!topActivity.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    @ReactMethod
    public void jumpToPushActivity(String activityName) {
        Logger.d(TAG, "Jumping to " + activityName);
        try {
            Intent intent = new Intent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(mRAC, mRAC.getPackageName() + "." + activityName);
            mRAC.startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @ReactMethod
    public void jumpToPushActivityWithParams(String activityName, ReadableMap map) {
        Logger.d(TAG, "Jumping to " + activityName);
        try {
            Intent intent = new Intent();
            if (null != map) {
                while (map.keySetIterator().hasNextKey()) {
                    String key = map.keySetIterator().nextKey();
                    String value = map.getString(key);
                    intent.putExtra(key, value);
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClassName(mRAC, mRAC.getPackageName() + "." + activityName);
            mRAC.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @ReactMethod
    public void finishActivity() {
        try {
            Activity activity = getCurrentActivity();
            activity.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean hasPermission(String appOpsServiceId) {

        Context context = getReactApplicationContext();
        if (Build.VERSION.SDK_INT >= 24) {
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            return mNotificationManager.areNotificationsEnabled();
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            AppOpsManager mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            ApplicationInfo appInfo = context.getApplicationInfo();

            String pkg = context.getPackageName();
            int uid = appInfo.uid;
            Class appOpsClazz;

            try {
                appOpsClazz = Class.forName(AppOpsManager.class.getName());
                Method checkOpNoThrowMethod = appOpsClazz.getMethod("checkOpNoThrow", Integer.TYPE, Integer.TYPE,
                        String.class);
                Field opValue = appOpsClazz.getDeclaredField(appOpsServiceId);
                int value = opValue.getInt(Integer.class);
                Object result = checkOpNoThrowMethod.invoke(mAppOps, value, uid, pkg);
                return Integer.parseInt(result.toString()) == AppOpsManager.MODE_ALLOWED;
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return false;
    }
}
