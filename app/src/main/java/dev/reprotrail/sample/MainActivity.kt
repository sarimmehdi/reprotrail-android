package dev.reprotrail.sample

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.reprotrail.runtime.ReproTrail
import dev.reprotrail.runtime.ReproTrailConfig
import kotlinx.coroutines.launch

/** Hosts the controlled Android View capture fixture. */
class MainActivity : ComponentActivity() {
    private lateinit var reproTrail: ReproTrail
    private var capturedTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        reproTrail = ReproTrail.create(this, ReproTrailConfig(policyVersion = SAMPLE_POLICY_VERSION))
        val target = findViewById<Button>(R.id.capture_target)
        ReproTrail.setReplayId(target, SAMPLE_REPLAY_ID)
        lifecycleScope.launch { reproTrail.startRecording() }
        target.setOnClickListener {
            lifecycleScope.launch {
                reproTrail.stopRecording()
                val trace = reproTrail.exportLatestTrace()
                capturedTapCount += 1
                findViewById<TextView>(R.id.capture_status).text =
                    getString(R.string.capture_complete, capturedTapCount, trace.absolutePath)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::reproTrail.isInitialized) reproTrail.captureTouchEvent(this, event)
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        if (::reproTrail.isInitialized) reproTrail.close()
        super.onDestroy()
    }

    private companion object {
        const val SAMPLE_POLICY_VERSION = "milestone-3-internal"
        const val SAMPLE_REPLAY_ID = "sample.capture-target"
    }
}
