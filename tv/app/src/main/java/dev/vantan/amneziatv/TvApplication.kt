package dev.vantan.amneziatv
import android.app.Application
import org.amnezia.vpn.util.Prefs
import org.amnezia.vpn.util.Log
class TvApplication : Application() {
 override fun onCreate() { super.onCreate(); Prefs.init(this); Log.init(this); Log.saveLogs = false }
}
