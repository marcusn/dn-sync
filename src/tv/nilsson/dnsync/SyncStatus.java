package tv.nilsson.dnsync;


public class SyncStatus {
  private String message;


  public interface Listener {
    void onSyncStatusChanged();
  }

  public SyncStatus(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
