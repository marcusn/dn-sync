package tv.nilsson.dnsync;

import android.net.Uri;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader {
  private static String SERVICE_ENDPOINT = "http://pdf.dn.se/dn-ssf/pdf/archive.jsp";
  private static String LOGIN_ENDPOINT = "http://pdf.dn.se//dn-ssf/j_spring_security_check";

  private String customerNr;
  private String email;

  public static class DownloadInfo {
    public Uri uri;
    public String filename;

    public DownloadInfo(Uri uri, String filename) {
      this.uri = uri;
      this.filename = filename;
    }
  }

  public Downloader(String customerNr, String email) {
    this.customerNr = customerNr;
    this.email = email;
  }

  public DownloadInfo obtainDownloadInfo() {
    try {
      Uri downloadUri = getDownloadUri(doLogin());
      return new DownloadInfo(downloadUri, extractFilename(downloadUri));
    }
    catch(IOException e) {
      return null;
    }
  }

  private String doLogin() throws IOException {
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

  private String extractEntity(HttpResponse response) throws IOException {ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    response.getEntity().writeTo(byteArrayOutputStream);
    return new String(byteArrayOutputStream.toByteArray());
  }

  private Uri getDownloadUri(String sessionId) throws IOException {
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

  private static String extractFilename(Uri downloadUri) {

    Matcher filenameMatcher = Pattern.compile("(.*?);jsessionid").matcher(downloadUri.getLastPathSegment());
    return filenameMatcher.find() ? filenameMatcher.group(1) : null;
  }
}
