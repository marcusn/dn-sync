package tv.nilsson.dnsync;

abstract public class DownloadException extends Exception {
  public DownloadException() {
    super();
  }

  public DownloadException(String message) {
    super(message);
  }

  public DownloadException(String message, Throwable cause) {
    super(message, cause);
  }

  public DownloadException(Throwable cause) {
    super(cause);
  }
}
