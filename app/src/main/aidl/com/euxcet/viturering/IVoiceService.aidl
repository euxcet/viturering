// IVoiceService.aidl
package com.euxcet.viturering;

// Declare any non-default types here with import statements
import com.euxcet.viturering.IVoiceResultCallback;

interface IVoiceService {
     void startVoiceListen(in Bundle bundle);
        void addVoiceCallback(IVoiceResultCallback callback);
        void removeVoiceCallback(IVoiceResultCallback callback);
        void stopVoiceListen();
}