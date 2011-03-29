package tv.nilsson.dnsync;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

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
            startService(new Intent("tv.nilsson.dnsync.SYNC"));

            return true;
          }
        });

    }
}
