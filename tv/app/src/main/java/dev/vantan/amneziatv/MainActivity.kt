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
 private lateinit var hint:TextView
 private lateinit var server:Button
 private lateinit var settingsButton:Button
 private lateinit var light:ImageView
 private var error:TextView?=null
 private val bg=Color.rgb(9,11,18)
 private val muted=Color.rgb(155,164,183)
 private val accent=Color.rgb(183,164,255)
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
  id=View.generateViewId();text=s;textSize=20f;isAllCaps=false;setTextColor(if(primary)bg else Color.WHITE)
  typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);minHeight=d(62);setPadding(d(22),d(12),d(22),d(12));isFocusable=true
  background=shape(if(primary)accent else Color.rgb(29,34,48),16)
  setOnFocusChangeListener { v,focused ->
   v.background=shape(if(primary)accent else if(focused)Color.rgb(45,53,74) else Color.rgb(29,34,48),16,if(focused)Color.WHITE else 0)
   v.animate().scaleX(if(focused)1.035f else 1f).scaleY(if(focused)1.035f else 1f).setDuration(110).start()
  }
 }
 private fun spacer(h:Int)=View(this).apply { layoutParams=LinearLayout.LayoutParams(1,d(h)) }
 private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
 private fun buildMain() {
  root=column().apply { clipChildren=false;clipToPadding=false;setBackgroundColor(bg);setPadding(d(48),d(30),d(48),d(30)) }
  setContentView(root)
  val top=LinearLayout(this).apply { clipChildren=false;clipToPadding=false;gravity=Gravity.CENTER_VERTICAL }
  top.addView(text("Amnezia",28f,bold=true),LinearLayout.LayoutParams(0,-2,1f))
  top.addView(text("TV EDITION",12f,muted),LinearLayout.LayoutParams(d(110),-2))
  val settings=button("Настройки").apply { setOnClickListener{settings()} };settingsButton=settings
  top.addView(settings,LinearLayout.LayoutParams(d(170),d(58)));root.addView(top)
  root.addView(spacer(22))
  val body=LinearLayout(this).apply { clipChildren=false;clipToPadding=false;gravity=Gravity.CENTER_VERTICAL }
  val left=column().apply { setPadding(0,0,d(45),0) }
  left.addView(text("Ваш VPN.\nНа большом экране.",38f,bold=true))
  left.addView(spacer(18));left.addView(text("Выберите сервер и нажмите\n«Подключиться» — всё готово.",19f,muted))
  left.addView(spacer(22));hint=text("Управление стрелками · выбор кнопкой OK",14f,muted);left.addView(hint)
  body.addView(left,LinearLayout.LayoutParams(0,-2,1f))
  val card=column().apply { clipChildren=false;clipToPadding=false;gravity=Gravity.CENTER;setPadding(d(26),d(24),d(26),d(24));background=shape(Color.rgb(20,24,38),26) }
  light=ImageView(this).apply{setImageResource(R.drawable.power);setColorFilter(accent)};card.addView(light,LinearLayout.LayoutParams(d(64),d(64)))
  card.addView(spacer(12));status=text("Не подключено",23f,bold=true).apply{gravity=Gravity.CENTER};card.addView(status)
  card.addView(spacer(18));connect=button("Подключиться",true).apply { setOnClickListener { toggle() } }
  card.addView(connect,LinearLayout.LayoutParams(-1,d(66)))
  card.addView(spacer(12));error=text("",13f,Color.rgb(255,175,175)).apply{gravity=Gravity.CENTER};card.addView(error)
  body.addView(card,LinearLayout.LayoutParams(d(320),-2));root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(spacer(20));server=button("Сервер  ›").apply { gravity=Gravity.CENTER_VERTICAL;setOnClickListener{servers()} }
  root.addView(server,LinearLayout.LayoutParams(-1,d(68)))
  settings.nextFocusDownId=connect.id;settings.nextFocusUpId=settings.id;settings.nextFocusLeftId=settings.id;settings.nextFocusRightId=settings.id
  connect.nextFocusUpId=settings.id;connect.nextFocusDownId=server.id;connect.nextFocusLeftId=connect.id;connect.nextFocusRightId=connect.id
  server.nextFocusUpId=connect.id;server.nextFocusDownId=server.id;server.nextFocusLeftId=server.id;server.nextFocusRightId=server.id
  refreshServer();render(TvVpnService.ui.value);connect.requestFocus()
 }
 private fun refreshServer() { try { selectedName=store.selected()?.optString("name") ?: "Добавить сервер";server.text="Сервер  ·  $selectedName      ›" } catch(e:Exception) { server.text="Не удалось открыть серверы";showError("Хранилище ключей недоступно. Данные не удалены.") } }
 private fun render(v:VpnUi) {
  val busy=v.state in listOf(ProtocolState.CONNECTING,ProtocolState.RECONNECTING,ProtocolState.DISCONNECTING)
  status.text=when(v.state){ProtocolState.CONNECTED->"Подключено";ProtocolState.CONNECTING->"Подключение…";ProtocolState.RECONNECTING->"Восстанавливаем связь…";ProtocolState.DISCONNECTING->"Отключение…";else->"Не подключено"}
  connect.text=when(v.state){ProtocolState.CONNECTED->"Отключиться";ProtocolState.CONNECTING,ProtocolState.RECONNECTING->"Отменить";ProtocolState.DISCONNECTING->"Подождите…";else->"Подключиться"}
  connect.isEnabled=true
  light.setColorFilter(if(v.state==ProtocolState.CONNECTED)Color.rgb(116,233,176) else accent)
  error?.text=v.error ?: if(v.state==ProtocolState.CONNECTED)v.name else ""
  if(v.error==null)error?.setTextColor(muted) else error?.setTextColor(Color.rgb(255,175,175))
  server.isEnabled=true
  hint.text=if(v.state==ProtocolState.CONNECTED)"VPN работает и после выхода из приложения" else "Управление стрелками · выбор кнопкой OK"
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
  val panel=column().apply { clipChildren=false;clipToPadding=false;setPadding(d(28),d(24),d(28),d(24));background=shape(Color.rgb(20,24,38),22) }
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
   {if(TvVpnService.ui.value.state!=ProtocolState.DISCONNECTED)showError("Сначала отключите VPN") else AlertDialog.Builder(this).setTitle("Удалить сервер?").setMessage("Ключ будет удалён только из Amnezia TV.").setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->runCatching{store.deleteSelected();refreshServer()}.onFailure{showError("Не удалось удалить сервер")}}.show()},
   {try{startActivity(Intent().setClassName("org.amnezia.vpn","org.amnezia.vpn.AmneziaActivity"))}catch(e:Exception){showError("Обычная Amnezia не установлена")}},
   {AlertDialog.Builder(this).setTitle("Amnezia TV 0.1").setMessage("Неофициальный TV-форк Amnezia VPN 5.0.3.0.\nVPN-движок: AmneziaWG из официальной Amnezia.\nПоддерживаются импортированные ключи AmneziaWG, включая AWG 2.\n\nИсходники: github.com/VanTan666/amnezia-client, ветка tv-ui. Лицензия GPL-3.0.\n\nСерверы хранятся зашифрованно на приставке. Телеметрии нет.").setPositiveButton("Понятно",null).show()}
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
