package com.youriptv.app.refresh

import android.app.job.JobParameters
import android.app.job.JobService
import com.youriptv.app.YourIptvApp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android owns this boot's wake-up; actual refreshes join the same application-owned operations. */
class RefreshJobService : JobService() {
    private class Run(val parameters: JobParameters) {
        val stopped = AtomicBoolean(false)
        var job: Job? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: Run? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as YourIptvApp
        if (!app.isTrustedBuild) return false
        val run = Run(params)
        active = run
        RefreshJobScheduler.started()
        run.job = scope.launch {
            var retry = false
            try { app.refreshCoordinator.runScheduled() }
            catch (_: CancellationException) { retry = true }
            catch (_: Exception) { retry = true }
            finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    if (!run.stopped.get()) jobFinished(params, retry)
                    if (active === run) { active = null; RefreshJobScheduler.stopped() }
                    // A stopped job uses Android's backoff; successful completion schedules the next committed due date.
                    if (!run.stopped.get() && !retry) app.refreshCoordinator.reconcileSchedule()
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        active?.takeIf { it.parameters.jobId == params.jobId }?.let {
            it.stopped.set(true)
            (application as YourIptvApp).refreshCoordinator.stopBackground()
            it.job?.cancel()
        }
        return true
    }

    override fun onDestroy() {
        active?.let {
            it.stopped.set(true)
            (application as YourIptvApp).refreshCoordinator.stopBackground()
        }
        scope.cancel()
        super.onDestroy()
    }
}
