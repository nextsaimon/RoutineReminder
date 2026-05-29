package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.RoutineViewModel
import com.example.ui.Screen
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScreen(
    viewModel: RoutineViewModel,
    routineId: Int?, // null means add new, Int means edit
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val routineName by viewModel.editRoutineName
    val tasks = viewModel.editTasks
    val errorText by viewModel.editErrorText

    var rawJsonText by remember { mutableStateOf("") }
    var showJsonPanel by remember { mutableStateOf(false) }

    // File pick launcher for JSON
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val contentStream = context.contentResolver.openInputStream(uri)
                val jsonText = contentStream?.bufferedReader()?.use { it.readText() } ?: ""
                val success = viewModel.parseJsonInput(jsonText)
                if (success) {
                    Toast.makeText(context, "JSON routine loaded!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "JSON Load error - check error message", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (routineId == null) "Add Routine" else "Edit Routine",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Home) },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveRoutine(routineId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .testTag("save_routine_btn")
                            .padding(end = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Error panel if any
            item {
                AnimatedVisibility(visible = errorText != null) {
                    val message = errorText ?: ""
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Routine Name input
            item {
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { viewModel.editRoutineName.value = it },
                    label = { Text("Routine Name") },
                    placeholder = { Text("e.g. University Routine") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // Play 20s Alarm Ringtone toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Play 20s Alarm Ringtone",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Play loud alarm sound for 20 seconds when triggered",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = viewModel.editPlayRingtone.value,
                            onCheckedChange = { viewModel.editPlayRingtone.value = it },
                            modifier = Modifier.testTag("ringtone_toggle")
                        )
                    }
                }
            }

            // JSON helper loader section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Import JSON Schedule Source",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { filePickerLauncher.launch("application/json") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse File", fontSize = 13.sp)
                            }

                            FilledTonalButton(
                                onClick = { showJsonPanel = !showJsonPanel },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (showJsonPanel) Icons.Default.ArrowBack else Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (showJsonPanel) "Hide Raw Input" else "Paste JSON", fontSize = 13.sp)
                            }
                        }

                        AnimatedVisibility(visible = showJsonPanel) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                OutlinedTextField(
                                    value = rawJsonText,
                                    onValueChange = { rawJsonText = it },
                                    label = { Text("Paste JSON Array") },
                                    placeholder = {
                                        Text("[{\n \"id\": 1,\n \"time\": \"06:00\",\n \"days\": [0,2],\n \"title\": \"Wake Up\",\n \"message\": \"Time!\"\n}]")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .testTag("json_raw_input"),
                                    maxLines = 10,
                                    colors = OutlinedTextFieldDefaults.colors()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (rawJsonText.trim().isNotEmpty()) {
                                            val parsed = viewModel.parseJsonInput(rawJsonText)
                                            if (parsed) {
                                                Toast.makeText(context, "JSON loaded into task forms!", Toast.LENGTH_SHORT).show()
                                                showJsonPanel = false
                                            } else {
                                                Toast.makeText(context, "Fail to parse, see errors above", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Raw JSON input field is empty!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_parse_json")
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Parse & Load Tasks Form")
                                }
                            }
                        }
                    }
                }
            }

            // Task list section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scheduled Tasks Forms (${tasks.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    TextButton(onClick = { viewModel.addNewBlankTask() }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Blank Card")
                    }
                }
            }

            // List of editable cards
            itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                TaskEditCard(
                    index = index,
                    task = task,
                    onTimeSelect = {
                        val parts = task.time.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val dialog = android.app.TimePickerDialog(
                            context,
                            { _, selectHour, selectMinute ->
                                val tf = String.format("%02d:%02d", selectHour, selectMinute)
                                viewModel.updateTaskTime(index, tf)
                            },
                            h, m, true
                        )
                        dialog.show()
                    },
                    onTitleChange = { viewModel.updateTaskTitle(index, it) },
                    onMessageChange = { viewModel.updateTaskMessage(index, it) },
                    onDayToggle = { viewModel.toggleDayInTask(index, it) },
                    onDelete = { viewModel.removeTaskAt(index) }
                )
            }
        }
    }
}

@Composable
fun TaskEditCard(
    index: Int,
    task: com.example.data.Task,
    onTimeSelect: () -> Unit,
    onTitleChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onDayToggle: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_edit_card_$index"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Task Identifier & delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reminder Card #${index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Time Selection Form
            Text(
                text = "Alert Time (24 hour HH:MM)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                onClick = onTimeSelect,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("time_picker_card_$index")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.time,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "CHANGE",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Days checklist
            Text(
                text = "Repeat Days Selection",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            // Chips wrapping Sun, Mon, Tue, etc
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayNames.forEachIndexed { i, dayName ->
                    val isSelected = task.days.contains(i)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDayToggle(i) },
                        label = {
                            Text(
                                text = dayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .testTag("day_chip_${index}_$i")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            OutlinedTextField(
                value = task.title,
                onValueChange = onTitleChange,
                label = { Text("Notification Title") },
                placeholder = { Text("e.g. Wake Up") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_title_input_$index"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Message
            OutlinedTextField(
                value = task.message,
                onValueChange = onMessageChange,
                label = { Text("Notification Body Message") },
                placeholder = { Text("e.g. Time to wake up! Ends at 07:15 AM.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("task_msg_input_$index"),
                maxLines = 3
            )
        }
    }
}
