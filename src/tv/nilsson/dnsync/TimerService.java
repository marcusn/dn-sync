package tv.nilsson.dnsync;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

public class TimerService extends Service {
  private Timer timer;
  private static String TAG = "TimerService";

  private TimerTask task = new TimerTask() {
    @Override
    public void run() {
      Log.d(TAG, "Doing Autosync");
      startService(new Intent(SyncService.ACTION_SYNC));
    }
  };
  private NotificationManager notificationManager;

  @Override
  public void onCreate() {
    timer = new Timer();
    timer.schedule(task, 5000, 15 * 60 * 1000);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {

    notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    Notification notification = new Notification(R.drawable.icon, "DN Autosync Started", System.currentTimeMillis());

    Context context = getApplicationContext();
    CharSequence contentTitle = "DN Autosync Started";
    CharSequence contentText = "DN will now be automatically downloaded";
    Intent notificationIntent = new Intent(this, TimerService.class);
    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

    notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    notificationManager.notify(1, notification);

    return START_STICKY;
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}
