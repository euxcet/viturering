package com.euxcet.viturering.voice;

public interface CommandListener {
    void onCommand(String words);
    void onEnd();
    default void onError(int code, String msg) {};
}
