package com.motionguard
import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity:Activity(){
 private lateinit var statusText:TextView;private lateinit var valuesText:TextView;private lateinit var actionButton:Button;private lateinit var cueView:MotionCueView
 private var monitoring=false;private var receiverRegistered=false
 private val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){
  if(intent?.action!=MotionMonitorService.ACTION_UPDATE)return
  val state=intent.getStringExtra(MotionMonitorService.EXTRA_STATUS)?:"等待数据"
  val x=intent.getFloatExtra(MotionMonitorService.EXTRA_X,0f);val y=intent.getFloatExtra(MotionMonitorService.EXTRA_Y,0f);val z=intent.getFloatExtra(MotionMonitorService.EXTRA_Z,0f)
  val mag=intent.getFloatExtra(MotionMonitorService.EXTRA_INTENSITY,0f);val rms=intent.getFloatExtra("rms",0f);val count=intent.getIntExtra(MotionMonitorService.EXTRA_SAMPLES,0)
  statusText.text=state;valuesText.text=String.format(Locale.getDefault(),"X %.2f  Y %.2f  Z %.2f m/s²\n瞬时强度 %.2f · 窗口 RMS %.2f m/s²\n样本 %d",x,y,z,mag,rms,count);cueView.setMotion(x,y,mag)
  if(state=="监测已停止"||state=="传感器启动失败"||state=="设备没有可用的加速度传感器")setUiRunning(false)
 }}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);buildUi();requestNotificationPermissionIfNeeded()}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(24,28,24,24)}
  val title=TextView(this).apply{text="晕动舒缓";textSize=28f;gravity=Gravity.CENTER}
  statusText=TextView(this).apply{text="尚未开始监测";textSize=18f;gravity=Gravity.CENTER;setPadding(0,14,0,10)}
  cueView=MotionCueView(this).apply{layoutParams=LinearLayout.LayoutParams(-1,260).apply{topMargin=12}}
  valuesText=TextView(this).apply{text="等待传感器数据";textSize=15f;gravity=Gravity.CENTER;setPadding(0,18,0,18)}
  actionButton=Button(this).apply{text="开始后台监测";setOnClickListener{toggleMonitoring()}}
  val note=TextView(this).apply{text="开启后通过前台服务持续采集运动传感器，切换应用后仍可运行。通知栏可停止监测。状态仅代表设备运动特征，不等于识别出乘车，也不能保证缓解晕动。动态提示目前显示在本应用内，不覆盖其他应用。";textSize=12f;gravity=Gravity.CENTER;setPadding(0,18,0,0)}
  root.addView(title);root.addView(statusText);root.addView(cueView);root.addView(valuesText);root.addView(actionButton);root.addView(note);setContentView(root)
 }
 private fun toggleMonitoring(){if(monitoring){startService(Intent(this,MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_STOP));setUiRunning(false)}else{val i=Intent(this,MotionMonitorService::class.java).setAction(MotionMonitorService.ACTION_START);if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i);setUiRunning(true)}}
 private fun setUiRunning(value:Boolean){monitoring=value;actionButton.text=if(value)"停止监测" else "开始后台监测";if(!value){cueView.setMotion(0f,0f,0f);statusText.text="监测已停止"}else if(statusText.text=="监测已停止"||statusText.text=="尚未开始监测")statusText.text="正在启动后台监测"}
 private fun requestNotificationPermissionIfNeeded(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)}
 override fun onStart(){super.onStart();val f=IntentFilter(MotionMonitorService.ACTION_UPDATE);if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED)else registerReceiver(receiver,f);receiverRegistered=true}
 override fun onStop(){if(receiverRegistered){unregisterReceiver(receiver);receiverRegistered=false};super.onStop()}
}
