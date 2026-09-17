package com.megamarket.app.ui.screens.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.megamarket.app.ui.components.MegaMarketTopBar
import com.megamarket.app.ui.components.SectionHeader

@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onConfirmPurchase: () -> Unit
) {
    var department by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var attemptedSubmit by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MegaMarketTopBar(
                title = "Finalizar compra",
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader(title = "Dirección de entrega")
            Spacer(modifier = Modifier.height(8.dp))
            CheckoutField(
                value = department,
                onValueChange = { department = it },
                label = "Departamento",
                showError = attemptedSubmit && department.isBlank()
            )
            CheckoutField(
                value = province,
                onValueChange = { province = it },
                label = "Provincia",
                showError = attemptedSubmit && province.isBlank()
            )
            CheckoutField(
                value = district,
                onValueChange = { district = it },
                label = "Distrito",
                showError = attemptedSubmit && district.isBlank()
            )
            CheckoutField(
                value = address,
                onValueChange = { address = it },
                label = "Dirección",
                showError = attemptedSubmit && address.isBlank()
            )
            CheckoutField(
                value = reference,
                onValueChange = { reference = it },
                label = "Referencia",
                showError = false
            )
            CheckoutField(
                value = phone,
                onValueChange = { phone = it },
                label = "Teléfono",
                keyboardType = KeyboardType.Phone,
                showError = attemptedSubmit && phone.isBlank()
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = "Resumen del pedido")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryLine("Subtotal", "S/ --.--")
                    SummaryLine("Delivery", "S/ --.--")
                    SummaryLine("Total", "S/ --.--", emphasize = true)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    attemptedSubmit = true
                    val isValid = listOf(department, province, district, address, phone)
                        .all { it.isNotBlank() }
                    if (isValid) {
                        onConfirmPurchase()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirmar compra")
            }
        }
    }
}

@Composable
private fun CheckoutField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showError: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        label = { Text(label) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) {
            { Text("Campo requerido") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    emphasize: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (emphasize) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
