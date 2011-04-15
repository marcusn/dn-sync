package tv.nilsson.dnsync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

public class AlarmScheduler extends BroadcastReceiver {
  public static final String ACTION_SCHEDULE = "tv.nilsson.dnsync.SCHEDULE";

  public void onReceive(Context context, Intent intent) {
    int SECS = 1000;
    int MINS = 60 * SECS;
    Calendar cal = Calendar.getInstance();
    Intent in = new Intent(AlarmReceiver.ACTION_AUTOSYNC);
    PendingIntent pi = PendingIntent.getBroadcast(context, 0, in, PendingIntent.FLAG_UPDATE_CURRENT);

    AlarmManager alarms = (AlarmManager)context.getSystemService(
      Context.ALARM_SERVICE);
    alarms.cancel(pi);
    alarms.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
            15 * MINS, pi);
  }
}
