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
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class SyncService extends IntentService {
  private static final String TAG = "SyncService";
  public static final String ACTION_SYNC = "tv.nilsson.dnsync.SYNC";

  private static PowerManager.WakeLock wakeLock=null;
  private Handler handler;
  private static final int ID_ONGOING = 1;
  private NotificationManager notificationManager;
  private Notification ongoingNotification;

  synchronized private static PowerManager.WakeLock getLock(Context context) {
    if (wakeLock==null) {
      PowerManager mgr=(PowerManager)context.getSystemService(Context.POWER_SERVICE);

      wakeLock=mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SyncService");
      wakeLock.setReferenceCounted(true);
    }

    return(wakeLock);
  }

  public static void keepAwake(Context context) {
    if (!getLock(context).isHeld()) {
      getLock(context).acquire();
    }
  }

  public SyncService() {
    super(TAG);
  }

  private boolean isAllowed() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    if (preferences.getBoolean("sync_wifi", false) && connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) return true;
    if (preferences.getBoolean("sync_3g", false) && connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnected()) return true;

    return false;
  }

  private void toast(final String message) {
    final Context context = this;
    handler.post(new Runnable() {
      public void run() {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
      }
    });
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
      download(preferences.getString("customer_nr", ""), preferences.getString("customer_email", ""));
    }
    catch(IOException e) {
      e.printStackTrace();
    }
    catch(AuthenticationFailedException e) {
      toast("DN Authentication failed, check username and password");
    }
    catch(DownloadException e) {
      e.printStackTrace();
      toast("DN Download failed");
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

  public void download(String customerNr, String email) throws IOException, DownloadException {
    if ("".equals(customerNr) || "".equals(email)) return;

    customerNr = customerNr.trim();
    email = email.trim();

    Downloader downloader = new Downloader(customerNr, email);

    final Downloader.DownloadInfo downloadInfo = downloader.obtainDownloadInfo();

    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    //noinspection ResultOfMethodCallIgnored
    downloads.mkdir();

    final File file = new File(downloads, downloadInfo.filename);

    if (file.exists()) return;

    Uri destination = Uri.fromFile(file);

    showDownloading(destination, "Downloading");

    try {
      copy(downloadInfo.uri, destination);
    }
    finally {
      notificationManager.cancel(ID_ONGOING);
    }

    notifyDownloaded(destination);
  }

  private void copy(Uri source, Uri destination) throws IOException {
    ContentResolver contentResolver = getContentResolver();

    HttpEntity entity = openWebUri(source);
    InputStream inputStream = (InputStream) entity.getContent();
    OutputStream outputStream = contentResolver.openOutputStream(destination, "w");

    long total = entity.getContentLength();
    if (total == 0) return;

    byte[] buf = new byte[65536];

    int bytesRead;
    long current = 0;
    int progress = 0;
    do {
      bytesRead = inputStream.read(buf);
      current += bytesRead;
      {
        int newProgress = (int) (current * 100 / total);
        if (newProgress != progress) {
          ongoingNotification.contentView.setProgressBar(R.id.download_progress_progress, 100, newProgress, false);
          notificationManager.notify(ID_ONGOING, ongoingNotification);

          progress = newProgress;
        }
      }
      Log.d(TAG, String.format("Got %d bytes", bytesRead));
      if (bytesRead > 0) {
        outputStream.write(buf, 0, bytesRead);
      }
    } while (bytesRead >= 0);
  }

  private HttpEntity openWebUri(Uri source) throws IOException {HttpClient httpClient = new DefaultHttpClient();
    HttpResponse response = httpClient.execute(new HttpGet(URI.create(source.toString())));
    return response.getEntity();
  }

  private void showDownloading(Uri localFileName, String contentTitle) {

    CharSequence contentText = localFileName.getLastPathSegment();
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
    notificationManager.notify(ID_ONGOING, notification);

  }
}
