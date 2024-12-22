package com.example.myapplication

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView


enum class TaskAction {
    COMPLETE, DELETE, CLICK
}


class TaskAdapter(
    private val tasks: List<MainActivity.TaskGeoFence>,
    private val onTaskAction: (Int, TaskAction) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task)

        // 添加点击事件，点击任务项时触发
        holder.itemView.setOnClickListener {
            onTaskAction(position, TaskAction.CLICK) // 触发点击事件
        }
    }



    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskName: TextView = itemView.findViewById(R.id.tvTaskName)
        private val taskCheckBox: CheckBox = itemView.findViewById(R.id.cbTaskCompleted)

        fun bind(task: MainActivity.TaskGeoFence) {
            taskName.text = task.name
            taskCheckBox.setOnCheckedChangeListener(null) // 解除旧的监听器，避免触发状态变更

            // 根据任务状态设置 CheckBox 和文本
            taskCheckBox.isChecked = task.isCompleted
            taskCheckBox.isChecked = task.isCompleted
            if (task.isCompleted) {
                taskName.paintFlags = taskName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                // 将任务名称设置为灰色
                taskName.setTextColor(ContextCompat.getColor(itemView.context, R.color.grey))
            } else {
                taskName.paintFlags = taskName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                // 将任务名称设置为黑色
                taskName.setTextColor(Color.BLACK)
            }


            // 添加新的监听器
            taskCheckBox.setOnCheckedChangeListener { _, _ ->
                onTaskAction(adapterPosition, TaskAction.COMPLETE)
            }

            itemView.setOnLongClickListener {
                onTaskAction(adapterPosition, TaskAction.DELETE)
                true
            }
        }
    }
}

