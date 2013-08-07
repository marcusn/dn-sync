package tv.nilsson.dnsync;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import org.apache.http.HttpEntity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SyncService extends IntentService {
  private static final String TAG = "SyncService";
  public static final String ACTION_SYNC = "tv.nilsson.dnsync.SYNC";

  private static PowerManager.WakeLock wakeLock=null;
  private Handler handler;
  private static final int ID_ONGOING = 1;
  private static final int ID_FINISHED = 2;
  private NotificationManager notificationManager;
  private Notification ongoingNotification;

  private final IBinder mBinder = new LocalBinder();

  private List<SyncStatus.Listener> syncStatusListeners = new ArrayList<SyncStatus.Listener>();
  private SyncStatus syncStatus = new SyncStatus("");

  public class LocalBinder extends Binder {
        SyncService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SyncService.this;
        }
    }
  synchronized private static PowerManager.WakeLock getLock(Context context) {
    if (wakeLock==null) {
      PowerManager mgr=(PowerManager)context.getSystemService(Context.POWER_SERVICE);

      wakeLock=mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncService");
      wakeLock.setReferenceCounted(true);
    }

    return(wakeLock);
  }

  public boolean addSyncStatusListener(SyncStatus.Listener listener) {
    return syncStatusListeners.add(listener);
  }

  public void removeSyncStatusListener(SyncStatus.Listener listener) {
    syncStatusListeners.remove(listener);
  }

  private void setSyncStatus(SyncStatus syncStatus) {
    this.syncStatus = syncStatus;

    for(SyncStatus.Listener listener : syncStatusListeners) {
      listener.onSyncStatusChanged();
    }
  }

  public SyncStatus getSyncStatus() {
    return syncStatus;
  }

  public static void keepAwake(Context context) {
    if (!getLock(context).isHeld()) {
      getLock(context).acquire();
    }
  }

  public SyncService() {
    super(TAG);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  private boolean isAllowed() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (!preferences.getBoolean("sync_enabled", false)) return false;

    ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    if (preferences.getBoolean("sync_wifi", false) && isConnected(connManager, ConnectivityManager.TYPE_WIFI)) return true;
    if (preferences.getBoolean("sync_3g", false) && isConnected(connManager, ConnectivityManager.TYPE_MOBILE)) return true;

    return false;
  }

  private boolean isConnected(ConnectivityManager connManager, int networkType) {
    NetworkInfo networkInfo = connManager.getNetworkInfo(networkType);
    return networkInfo != null && networkInfo.isConnected();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    handler = new Handler(Looper.getMainLooper());

    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    Log.d(TAG, "onHandleIntent");

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    try {
      if (!isAllowed()) return;
      setSyncStatus(new SyncStatus("Contacting DN"));
      download(preferences.getString("customer_email", ""), preferences.getString("customer_password", ""));
    }
    catch(IOException e) {
      setSyncStatus(new SyncStatus("Sync Failed"));
      e.printStackTrace();
    }
    catch(AuthenticationFailedException e) {
      setSyncStatus(new SyncStatus("Authentication failed, check authentication details"));
    }
    catch(DownloadException e) {
      setSyncStatus(new SyncStatus("Download Failed"));
      e.printStackTrace();
    }
    finally {
      releaseWakeLock();
    }
  }

  private void releaseWakeLock() {
    if (getLock(this).isHeld()) {
      getLock(this).release();
    }
  }

  public void download(String email, String password) throws IOException, DownloadException {
    if ("".equals(email) || "".equals(password)) {
        setSyncStatus(new SyncStatus("No authentication details"));
        return;
    }

    email = email.trim();
    password = password.trim();

    Downloader downloader = new Downloader(email, password);

    final Downloader.DownloadInfo downloadInfo = downloader.obtainDownloadInfo();

    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    //noinspection ResultOfMethodCallIgnored
    downloads.mkdir();

    final File file = new File(downloads, downloadInfo.filename);
    final File tempFile = new File(downloads, downloadInfo.filename + ".tmp");

    if (file.exists()) {
      setSyncStatus(new SyncStatus("No new DN is available"));
      return;
    }

    showDownloading();
    setSyncStatus(new SyncStatus("Downloading"));

    try {

      if (tryDownload(downloader, tempFile, downloadInfo.uri)) {
        Uri destination = Uri.fromFile(file);
        tempFile.renameTo(file);
        notifyDownloaded(destination);

        setSyncStatus(new SyncStatus("Downloaded"));

      } else {
        tempFile.delete();
      }

    }
    finally {
      notificationManager.cancel(ID_ONGOING);
    }
  }

  private boolean tryDownload(Downloader downloader, File file, Uri source) {
    try {
        long totalSize = copy(source, Uri.fromFile(file), downloader);

        if (totalSize != file.length()) {
            setSyncStatus(new SyncStatus("DN Download failed, invalid file size"));
            return false;
        } else {
            return true;
        }
    }
    catch(IOException e) {
        setSyncStatus(new SyncStatus("DN Download failed"));
        return false;
    }
  }

  private long copy(Uri source, Uri destination, Downloader downloader) throws IOException {
    ContentResolver contentResolver = getContentResolver();

    HttpEntity entity = downloader.openUri(source);
    InputStream inputStream = (InputStream) entity.getContent();
    OutputStream outputStream = contentResolver.openOutputStream(destination, "w");

    long total = entity.getContentLength();
    if (total == 0) return 0;
    boolean hasTotal = total > 0; // -1 indicates chunked encoding

    if (!hasTotal) total = 50 * 1024 * 1024; // Guess to get progress

    byte[] buf = new byte[65536];

    int bytesRead;
    long current = 0;
    int progress = 0;
    do {
      bytesRead = inputStream.read(buf);
      if (bytesRead > 0) current += bytesRead;
      {
        int newProgress = (int) (current * 100 / total);
        if (newProgress != progress) {
          ongoingNotification.contentView.setProgressBar(R.id.download_progress_progress, 100, newProgress, false);
          notificationManager.notify(ID_ONGOING, ongoingNotification);
          setSyncStatus(new SyncStatus("Downloaded " + newProgress + "%"));

          progress = newProgress;
        }
      }
      Log.d(TAG, String.format("Got %d bytes", bytesRead));
      if (bytesRead > 0) {
        outputStream.write(buf, 0, bytesRead);
      }
    } while (bytesRead >= 0);

      outputStream.close();

    return hasTotal ? total : current;
  }

  private void showDownloading() {
    Intent notificationIntent = new Intent(this, SyncService.class);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

    ongoingNotification = new Notification(R.drawable.dn, "DN Downloading", System.currentTimeMillis());
    ongoingNotification.contentIntent = contentIntent;

    RemoteViews contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.download_progress);
    contentView.setImageViewResource(R.id.download_progress_icon, R.drawable.dn);
    contentView.setTextViewText(R.id.download_progress_text, "DN Downloading");
    contentView.setProgressBar(R.id.download_progress_progress, 100, 0, false);

    ongoingNotification.contentView = contentView;

    ongoingNotification.flags = Notification.FLAG_ONGOING_EVENT;

    notificationManager.notify(ID_ONGOING, ongoingNotification);
  }

  private void notifyDownloaded(Uri localFileName) {
    Notification notification = new Notification(R.drawable.dn, "DN Downloaded", System.currentTimeMillis());

    CharSequence contentTitle = "DN Downloaded";
    CharSequence contentText = "New DN: " + localFileName.getLastPathSegment();
    Intent notificationIntent = new Intent(Intent.ACTION_VIEW, localFileName);
    notificationIntent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    notificationIntent.setDataAndType(localFileName, "application/pdf");
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

    notification.setLatestEventInfo(this, contentTitle, contentText, contentIntent);
    notification.flags = Notification.FLAG_AUTO_CANCEL;
    NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    notificationManager.notify(ID_FINISHED, notification);

  }
}
