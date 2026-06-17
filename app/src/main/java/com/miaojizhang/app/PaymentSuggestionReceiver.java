package com.miaojizhang.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PaymentSuggestionReceiver extends BroadcastReceiver {
    public static final String ACTION_IGNORE = "com.miaojizhang.app.AUTO_PAYMENT_IGNORE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_IGNORE.equals(intent.getAction())) {
            String id = intent.getStringExtra("auto_payment_id");
            int notificationId = intent.getIntExtra("notification_id", 0);
            AutoPaymentStore.markStatus(context, id, "ignored");
            try {
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && notificationId != 0) nm.cancel(notificationId);
            } catch (Exception ignored) {}
        }
    }
}
