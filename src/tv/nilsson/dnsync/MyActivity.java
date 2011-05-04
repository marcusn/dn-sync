package tv.nilsson.dnsync;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

public class MyActivity extends PreferenceActivity implements SyncStatus.Listener {
    private SyncService syncService;

    private String TAG = "MyActivity";

    private Handler handler;

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

      handler = new Handler(Looper.getMainLooper());

      //getListView().addFooterView(statusView);

      sendBroadcast(new Intent(AlarmScheduler.ACTION_SCHEDULE));
    }

  @Override
  protected void onStart() {
    super.onStart();

    Intent intent = new Intent(this, SyncService.class);

    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    super.onStart();
  }

   @Override
   protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (syncService != null) {
            syncService.removeSyncStatusListener(MyActivity.this);

            unbindService(mConnection);
            syncService = null;
       }
   }

   private ServiceConnection mConnection = new ServiceConnection() {

      public void onServiceConnected(ComponentName className,
              IBinder service) {
          // We've bound to LocalService, cast the IBinder and get LocalService instance
          SyncService.LocalBinder binder = (SyncService.LocalBinder) service;
          syncService = binder.getService();
          syncService.addSyncStatusListener(MyActivity.this);

          updateStatus();
      }

      public void onServiceDisconnected(ComponentName arg0) {
        syncService = null;
      }
  };

  private void updateStatus() {
    handler.post(new Runnable() {
      public void run() {
        String status = "";
        if (syncService != null) {
          status = syncService.getSyncStatus().getMessage();
        }

        Preference preference = findPreference("sync_enabled");

        preference.setSummary(status);
      }
    });
  }

  public void onSyncStatusChanged() {
    updateStatus();
  }
}
