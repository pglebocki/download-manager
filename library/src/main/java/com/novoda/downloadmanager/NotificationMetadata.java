package com.novoda.downloadmanager;

import android.app.NotificationChannel;
import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;

public interface NotificationMetadata<T> {
    NotificationInformation createNotification(T notificationPayload);

    @RequiresApi(Build.VERSION_CODES.O)
    NotificationChannel createNotificationChannel();

    class Factory {
        private Factory() {
            // Uses static methods
        }

        public static <T> NotificationMetadata<T> build(Context context, String channelId, String userFacingChannelDescription, NotificationCustomizer<T> customizer, @Importance int importance) {
            return new NotificationConfig<>(context, channelId, userFacingChannelDescription, customizer, importance);
        }
    }
}
