package com.example.myapplication
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 自动生成主键
    val name: String,
    val time: String,
    val location: String,
    val isCompleted: Boolean
)
