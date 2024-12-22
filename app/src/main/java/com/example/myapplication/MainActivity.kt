package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao

    data class TaskGeoFence(
        val id: Int,
        val name: String,
        val time: String,
        val location: AddTaskActivity.GeoFence,
        val isCompleted: Boolean
    )
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskAdapter: TaskAdapter
    private val tasks = mutableListOf<TaskGeoFence>()
    private val handler = Handler(Looper.getMainLooper())
    private val locationManager: LocationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private var locationListener: LocationListener? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查并请求精准闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // 引导用户去设置页面开启精准闹钟权限
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }


        // 初始化 Room 数据库
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "tasks-database"
        ).allowMainThreadQueries() // 允许在主线程操作（仅用于简单场景，建议使用协程）
            .build()
        taskDao = db.taskDao()

        // 读取任务并显示
        val allTask = taskDao.getAllTasks()
        val gson = Gson()
        tasks.addAll(allTask.map { task ->
            val geoFence = gson.fromJson(task.location, AddTaskActivity.GeoFence::class.java)
            TaskGeoFence(
                id = task.id,
                name = task.name,
                time = task.time,
                location = geoFence,
                isCompleted = task.isCompleted
            )
        })

        // 设置任务提醒
        tasks.forEach { task ->
            if (!task.isCompleted) {
                setTaskReminder(task.id, task.name, task.time)
            }
        }

        // 权限检查
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }

        // 启动定时位置更新
        startLocationUpdates()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupAddTaskButton()

        // 权限检查
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }


        // 检查任务是否为空，显示提示信息
        toggleNoTasksMessage()
    }

    private fun startLocationUpdates() {
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // 在此处理获取到的位置信息
                val latitude = location.latitude
                val longitude = location.longitude
                println("Latitude: $latitude, Longitude: $longitude")
                // 轮询任务列表，检查当前位置是否在任务的地理围栏内
                tasks.forEach { task ->
                        val area = task.location
                        if (area.contains(latitude, longitude)) {
                            println("yes")
                            // 如果在围栏内，显示通知
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            val notification = NotificationCompat.Builder(this@MainActivity, "task_reminder_channel")
                                .setSmallIcon(R.drawable.notify)
                                .setContentTitle("任务提醒: ${task.name}")
                                .setContentText("您已进入任务 ${task.name} 的范围，快去完成它吧!!!")
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true)
                                .build()
                            notificationManager.notify(task.name.hashCode(), notification)
                    }
                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 启动位置更新，每30秒获取一次
        handler.post(locationUpdateRunnable)
    }

    // 定时获取地理位置的Runnable
    private val locationUpdateRunnable = object : Runnable {
        override fun run() {
            // 获取当前位置
            getCurrentLocation()
            // 每30秒轮询一次
            handler.postDelayed(this, 60000)
        }
    }

    // 获取当前地理位置
    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，则不进行操作
            return
        }

        // 获取当前的GPS位置
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (location != null) {
            locationListener?.onLocationChanged(location)
        }
    }




    private fun setTaskReminder(id: Int, name: String, time: String) {
        // 将任务的时间转换为时间戳
        val taskTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(time)?.time ?: return

        val context = this
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("TASK_NAME", name)
            putExtra("TASK_TIME", time)
        }

        val pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 设置定时提醒
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, taskTime, pendingIntent)
    }



    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "task_reminder_channel",
                "Task Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for task reminders"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }



    private fun toggleNoTasksMessage() {
        if (tasks.isEmpty()) {
            binding.tvNoTasks.visibility = View.VISIBLE // 显示 "没有任务" 提示
            binding.recyclerView.visibility = View.GONE  // 隐藏 RecyclerView
        } else {
            binding.tvNoTasks.visibility = View.GONE  // 隐藏 "没有任务" 提示
            binding.recyclerView.visibility = View.VISIBLE  // 显示 RecyclerView
        }
    }



    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(tasks) { position, action ->
            when (action) {
                TaskAction.COMPLETE -> toggleTaskCompletion(position)
                TaskAction.DELETE -> deleteTask(position)
                TaskAction.CLICK -> showTaskDetails(position) // 点击任务时显示详细信息
            }
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = taskAdapter
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private val background = ColorDrawable(Color.RED)
            private val deleteIcon: Drawable? = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)
            private val iconMargin = resources.getDimensionPixelSize(R.dimen.icon_margin)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                deleteTask(position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val iconTop = itemView.top + (itemView.height - (deleteIcon?.intrinsicHeight ?: 0)) / 2
                val iconLeft = itemView.left + iconMargin
                val iconRight = iconLeft + (deleteIcon?.intrinsicWidth ?: 0)
                val iconBottom = iconTop + (deleteIcon?.intrinsicHeight ?: 0)

                background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                background.draw(c)

                deleteIcon?.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                deleteIcon?.draw(c)

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun setupAddTaskButton() {
        binding.fabAddTask.setOnClickListener {
            val intent = Intent(this, AddTaskActivity::class.java)
            startActivityForResult(intent, REQUEST_ADD_TASK)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_TASK && resultCode == Activity.RESULT_OK) {
            val taskName = data?.getStringExtra("TASK_NAME") ?: return
            val taskTime = data.getStringExtra("TASK_TIME")
            val taskLocation = data.getStringExtra("TASK_LOCATION")

            taskLocation?.let { taskTime?.let { it1 ->
                val newTask = Task(name = taskName, time = it1, location = it, isCompleted = false)
                taskDao.insertTask(newTask)  // 插入任务到数据库
                val gson = Gson()
                val geoFence = gson.fromJson(newTask.location, AddTaskActivity.GeoFence::class.java)

                val taskGeoFence = TaskGeoFence(
                    id = newTask.id,
                    name = newTask.name,
                    time = newTask.time,
                    location = geoFence,
                    isCompleted = newTask.isCompleted
                )
                tasks.add(taskGeoFence)
                taskAdapter.notifyItemInserted(tasks.size - 1)
                toggleNoTasksMessage()  // 更新"没有任务"的显示状态

                // 设置任务提醒
                setTaskReminder(newTask.id, newTask.name, newTask.time)

            }}
        }
    }

    private fun showTaskDetails(position: Int) {
        val task = tasks[position]

        // 创建一个 AlertDialog 来显示任务的详细信息
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle(task.name)
            .setMessage(
                "时间: ${task.time}\n" +
                        "地点: \n" +
                        "\t纬度: ${task.location.latitude}\n" +
                        "\t经度: ${task.location.longitude}\n" +
                        "状态: ${if (task.isCompleted) "完成" else "未完成"}"
            )
            .setPositiveButton("OK", null) // 点击OK按钮关闭弹窗
            .show()
    }

    private fun toggleTaskCompletion(position: Int) {
        val task = tasks[position]
        val action = if (task.isCompleted) "标记未完成" else "标记完成"

        showConfirmationDialog(
            title = action,
            message = "你确定要将 \"${task.name}\" $action 吗?",
            onConfirm = {
                val task1 = tasks[position]
                val updatedTask = task1.copy(isCompleted = !task1.isCompleted)
                val gson = Gson()
                val updatedTask1 = Task(
                    id = updatedTask.id,
                    name = updatedTask.name,
                    time = updatedTask.time,
                    location = gson.toJson(updatedTask.location), // 转换 GeoFence 为 JSON 字符串
                    isCompleted = updatedTask.isCompleted
                )

                // 使用协程来确保任务更新在数据库操作后进行
                lifecycleScope.launch {
                    // 在后台线程更新数据库
                    withContext(Dispatchers.IO) {
                        taskDao.updateTask(updatedTask1) // 更新数据库
                    }

                    // 在主线程更新任务列表和 UI
                    withContext(Dispatchers.Main) {
                        tasks[position] = updatedTask
                        taskAdapter.notifyItemChanged(position) // 更新单个任务的显示
                    }

                    // 更新任务提醒
                    if (updatedTask.isCompleted) {
                        // 取消提醒
                        val context = this@MainActivity
                        val intent = Intent(context, ReminderReceiver::class.java)
                        val pendingIntent = PendingIntent.getBroadcast(context, updatedTask.id, intent, PendingIntent.FLAG_IMMUTABLE)
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        alarmManager.cancel(pendingIntent)
                    } else {
                        // 设置提醒
                        setTaskReminder(updatedTask.id, updatedTask.name, updatedTask.time)
                    }
                }
            },
            onCancel = {
                taskAdapter.notifyItemChanged(position)
            }
        )
    }

    private fun deleteTask(position: Int) {
        val taskGeoFence = tasks[position]
        val gson = Gson()

        // 将 TaskGeoFence 转换回 Task
        val task = Task(
            id = taskGeoFence.id,
            name = taskGeoFence.name,
            time = taskGeoFence.time,
            location = gson.toJson(taskGeoFence.location), // 转换 GeoFence 为 JSON 字符串
            isCompleted = taskGeoFence.isCompleted
        )

        showConfirmationDialog(
            title = "删除任务",
            message = "你确定要删除 \"${task.name}\" 吗?",
            onConfirm = {
                // 使用协程确保在后台线程执行数据库操作
                lifecycleScope.launch {
                    // 从数据库删除任务
                    withContext(Dispatchers.IO) {
                        taskDao.deleteTask(task) // 从数据库删除
                    }

                    // 从任务列表中移除任务
                    tasks.removeAt(position)

                    // 更新UI
                    withContext(Dispatchers.Main) {
                        taskAdapter.notifyItemRemoved(position)
                        toggleNoTasksMessage()  // 更新"没有任务"的显示状态
                    }

                    // 取消任务提醒
                    val context = this@MainActivity
                    val intent = Intent(context, ReminderReceiver::class.java)
                    val pendingIntent = PendingIntent.getBroadcast(context, task.id, intent, PendingIntent.FLAG_IMMUTABLE)
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarmManager.cancel(pendingIntent)
                }
            },
            onCancel = {
                taskAdapter.notifyItemChanged(position)
            }
        )
    }


    private fun showConfirmationDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog.Builder(this).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton("YES") { _, _ -> onConfirm() }
            setNegativeButton("NO") { _, _ -> onCancel() }
        }.create().show()
    }

    companion object {
        private const val REQUEST_ADD_TASK = 1
    }
    // 在活动销毁时取消定位请求和停止轮询
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null) // 停止轮询
        locationManager.removeUpdates(locationListener!!) // 停止位置更新
    }
}
