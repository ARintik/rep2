package com.example.kanban

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore("kanban_data")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KanbanBoard()
        }
    }
}

@Serializable
data class Task(
    val id: Int,
    var text: String,
    var isDone: Boolean = false,
    var color: Long = Color.White.value
)

@Serializable
data class ColumnData(
    var name: String,
    var color: Long = Color.LightGray.value,
    val tasks: MutableList<Task> = mutableListOf()
)

@Composable
fun KanbanBoard() {
    val context = LocalContext.current
    var columns by remember { mutableStateOf(mutableListOf<ColumnData>()) }
    var draggingTask by remember { mutableStateOf<Pair<Task, Int>?>(null) }
    val dataKey = stringPreferencesKey("board")

    // Загрузка из DataStore
    LaunchedEffect(true) {
        val saved = runBlocking { context.dataStore.data.first()[dataKey] }
        if (!saved.isNullOrBlank()) {
            columns = Json.decodeFromString<List<ColumnData>>(saved).toMutableStateList()
        }
    }

    // Сохранение
    LaunchedEffect(columns) {
        context.dataStore.edit {
            it[dataKey] = Json.encodeToString(columns)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .weight(1f)) {
            columns.forEachIndexed { index, column ->
                KanbanColumn(
                    column = column,
                    columnIndex = index,
                    onAddTask = { text -> column.tasks.add(Task(column.tasks.size, text)) },
                    onRemoveTask = { task -> column.tasks.remove(task) },
                    onStartDrag = { task -> draggingTask = task to index },
                    onDropTask = { targetIndex ->
                        draggingTask?.let { (task, fromIndex) ->
                            if (targetIndex != fromIndex) {
                                columns[targetIndex].tasks.add(task)
                                columns[fromIndex].tasks.remove(task)
                            }
                            draggingTask = null
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
            }

            IconButton(onClick = {
                columns.add(ColumnData("New Column"))
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Column")
            }
        }
    }
}

@Composable
fun KanbanColumn(
    column: ColumnData,
    columnIndex: Int,
    onAddTask: (String) -> Unit,
    onRemoveTask: (Task) -> Unit,
    onStartDrag: (Task) -> Unit,
    onDropTask: (Int) -> Unit
) {
    var taskText by remember { mutableStateOf("") }
    var editingTitle by remember { mutableStateOf(false) }
    var showColumnColorPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(260.dp)
            .background(Color(column.color), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onDropTask(columnIndex) })
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editingTitle) {
                BasicTextField(
                    value = column.name,
                    onValueChange = { column.name = it },
                    modifier = Modifier
                        .background(Color.White)
                        .padding(4.dp)
                        .weight(1f)
                )
            } else {
                Text(column.name,
                    modifier = Modifier
                        .clickable { editingTitle = true }
                        .weight(1f)
                        .padding(4.dp))
            }
            Button(onClick = { showColumnColorPicker = !showColumnColorPicker }) {
                Text("🎨")
            }
        }

        if (showColumnColorPicker) {
            ColorPicker { selected ->
                column.color = selected.value
                showColumnColorPicker = false
            }
        }

        Spacer(Modifier.height(8.dp))

        column.tasks.forEach { task ->
            var showTaskColorPicker by remember { mutableStateOf(false) }

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(task.color))
                        .padding(6.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onStartDrag(task) }
                            )
                        }
                ) {
                    Checkbox(checked = task.isDone, onCheckedChange = { task.isDone = !task.isDone })
                    Text(task.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemoveTask(task) }) {
                        Icon(Icons.Default.Check, contentDescription = "Remove")
                    }
                    Button(onClick = { showTaskColorPicker = !showTaskColorPicker }) {
                        Text("🎨")
                    }
                }

                if (showTaskColorPicker) {
                    ColorPicker { selected ->
                        task.color = selected.value
                        showTaskColorPicker = false
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row {
            BasicTextField(
                value = taskText,
                onValueChange = { taskText = it },
                modifier = Modifier
                    .background(Color.White)
                    .weight(1f)
                    .padding(4.dp)
            )
            IconButton(onClick = {
                if (taskText.isNotBlank()) {
                    onAddTask(taskText)
                    taskText = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    }
}

@Composable
fun ColorPicker(onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.White, Color.LightGray, Color.Yellow, Color.Cyan,
        Color.Green, Color.Blue, Color.Red, Color.Magenta
    )

    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(4.dp)
                    .background(color, CircleShape)
                    .border(2.dp, Color.Black, CircleShape)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}