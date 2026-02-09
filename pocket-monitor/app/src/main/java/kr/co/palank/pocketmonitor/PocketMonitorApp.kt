package kr.co.palank.pocketmonitor

import android.app.Application
import com.google.android.gms.ads.MobileAds
import kr.co.palank.pocketmonitor.ad.AppOpenAdManager

class PocketMonitorApp : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        appOpenAdManager = AppOpenAdManager()
        appOpenAdManager.init(this)
    }
}
