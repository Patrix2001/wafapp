package com.example.wafnewapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.aliyun.TigerTally.TigerTallyAPI;
import com.aliyun.TigerTally.captcha.api.TTCaptcha;


import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "TigerTally-Demo";

    private final static String APP_HOST = "www.demopoc.top";
    private final static String APP_URL  = "http://www.demopoc.top/test.php";
    private final static String APP_KEY  = "grR";

    private final static OkHttpClient okHttpClient = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        doTest();
    }

    private void doTest() {
        Log.d(TAG, "captcha flow");
        new Thread(() -> {
            // 初始化 全量采集
            int ret = TigerTallyAPI.init(this, APP_KEY, TigerTallyAPI.CollectType.DEFAULT);
            // 不采集隐私字段
            // int ret = TigerTallyAPI.init(this, APP_KEY, TigerTallyAPI.CollectType.NOT_GRANTED);
            Log.d(TAG, "tiger tally init: " + ret);

            // 不能立即同步调用
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            //  签名
            String data = "hello world";
            String whash = null, wtoken = null;
            // 自定义加签
            whash = TigerTallyAPI.vmpHash(TigerTallyAPI.RequestType.GET, data.getBytes());
//            wtoken = TigerTallyAPI.vmpSign(1, data.getBytes());
//            Log.d(TAG, "tiger tally vmp: " + whash + ", " + wtoken);

            // 正常加签
             wtoken = TigerTallyAPI.vmpSign(1, whash.getBytes());
             Log.d(TAG, "tiger tally vmp: " + wtoken);

            // 请求接口
            doPost(APP_URL, APP_HOST, whash, wtoken, data, (code, cookie, body) -> {
                // 判断是否需要显示滑块
                int recheck = TigerTallyAPI.cptCheck(cookie, body);
                Log.d(TAG, "captcha check result: " + recheck);
                //NOTE: Change when available
                if (recheck == 0) return;
                this.runOnUiThread(this::doShow);
            });
        }).start();
    }

    // 显示滑块
    public void doShow() {
        Log.d(TAG, "captcha show");

        TTCaptcha.TTOption option = new TTCaptcha.TTOption();
        // option.customUri = "file:///android_asset/ali-tt-captcha-demo.html";
        // option.traceId    = "4534534534adf433534534543";
        option.titleText  = "测试 Title";
        option.descText   = "测试 Description";
        option.language   = "cn";
        option.cancelable = true;
        option.hideError  = true;
        option.slideColor = "#007FFF";

        TTCaptcha captcha = TigerTallyAPI.cptCreate(this, option, new TTCaptcha.TTListener() {
            @Override
            public void success(TTCaptcha captcha, String data) {
                Log.d(TAG, "captcha check success:" + data);
                captcha.dismiss();
            }

            @Override
            public void failed(TTCaptcha captcha, String code) {
                Log.d(TAG, "captcha check failed:" + code);
            }

            @Override
            public void error(TTCaptcha captcha, int code, String message) {
                Log.d(TAG, "captcha check error, code: " + code + ", message: " + message);
            }
        });

        captcha.show();
    }

    // 发送请求
    public static void doPost(String url, String host, String whash, String wtoken, String body, Callback callback) {
        Log.d(TAG, "start request post");

        int responseCode = 0;
        String responseBody = "";
        StringBuilder responseCookie = new StringBuilder();
        try {
            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .addHeader("wToken", wtoken)
                    .addHeader("Host",   host)
                    .post(RequestBody.create(MediaType.parse("text/x-markdown"), body.getBytes()));

            if (whash != null) {
                builder.addHeader("ali_sign_whash", whash);
            }
            Response response = okHttpClient.newCall(builder.build()).execute();

            responseCode = response.code();
            responseBody = response.body() == null ? "" : response.body().string();
            for (String item : response.headers("Set-Cookie")) {
                responseCookie.append(item).append(";");
            }

            Log.d(TAG, "response code:" + responseCode);
            Log.d(TAG, "response cookie:" + responseCookie);
            Log.d(TAG, "response body:" + (responseBody.length() > 100 ? responseBody.substring(0, 100) : ""));

            if (response.isSuccessful()) {
                Log.d(TAG, "success: " + response.code() + ", " + response.message());
            } else {
                Log.e(TAG, "failed: " + response.code() + ", " + response.message());
            }

            response.close();
        } catch (Exception e) {
            e.printStackTrace();
            responseCode = -1;
            responseBody = e.toString();
        } finally {
            if (callback != null) {
                callback.onResponse(responseCode, responseCookie.toString(), responseBody);
            }
        }
    }

    public interface Callback {
        void onResponse(int code, String cookie, String body);
    }
}