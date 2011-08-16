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
  private String sessionId;

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

  public DownloadInfo obtainDownloadInfo() throws DownloadException {
    try {
      Uri downloadUri = getDownloadUri(doLogin());

      return new DownloadInfo(downloadUri, extractFilename(downloadUri));
    }
    catch (IOException e) {
      throw new DownloadIOException();
    }
    catch(RuntimeException e) {
      throw new DownloadInternalErrorException(e);
    }
  }

  private String doLogin() throws IOException, DownloadException {
    HttpClient httpClient = newHttpClient();
    Uri.Builder uriBuilder = Uri.parse(LOGIN_ENDPOINT).buildUpon();

    HttpGet request = new HttpGet(uriBuilder.appendQueryParameter("j_username", customerNr).appendQueryParameter("j_password", email).toString());
    HttpResponse response = httpClient.execute(request);

    int status = response.getStatusLine().getStatusCode();
    if (status != 200) {
      throw new AuthenticationFailedException();
    }

    String s = extractEntity(response);

    if (s.contains("j_spring_security_check")) throw new AuthenticationFailedException();

    Pattern pattern = Pattern.compile(";jsessionid=(.*?)\"");

    Matcher matcher = pattern.matcher(s);
    if (matcher.find()) {
      String jsessionId = matcher.group(1);

      return jsessionId;
    }

    throw new DownloadUnexpectedResponseException();
  }

  private String extractEntity(HttpResponse response) throws DownloadException {
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      response.getEntity().writeTo(byteArrayOutputStream);
      return new String(byteArrayOutputStream.toByteArray());
    }
    catch(IOException e) {
      throw new DownloadUnexpectedResponseException();
    }
  }

  private Uri getDownloadUri(String sessionId) throws IOException, DownloadException {
    HttpClient httpClient = newHttpClient();
    Uri.Builder uriBuilder = Uri.parse(SERVICE_ENDPOINT + ";jsessionid=" + sessionId).buildUpon().appendQueryParameter("date", "latest");


    HttpGet request = new HttpGet(uriBuilder.toString());
    HttpResponse response = httpClient.execute(request);

    if (response.getStatusLine().getStatusCode() != 200) throw new DownloadUnexpectedResponseException();

    String s = extractEntity(response);

    Pattern pattern = Pattern.compile("\"(.*?DN\\.pdf.*?)\"");

    Matcher matcher = pattern.matcher(s);
    if (matcher.find()) {
      String link = matcher.group(1);
      return Uri.parse("http://pdf.dn.se" + link);
    }

    throw new DownloadUnexpectedResponseException();
  }

  public static HttpClient newHttpClient() {return new DefaultHttpClient();}

  private static String extractFilename(Uri downloadUri) {

    Matcher filenameMatcher = Pattern.compile("(.*?);jsessionid").matcher(downloadUri.getLastPathSegment());
    return filenameMatcher.find() ? filenameMatcher.group(1) : null;
  }
}
