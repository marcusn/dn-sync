package tv.nilsson.dnsync;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

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

  @Override
  public void onCreate() {
    timer = new Timer();
    timer.schedule(task, 5000, 15 * 60 * 1000);
  }

  @Override
  public void onDestroy() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {

    Toast.makeText(this, "DN Autosync Started", Toast.LENGTH_SHORT).show();

    return START_STICKY;
  }

  public IBinder onBind(Intent intent) {
    return null;
  }
}
