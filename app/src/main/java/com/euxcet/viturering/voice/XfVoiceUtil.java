package com.euxcet.viturering.voice;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.common.collect.Lists;
import com.euxcet.viturering.voice.VoiceConstants;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class XfVoiceUtil {

    public static final int STATUS_INIT_SUCCESS = 2;
    public static final int STATUS_INIT_FAIL = -1;

    private static final String TAG = "XfVoiceUtil";

    public static final String KEY_VOICE_ERROR = "XFVOICE_ERROR_KEY";

    private static XfVoiceUtil xfVoiceUtil = null;

    private static boolean hasLexicon = false;

    private Context mContext;

    // 用HashMap存储听写结果
    private HashMap<String, List<String>> mIatResults = new LinkedHashMap<>();

    /**
     * 无UI的语音识别模块参数
     */
    private SpeechRecognizer mIat = null;

    private boolean interruptVoice = false;

    private List<String> resultList;

    /**
     * 带UI的语音识别模块参数
     */
    private RecognizerDialog mIatDialog = null;

    private ResultListener mResultListener = null;

    private RecognizeListener mRecognizeListener;

    private static boolean init_writeAudio = false;
    private static boolean init_hasPunctuation = false;

    public static XfVoiceUtil getInstance() {
        return xfVoiceUtil;
    }

    private XfVoiceUtil(Context context, boolean useWriteAudio, boolean hasPunctuation) {
        mContext = context;

        // 请勿在“=”与appid之间添加任何空字符或者转义符
        SpeechUtility.createUtility(mContext, SpeechConstant.APPID + "=" + VoiceConstants.XUNFEI_APP_ID);
        initIat(useWriteAudio, hasPunctuation);
    }

    private XfVoiceUtil(Context context, int useType, boolean useWriteAudio, boolean hasPunctuation) {
        mContext = context;
        // 请勿在“=”与appid之间添加任何空字符或者转义符
        SpeechUtility.createUtility(mContext, SpeechConstant.APPID + "=" + VoiceConstants.XUNFEI_APP_ID);
        if (useType == 1) {
            initRingIat(true);
        } else {
            initIat(useWriteAudio, hasPunctuation);
        }
    }

    public static void init(Context context, boolean useWriteAudio) {
        init(context, 0,  useWriteAudio, true);
    }
    public static void init(Context context, int userType, boolean useWriteAudio, boolean hasPunctuation) {
        if (xfVoiceUtil == null && context != null) {
            synchronized (XfVoiceUtil.class) {
                if (xfVoiceUtil == null && context != null) {
                    xfVoiceUtil = new XfVoiceUtil(context, userType, useWriteAudio, hasPunctuation);
                }
            }
        }
    }

    public static void reInit(Context context, int useType, boolean useWriteAudio, boolean hasPunctuation) {
        if (useWriteAudio != init_writeAudio || hasPunctuation != init_hasPunctuation) {
            destroySelf();
            init(context, useType, useWriteAudio, hasPunctuation);
        }
    }



    public static void init(Context context) {
        init(context, 0,  false, true);
    }

    /**
     * 初始化无UI的语音识别模块
     */
    public void initNoUiVoiceUtil(ResultListener resultListener) {
        mResultListener = resultListener;
        resultList = new ArrayList<>();
    }

    public void setVoiceRecognize(RecognizeListener listener) {
        mRecognizeListener = listener;
    }

    public boolean isInit() {
        return mIat != null;
    }

    public void initIat() {
        initIat(false, true);
    }

    public void initIat(boolean useWriteAudio, boolean hasPunctuation) {
        mIat = SpeechRecognizer.getRecognizer();
//        if (mIat != null) {
//        //    return;
//        }
        //初始化识别无UI识别对 象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        //mIat = SpeechRecognizer.createRecognizer(IatDemo.this, mInitListener);
        if (mIat == null) {
            mIat = SpeechRecognizer.createRecognizer(mContext, mInitListener);
        }

        if (mIat == null) {
            // 初始化失败
            return;
        }

        //设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIat.setParameter(SpeechConstant.SUBJECT, null);
        //设置返回结果格式，目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        //此处engineType为“cloud”
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        //设置语音输入语言，zh_cn为简体中文
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        //设置结果返回语言
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");  //普通话
//        mIat.setParameter(SpeechConstant.ACCENT, "gansunese");  //甘肃方言
//        mIat.setParameter(SpeechConstant.ACCENT, "cn_cantonese");  //普通话 + 广东方言
        // 设置语音前端点:静音超时时间，单位ms，即用户多长时间不说话则当做超时处理
        // 取值范围{1000～10000}
        mIat.setParameter(SpeechConstant.VAD_BOS, "10000");
        //设置语音后端点:后端点静音检测时间，单位ms，即用户停止说话多长时间内即认为不再输入，
        // 自动停止录音，范围{0~10000}
        mIat.setParameter(SpeechConstant.VAD_EOS, "10000");
        //设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, hasPunctuation ? "1" : "0");
        //设置语音录入的音量,仅支持"8000"(网络需求)和"16000"
        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
        //设置返回的候选结果，根据可能性，取值[1,5],需要在讯飞开通功能，不开通只返回一个结果
        mIat.setParameter(SpeechConstant.ASR_WBEST, "1");
        mIat.setParameter(SpeechConstant.ASR_NBEST, "1");
         mIat.setParameter("dwa", "wpgs"); // 开启动态纠正功能
        if (useWriteAudio) {
            mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
            mIat.setParameter(SpeechConstant.SAMPLE_RATE, "16000");
            mIat.setParameter(SpeechConstant.AUDIO_FORMAT_AUE, "raw");
            mIat.setParameter(SpeechConstant.ASR_PTT, hasPunctuation ? "1": "0"); //无标点符号
        }
        init_writeAudio = useWriteAudio;
        init_hasPunctuation = hasPunctuation;
    }

    public void initRingIat(boolean hasPunctuation) {
        mIat = SpeechRecognizer.getRecognizer();
//        if (mIat != null) {
//        //    return;
//        }
        //初始化识别无UI识别对 象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
        //mIat = SpeechRecognizer.createRecognizer(IatDemo.this, mInitListener);
        if (mIat == null) {
            mIat = SpeechRecognizer.createRecognizer(mContext, mInitListener);
        }

        if (mIat == null) {
            // 初始化失败
            return;
        }

        //设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
        mIat.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIat.setParameter(SpeechConstant.SUBJECT, null);
        //设置返回结果格式，目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        //此处engineType为“cloud”
        mIat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        //设置语音输入语言，zh_cn为简体中文
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        //设置结果返回语言
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");  //普通话
//        mIat.setParameter(SpeechConstant.ACCENT, "gansunese");  //甘肃方言
//        mIat.setParameter(SpeechConstant.ACCENT, "cn_cantonese");  //普通话 + 广东方言
        // 设置语音前端点:静音超时时间，单位ms，即用户多长时间不说话则当做超时处理
        // 取值范围{1000～10000}
        mIat.setParameter(SpeechConstant.VAD_BOS, "10000");
        //设置语音后端点:后端点静音检测时间，单位ms，即用户停止说话多长时间内即认为不再输入，
        // 自动停止录音，范围{0~10000}
        mIat.setParameter(SpeechConstant.VAD_EOS, "10000");
        //设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT, hasPunctuation ? "1" : "0");
        //设置语音录入的音量,仅支持"8000"(网络需求)和"16000"
        mIat.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
        //设置返回的候选结果，根据可能性，取值[1,5],需要在讯飞开通功能，不开通只返回一个结果
        mIat.setParameter(SpeechConstant.ASR_WBEST, "1");
        mIat.setParameter(SpeechConstant.ASR_NBEST, "1");
        mIat.setParameter("dwa", "wpgs"); // 开启动态纠正功能
        mIat.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT_AUE, "raw");
        init_writeAudio = true;
        init_hasPunctuation = hasPunctuation;
    }

    public void updateLexicon(String name, List<String> words) {
        if (mIat == null)
            return;

        JSONObject childJsonObject = new JSONObject();
        JSONObject rootJsonObject = new JSONObject();
        try {
            childJsonObject.put("name", name);
            childJsonObject.put("words", new JSONArray(words));
            List<JSONObject> childList = new ArrayList<>();
            childList.add(childJsonObject);
            rootJsonObject.put("userword", new JSONArray(childList));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "热词:" + rootJsonObject.toString());
        int res = mIat.updateLexicon("userword", rootJsonObject.toString(), mLexiconListener);
        if (res != ErrorCode.SUCCESS) {
            Log.e(TAG, "热词上传失败，错误码:[" + res + "]");
        } else {
            hasLexicon = true;
        }
    }

    public boolean hasLexicon() {
        return hasLexicon;
    }

    private final LexiconListener mLexiconListener = (s, speechError) -> {
        if (speechError != null) {
            Log.e(TAG, "热词错误:[" + speechError.toString() + "]");
        }
    };

    private int mInitResultCode;

    /**
     * 初始化监听器。
     */
    private final InitListener mInitListener = code -> {
        Log.d(TAG, "SpeechRecognizer init() code = " + code);
        if (code != ErrorCode.SUCCESS) {
            //showTip("初始化失败，错误码：" + code+",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
        }
        mInitResultCode = code;
    };

    private int startListenerErrorCode;

    public int getStartListenerErrorCode() {
        return startListenerErrorCode;
    }
    public int getInitResultCode() {
        return mInitResultCode;
    }

    /**
     * 无UI的语音识别模块开始监听
     *
     * @return
     */
    public boolean startListening() {
        //开始识别，并设置监听器
        if (mIat != null && (startListenerErrorCode = mIat.startListening(mRecognizerListener)) == ErrorCode.SUCCESS) {
            Log.e("TEST_VOICE", "xunfei start listening");
            mIatResults.clear();
            interruptVoice = false;
            resultList = new ArrayList<>();
            return true;
        }
        return false;
    }

    public int writeAudio(byte[] bytes) {
        if (mIat != null) {
            return mIat.writeAudio(bytes, 0, bytes.length);
        }
        return -1;
    }

    /**
     * 无UI的语音识别模块停止监听
     */
    public void stopListening() {
        if (mIat != null) {
            Log.e("TEST_VOICE", "xunfei stop listening");
            //Utility.enqueueSpeak("未识别到有效内容，语音输入已结束");
            interruptVoice = true;
            mIat.stopListening();
            //mIatResults.clear();
        }
    }

    public void cancelListening() {
        if (mIat != null) {
            interruptVoice = true;
            mIat.cancel();
            mIatResults.clear();
        }
    }

    public void clearCacheResult() {
        if (mIatResults != null) {
            mIatResults.clear();
        }
    }

    public boolean isListening() {
        return mIat != null && mIat.isListening();
    }

    private void handleRplResult(RecognizerResult results, boolean isLast) {
        String text = results.getResultString();
        StringBuilder resultTextBuilder = new StringBuilder();
        String sn = null;
        String pgs = null;
        String rg = null;
        JSONArray words = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
            pgs = resultJson.optString("pgs");
            rg = resultJson.optString("rg");
            words = resultJson.getJSONArray("ws");
            for (int i = 0; i < words.length(); i++) {
                JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                /** 转写结果词，默认使用第一个结果 */
                JSONObject obj = items.getJSONObject(0);
                resultTextBuilder.append(obj.getString("w"));
                /** 尝试默认使用最长的结果 */
                /*String longestWord = "";
                for (int j = 0; j < items.length(); j++) {
                    String curWord = items.getJSONObject(j).getString("w");
                    if (curWord.length() > longestWord.length()) {
                        longestWord = curWord;
                    }
                }
                resultTextBuilder.append(longestWord);*/


            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //如果pgs是rpl就在已有的结果中删除掉要覆盖的sn部分
        if (pgs.equals("rpl")) {
            String[] strings = rg.replace("[", "").replace("]", "").split(",");
            int begin = Integer.parseInt(strings[0]);
            int end = Integer.parseInt(strings[1]);
            for (int i = begin; i <= end; i++) {
                mIatResults.remove(i+"");
            }
        }
        mIatResults.put(sn, Lists.newArrayList(text, resultTextBuilder.toString()));
        StringBuilder resultBuffer = new StringBuilder();
        for (String key : mIatResults.keySet()) {
            List<String> values = mIatResults.get(key);
            resultBuffer.append(values != null && values.size() > 1 ? values.get(1) : "");
        }
        Log.e("TEST_VOICE", "xunfei rp result:" + resultBuffer.toString());
        Log.e("TEST_VOICE", "xunfei rp result:" + results.getResultString());
        if (mRecognizeListener != null) {
            mRecognizeListener.onRecognize(Lists.newArrayList(resultBuffer.toString()));
        }
    }

    private void handleResult(RecognizerResult results, boolean isLast) {
        String sn = "";
        /** 读取json结果中的sn字段 */
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            Log.d(TAG, resultJson.toString());
            sn = resultJson.optString("sn");
            JSONTokener tokener = new JSONTokener(results.getResultString());
            JSONObject joResult = new JSONObject(tokener);
            JSONArray words = joResult.getJSONArray("ws");
            List<String> recognizeList = new ArrayList<>();
            for (int i = 0; i < words.length(); i++) {
                JSONArray items = words.getJSONObject(i).getJSONArray("cw");
                /** 转写结果词，默认使用第一个结果 */
//                    JSONObject obj = items.getJSONObject(0);
//                    resultList.add(obj.getString("w"));
                /** 如果需要多候选结果，解析数组其他字段 */
                for (int j = 0; j < items.length(); j++) {
                    JSONObject obj = items.getJSONObject(j);
                    String word = obj.getString("w");
                    resultList.add(word);
                    recognizeList.add(word);
                }
            }
            if (mRecognizeListener != null) {
                mRecognizeListener.onRecognize(recognizeList);
            }
        } catch (JSONException e) {
            Log.e(TAG, "语音识别结果解析错误");
            e.printStackTrace();
        }
        if (isLast)
            printResult(sn);
    }

    /**
     * 听写监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            // showTip("开始说话");
            if (mRecognizeListener != null) {
                mRecognizeListener.onVoiceBegin();
            }
        }

        @Override
        public void onError(SpeechError error) {
            // Tips：
            // 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。

            //showTip(error.getPlainDescription(true));
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                String speakText = error.getPlainDescription(true);
                stopListening();
                String errorString = "语音异常";
                if (speakText.contains("网络")) {
                    errorString = "网络连接异常";
                    Log.e(TAG, errorString);
                    resultList.add(errorString);
                } else if (speakText.contains("说话")) {
                    errorString = "未听清内容";
                } else if (speakText.contains("启动录音失败")) {
                    errorString = "没有麦克风权限";
                } else {
                    errorString = "其他异常:" + speakText;
                }
                // 10118 讯飞未听清说话内容
                if (mRecognizeListener != null && error.getErrorCode() != 10118) {
                    mRecognizeListener.onError(error.getErrorCode(), errorString);
                }
                Log.e(TAG, errorString);
                resultList.add(errorString);

                printResult(KEY_VOICE_ERROR);
                Log.e("TEST_VOICE", "xunfei onerror:" + errorString);
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            //showTip("结束说话");
//            Utility.enqueueSpeak("语音输入已结束");
            if (mRecognizeListener != null) {
                mRecognizeListener.onVoiceEnd();
            }
            Log.e("TEST_VOICE", "xunfei end speech");
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
           handleRplResult(results, isLast);
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            if (mRecognizeListener != null) {
                mRecognizeListener.onVolumeChanged(volume, data);
            }
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
     * 初始化带UI的语音识别模块
     */
    public void initWithUiVoiceUtil(ResultListener resultListener) {
        mResultListener = resultListener;
        // 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
        // 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
        mIatDialog = new RecognizerDialog(mContext, mInitListener);


        //以下为dialog设置听写参数
        //mIatDialog.setParams("xxx","xxx");
        //设置语法ID和 SUBJECT 为空，以免因之前有语法调用而设置了此参数；或直接清空所有参数，具体可参考 DEMO 的示例。
        mIatDialog.setParameter(SpeechConstant.CLOUD_GRAMMAR, null);
        mIatDialog.setParameter(SpeechConstant.SUBJECT, null);
        //设置返回结果格式，目前支持json,xml以及plain 三种格式，其中plain为纯听写文本内容
        mIatDialog.setParameter(SpeechConstant.RESULT_TYPE, "json");
        //此处engineType为“cloud”
        mIatDialog.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        //设置语音输入语言，zh_cn为简体中文
        mIatDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        //设置结果返回语言
        mIatDialog.setParameter(SpeechConstant.ACCENT, "mandarin");
        // 设置语音前端点:静音超时时间，单位ms，即用户多长时间不说话则当做超时处理
        // 取值范围{1000～10000}
        mIatDialog.setParameter(SpeechConstant.VAD_BOS, "5000");//设置语音后端点:后端点静音检测时间，单位ms，即用户停止说话多长时间内即认为不再输入，
        // 自动停止录音，范围{0~10000}
        mIatDialog.setParameter(SpeechConstant.VAD_EOS, "1000");
        //设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIatDialog.setParameter(SpeechConstant.ASR_PTT, "1");

    }

    /**
     * 显示带UI的语音识别模块的对话框
     */
    public void showDialog() {
        // 开始识别并设置监听器
        mIatDialog.setListener(mRecognizerDialogListener);
        //显示听写对话框
        mIatDialog.show();
    }

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult results, boolean isLast) {

//            printResult(results);

        }

        /**
         * 识别回调错误.
         */
        public void onError(SpeechError error) {
            //showTip(error.getPlainDescription(true));

        }

    };

    /**
     * 识别语音识别的结果，将结果返回快捷方式
     *
     * @param sn 结果map中的key值
     */
    private void printResult(String sn) {

        mIatResults.put(sn, resultList);
        new Thread(() -> {
            synchronized (XfVoiceUtil.class) {
                if (mResultListener != null) {
                    mResultListener.onResult(mIatResults);
                }
                mIatResults.clear();
                resultList = new ArrayList<>();
            }
        }).start();
    }

    public HashMap<String, List<String>> getIatResults() {
        return mIatResults;
    }

    public interface ResultListener {
        void onResult(HashMap<String, List<String>> result);
    }

    public interface RecognizeListener {
        void onRecognize(List<String> words);
        default void onError(int code, String msg) {};

        default void onVoiceBegin() {};
        /**
         * 语音输入结束，当主动结束监听时不会调用此方法
         */
        default void onVoiceEnd() {};

        default void onVolumeChanged(int volume, byte[] data) {}
    }

    public static void destroySelf() {
        if (SpeechUtility.getUtility() != null) {
            SpeechUtility.getUtility().destroy();
        }
        hasLexicon = false;
        xfVoiceUtil = null;
    }

}
