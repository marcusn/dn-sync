package tv.nilsson.dnsync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import java.util.Calendar;

public class MyActivity extends PreferenceActivity
{
    private String TAG = "MyActivity";
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        findPreference("sync").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
          public boolean onPreferenceClick(Preference preference) {

            Log.d(TAG, "Starting SYNC");
            startService(new Intent(SyncService.ACTION_SYNC));

            return true;
          }
        });

      scheduleAlarm();
    }

  private void scheduleAlarm() {
    int SECS = 1000;
    int MINS = 60 * SECS;
    Calendar cal = Calendar.getInstance();
    Intent in = new Intent(AlarmReceiver.ACTION_AUTOSYNC);
    PendingIntent pi = PendingIntent.getBroadcast(this, 0, in, PendingIntent.FLAG_UPDATE_CURRENT);

    AlarmManager alarms = (AlarmManager)getSystemService(
      Context.ALARM_SERVICE);
    alarms.cancel(pi);
    alarms.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
            15 * MINS, pi);
  }
}
