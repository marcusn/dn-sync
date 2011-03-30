package tv.nilsson.dnsync;

import android.app.DownloadManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyncService extends IntentService {
  private static final String TAG = "SyncService";
  public static final String ACTION_SYNC = "tv.nilsson.dnsync.SYNC";
  private int NOTIFICATION = R.string.sync_service_started;
  private NotificationManager notificationManager;
  private static String SERVICE_ENDPOINT = "http://pdf.dn.se/dn-ssf/pdf/archive.jsp";
  private static String LOGIN_ENDPOINT = "http://pdf.dn.se//dn-ssf/j_spring_security_check";

  public SyncService() {
    super(TAG);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    Log.d(TAG, "onHandleIntent");

    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    try {
      download(preferences.getString("customer_nr", ""), preferences.getString("customer_email", ""));
    }
    catch(IOException e) {
      e.printStackTrace();
    }
  }

  private static String login(String customerNr, String email) throws IOException {
    HttpClient httpClient = new DefaultHttpClient();
    Uri.Builder uriBuilder = Uri.parse(LOGIN_ENDPOINT).buildUpon();

    HttpGet request = new HttpGet(uriBuilder.appendQueryParameter("j_username", customerNr).appendQueryParameter("j_password", email).toString());
    HttpResponse response = httpClient.execute(request);

    int status = response.getStatusLine().getStatusCode();
    if (status != 200) return null;

    String s = extractEntity(response);

    Pattern pattern = Pattern.compile(";jsessionid=(.*?)\"");

    Matcher matcher = pattern.matcher(s);
    if (matcher.find()) {
      String jsessionId = matcher.group(1);

      return jsessionId;
    }

    return null;
  }

  private static String extractEntity(HttpResponse response) throws IOException {ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    response.getEntity().writeTo(byteArrayOutputStream);
    return new String(byteArrayOutputStream.toByteArray());
  }

  private static Uri getDownloadUri(String sessionId) throws IOException {
    HttpClient httpClient = new DefaultHttpClient();
    Uri.Builder uriBuilder = Uri.parse(SERVICE_ENDPOINT + ";jsessionid=" + sessionId).buildUpon().appendQueryParameter("date", "latest");


    HttpGet request = new HttpGet(uriBuilder.toString());
    HttpResponse response = httpClient.execute(request);

    if (response.getStatusLine().getStatusCode() != 200) return null;

    String s = extractEntity(response);

    Pattern pattern = Pattern.compile("\"(.*?DN\\.pdf.*?)\"");

    Matcher matcher = pattern.matcher(s);
    if (matcher.find()) {
      String link = matcher.group(1);
      return Uri.parse("http://pdf.dn.se" + link);
    }

    return null;
  }

  public void download(String customerNr, String email) throws IOException {
    String sessionId = login(customerNr, email);
    if (sessionId == null) return;

    Uri downloadUri = getDownloadUri(sessionId);
    if (downloadUri == null) return;

    String filename = extractFilename(downloadUri);
    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

    downloads.mkdir();

    File file = new File(downloads, filename);

    if (file.exists()) return;

    DownloadManager downloadMananger = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

    //downloadUri = Uri.parse("http://www.google.se");

    downloadMananger.enqueue(new DownloadManager.Request(downloadUri).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename));

  }

  private static String extractFilename(Uri downloadUri) {

    Matcher filenameMatcher = Pattern.compile("(.*?);jsessionid").matcher(downloadUri.getLastPathSegment());
    return filenameMatcher.find() ? filenameMatcher.group(1) : null;
  }


}
