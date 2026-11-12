package dev.scw.clockbridge;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Runs when the network is back; retries the pending alarm list with backoff. */
public class RetryJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> jobFinished(params, !Sender.flush(this))).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
