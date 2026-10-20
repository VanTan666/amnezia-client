package dev.vantan.amneziatv

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.*
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.amnezia.vpn.protocol.awg.Awg
import org.amnezia.vpn.protocol.ProtocolState
import org.amnezia.vpn.util.net.NetworkState
import org.json.JSONObject

data class VpnUi(val state: ProtocolState=ProtocolState.DISCONNECTED,val name:String="",val error:String?=null)
class TvVpnService : VpnService() {
 companion object { val ui=MutableStateFlow(VpnUi()); const val STOP="stop" }
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val operations=Mutex()
 private val protocol=Awg()
 private val state=MutableStateFlow(ProtocolState.DISCONNECTED)
 private var config:JSONObject?=null
 private var activeJob:Job?=null
 private var timeout:Job?=null
 private lateinit var network:NetworkState
 override fun onCreate() {
  super.onCreate()
  network=NetworkState(this) { reconnect() }
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("vpn","VPN",NotificationManager.IMPORTANCE_LOW))
  scope.launch { state.collect { s ->
   ui.value=ui.value.copy(state=s)
   if(s==ProtocolState.CONNECTED) { timeout?.cancel();network.bindNetworkListener() }
   if(s==ProtocolState.DISCONNECTED) network.unbindNetworkListener()
   if(s!=ProtocolState.DISCONNECTED) getSystemService(NotificationManager::class.java).notify(1,notification())
  } }
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==STOP) { disconnect();return START_NOT_STICKY }
  if(state.value in listOf(ProtocolState.CONNECTED,ProtocolState.CONNECTING,ProtocolState.RECONNECTING,ProtocolState.DISCONNECTING)) return START_NOT_STICKY
  if(Build.VERSION.SDK_INT>=34) startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) else startForeground(1,notification())
  try {
   check(prepare(this)==null) { "Разрешите VPN в системном окне" }
   val profile=ProfileStore(this).selected() ?: error("Выберите сервер")
   config=profile.getJSONObject("config")
   ui.value=VpnUi(ProtocolState.CONNECTING,profile.getString("name"));state.value=ProtocolState.CONNECTING
   activeJob=scope.launch {
    try { withContext(Dispatchers.IO) { operations.withLock {
     protocol.initialize(applicationContext,state) { fail("Ошибка VPN. Попробуйте подключиться снова.") }
     protocol.startVpn(config!!,Builder(),::protect)
    } };startTimeout() }
    catch(e:CancellationException) { throw e }
    catch(e:Exception) { fail("Не удалось подключиться. Проверьте сервер и интернет.") }
   }
  } catch(e:Exception) { fail(e.message ?: "Не удалось открыть сервер") }
  return START_NOT_STICKY
 }
 private fun startTimeout() { timeout?.cancel(); timeout=scope.launch { delay(25000);if(state.value!=ProtocolState.CONNECTED) fail("Сервер не отвечает. Проверьте интернет или выберите другой сервер.") } }
 private fun reconnect() {
  if(state.value!=ProtocolState.CONNECTED)return
  state.value=ProtocolState.RECONNECTING
  activeJob=scope.launch { try { withContext(Dispatchers.IO) { operations.withLock { protocol.reconnectVpn(Builder(),::protect) } };startTimeout() } catch(e:CancellationException){throw e} catch(e:Exception){fail("Связь прервалась. Подключитесь снова.")} }
 }
 private fun fail(message:String) { scope.launch { ui.value=ui.value.copy(error=message);disconnect() } }
 private fun disconnect() {
  timeout?.cancel();timeout=null
  if(state.value==ProtocolState.DISCONNECTING)return
  state.value=ProtocolState.DISCONNECTING
  val previous=activeJob;activeJob=null
  scope.launch {
   previous?.cancelAndJoin()
   withContext(Dispatchers.IO) { operations.withLock { runCatching { protocol.stopVpn() } } }
   state.value=ProtocolState.DISCONNECTED
   ui.value=ui.value.copy(state=ProtocolState.DISCONNECTED)
   stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
  }
 }
 private fun notification():Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,1,Intent(this,TvVpnService::class.java).setAction(STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val text=when(state.value){ProtocolState.CONNECTED->"Подключено";ProtocolState.DISCONNECTING->"Отключение";else->"Подключение"}
  return Notification.Builder(this,"vpn").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("Amnezia · $text").setContentText(ui.value.name).setContentIntent(open).addAction(Notification.Action.Builder(null,"Отключить",stop).build()).setOngoing(true).build()
 }
 override fun onRevoke(){disconnect()}
 override fun onDestroy(){network.unbindNetworkListener();timeout?.cancel();scope.cancel();runBlocking(Dispatchers.IO){operations.withLock{runCatching{protocol.stopVpn()}}};state.value=ProtocolState.DISCONNECTED;ui.value=ui.value.copy(state=ProtocolState.DISCONNECTED);super.onDestroy()}
}
