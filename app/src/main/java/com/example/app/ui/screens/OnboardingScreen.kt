package com.example.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { UserPreferencesRepository(context) }
    
    var currentStep by remember { mutableStateOf(0) }
    
    val steps = listOf(
        "Welcome to OverRead!\nCapture and process text on your screen easily.",
        "Privacy First: We process everything locally. No screens are uploaded.",
        "Overlay Permission: We need permission to show the floating button over your apps.",
        "Capture Permission: We only capture the screen when you tap the button."
    )

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = steps[currentStep],
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).wrapContentHeight()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 0) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }
                
                Button(
                    onClick = {
                        if (currentStep < steps.lastIndex) {
                            currentStep++
                        } else {
                            scope.launch {
                                repository.setOnboardingCompleted(true)
                                onFinish()
                            }
                        }
                    }
                ) {
                    Text(if (currentStep == steps.lastIndex) "Get Started" else "Next")
                }
            }
        }
    }
}
