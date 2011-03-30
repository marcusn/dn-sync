package tv.nilsson.dnsync;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class DownloadCompleteReceiver extends BroadcastReceiver {
  private NotificationManager notificationManager;

  public void onReceive(Context context, Intent intent) {
    notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

    DownloadManager downloadMananger = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

    long downloadId = (Long)intent.getExtras().get(DownloadManager.EXTRA_DOWNLOAD_ID);

    Cursor cursor = downloadMananger.query(new DownloadManager.Query().setFilterById(downloadId));
    cursor.moveToFirst();

    try {
      int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
      if (status != DownloadManager.STATUS_SUCCESSFUL) return;

      String uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
      if (uriString == null) return;

      if (uriString.contains("DN")) {
        Uri localFileName = Uri.parse(uriString);

        Notification notification = new Notification(R.drawable.icon, "DN Downloaded", System.currentTimeMillis());

        CharSequence contentTitle = "DN Downloaded";
        CharSequence contentText = "New DN: " + localFileName.getLastPathSegment();
        Intent notificationIntent = new Intent(Intent.ACTION_VIEW, localFileName);
        notificationIntent.setDataAndType(localFileName, "application/pdf");
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
        notificationManager.notify(1, notification);
      }
    }
    finally {
      cursor.close();
    }

  }
}
