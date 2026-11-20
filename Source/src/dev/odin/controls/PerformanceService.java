package dev.odin.controls;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;

/** Foreground heartbeat paired with an independent, leased privileged watchdog. */
public final class PerformanceService extends Service {
    private static final int NOTICE = 3;
    private static final String CHANNEL = "performance";
    private static final ConcurrentHashMap<String, CompletableFuture<String>> requests = new ConcurrentHashMap<>();
    public static volatile boolean running;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> ticker;
    private volatile boolean closing;
    private String token;
    private String status = "Starting safety monitor…";

    static String apply(Context context, String limits) throws Exception {
        ControlNotifications.ensure(context);
        String id = UUID.randomUUID().toString();
        CompletableFuture<String> result = new CompletableFuture<>();
        requests.put(id, result);
        try {
            context.startForegroundService(new Intent(context, PerformanceService.class).putExtra("request", id).putExtra("limits", limits));
            return result.get(20, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            throw new IOException(failure.getCause().getMessage(), failure.getCause());
        } finally {
            requests.remove(id);
        }
    }

    private Notification notice(boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 3, new Intent(this, MainActivity.class).putExtra("performanceSafety", true), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return ControlNotifications.status(this, CHANNEL, "Raven · Performance safety", status).setStyle(new Notification.BigTextStyle().bigText(status))
            .setContentIntent(open).setOngoing(ongoing).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTICE, notice(true));
        if (intent == null || closing) {
            if(intent!=null){
                String requestId=intent.getStringExtra("request");
                CompletableFuture<String> pending=requestId==null?null:requests.get(requestId);
                if(pending!=null)pending.completeExceptionally(new IOException("Safety monitor is restarting. Apply the profile again when ready."));
            }
            if (!running) stopSelf(startId);
            return START_NOT_STICKY;
        }
        String id = intent.getStringExtra("request"), limits = intent.getStringExtra("limits");
        CompletableFuture<String> result = id == null ? null : requests.get(id);
        if (result == null || limits == null) {
            if (!running) stopSelf(startId);
            return START_NOT_STICKY;
        }
        worker.execute(() -> {
            String nextToken = UUID.randomUUID().toString().replace("-", "");
            try {
                String answer = Root.run(this, "performance-guard " + nextToken + " " + limits);
                token = nextToken;
                ControlNotifications.clear(this,ControlNotifications.CPU_ALERT);
                running = true;
                status = "Guard active · recovery at 78°C or loss of monitoring";
                getSystemService(NotificationManager.class).notify(NOTICE, notice(true));
                if (ticker == null) ticker = worker.scheduleWithFixedDelay(this::heartbeat, 1, 3, TimeUnit.SECONDS);
                result.complete(answer);
            } catch (Exception error) {
                try { Root.run(this, "performance-recover " + nextToken + " apply-failed"); }
                catch (Exception recovery) { android.util.Log.e("RavenPerformance", "Recovery verification failed", recovery); }
                result.completeExceptionally(error);
                finishMonitor("Profile was not applied. Check the Performance safety status in Raven.", true);
            }
        });
        return START_NOT_STICKY;
    }

    private void heartbeat() {
        if (closing || token == null) return;
        try {
            String answer = Root.run(this, "performance-heartbeat " + token);
            if (answer.startsWith("OK armed")) return;
            if (answer.startsWith("OK recovery")) {
                finishMonitor("Conservative recovery limits active · " + answer.substring("OK recovery".length()).trim().replace('-', ' '), true);
            } else finishMonitor("Stock performance control restored", false);
        } catch (Exception error) {
            try { Root.run(this, "performance-recover " + token + " monitor-stopped"); }
            catch (Exception recovery) { android.util.Log.e("RavenPerformance", "Privileged watchdog will recover after lease expiry", recovery); }
            finishMonitor("Monitoring interrupted. Conservative recovery requested. Open Raven to check recovery.", true);
        }
    }

    private void finishMonitor(String message, boolean recoveryNotice) {
        if (closing) return;
        closing = true;
        running = false;
        status = message;
        if (ticker != null) ticker.cancel(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (recoveryNotice) ControlNotifications.recovery(this,ControlNotifications.CPU_ALERT,"Raven · Safe recovery",status);
        stopSelf();
        worker.shutdown();
    }

    @Override public void onDestroy() {
        if (!closing) {
            closing = true;
            running = false;
            if (ticker != null) ticker.cancel(false);
            worker.execute(() -> {
                try { if (token != null){Root.run(this, "performance-recover " + token + " monitor-stopped");if(Root.run(this,"performance-status").contains("\nguard=recovery"))ControlNotifications.recovery(this,ControlNotifications.CPU_ALERT,"Raven · Safe recovery","Performance monitoring stopped. Conservative limits were requested; check Performance before restoring stock.");} }
                catch (Exception error) { android.util.Log.e("RavenPerformance", "Privileged lease fallback remains active", error); }
            });
            worker.shutdown();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
