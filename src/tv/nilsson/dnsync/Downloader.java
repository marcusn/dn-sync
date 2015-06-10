package tv.nilsson.dnsync;

import android.net.Uri;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.io.*;
import java.net.*;


import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader {
  private static String SERVICE_ENDPOINT = "https://kund.dn.se/service/pdf/";
  private static String LOGIN_ENDPOINT = "https://auth.dn.se/security/authenticate";
  private static String LOGIN_FORM_ENDPOINT = "https://auth.dn.se/login?appId=dagensnyheter.se&lc=sv&callback=https%3A%2F%2Fkund.dn.se%2Fservice%2Floginplus%3Fredirect%3D%2F";
  public static final int TIMEOUT_MS = 30000;
  private String email;
  private String password;

  private CookieManager cookieManager;

  public static class DownloadInfo {
    public Uri uri;
    public String filename;

    public DownloadInfo(Uri uri, String filename) {
      this.uri = uri;
      this.filename = filename;
    }
  }

  public Downloader(String email, String password) {
    this.email = email;
    this.password = password;
    this.cookieManager = new CookieManager();
    CookieHandler.setDefault(cookieManager);
  }

  public DownloadInfo obtainDownloadInfo() throws DownloadException {
    try {
      doLogin();

      HttpURLConnection urlConnection = openURL(SERVICE_ENDPOINT);


      if (urlConnection.getResponseCode() != 200) throw new DownloadUnexpectedResponseException();
      String s = extractContent(urlConnection);

      Pattern pattern = Pattern.compile("/service/download/(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)/DN.pdf");

      Matcher matcher = pattern.matcher(s);
      if (matcher.find()) {
        String filename = MessageFormat.format("{0}{1}{2}_DN.pdf", matcher.group(1), matcher.group(2), matcher.group(3));
        Uri downloadUri = Uri.parse(SERVICE_ENDPOINT).buildUpon().path(matcher.group(0)).build();

        return new DownloadInfo(downloadUri, filename);
      }
      else {
        throw new DownloadUnexpectedResponseException();
      }
    }
    catch (IOException e) {
      throw new DownloadIOException();
    }
    catch(RuntimeException e) {
      throw new DownloadInternalErrorException(e);
    }
  }

  public HttpURLConnection openURL(String url) throws IOException {
    HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
    urlConnection.setConnectTimeout(TIMEOUT_MS);
    urlConnection.setReadTimeout(TIMEOUT_MS);
    return urlConnection;
  }

  private boolean isLoggedIn() {
    for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
      if (cookie.getName().equals("ACCOUNTWEBAPP_SESSION")) return true;
    }

    return false;
  }

  private String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException
  {
    StringBuilder result = new StringBuilder();
    boolean first = true;

    for (NameValuePair pair : params)
    {
      if (first)
        first = false;
      else
        result.append("&");

      result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
      result.append("=");
      result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
    }

    return result.toString();
  }

  private void doLogin() throws IOException, DownloadException {
    String authenticityToken = fetchAuthenticityToken();
    HttpURLConnection urlConnection = openURL(LOGIN_ENDPOINT);
    urlConnection.setDoInput(true);
    urlConnection.setDoOutput(true);
    urlConnection.setRequestMethod("POST");

    List<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new BasicNameValuePair("form.username", email));
    params.add(new BasicNameValuePair("form.password", password));
    params.add(new BasicNameValuePair("authenticityToken", authenticityToken));
    params.add(new BasicNameValuePair("appId", "dagensnyheter.se"));

    OutputStream os = urlConnection.getOutputStream();
    BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(os, "UTF-8"));
    writer.write(getQuery(params));
    writer.flush();
    writer.close();
    os.close();

    urlConnection.connect();

    int status = urlConnection.getResponseCode();
    if (status != 200 && status != 302) {
      throw new AuthenticationFailedException();
    }

    String responseString = extractContent(urlConnection);
    if (responseString.contains("felaktigt")) {
      throw new AuthenticationFailedException();
    }

    if (!isLoggedIn()) throw new AuthenticationFailedException();
  }

  private String fetchAuthenticityToken() throws IOException, DownloadException {
    HttpURLConnection urlConnection = openURL(LOGIN_FORM_ENDPOINT);

    if (urlConnection.getResponseCode() != 200) throw new DownloadUnexpectedResponseException();

    String responseString = extractContent(urlConnection);
    Pattern pattern = Pattern.compile("name=\"authenticityToken\" value=\"(.*?)\"");

    Matcher matcher = pattern.matcher(responseString);
    boolean found = matcher.find();
    if (!found) {
      throw new DownloadUnexpectedResponseException();
    }

    return matcher.group(1);
  }

  private static String readInputStream(InputStream inputStream)
          throws IOException {
    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
    String responseLine;
    StringBuilder responseBuilder = new StringBuilder();
    while ((responseLine = bufferedReader.readLine()) != null) {
      responseBuilder.append(responseLine);
    }
    return responseBuilder.toString();
  }

  private String extractContent(HttpURLConnection urlConnection) throws DownloadException {
    try {
      return readInputStream(urlConnection.getInputStream());
    }
    catch(IOException e) {
      throw new DownloadUnexpectedResponseException();
    }
  }
}
