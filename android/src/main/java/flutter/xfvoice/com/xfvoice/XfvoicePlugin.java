package flutter.xfvoice.com.xfvoice;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.Setting;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * XfvoicePlugin
 */
public class XfvoicePlugin implements MethodCallHandler {
    private static String TAG = XfvoicePlugin.class.getSimpleName();
    private MethodChannel channel;
    private Context applicationContext;
    //private Activity activity;
    private SpeechRecognizer recognizer;
    private String filePath;

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "xfvoice");
        channel.setMethodCallHandler(new XfvoicePlugin(channel, registrar.activity()));
    }

    private XfvoicePlugin(MethodChannel channel, Activity activity) {
        this.channel = channel;
        this.applicationContext = activity.getApplicationContext();
        //this.activity = activity;
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("init")) {
            init(call, result);
        } else if (call.method.equals("setParameter")) {
            setParameter(call, result);
        } else if (call.method.equals("start")) {
            start(call, result);
        } else if (call.method.equals("stop")) {
            stop(call, result);
        } else if (call.method.equals("cancel")) {
            cancel(call, result);
        } else if (call.method.equals("dispose")) {
            dispose(call, result);
        } else {
            Log.e(TAG, "未找到方法，请检查方法名是否正确");
        }
    }

    private RecognizerListener mRecognizerListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            Log.d(TAG, "onBeginOfSpeech()");

            channel.invokeMethod("onBeginOfSpeech", null);
        }

        @Override
        public void onError(SpeechError error) {
            Log.d(TAG, "onError():" + error.getPlainDescription(true));

            HashMap errorInfo = new HashMap();
            errorInfo.put("code", error.getErrorCode());
            errorInfo.put("desc", error.getErrorDescription());
            ArrayList<Object> arguments = new ArrayList<>();
            arguments.add(errorInfo);
            arguments.add(filePath);
            channel.invokeMethod("onCompleted", arguments);
        }

        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech()");

            channel.invokeMethod("onEndOfSpeech", null);
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            Log.d(TAG, "onResult():" + results.getResultString());

            ArrayList<Object> arguments = new ArrayList<>();
            arguments.add(results.getResultString());
            arguments.add(isLast);
            channel.invokeMethod("onResults", arguments);

            if (isLast) {
                ArrayList<Object> args = new ArrayList<>();
                arguments.add(null);
                arguments.add(filePath);
                channel.invokeMethod("onCompleted", args);
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            channel.invokeMethod("onVolumeChanged", volume);
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }
    };

    /**
     * 初始化
     */
    private void init(MethodCall call, Result result) {
        //getPermisson();

        SpeechUtility.createUtility(applicationContext, SpeechConstant.APPID + "=" + call.arguments);
        Setting.setLocationEnable(false);// 关闭定位
        recognizer = SpeechRecognizer.createRecognizer(applicationContext, new InitListener() {
            @Override
            public void onInit(int code) {
                if (code != ErrorCode.SUCCESS) {
                    Log.e(TAG, "创建recognizer失败，错误码：" + code);
                }
            }
        });

        result.success(null);
    }

    /**
     * 设置参数
     */
    private void setParameter(MethodCall call, Result result) {
        if (recognizer == null) {
            Log.e(TAG, "recongnizer为null");
        } else {
            try {
                Map<String, String> map = (Map<String, String>) call.arguments;
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    if (entry.getKey().equals(SpeechConstant.ASR_AUDIO_PATH)) {
                        filePath = Environment.getExternalStorageDirectory() + "/msc/" + entry.getValue();
                        recognizer.setParameter(SpeechConstant.ASR_AUDIO_PATH, filePath);
                    } else {
                        recognizer.setParameter(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        result.success(null);
    }

    /**
     * 开始听写
     */
    private void start(MethodCall call, Result result) {
        if (recognizer == null) {
            Log.e(TAG, "recongnizer为null");
        } else {
            // 如何判断一次听写结束：OnResult isLast=true 或者 onError
            int code = recognizer.startListening(mRecognizerListener);
            if (code != ErrorCode.SUCCESS) {
                Log.e(TAG, "开始听写失败,错误码：" + code);
            }
        }

        result.success(null);
    }

    /**
     * 停止听写
     */
    private void stop(MethodCall call, Result result) {
        if (recognizer == null) {
            Log.e(TAG, "recongnizer为null");
        } else {
            recognizer.stopListening();
        }

        result.success(null);
    }

    /**
     * 取消当前会话
     */
    private void cancel(MethodCall call, Result result) {
        if (recognizer == null) {
            Log.e(TAG, "recongnizer为null");
        } else {
            recognizer.cancel();
        }

        result.success(null);
    }

    /**
     * 释放资源
     */
    private void dispose(MethodCall call, Result result) {
        if (recognizer == null) {
            Log.e(TAG, "recongnizer为null");
        } else {
            recognizer.cancel();//取消当前会话
            recognizer.destroy();//销毁recognizer单例
            recognizer = null;
        }

        result.success(null);
    }

//-----------------------------------------------------------

//    String[] permissions = {
//            Manifest.permission.INTERNET,
//            Manifest.permission.RECORD_AUDIO,
//            Manifest.permission.ACCESS_NETWORK_STATE,
//            Manifest.permission.ACCESS_WIFI_STATE,
//            Manifest.permission.CHANGE_NETWORK_STATE,
//            Manifest.permission.READ_PHONE_STATE,
//            //Manifest.permission.READ_CONTACTS,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.READ_EXTERNAL_STORAGE,
//            //Manifest.permission.ACCESS_FINE_LOCATION,
//    };
//
//    private void getPermisson() {
//        ActivityCompat.requestPermissions(activity, permissions, 0);
//    }
}
