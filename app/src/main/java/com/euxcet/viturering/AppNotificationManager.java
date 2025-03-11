package com.euxcet.viturering;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.RequiresApi;

public class AppNotificationManager {

    @RequiresApi(26)
    public static void createNotificationChannel(Context context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createMediaInviteChannel(notificationManager);
        createHighImportantChannel(notificationManager, ChannelID.NEW_MESSAGE, "新消息通知");
        createMiddleImportantChannel(notificationManager, ChannelID.SYSTEM_MESSAGE, "系统通知");
        createLowImportantChannel(notificationManager, ChannelID.NORMAL_SERVICE, "服务消息");
    }

    @RequiresApi(26)
    private static void createMediaInviteChannel(NotificationManager notificationManager) {
        NotificationChannel notificationChannel = new NotificationChannel(ChannelID.MEDIA_INVITE, "音视频通话邀请", NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableVibration(true);
        notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400});
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @RequiresApi(26)
    private static void createHighImportantChannel(NotificationManager notificationManager, String channelId, String channelName) {
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableVibration(true);
        notificationChannel.setVibrationPattern(new long[]{100, 200, 300, 400});
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @RequiresApi(26)
    private static void createMiddleImportantChannel(NotificationManager notificationManager, String channelId, String channelName) {
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(notificationChannel);
    }

    @RequiresApi(26)
    private static void createLowImportantChannel(NotificationManager notificationManager, String channelId, String channelName) {
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(notificationChannel);
    }

    public static class ChannelID {
        public static final String NEW_MESSAGE = "NEW_MESSAGE";
        public static final String SYSTEM_MESSAGE = "SYSTEM_MESSAGE";
        public static final String MEDIA_INVITE = "MEDIA_INVITE";
        public static final String NORMAL_SERVICE = "NORMAL_SERVICE";
    }
}
