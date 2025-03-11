// IVoiceResultCallback.aidl
package com.euxcet.viturering;

// Declare any non-default types here with import statements

interface IVoiceResultCallback {
    void onRecordStart();
        void onCommand(String text);
        void onFinish();
        void onError(int code, String msg);
}