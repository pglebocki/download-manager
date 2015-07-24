package com.novoda.downloadmanager.lib;

import java.io.InputStream;

interface DataTransferer {
    DownloadThread.State transferData(DownloadThread.State state, InputStream in) throws StopRequestException;
}