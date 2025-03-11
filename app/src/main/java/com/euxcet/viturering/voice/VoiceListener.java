package com.euxcet.viturering.voice;

public interface VoiceListener {
    void onRecordStart();
    void onCommand(String text);
    default void onError(int code, String msg) {};
    void onFinish();
}
