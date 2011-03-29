package tv.nilsson.dnsync;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class SyncService extends IntentService {
  private static final String TAG = "SyncService";
  private int NOTIFICATION = R.string.sync_service_started;
  private NotificationManager mNM;

  public SyncService() {
    super(TAG);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    Log.d(TAG, "onHandleIntent");
    mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

    CharSequence text = "Test";

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.icon, text, System.currentTimeMillis());


        Context context = getApplicationContext();
        CharSequence contentTitle = "My notification";
        CharSequence contentText = "Hello World!";
        Intent notificationIntent = new Intent(this, SyncService.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
  }
}
