package dev.vantan.amneziatv

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.*
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import org.amnezia.vpn.protocol.ProtocolState

class MainActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private lateinit var store:ProfileStore
 private lateinit var root:LinearLayout
 private lateinit var connect:Button
 private lateinit var status:TextView
 private lateinit var server:Button
 private lateinit var settingsButton:Button
 private var error:TextView?=null
 private val bg=Color.rgb(14,14,17)
 private val muted=Color.rgb(135,139,145)
 private val accent=Color.rgb(251,178,106)
 private var selectedName="Сервер не добавлен"
 private fun d(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
  store=ProfileStore(this)
  buildMain()
  handleImport(intent)
  scope.launch { TvVpnService.ui.collectLatest { render(it) } }
 }
 override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);handleImport(intent) }
 private fun handleImport(intent:Intent?) {
  if(intent?.action==Intent.ACTION_VIEW && intent.data?.scheme=="vpn") {
   val raw=intent.dataString ?: return
   // Consume immediately; don't retain the key in the Activity's Intent.
   intent.data=null;setIntent(Intent(this,MainActivity::class.java))
   importText(raw)
  }
 }
 private fun shape(color:Int,r:Int=22,stroke:Int=0)=GradientDrawable().apply { setColor(color);cornerRadius=d(r).toFloat();if(stroke!=0)setStroke(d(3),stroke) }
 private fun text(s:String,size:Float,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply { text=s;textSize=size;setTextColor(color);if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);includeFontPadding=false }
 private fun button(s:String,primary:Boolean=false)=Button(this).apply {
  id=View.generateViewId();text=s;textSize=18f;isAllCaps=false;setTextColor(Color.rgb(215,216,219))
  typeface=Typeface.create("sans-serif",Typeface.NORMAL);minHeight=d(58);setPadding(d(20),d(10),d(20),d(10));isFocusable=true
  stateListAnimator=null;elevation=0f
  background=shape(Color.rgb(28,29,33),12)
  setOnFocusChangeListener { v,focused ->
   v.background=shape(if(focused)Color.rgb(44,45,48) else Color.rgb(28,29,33),12,if(focused)accent else 0)
  }
 }
 private fun connectionBackground(focused:Boolean) = GradientDrawable().apply {
  shape=GradientDrawable.OVAL
  setColor(if(focused)Color.rgb(38,30,26) else bg)
  val connected=TvVpnService.ui.value.state==ProtocolState.CONNECTED
  setStroke(d(if(focused)4 else 2),if(focused || connected)accent else Color.rgb(135,139,145))
 }
 private fun spacer(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,d(h)) }
 private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
 private fun buildMain() {
  root=column().apply { clipChildren=false;clipToPadding=false;setBackgroundColor(bg);setPadding(d(48),d(28),d(48),d(28)) }
  setContentView(root)
  val top=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
  top.addView(ImageView(this).apply{setImageResource(R.drawable.amnezia_mark)},LinearLayout.LayoutParams(d(30),d(30)).apply{rightMargin=d(12)})
  top.addView(text("Amnezia",26f),LinearLayout.LayoutParams(0,-2,1f))
  settingsButton=button("Настройки").apply{setOnClickListener{settings()}}
  top.addView(settingsButton,LinearLayout.LayoutParams(d(148),d(54)));root.addView(top)
  val body=column().apply{gravity=Gravity.CENTER;clipChildren=false;clipToPadding=false}
  status=text("Не подключено",16f,muted).apply{gravity=Gravity.CENTER};body.addView(status)
  body.addView(spacer(14))
  connect=button("Подключиться",true).apply {
   textSize=19f;gravity=Gravity.CENTER;setPadding(d(12),d(35),d(12),d(35))
   val icon=getDrawable(R.drawable.power)!!.mutate().apply{setTint(Color.rgb(215,216,219));setBounds(0,0,d(58),d(58))}
   setCompoundDrawables(null,icon,null,null);compoundDrawablePadding=d(18)
   background=connectionBackground(true)
   setOnFocusChangeListener{v,focused->v.background=connectionBackground(focused)}
   setOnClickListener{toggle()}
  }
  body.addView(connect,LinearLayout.LayoutParams(d(220),d(220)))
  body.addView(spacer(24))
  server=button("Добавить сервер  ›").apply { gravity=Gravity.CENTER;setOnClickListener{servers()} }
  body.addView(server,LinearLayout.LayoutParams(d(360),d(60)))
  body.addView(spacer(12));error=text("",14f,Color.rgb(235,87,87)).apply{gravity=Gravity.CENTER};body.addView(error,LinearLayout.LayoutParams(-1,d(36)))
  root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  refreshServer();render(TvVpnService.ui.value);connect.requestFocus()
 }
 private fun refreshServer() { try { selectedName=store.selected()?.optString("name") ?: "Добавить сервер";server.text="$selectedName    ›" } catch(e:Exception) { server.text="Не удалось открыть серверы";showError("Хранилище ключей недоступно. Данные не удалены.") } }
 private fun render(v:VpnUi) {
  status.text=when(v.state){ProtocolState.CONNECTED->"Подключено";ProtocolState.CONNECTING->"Подключение…";ProtocolState.RECONNECTING->"Восстанавливаем связь…";ProtocolState.DISCONNECTING->"Отключение…";else->"Не подключено"}
  connect.text=when(v.state){ProtocolState.CONNECTED->"Отключиться";ProtocolState.CONNECTING,ProtocolState.RECONNECTING->"Отменить";ProtocolState.DISCONNECTING->"Подождите…";else->"Подключиться"}
  connect.isEnabled=true
  connect.compoundDrawables[1]?.setTint(if(v.state==ProtocolState.CONNECTED)accent else Color.rgb(215,216,219))
  connect.background=connectionBackground(connect.hasFocus())
  error?.text=v.error ?: ""
  if(v.error==null)error?.setTextColor(muted) else error?.setTextColor(Color.rgb(255,175,175))
  server.isEnabled=true
 }
 private fun toggle() {
  val state=TvVpnService.ui.value.state
  if(state==ProtocolState.DISCONNECTING)return
  if(state!=ProtocolState.DISCONNECTED && state!=ProtocolState.UNKNOWN) { startService(Intent(this,TvVpnService::class.java).setAction(TvVpnService.STOP));return }
  try { if(store.selected()==null){importDialog();return} }catch(e:Exception){showError("Не удалось прочитать хранилище серверов");return}
  val consent=VpnService.prepare(this)
  if(consent!=null)startActivityForResult(consent,1) else startVpn()
 }
 private fun startVpn() { startForegroundService(Intent(this,TvVpnService::class.java)) }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==1){if(resultCode==RESULT_OK)startVpn() else showError("VPN не включён: разрешение не предоставлено");connect.post{connect.requestFocus()}}
 }
 private fun showError(message:String) { error?.text=message;error?.setTextColor(Color.rgb(255,175,175)) }
 private fun dialog(title:String,labels:List<String>,actions:List<()->Unit>) {
  val panel=column().apply { clipChildren=false;clipToPadding=false;setPadding(d(28),d(24),d(28),d(24));background=shape(Color.rgb(28,29,33),22) }
  panel.addView(text(title,26f,bold=true));panel.addView(spacer(20))
  val scroll=ScrollView(this);val choices=column().apply{clipChildren=false;clipToPadding=false;setPadding(d(10),d(4),d(10),d(4))};scroll.clipChildren=false;scroll.clipToPadding=false;scroll.addView(choices)
  val dialog=Dialog(this).apply {setContentView(panel);window?.setBackgroundDrawableResource(android.R.color.transparent);window?.setLayout(d(580),-2)}
  val buttons=labels.mapIndexed{index,label->button(label).apply{gravity=Gravity.CENTER_VERTICAL;setOnClickListener{dialog.dismiss();actions[index]()};choices.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})}}
  panel.addView(scroll,LinearLayout.LayoutParams(-1,-2));dialog.show();dialog.window?.setLayout(d(580),d(430).coerceAtMost(resources.displayMetrics.heightPixels-d(60)))
  buttons.firstOrNull()?.requestFocus()
 }
 private fun servers() {
  if(TvVpnService.ui.value.state!=ProtocolState.DISCONNECTED) {
   AlertDialog.Builder(this).setTitle("Выбор сервера").setMessage("Сначала отключим текущий VPN, затем откроем список серверов.")
    .setNegativeButton("Отмена",null).setPositiveButton("Отключить и выбрать"){_,_->
     startService(Intent(this,TvVpnService::class.java).setAction(TvVpnService.STOP))
     scope.launch { withTimeoutOrNull(10000) { TvVpnService.ui.first { it.state==ProtocolState.DISCONNECTED } }?.let { servers() } }
    }.show();return
  }
  try {
   val profiles=store.profiles()
   dialog("Выбор сервера",profiles.map{(if(it.getString("id")==store.selected()?.getString("id"))"✓  " else "")+it.getString("name")}+"＋  Добавить сервер",profiles.map { p -> {store.select(p.getString("id"));refreshServer()} }+{importDialog()})
  } catch(e:Exception){showError("Не удалось открыть серверы")}
 }
 private fun settings() {
  dialog("Настройки",listOf("Добавить сервер по ключу","Удалить выбранный сервер","Открыть обычную Amnezia","Об этом приложении"),listOf(
   {importDialog()},
   {if(TvVpnService.ui.value.state!=ProtocolState.DISCONNECTED)showError("Сначала отключите VPN") else AlertDialog.Builder(this).setTitle("Удалить сервер?").setMessage("Ключ будет удалён только из этой сборки.").setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->runCatching{store.deleteSelected();refreshServer()}.onFailure{showError("Не удалось удалить сервер")}}.show()},
   {try{startActivity(Intent().setClassName("org.amnezia.vpn","org.amnezia.vpn.AmneziaActivity"))}catch(e:Exception){showError("Обычная Amnezia не установлена")}},
   {AlertDialog.Builder(this).setTitle("Amnezia · 0.2").setMessage("Неофициальная сборка на основе Amnezia VPN 5.0.3.0.\nVPN-движок: AmneziaWG из официальной Amnezia.\nПоддерживаются импортированные ключи AmneziaWG, включая AWG 2.\n\nИсходники: github.com/VanTan666/amnezia-client, ветка tv-ui. Лицензия GPL-3.0.\n\nСерверы хранятся зашифрованно на приставке. Телеметрии нет.").setPositiveButton("Понятно",null).show()}
  ))
 }
 private fun importDialog() {
  if(TvVpnService.ui.value.state!=ProtocolState.DISCONNECTED){showError("Сначала отключите VPN");return}
  val input=EditText(this).apply { hint="vpn://…";setSingleLine(false);maxLines=4;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setPadding(d(18),d(18),d(18),d(18));setTextColor(Color.WHITE) }
  val dialog=AlertDialog.Builder(this).setTitle("Добавить сервер").setMessage("Вставьте ключ Amnezia. На приставку также можно открыть ссылку vpn://.").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create()
  dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{if(importText(input.text.toString())){input.text.clear();dialog.dismiss()} else input.error="Не удалось импортировать ключ"}};dialog.show()
 }
 private fun importText(raw:String):Boolean {
  if(TvVpnService.ui.value.state!=ProtocolState.DISCONNECTED){showError("Сначала отключите VPN");return false}
  return try{store.add(raw);refreshServer();Toast.makeText(this,"Сервер добавлен",Toast.LENGTH_SHORT).show();true}catch(e:Exception){showError(if(e is IllegalArgumentException)e.message ?: "Неверный ключ" else "Не удалось сохранить ключ. Проверьте формат и хранилище.");false}
 }
 override fun dispatchKeyEvent(event:KeyEvent):Boolean {
  if(::settingsButton.isInitialized && ::connect.isInitialized && ::server.isInitialized) {
   val focused=currentFocus
   val buttons=listOf(settingsButton,connect,server)
   val index=buttons.indexOf(focused)
   if(index>=0 && event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT)) {
    if(event.action==KeyEvent.ACTION_DOWN) {
     val delta=when(event.keyCode){KeyEvent.KEYCODE_DPAD_UP->-1;KeyEvent.KEYCODE_DPAD_DOWN->1;else->0}
     buttons[(index+delta).coerceIn(0,2)].requestFocus()
    }
    return true
   }
  }
  return super.dispatchKeyEvent(event)
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
