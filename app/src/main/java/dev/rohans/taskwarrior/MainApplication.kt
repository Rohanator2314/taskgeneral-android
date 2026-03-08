package dev.rohans.taskwarrior

import android.app.Application

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application initialization will happen here
        // Rust bindings will be initialized in future tasks
    }
}
