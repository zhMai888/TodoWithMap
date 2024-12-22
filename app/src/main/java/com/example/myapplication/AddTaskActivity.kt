package com.example.myapplication

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.util.Calendar
import kotlin.math.pow

class AddTaskActivity : AppCompatActivity() {

    private lateinit var taskNameInput: EditText
    private lateinit var btnSelectTime: Button
    private lateinit var btnSelectLocation: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private var selectedDateTime: String? = null
    private var selectedLocation: String? = null
    private var isCompleted: Boolean = false // 新任务默认未完成

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)

        // 初始化视图
        taskNameInput = findViewById(R.id.etTaskName)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        btnSelectLocation = findViewById(R.id.btnSelectLocation)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        // 设置按钮事件
        btnSelectTime.setOnClickListener { showDateTimePicker() }
        btnSelectLocation.setOnClickListener { showLocationPicker() }
        btnSave.setOnClickListener { saveTask() }
        btnCancel.setOnClickListener { finish() }
    }

    // 日期和时间选择器
    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // 日期选择器
        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            // 时间选择器
            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                selectedDateTime = String.format(
                    "%04d-%02d-%02d %02d:%02d",
                    selectedYear, selectedMonth + 1, selectedDay, selectedHour, selectedMinute
                )
                btnSelectTime.text = "时间: $selectedDateTime"
            }, hour, minute, true).show()

        }, year, month, day).show()
    }

    // 封装地点
    data class GeoFence(val latitude: Double, val longitude: Double, val radius: Double) {
        // 检查一个点是否在范围内
        fun contains(lat: Double, lon: Double): Boolean {
            val earthRadius = 6371.0 // 地球半径，单位：公里
            val dLat = Math.toRadians(lat - latitude)
            val dLon = Math.toRadians(lon - longitude)

            // 使用哈弗辛公式计算两点间的距离
            val a = Math.sin(dLat / 2).pow(2) +
                    Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat)) *
                    Math.sin(dLon / 2).pow(2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

            val distance = earthRadius * c // 得到的距离，单位：公里
            return distance <= radius // 如果距离小于等于半径，说明在范围内
        }
    }


    // 定义一个 ActivityResultLauncher 来处理返回的数据
    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val latitude = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val longitude = data?.getDoubleExtra("longitude", 0.0) ?: 0.0
            val radius = data?.getDoubleExtra("radius", 0.0) ?: 0.0
            // 创建一个 GeoFence 对象
            val geoFence = GeoFence(latitude, longitude, radius)
            val gson = Gson() // 创建 Gson 实例
            selectedLocation = gson.toJson(geoFence) // 转换为 JSON 字符串
            btnSelectLocation.text = "地点已选择"
            // 提示用户选择地点
            Toast.makeText(this, "选择地点成功", Toast.LENGTH_SHORT).show()
        }
    }

    // 位置选择器
    private fun showLocationPicker() {
        val intent = Intent(this, GetMap::class.java)
        resultLauncher.launch(intent)
    }


    // 保存任务
    private fun saveTask() {
        val taskName = taskNameInput.text.toString()

        // 校验任务名称是否为空
        if (taskName.isEmpty()) {
            taskNameInput.error = "请输入任务内容"
            return
        }

        // 校验是否选择了时间和地点
        if (selectedDateTime == null) {
            Toast.makeText(this, "请选择任务时间", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedLocation == null) {
            Toast.makeText(this, "请选择任务地点", Toast.LENGTH_SHORT).show()
            return
        }

        // 返回任务数据（与 Task 数据类匹配）
        val resultIntent = Intent().apply {
            putExtra("TASK_NAME", taskName)
            putExtra("TASK_TIME", selectedDateTime ?: "")
            putExtra("TASK_LOCATION", selectedLocation)
            putExtra("TASK_COMPLETED", isCompleted) // 默认未完成
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
