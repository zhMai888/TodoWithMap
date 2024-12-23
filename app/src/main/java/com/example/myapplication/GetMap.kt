package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baidu.mapapi.SDKInitializer
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.CircleOptions
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.Overlay
import com.baidu.mapapi.map.Stroke
import com.baidu.mapapi.model.LatLng
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class GetMap : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var baiduMap: BaiduMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedMarker: Marker? = null
    private var rangeOverlay: Overlay? = null // 圆形范围覆盖物

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 显示隐私协议对话框
        showPrivacyPolicyDialog()
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("隐私政策")
            .setMessage("为了提供定位服务，应用需要同意百度地图的隐私政策。请确认是否同意？")
            .setPositiveButton("同意") { _, _ ->
                // 用户同意隐私政策
                initializeMap()
            }
            .setNegativeButton("拒绝") { _, _ ->
                // 用户拒绝隐私政策
                finish() // 退出应用
            }
            .setCancelable(false) // 禁止取消对话框
            .show()
    }

    private fun initializeMap() {
        // 设置同意隐私协议
        SDKInitializer.setAgreePrivacy(applicationContext, true)

        // 初始化百度地图 SDK
        SDKInitializer.initialize(applicationContext)

        // 设置布局
        setContentView(R.layout.map_activity)

        // 初始化 MapView 和 BaiduMap
        mapView = findViewById(R.id.baiduMapView)
        baiduMap = mapView.map

        // 启用定位功能
        baiduMap.isMyLocationEnabled = true

        // 初始化定位客户端
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 延迟 500ms 后设置地图的初始位置
        Handler(Looper.getMainLooper()).postDelayed({
            // 获取当前位置并设置地图位置
            getCurrentLocation()
        }, 500) // 延迟 500ms，确保地图准备好

        // 设置地图点击事件监听
        baiduMap.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(latLng: LatLng?) {
                latLng?.let {
                    // 移除之前的标记和范围
                    selectedMarker?.remove()
                    rangeOverlay?.remove()

                    // 创建标记图标
                    val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(resizeBitmap(R.drawable.positioning, 100, 100))

                    // 添加新标记，确保图标中心对齐点击位置
                    val markerOptions = MarkerOptions()
                        .position(latLng)  // 点击的位置作为标记的中心
                        .icon(bitmapDescriptor)
                        .anchor(0.5f, 0.5f)  // 确保图标中心对准点击位置

                    selectedMarker = baiduMap.addOverlay(markerOptions) as Marker

                    // 如果存在范围输入，则重新绘制范围
                    val rangeValue = findViewById<EditText>(R.id.rangeInput).text.toString().toDoubleOrNull()
                    if (rangeValue != null) {
                        drawRangeCircle(latLng, rangeValue)
                    }
                }
            }

            override fun onMapPoiClick(p0: com.baidu.mapapi.map.MapPoi?) {
                // 不需要处理地图上的POI点击事件
            }
        })


        // 设置范围输入框的文本变化监听
        val rangeInput: EditText = findViewById(R.id.rangeInput)

        rangeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val rangeValue = s.toString().toDoubleOrNull()
                if (rangeValue != null && selectedMarker != null) {
                    // 如果范围值有效且已选择标记，则绘制范围
                    drawRangeCircle(selectedMarker!!.position, rangeValue)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // 设置确定按钮点击事件
        val confirmButton: Button = findViewById(R.id.confirmButton)
        confirmButton.setOnClickListener {
            // 震动反馈
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
            if (selectedMarker == null) {
                Toast.makeText(this, "请先选择一个位置", Toast.LENGTH_SHORT).show()
            } else {
                val rangeValue = rangeInput.text.toString().toDoubleOrNull()
                if (rangeValue == null) {
                    Toast.makeText(this, "请输入有效的范围值", Toast.LENGTH_SHORT).show()
                } else {
                    // 显示确认位置对话框
                    showLocationSelectionDialog(selectedMarker!!.position)
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // 已确认权限已授予
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    // 获取到当前位置，设置地图的中心点，并添加标记
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    setMapLocation(currentLatLng)
                } else {
                    // 无法获取位置
                    Toast.makeText(this, "无法获取当前位置", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "定位失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 请求定位权限
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun drawRangeCircle(center: LatLng, radius: Double) {
        // 移除旧的范围
        rangeOverlay?.remove()

        // 创建新的圆形覆盖物
        val circleOptions = CircleOptions()
            .center(center)          // 圆心位置
            .radius(radius.toInt())  // 半径（单位：米）
            .fillColor(0x3000FF00)   // 填充颜色（绿色，带透明度）
            .stroke(Stroke(2, 0xFF00FF00.toInt())) // 边框颜色（绿色），边框宽度为2

        // 在地图上添加圆形覆盖物
        rangeOverlay = baiduMap.addOverlay(circleOptions)
    }

    private fun setMapLocation(latLng: LatLng) {
        // 创建 MapStatus，用于设置地图的显示状态
        val mapStatus = MapStatus.Builder()
            .target(latLng)  // 设置地图中心为当前位置
            .zoom(18f)       // 设置缩放级别
            .build()

        // 更新地图状态
        val mapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus)
        baiduMap.setMapStatus(mapStatusUpdate)

        // 创建标记图标
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(resizeBitmap(R.drawable.location, 100, 100))

        // 添加标记
        val markerOptions = MarkerOptions()
            .position(latLng)  // 设置标记位置
            .icon(bitmapDescriptor)
        baiduMap.addOverlay(markerOptions)  // 在地图上添加标记
    }

    private fun showLocationSelectionDialog(latLng: LatLng) {
        AlertDialog.Builder(this)
            .setTitle("选择位置")
            .setMessage("您选择的位置是：\n纬度: ${latLng.latitude}\n经度: ${latLng.longitude}\n是否确认选择该位置？")
            .setPositiveButton("确认") { _, _ ->
                // 确认选择该位置，执行逻辑
//                Toast.makeText(this, "位置已确认：纬度 = ${latLng.latitude}, 经度 = ${latLng.longitude}", Toast.LENGTH_SHORT).show()
                // 返回选择的位置
                val resultIntent = Intent().apply {
                    putExtra("latitude", latLng.latitude)
                    putExtra("longitude", latLng.longitude)
                    putExtra("radius", findViewById<EditText>(R.id.rangeInput).text.toString().toDouble())
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()

                // 清空输入框
                findViewById<EditText>(R.id.rangeInput).text.clear()
                // 移除标记
                selectedMarker?.remove()
                selectedMarker = null
                // 删除范围覆盖物
                rangeOverlay?.remove()
                rangeOverlay = null
            }
            .setNegativeButton("取消") { _, _ ->
                // 取消选择时什么都不用干
            }
            .setCancelable(true)
            .show()
    }

    private fun resizeBitmap(resId: Int, width: Int, height: Int): Bitmap {
        // 获取原始 Bitmap
        val originalBitmap = BitmapFactory.decodeResource(resources, resId)
        // 创建缩放后的 Bitmap
        return Bitmap.createScaledBitmap(originalBitmap, width, height, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保 mapView 已经初始化后再销毁
        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }
    }

    override fun onResume() {
        super.onResume()
        // 确保 mapView 已经初始化后再恢复
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        // 确保 mapView 已经初始化后再暂停
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
    }
}