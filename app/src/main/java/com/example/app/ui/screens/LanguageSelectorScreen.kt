package com.example.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.UserPreferencesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    
    val targetLanguage by repository.targetLanguageFlow.collectAsState(initial = "en")

    val supportedLanguages = listOf(
        "en" to "English",
        "pt" to "Portuguese",
        "es" to "Spanish",
        "de" to "German",
        "ja" to "Japanese",
        "ko" to "Korean"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Language") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(supportedLanguages) { (code, name) ->
                ListItem(
                    headlineContent = { Text(name) },
                    trailingContent = {
                        if (code == targetLanguage) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.clickable {
                        scope.launch {
                            repository.setTargetLanguage(code)
                            onBack()
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
