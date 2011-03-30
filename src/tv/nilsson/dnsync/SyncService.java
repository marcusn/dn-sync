package tv.nilsson.dnsync;

import android.app.DownloadManager;
import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class SyncService extends IntentService {
  private static final String TAG = "SyncService";
  public static final String ACTION_SYNC = "tv.nilsson.dnsync.SYNC";

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
  }

  public void download(String customerNr, String email) throws IOException {
    Downloader downloader = new Downloader(customerNr, email);

    Downloader.DownloadInfo downloadInfo = downloader.obtainDownloadInfo();

    if (downloadInfo == null) {
      Toast.makeText(this, "DN Download failed", Toast.LENGTH_SHORT).show();
      return;
    }

    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    //noinspection ResultOfMethodCallIgnored
    downloads.mkdir();

    File file = new File(downloads, downloadInfo.filename);

    if (file.exists()) return;

    DownloadManager downloadMananger = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

    //downloadInfo.uri = Uri.parse("http://www.google.se");


    downloadMananger.enqueue(new DownloadManager.Request(downloadInfo.uri).setDestinationUri(Uri.fromFile(file)));
  }
}
