package org.videopocket.probe.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdManager {
    const val AD_UNIT_ID = "ca-app-pub-5797893761930988/7623691681"

    private var initialized = false
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MobileAds.initialize(context) {
                    loadInterstitial(context)
                }
            } catch (e: Exception) {
                Log.e("AdManager", "Error initializing AdMob", e)
            }
        }
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isAdLoading) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onClosed: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onClosed()
                }
            }
            ad.show(activity)
        } else {
            loadInterstitial(activity)
            onClosed()
        }
    }
}
