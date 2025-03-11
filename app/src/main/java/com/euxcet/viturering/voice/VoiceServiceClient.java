package com.euxcet.viturering.voice;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.euxcet.viturering.IVoiceResultCallback;
import com.euxcet.viturering.IVoiceService;
import com.euxcet.viturering.MainApplication;

public class VoiceServiceClient extends IVoiceResultCallback.Stub {

    public static final String TAG = "VoiceServiceClient";

    private Context mContext;

    public VoiceServiceClient(Context context) {
        mContext = context;
    }

    private IVoiceService mVoiceRemote;
    private VoiceListener mVoiceListener;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            runInMain(() -> {
                Log.e("TEST_VOICE", "onServiceConnected");
                if (service != null) {
                    mVoiceRemote = IVoiceService.Stub.asInterface(service);
                    if (mRequestStartListen) {
                        try {
                            Bundle bundle = new Bundle();
                            bundle.putString("trigger", mStartListenTrigger);
                            mVoiceRemote.startVoiceListen(bundle);
                        } catch (Exception exc) {
                            Log.e(TAG, "start voice listen fail", exc);
                        }
                        mStartListenTrigger = null;
                    }
                    try {
                        mVoiceRemote.addVoiceCallback(VoiceServiceClient.this);
                    } catch (Exception exc) {
                        Log.e(TAG, "", exc);
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            runInMain(() -> {
                mVoiceRemote = null;
            });
        }
    };

    private boolean mRequestBind;

    public boolean bindService() {
        try {
            if (mRequestBind) {
                return false;
            }
//            Intent intent = new Intent(mContext, VoiceService.class);
            Intent intent = new Intent(mContext, StereoVoiceService.class);
            mContext.bindService(intent, mServiceConnection, Service.BIND_AUTO_CREATE);
            mRequestBind = true;
            return true;
        } catch (Exception exc) {
            Log.e(TAG, "bind service fail", exc);
        }
        return false;
    }

    public void unbindService() {
        if (!mRequestBind) {
            return;
        }
        mRequestStartListen = false;
        mRequestBind = false;
        try {
            if (mVoiceRemote != null) {
                mVoiceRemote.removeVoiceCallback(this);
            }
        } catch (Exception exc) {
            Log.e(TAG, "remove callback fail", exc);
        }
        try {
            mContext.unbindService(mServiceConnection);
        } catch (Exception exc) {
            Log.e(TAG, "unbind fail", exc);
        }
        mVoiceRemote = null;
    }

    public void runInMain(Runnable runnable) {
        MainApplication.instance.getMainHandler().post(runnable);
    }

    private boolean mRequestStartListen;

    private String mStartListenTrigger;
    /**
     * 开始监听
     * @param trigger 触发方式，包括凑近、解锁、快捷面板、指令面板、ai助手语音等
     */
    public void startListen(String trigger) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new IllegalThreadStateException("need run on main thread");
        }
        mStartListenTrigger = trigger;
        if (mVoiceRemote != null) {
            try {
                Bundle bundle = new Bundle();
                bundle.putString("trigger", trigger);
                mVoiceRemote.startVoiceListen(bundle);
            } catch (Exception exc) {
                Log.e(TAG, "start voice listen fail", exc);
            }
        } else {
            if (bindService()) {
                Log.e("TEST_VOICE", "start listen");
                mRequestStartListen = true;
            }
        }
    }

    public void stopListen() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread()) {
            throw new IllegalThreadStateException("need run on main thread");
        }
        Log.e("TEST_VOICE", "stop listen");
        mRequestStartListen = false;
        if (mVoiceRemote != null) {
            try {
                mVoiceRemote.stopVoiceListen();
            } catch (Exception exc) {
                Log.e(TAG, "stop voice listen fail", exc);
            }
            unbindService();
        }
    }

    public boolean isServiceConnected() {
        return mVoiceRemote != null;
    }

    public void setVoiceListener(VoiceListener listener) {
        mVoiceListener = listener;
    }

    @Override
    public void onCommand(String text) throws RemoteException {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            if (mVoiceListener != null) {
                mVoiceListener.onCommand(text);
            }
        } else {
            runInMain(() -> {
                if (mVoiceListener != null) {
                    mVoiceListener.onCommand(text);
                }
            });
        }
    }

    @Override
    public void onFinish() throws RemoteException {
        if (mVoiceListener != null) {
            mVoiceListener.onFinish();
        }
        unbindService();
    }

    @Override
    public void onError(int code, String msg) throws RemoteException {
        if (mVoiceListener != null) {
            mVoiceListener.onError(code, msg);
        }
        unbindService();
    }

    @Override
    public void onRecordStart() throws RemoteException {
        if (mVoiceListener != null) {
            mVoiceListener.onRecordStart();
        }
    }

    public boolean isVoiceValid() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        // 通话中
        /*if (MyApplication.instance.getPhoneCallState() != TelephonyManager.CALL_STATE_IDLE) {
            return false;
        }*/
        return !isMicrophoneInUse(mContext);
    }

    public static boolean isMicrophoneInUse(Context context) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return am.getMode() == AudioManager.MODE_IN_COMMUNICATION;
        } catch (Exception exc) {
            Log.e(TAG, "get audio mode fail", exc);
        }
        return false;
    }
}
