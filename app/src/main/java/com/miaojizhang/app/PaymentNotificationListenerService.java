package com.miaojizhang.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import java.util.LinkedHashSet;
import java.util.Set;

public class PaymentNotificationListenerService extends NotificationListenerService {
    public static final String ACTION_RECORD = "com.miaojizhang.app.AUTO_PAYMENT_RECORD";
    public static final String CHANNEL_ID = "miaojizhang_auto_payment";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        Context context = getApplicationContext();
        if (!AutoPaymentStore.isEnabled(context)) return;
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(context.getPackageName())) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        String text = collectNotificationText(n.extras);
        if (text.length() == 0) return;

        PaymentParser.Suggestion suggestion = PaymentParser.parse(pkg, appName(pkg), "", text, sbn.getPostTime());
        if (suggestion == null) return;
        if (AutoPaymentStore.contains(context, suggestion.id)) return;

        AutoPaymentStore.add(context, suggestion, "pending");
        showSuggestionNotification(context, suggestion);
    }

    private String collectNotificationText(Bundle extras) {
        Set<String> parts = new LinkedHashSet<>();
        addPart(parts, extras.getCharSequence(Notification.EXTRA_TITLE));
        addPart(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        addPart(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        addPart(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        addPart(parts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) addPart(parts, line);
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.toString();
    }

    private void addPart(Set<String> parts, CharSequence s) {
        if (s == null) return;
        String v = s.toString().trim();
        if (v.length() > 0) parts.add(v);
    }

    private String appName(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label == null ? pkg : label.toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "收付款识别提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("从系统通知中识别收付款金额后提醒确认记账");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private static void showSuggestionNotification(Context context, PaymentParser.Suggestion s) {
        ensureChannel(context);
        int notificationId = 730000 + Math.abs((s.id == null ? 0 : s.id.hashCode()) % 200000);
        String typeText = "income".equals(s.type) ? "收入" : "支出";
        String amountText = "¥" + String.format(java.util.Locale.CHINA, "%.2f", s.amount);

        Intent recordIntent = new Intent(context, MainActivity.class);
        recordIntent.setAction(ACTION_RECORD);
        recordIntent.putExtra("auto_payment_json", s.toJsonString());
        recordIntent.putExtra("auto_payment_id", s.id);
        recordIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent recordPi = PendingIntent.getActivity(context, notificationId, recordIntent, flags);

        Intent ignoreIntent = new Intent(context, PaymentSuggestionReceiver.class);
        ignoreIntent.setAction(PaymentSuggestionReceiver.ACTION_IGNORE);
        ignoreIntent.putExtra("auto_payment_id", s.id);
        ignoreIntent.putExtra("notification_id", notificationId);
        PendingIntent ignorePi = PendingIntent.getBroadcast(context, notificationId + 1, ignoreIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("检测到一笔" + typeText + " " + amountText)
                .setContentText("来源：" + s.source + " · " + s.note)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("来源：" + s.source + "\n备注：" + s.note + "\n点击“记一笔”后自动填入金额、类型、备注和时间。"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(recordPi)
                .addAction(android.R.drawable.ic_menu_edit, "记一笔", recordPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "忽略", ignorePi);

        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            // Android 13+ 若用户未允许本应用发通知，这里会失败；监听仍可工作，前端会继续显示历史。
        } catch (Exception ignored) {}
    }
}
