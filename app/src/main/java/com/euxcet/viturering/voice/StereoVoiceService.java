package com.euxcet.viturering.voice;


import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.euxcet.viturering.AppNotificationManager;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.euxcet.viturering.IVoiceResultCallback;
import com.euxcet.viturering.IVoiceService;
import com.iflytek.cloud.ErrorCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class StereoVoiceService extends Service implements CommandListener {
    final int NOTIFICATION_ID = 165;
    private static final String TAG = "StereoVoiceService";
    private static int miniBufferSize = 0;
    static final String hostUrl = "https://iat-api.xfyun.cn/v2/iat"; //中英文，http url 不支持解析 ws/wss schema
    private static final String appid = "6c157d10"; //在控制台-我的应用获取
    static final String apiSecret = "MGY0YjM4NWMyZDYyYWRlMmI2MTlhZmZk"; //在控制台-我的应用-语音听写（流式版）获取
    static final String apiKey = "8735f05eb184366efebb03483591ff41"; //在控制台-我的应用-语音听写（流式版）获取
    private static final int MAX_QUEUE_SIZE = 2500;  // 100 seconds audio, 1 / 0.04 * 100

    public static final Gson json = new Gson();
    private static final int SAMPLE_RATE = 16000;
    private static AudioRecord record = null;
    private static AtomicBoolean recording = new AtomicBoolean(false);
    private static AtomicBoolean recognizing = new AtomicBoolean(false);
    private static final BlockingQueue<byte[]> bufferQueue = new ArrayBlockingQueue<byte[]>(MAX_QUEUE_SIZE);

    private static WeakReference<StereoVoiceService> sVoiceService;

    private boolean saveAudioFile() {
        return false;
        /*SharedPreferences pref = PreferenceUtil.getSharedPreference(getApplication(), ContextLibDebugPreferenceActivity.PREFER_NAME);
        boolean save = pref.getBoolean("context_lib_save_audio", false);
        return save;*/
    }

    public static StereoVoiceService getVoiceService() {
        if (sVoiceService == null) {
            return null;
        }
        return sVoiceService.get();
    }

    private Vibrator vibrator;

    String audioFileDirName;
    FileOutputStream audioFileOS = null;
    FileOutputStream diffAudioFileOS = null;
    FileWriter recResultFileWriter = null;
    private PcmToWavUtil pcmToWavUtil = null;
    private static final SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-HH-mm-ss-");

    private void initRecorder() {
        vibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        // buffer size in bytes 1280
        miniBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (miniBufferSize == AudioRecord.ERROR || miniBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Audio buffer can't initialize!");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission!");
            return;
        }
        record = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                miniBufferSize);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio Record can't initialize!");
        }
        if (saveAudioFile()) {
            try {
                long timeStamp =  System.currentTimeMillis();
                audioFileDirName = Environment.getExternalStorageDirectory() + "/scannerAudioFiles/"  + formatter.format(new Date(timeStamp)) + timeStamp+ "/";
                File file = new File(audioFileDirName);
                if (!file.mkdirs()) {
                    Log.e(TAG, "create file output dir  exception: " + audioFileDirName);
                }
                audioFileOS = new FileOutputStream(audioFileDirName + "audio.pcm");
                diffAudioFileOS = new FileOutputStream(audioFileDirName + "audio_diff.pcm");
                recResultFileWriter = new FileWriter(audioFileDirName + "rec_result.txt");
            } catch (IOException e) {
                Log.e(TAG, "create file outputstream exception", e);
            }
            if (pcmToWavUtil == null) {
                pcmToWavUtil = new PcmToWavUtil(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            }

        }
    }



    void startRecordThread() {
        if (recording.get()) return;
        recording.set(true);
        recognizing.set(false);
        new Thread(() -> {
            try {
                if (record != null) {
                    record.startRecording();
                } else {
                    initRecorder();
                    record.startRecording();
                }
            } catch (Exception exc) {
                Log.e(TAG, "start recording fail", exc);
                return;
            }
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);  // 声音线程的标准级别
            notifyRecordStart();
//            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{50, 50}, -1));
//            if (!XfVoiceUtil.getInstance().startListening()) {
//                notifyStartListenError(XfVoiceUtil.getInstance().getStartListenerErrorCode());
//            }
            while (recording.get()) {
                byte[] buffer = new byte[miniBufferSize];
                byte[] bufferDiff = new byte[buffer.length / 2];
                byte[] bufferLeft = new byte[buffer.length / 2];
                byte[] bufferRight = new byte[buffer.length/ 2];
                int read = record.read(buffer, 0, buffer.length);  // 用byte也可以
                if (read <= 0) {
                    notifyRecordAudioError(read, "AudioRecord.read fails");
                    recording.set(false);
                    break;
                }

                if (saveAudioFile()) {
                    try {
                        audioFileOS.write(buffer);
                    } catch (IOException e) {
                        Log.e(TAG, "save audio file data exception", e);
                    }
                }

                for (int i = 0; i < bufferLeft.length; i++) {
                    bufferLeft[i] = buffer[(i / 2) * 4 + (i % 2)];
                    bufferRight[i] = buffer[(i / 2) * 4 + 2 + (i % 2)];
                }
                for (int i = 0; i <bufferLeft.length; i+=2) {
                    short left = (short) (((bufferLeft[i+1] & 0xff) << 8) + (bufferLeft[i] & 0xff));
                    short right = (short) (((bufferRight[i+1] & 0xff) << 8) + (bufferRight[i] & 0xff));
                    short diff = (short) (left - right);
                    bufferDiff[i] = (byte)(diff & 0xff);
                    bufferDiff[i+1] = (byte)((diff >> 8) & 0xff);
                }
                double dbDiff = calculateDb(bufferDiff);
                double dbLeft = calculateDb(bufferLeft);
                double dbRight = calculateDb(bufferRight);

                double dbdiff2 = calculateDb2(bufferDiff);
                double dbleft2 = calculateDb2(bufferLeft);
                double dbright2 = calculateDb2(bufferRight);


//                Log.e(TAG, "dbDiff:" + dbDiff + ",dbLeft:" + dbLeft + ",dbRight:" + dbRight + ", is valid\t" + !(dbDiff <= 33.5 || (dbDiff > 33.5 && Math.abs(dbLeft - dbRight) < 2)));
                //Log.e(TAG, "dbDiff:" + dbdiff2 + ",dbLeft:" + dbleft2 + ",dbRight:" + dbright2 + ", is valid\t");

                if (Double.isInfinite(dbDiff) && !Double.isInfinite(dbLeft)) {
                    bufferDiff = bufferLeft;
                    recognizing.set(true);
                } else if (dbDiff <= 33.5 || (dbDiff > 33.5 && Math.abs(dbLeft - dbRight) < 0.1 * dbDiff)) { //0.04 /*004*/ * dbDiff)) {
//                    Log.e(TAG, "fill 0");
                    Arrays.fill(bufferDiff, (byte) 0);
                } else {
                    recognizing.set(true);
                    //Go
                }
                try {
                    if (recognizing.get()) {
                        bufferQueue.put(bufferDiff);
//                        bufferQueue.put(bufferLeft);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "put audio buffer exception", e);
                }

                if (recognizing.get()) {
                    if (XfVoiceUtil.getInstance() == null || !XfVoiceUtil.getInstance().isInit()) {
                        recording.set(false);
                    } else {
                        if (!XfVoiceUtil.getInstance().isListening()) {
                            if (!XfVoiceUtil.getInstance().startListening()) {
                                notifyStartListenError(XfVoiceUtil.getInstance().getStartListenerErrorCode());
                            }
                        }
                        try {
                            if (recording.get()) {
                                int size = bufferQueue.size();
                                for (int i = 0; i < size; i++) {
                                    byte[] b = bufferQueue.take();
                                    int code = XfVoiceUtil.getInstance().writeAudio(b);
                                    if (saveAudioFile()) {
                                        try {
                                            diffAudioFileOS.write(b);
                                        } catch (IOException e) {
                                            Log.e(TAG, "save diff audio file data exception", e);
                                        }
                                    }
                                    if (code != ErrorCode.SUCCESS) {
                                        recording.set(false);
                                        notifyWriteAudioError(code, "XfAudioUtil write audio failed!");
                                    }

                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "exception when get audio from buffer and to xunfei", e);
                        }
                    }

//                    if (XfVoiceUtil.getInstance() == null || !XfVoiceUtil.getInstance().isInit()) {
//                        recording.set(false);
//                    } else {
//                        if (recording.get()) {
//                            int code = XfVoiceUtil.getInstance().writeAudio(bufferDiff);
//                            if (code != ErrorCode.SUCCESS) {
//                                recording.set(false);
//                                notifyWriteAudioError(code, "XfAudioUtil write audio failed!");
//                            }
//                        }
//                    }

                }


            }
            if (XfVoiceUtil.getInstance() != null) {
                XfVoiceUtil.getInstance().stopListening();
            }
            try {
                record.stop();
                record.release();
            } catch (Exception exc) {
                Log.e(TAG, "stop recording fail", exc);
            }
            if (saveAudioFile()) {
                try {
                    audioFileOS.close();
                    diffAudioFileOS.close();
                    recResultFileWriter.close();
                    if (pcmToWavUtil != null) {
                        pcmToWavUtil.pcmToWav(audioFileDirName+"audio.pcm", audioFileDirName+"audio.wav", 2);
                        pcmToWavUtil.pcmToWav(audioFileDirName+"audio_diff.pcm", audioFileDirName+"audio_diff.wav", 1);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "close audio file os exception", e);
                }
            }
            record = null;
        }).start();
    }

    static double calculateDb(byte[] buffer) {  // 实际上不是dB
        double sum = 0;
        for (int i = 0; i < buffer.length; i+=2) {
            short v = (short) (((buffer[i+1] & 0xff) << 8) + (buffer[i] & 0xff));
            sum += (v * v);
        }
        double rms2 = sum / (buffer.length / 2);
        return 10 * Math.log10(rms2);
    }

    static double calculateDb2(byte[] buffer) {  // 实际上不是dB
        double sum = 0;
        for (int i = 0; i < buffer.length; i+=2) {
            short v = (short) (((buffer[i+1] & 0xff) << 8) + (buffer[i] & 0xff));
            sum += Math.abs(v);
        }
        double rms2 = sum / (buffer.length / 2);
        return 20 * Math.log10(rms2);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sVoiceService = new WeakReference<>(this);
        recording.set(false);
        mHandler = new Handler(Looper.getMainLooper());
        initRecorder();
        XfVoiceUtil.init(this, 0, true, false);
        mCallbackList = Lists.newArrayList();
    }

    NotificationManager mNotificationManager;
    private Handler mHandler;
    private List<IVoiceResultCallback> mCallbackList;
    private final long SILENT_TIMEOUT = 30000;

    Runnable mSilentRunnable = () -> {
        recognizing.set(false);
        recording.set(false);
        notifyFinish();
        Log.e("TEST_VOICE", "silent time out");
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        startForeground(NOTIFICATION_ID, createNotification("拍拍助手", "正在提供语音识别服务"));
        return new IVoiceService.Stub() {
            @Override
            public void startVoiceListen(Bundle bundle) throws RemoteException {
                Log.e("TEST_VOICE", "START VOICE LISTEN");
                String trigger = "";
                if (bundle != null) {
                    trigger = bundle.getString("trigger");
                }
                Log.e(TAG, "trigger!!!:" + trigger + ", " + trigger.equals("ai_chat"));
                XfVoiceUtil.reInit(StereoVoiceService.this, 0, true, trigger.equals("ai_chat"));
                mHandler.post(() -> {
                    if (!XfVoiceUtil.getInstance().isInit()) {
                        notifyInitFail(XfVoiceUtil.getInstance().getInitResultCode());
                        return;
                    }
                    mHandler.removeCallbacks(mSilentRunnable);
                    mHandler.postDelayed(mSilentRunnable, SILENT_TIMEOUT);
                    if (XfVoiceUtil.getInstance().isListening()) {
                        XfVoiceUtil.getInstance().stopListening();
                        recognizing.set(false);
                    }
                    XfVoiceUtil.getInstance().setVoiceRecognize(new XfVoiceUtil.RecognizeListener() {
                        @Override
                        public void onRecognize(List<String> words) {
                            if (words != null && words.size() > 0 /*&& recording.get()*/) {
//                                Log.e(TAG, "xunfei result:" + words.get(0));
                                onCommand(words.get(0));
                            }
                        }
                        @Override
                        public void onError(int code, String msg) {
                            recording.set(false);
                            recognizing.set(false);
                            mHandler.removeCallbacks(mSilentRunnable);
                            Log.e(TAG, "code: " + code + " msg: " + msg);
                            notifyRecognizeError(code, msg);
                        }

                        @Override
                        public void onVoiceEnd() {
                            recording.set(false);
                            recognizing.set(false);
                            mHandler.removeCallbacks(mSilentRunnable);
                            notifyFinish();
                        }
                    });
                    startRecordThread();
                });
            }

            @Override
            public void addVoiceCallback(IVoiceResultCallback callback) throws RemoteException {
                mHandler.post(() -> {
                    mCallbackList.add(callback);
                    for (int i = 0; i < mCallbackList.size(); i++) {
                        try {
                            IVoiceResultCallback cb = mCallbackList.get(i);
                            if (!cb.asBinder().isBinderAlive()) {
                                mCallbackList.remove(cb);
                                i--;
                            }
                        } catch (Exception exc) {
                            Log.e(TAG, "remove callback fail", exc);
                        }
                    }
                });
            }

            @Override
            public void removeVoiceCallback(IVoiceResultCallback callback) throws RemoteException {
                mHandler.post(() -> {
                    mCallbackList.remove(callback);
                });
            }

            @Override
            public void stopVoiceListen() throws RemoteException {
                mHandler.removeCallbacks(mSilentRunnable);
                mHandler.post(() -> {
                    recording.set(false);
                    Log.e("TEST_VOICE", "STOP VOICE LISTEN");
                });
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
        } catch (Exception exc) {
            Log.e(TAG, "stop foreground fail", exc);
        }
        recording.set(false);
        sVoiceService = null;
    }

    private Notification createNotification(String title, String content) {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        Notification notification = new NotificationCompat.Builder(this, AppNotificationManager.ChannelID.NORMAL_SERVICE)
                .setSmallIcon(com.euxcet.viturering.R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    private String mLastVoiceText;
    @Override
    public void onCommand(String words) {
        mHandler.post(() -> {
//            if (!recognizing.get()) {
//                return;
//            }
            Log.e("TEST_VOICE", "oncommand:" + words);
            if (saveAudioFile()) {
                try {
                    recResultFileWriter.write(words + "\n");
                } catch (IOException e) {
                    Log.e(TAG, "write recognize result exception", e);
                }
            }
            mLastVoiceText = words;
            if (!TextUtils.isEmpty(words)) {
                mHandler.removeCallbacks(mSilentRunnable);
            }
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onCommand(words);
                } catch (Exception exc) {
                    Log.e(TAG, "notify voice command fail", exc);
                }
            }
        });
    }

    @Override
    public void onEnd() {
    }

    public void notifyFinish() {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onFinish();
                } catch (Exception exc) {
                    Log.e(TAG, "notify finish fail", exc);
                }
            }
        });
    }

    public void notifyInitFail(int code) {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onError(code, "init fail");
                } catch (Exception exc) {
                    Log.e(TAG, "notify init fail", exc);
                }
            }
        });
    }

    public void notifyRecordStart() {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onRecordStart();
                } catch (Exception exc) {
                    Log.e(TAG, "notify record start fail", exc);
                }
            }
        });
    }

    public void notifyStartListenError(int code) {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onError(code, "start listen fail");
                } catch (Exception exc) {
                    Log.e(TAG, "notify start listen error", exc);
                }
            }
        });
    }

    public void notifyRecognizeError(int code, String msg) {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onError(code, msg);
                } catch (Exception exc) {
                    Log.e(TAG, "notify recognize error:" + msg, exc);
                }
            }
        });
    }

    public void notifyWriteAudioError(int code, String msg) {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onError(code, msg);
                } catch (Exception exc) {
                    Log.e(TAG, "notify writeAudio error:" + msg, exc);
                }
            }
        });
    }

    public void notifyRecordAudioError(int code, String msg) {
        mHandler.post(() -> {
            for (IVoiceResultCallback iVoiceResultCallback : mCallbackList) {
                try {
                    iVoiceResultCallback.onError(code, msg);
                } catch (Exception exc) {
                    Log.e(TAG, "notify record audio error", exc);
                }
            }
        });
    }

    public boolean isRecording() {
        return recording.get();
    }

    public String getCurrentVoiceText() {
        return mLastVoiceText != null ? mLastVoiceText : "";
    }
}

