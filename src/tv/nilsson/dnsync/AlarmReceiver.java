package tv.nilsson.dnsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
  public static final String ACTION_AUTOSYNC = "tv.nilsson.dnsync.AUTOSYNC";

  public void onReceive(Context context, Intent intent) {
    context.startService(new Intent(SyncService.ACTION_SYNC));
  }
}
