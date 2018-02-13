package com.novoda.downloadmanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.DrawableRes;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.novoda.merlin.MerlinsBeard;
import com.novoda.notils.logger.simple.Log;
import com.squareup.okhttp.OkHttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DownloadManagerBuilder {

    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int TIMEOUT = 5;

    private final Context applicationContext;
    private final Handler callbackHandler;

    private FilePersistenceCreator filePersistenceCreator;
    private FileSizeRequester fileSizeRequester;
    private FileDownloader fileDownloader;
    private DownloadService downloadService;
    private DownloadManager downloadManager;
    private NotificationCreator<DownloadBatchStatus> notificationCreator;
    private NotificationChannelProvider notificationChannelProvider;
    private ConnectionType connectionTypeAllowed;
    private boolean allowNetworkRecovery;
    private Class<? extends CallbackThrottle> customCallbackThrottle;
    private DownloadsPersistence downloadsPersistence;
    private CallbackThrottleCreator.Type callbackThrottleCreatorType;
    private TimeUnit timeUnit;
    private long frequency;

    public static DownloadManagerBuilder newInstance(Context context, Handler callbackHandler, @DrawableRes final int notificationIcon) {
        Log.setShowLogs(true);
        Context applicationContext = context.getApplicationContext();

        // File persistence
        FilePersistenceCreator filePersistenceCreator = FilePersistenceCreator.newInternalFilePersistenceCreator(applicationContext);

        // Downloads information persistence
        DownloadsPersistence downloadsPersistence = RoomDownloadsPersistence.newInstance(applicationContext);

        // Network downloader
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(TIMEOUT, TimeUnit.SECONDS);
        okHttpClient.setWriteTimeout(TIMEOUT, TimeUnit.SECONDS);
        okHttpClient.setReadTimeout(TIMEOUT, TimeUnit.SECONDS);
        HttpClient httpClient = new WrappedOkHttpClient(okHttpClient);

        NetworkRequestCreator requestCreator = new NetworkRequestCreator();
        FileSizeRequester fileSizeRequester = new NetworkFileSizeRequester(httpClient, requestCreator);
        FileDownloader fileDownloader = new NetworkFileDownloader(httpClient, requestCreator);

        NotificationChannelProvider notificationChannelProvider = new DefaultNotificationChannelProvider(
                context.getResources().getString(R.string.download_notification_channel_name),
                context.getResources().getString(R.string.download_notification_channel_description),
                NotificationManagerCompat.IMPORTANCE_LOW
        );
        NotificationCustomizer<DownloadBatchStatus> notificationCustomizer = new DownloadNotificationCustomizer(
                context.getResources(),
                notificationIcon
        );
        NotificationCreator<DownloadBatchStatus> notificationCreator = new NotificationCreator<>(
                context,
                notificationCustomizer,
                notificationChannelProvider
        );

        ConnectionType connectionTypeAllowed = ConnectionType.ALL;
        boolean allowNetworkRecovery = true;

        CallbackThrottleCreator.Type callbackThrottleCreatorType = CallbackThrottleCreator.Type.THROTTLE_BY_PROGRESS_INCREASE;

        return new DownloadManagerBuilder(
                applicationContext,
                callbackHandler,
                filePersistenceCreator,
                downloadsPersistence,
                fileSizeRequester,
                fileDownloader,
                notificationChannelProvider,
                notificationCreator,
                connectionTypeAllowed,
                allowNetworkRecovery,
                callbackThrottleCreatorType
        );
    }

    @SuppressWarnings({"checkstyle:parameternumber", "PMD.ExcessiveParameterList"})     // Can't group anymore these are customisable options.
    private DownloadManagerBuilder(Context applicationContext,
                                   Handler callbackHandler,
                                   FilePersistenceCreator filePersistenceCreator,
                                   DownloadsPersistence downloadsPersistence,
                                   FileSizeRequester fileSizeRequester,
                                   FileDownloader fileDownloader,
                                   NotificationChannelProvider notificationChannelProvider,
                                   NotificationCreator<DownloadBatchStatus> notificationCreator,
                                   ConnectionType connectionTypeAllowed,
                                   boolean allowNetworkRecovery,
                                   CallbackThrottleCreator.Type callbackThrottleCreatorType) {
        this.applicationContext = applicationContext;
        this.callbackHandler = callbackHandler;
        this.filePersistenceCreator = filePersistenceCreator;
        this.downloadsPersistence = downloadsPersistence;
        this.fileSizeRequester = fileSizeRequester;
        this.fileDownloader = fileDownloader;
        this.notificationChannelProvider = notificationChannelProvider;
        this.notificationCreator = notificationCreator;
        this.connectionTypeAllowed = connectionTypeAllowed;
        this.allowNetworkRecovery = allowNetworkRecovery;
        this.callbackThrottleCreatorType = callbackThrottleCreatorType;
    }

    public DownloadManagerBuilder withFilePersistenceInternal() {
        filePersistenceCreator = FilePersistenceCreator.newInternalFilePersistenceCreator(applicationContext);
        return this;
    }

    public DownloadManagerBuilder withFilePersistenceExternal() {
        filePersistenceCreator = FilePersistenceCreator.newExternalFilePersistenceCreator(applicationContext);
        return this;
    }

    public DownloadManagerBuilder withFileDownloaderCustom(FileSizeRequester fileSizeRequester, FileDownloader fileDownloader) {
        this.fileSizeRequester = fileSizeRequester;
        this.fileDownloader = fileDownloader;
        return this;
    }

    public DownloadManagerBuilder withFilePersistenceCustom(Class<? extends FilePersistence> customFilePersistenceClass) {
        filePersistenceCreator = FilePersistenceCreator.newCustomFilePersistenceCreator(applicationContext, customFilePersistenceClass);
        return this;
    }

    public DownloadManagerBuilder withDownloadsPersistenceCustom(DownloadsPersistence downloadsPersistence) {
        this.downloadsPersistence = downloadsPersistence;
        return this;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public DownloadManagerBuilder withNotificationChannel(NotificationChannel notificationChannel) {
        this.notificationChannelProvider = new OreoNotificationChannelProvider(notificationChannel);
        this.notificationCreator.setNotificationChannelProvider(notificationChannelProvider);
        return this;
    }

    public DownloadManagerBuilder withNotificationChannel(String channelId, String name, @Importance int importance) {
        this.notificationChannelProvider = new DefaultNotificationChannelProvider(channelId, name, importance);
        this.notificationCreator.setNotificationChannelProvider(notificationChannelProvider);
        return this;
    }

    public DownloadManagerBuilder withNotification(NotificationCustomizer<DownloadBatchStatus> notificationCustomizer) {
        this.notificationCreator = new NotificationCreator<>(applicationContext, notificationCustomizer, notificationChannelProvider);
        return this;
    }

    public DownloadManagerBuilder withAllowedConnectionType(ConnectionType connectionTypeAllowed) {
        this.connectionTypeAllowed = connectionTypeAllowed;
        return this;
    }

    public DownloadManagerBuilder withoutNetworkRecovery() {
        allowNetworkRecovery = false;
        return this;
    }

    public DownloadManagerBuilder withCallbackThrottleCustom(Class<? extends CallbackThrottle> customCallbackThrottle) {
        this.callbackThrottleCreatorType = CallbackThrottleCreator.Type.CUSTOM;
        this.customCallbackThrottle = customCallbackThrottle;
        return this;
    }

    public DownloadManagerBuilder withCallbackThrottleByTime(TimeUnit timeUnit, long frequency) {
        this.callbackThrottleCreatorType = CallbackThrottleCreator.Type.THROTTLE_BY_TIME;
        this.timeUnit = timeUnit;
        this.frequency = frequency;
        return this;
    }

    public DownloadManagerBuilder withCallbackThrottleByProgressIncrease() {
        this.callbackThrottleCreatorType = CallbackThrottleCreator.Type.THROTTLE_BY_PROGRESS_INCREASE;
        return this;
    }

    public DownloadManager build() {
        Intent intent = new Intent(applicationContext, LiteDownloadService.class);
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                LiteDownloadService.DownloadServiceBinder binder = (LiteDownloadService.DownloadServiceBinder) service;
                downloadService = binder.getService();
                downloadManager.submitAllStoredDownloads(() -> {
                    downloadManager.initialise(downloadService);

                    if (allowNetworkRecovery) {
                        DownloadsNetworkRecoveryCreator.createEnabled(applicationContext, downloadManager, connectionTypeAllowed);
                    } else {
                        DownloadsNetworkRecoveryCreator.createDisabled();
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // no-op
            }
        };

        applicationContext.bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE);

        FileOperations fileOperations = new FileOperations(filePersistenceCreator, fileSizeRequester, fileDownloader);
        ArrayList<DownloadBatchStatusCallback> callbacks = new ArrayList<>();

        CallbackThrottleCreator callbackThrottleCreator = getCallbackThrottleCreator(
                callbackThrottleCreatorType,
                timeUnit,
                frequency,
                customCallbackThrottle
        );

        Executor executor = Executors.newSingleThreadExecutor();
        DownloadsFilePersistence downloadsFilePersistence = new DownloadsFilePersistence(downloadsPersistence);
        MerlinsBeard merlinsBeard = MerlinsBeard.from(applicationContext);
        ConnectionChecker connectionChecker = new ConnectionChecker(merlinsBeard, connectionTypeAllowed);
        DownloadsBatchPersistence downloadsBatchPersistence = new DownloadsBatchPersistence(
                executor,
                downloadsFilePersistence,
                downloadsPersistence,
                callbackThrottleCreator,
                connectionChecker
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannelProvider.registerNotificationChannel(applicationContext);
        }

        NotificationDispatcher notificationDispatcher = new NotificationDispatcher(LOCK, notificationCreator, downloadsBatchPersistence);

        LiteDownloadManagerDownloader downloader = new LiteDownloadManagerDownloader(
                LOCK,
                EXECUTOR,
                callbackHandler,
                fileOperations,
                downloadsBatchPersistence,
                downloadsFilePersistence,
                notificationDispatcher,
                connectionChecker,
                callbacks,
                callbackThrottleCreator
        );

        downloadManager = new DownloadManager(
                LOCK,
                EXECUTOR,
                callbackHandler,
                new HashMap<>(),
                callbacks,
                fileOperations,
                downloadsBatchPersistence,
                downloader,
                connectionChecker
        );

        return downloadManager;
    }

    private CallbackThrottleCreator getCallbackThrottleCreator(CallbackThrottleCreator.Type callbackThrottleType,
                                                               TimeUnit timeUnit,
                                                               long frequency,
                                                               Class<? extends CallbackThrottle> customCallbackThrottle) {
        switch (callbackThrottleType) {
            case THROTTLE_BY_TIME:
                return CallbackThrottleCreator.byTime(timeUnit, frequency);
            case THROTTLE_BY_PROGRESS_INCREASE:
                return CallbackThrottleCreator.byProgressIncrease();
            case CUSTOM:
                return CallbackThrottleCreator.byCustomThrottle(customCallbackThrottle);
            default:
                throw new IllegalStateException("callbackThrottle type " + callbackThrottleType + " not implemented yet");
        }
    }

    private static class DownloadNotificationCustomizer implements NotificationCustomizer<DownloadBatchStatus> {

        private static final boolean NOT_INDETERMINATE = false;
        private final Resources resources;
        private final int notificationIcon;

        DownloadNotificationCustomizer(Resources resources, int notificationIcon) {
            this.resources = resources;
            this.notificationIcon = notificationIcon;
        }

        @Override
        public Notification customNotificationFrom(NotificationCompat.Builder builder, DownloadBatchStatus payload) {
            DownloadBatchTitle downloadBatchTitle = payload.getDownloadBatchTitle();
            String title = downloadBatchTitle.asString();
            builder.setSmallIcon(notificationIcon)
                    .setContentTitle(title);

            switch (payload.status()) {
                case DELETION:
                    return createDeletedNotification(builder);
                case ERROR:
                    return createErrorNotification(builder, payload.getDownloadErrorType());
                default:
                    return createProgressNotification(builder, payload);
            }
        }

        private Notification createDeletedNotification(NotificationCompat.Builder builder) {
            String content = resources.getString(R.string.download_notification_content_deleted);
            return builder
                    .setContentText(content)
                    .build();
        }

        private Notification createErrorNotification(NotificationCompat.Builder builder, DownloadError.Error errorType) {
            String content = resources.getString(R.string.download_notification_content_error, errorType);
            return builder
                    .setContentText(content)
                    .build();
        }

        private Notification createProgressNotification(NotificationCompat.Builder builder, DownloadBatchStatus payload) {
            int bytesFileSize = (int) payload.bytesTotalSize();
            int bytesDownloaded = (int) payload.bytesDownloaded();
            String content = resources.getString(R.string.download_notification_content_progress, payload.percentageDownloaded());

            return builder
                    .setProgress(bytesFileSize, bytesDownloaded, NOT_INDETERMINATE)
                    .setContentText(content)
                    .build();
        }

    }
}
