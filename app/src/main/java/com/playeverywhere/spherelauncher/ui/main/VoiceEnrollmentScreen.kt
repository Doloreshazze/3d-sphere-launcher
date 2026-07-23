package com.playeverywhere.spherelauncher.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playeverywhere.spherelauncher.R
import com.playeverywhere.spherelauncher.audio.SpeechState
import com.playeverywhere.spherelauncher.audio.VoiceViewModel
import com.playeverywhere.spherelauncher.data.AppInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceEnrollmentScreen(
    apps: List<AppInfo>,
    onBack: () -> Unit,
    voiceViewModel: VoiceViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var wakeWord by remember { mutableStateOf("") }
    var appWord by remember { mutableStateOf("") }

    val speechState by voiceViewModel.speechState.collectAsState()
    val recognizedText by voiceViewModel.recognizedText.collectAsState()
    val lastEnrolledPrint by voiceViewModel.lastEnrolledPrint.collectAsState()

    val allEnrolledPrints by voiceViewModel.allEnrolledPrints.collectAsState()

    val micDeniedMsg = stringResource(R.string.voice_mic_permission_required)
    val commandClearedMsg = stringResource(R.string.voice_command_cleared)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (wakeWord.isNotBlank() && appWord.isNotBlank() && selectedApp != null) {
                voiceViewModel.startListeningForEnrollment(wakeWord, appWord, selectedApp!!.packageName)
            }
        } else {
            Toast.makeText(context, micDeniedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        voiceViewModel.initializeListener(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_launch_setup), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x2200F2FE)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF00F2FE),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.voice_english_only_notice),
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }

            if (selectedApp == null) {
                Text(stringResource(R.string.voice_select_app_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(apps) { app ->
                        val enrolledPrint = allEnrolledPrints.find { it.packageName == app.packageName }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2C2C2C))
                                .clickable { selectedApp = app }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(bitmap = app.iconBitmap, contentDescription = app.label, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, color = Color.White, fontSize = 16.sp)
                                if (enrolledPrint != null) {
                                    Text(
                                        text = stringResource(R.string.voice_command_format, enrolledPrint.wakeWordText, enrolledPrint.appWordText),
                                        color = Color.Green,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap = selectedApp!!.iconBitmap, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.voice_configuring_app, selectedApp!!.label),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    label = { Text(stringResource(R.string.voice_wake_word_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = appWord,
                    onValueChange = { appWord = it },
                    label = { Text(stringResource(R.string.voice_app_word_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.voice_press_mic_hint, wakeWord, appWord),
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (wakeWord.isNotBlank() && appWord.isNotBlank()) {
                            if (speechState == SpeechState.LISTENING) {
                                voiceViewModel.stopListening()
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    voiceViewModel.startListeningForEnrollment(wakeWord, appWord, selectedApp!!.packageName)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (speechState == SpeechState.LISTENING) Color.Red else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (speechState == SpeechState.LISTENING) stringResource(R.string.voice_listening) else stringResource(R.string.voice_start_training))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.voice_recognized_format, recognizedText),
                    color = Color.Cyan,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (lastEnrolledPrint != null && lastEnrolledPrint?.packageName == selectedApp?.packageName) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.voice_enrolled_success),
                        color = Color.Green,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                val currentPrint = allEnrolledPrints.find { it.packageName == selectedApp?.packageName }
                if (currentPrint != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            voiceViewModel.deleteEnrollment(selectedApp!!.packageName)
                            Toast.makeText(context, commandClearedMsg, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.voice_delete_command))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                
                OutlinedButton(
                    onClick = { 
                        selectedApp = null 
                        wakeWord = ""
                        appWord = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.voice_select_different_app))
                }
            }
        }
    }
}
