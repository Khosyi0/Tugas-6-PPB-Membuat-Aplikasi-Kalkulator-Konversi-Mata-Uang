package com.example.currencyconverter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CurrencyConverterApp()
                }
            }
        }
    }
}

data class CurrencyResponse(
    val success: Boolean,
    val rates: Map<String, Double>
)

interface CurrencyApi {
    @GET("latest")
    suspend fun getLatestRates(@Query("base") base: String): CurrencyResponse
}

data class CurrencyUiState(
    val rates: Map<String, Double> = emptyMap(),
    val amount: Double = 0.0,
    val fromCurrency: String = "USD",
    val toCurrency: String = "IDR",
    val result: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CurrencyViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CurrencyUiState())
    val uiState: StateFlow<CurrencyUiState> = _uiState.asStateFlow()

    private val apiKey = "rFeNGxt48OcMbIXqJrpOaYfSiVHW6Cba"
    private val api: CurrencyApi

    val currencies = listOf("USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "CNY", "IDR", "SGD")

    init {
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", apiKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.apilayer.com/exchangerates_data/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        api = retrofit.create(CurrencyApi::class.java)

        fetchCurrencyRates()
    }

    private fun fetchCurrencyRates() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val response = api.getLatestRates("EUR")

                if (response.success) {
                    _uiState.value = _uiState.value.copy(
                        rates = response.rates,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to fetch rates"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun updateAmount(amount: String) {
        val amountValue = amount.toDoubleOrNull() ?: 0.0
        _uiState.value = _uiState.value.copy(amount = amountValue)
    }

    fun updateFromCurrency(currency: String) {
        _uiState.value = _uiState.value.copy(fromCurrency = currency)
    }

    fun updateToCurrency(currency: String) {
        _uiState.value = _uiState.value.copy(toCurrency = currency)
    }

    fun convertCurrency() {
        val rates = _uiState.value.rates
        val fromCurrency = _uiState.value.fromCurrency
        val toCurrency = _uiState.value.toCurrency
        val amount = _uiState.value.amount

        if (rates.isEmpty() || !rates.containsKey(fromCurrency) || !rates.containsKey(toCurrency)) {
            _uiState.value = _uiState.value.copy(error = "Currency rates not available")
            return
        }

        val baseCurrency = "EUR"

        val fromRate = if (fromCurrency == baseCurrency) 1.0 else rates[fromCurrency] ?: 1.0
        val toRate = if (toCurrency == baseCurrency) 1.0 else rates[toCurrency] ?: 1.0

        val result = if (fromCurrency == baseCurrency) {
            amount * toRate
        } else {
            val eurAmount = amount / fromRate
            eurAmount * toRate
        }


        _uiState.value = _uiState.value.copy(result = result, error = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyConverterApp() {
    val viewModel: CurrencyViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Currency Converter",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = if (uiState.amount == 0.0) "" else uiState.amount.toString(),
            onValueChange = { viewModel.updateAmount(it) },
            label = { Text("Amount") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        CurrencyDropdown(
            label = "From Currency",
            selectedCurrency = uiState.fromCurrency,
            currencies = viewModel.currencies,
            onCurrencySelected = { viewModel.updateFromCurrency(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        CurrencyDropdown(
            label = "To Currency",
            selectedCurrency = uiState.toCurrency,
            currencies = viewModel.currencies,
            onCurrencySelected = { viewModel.updateToCurrency(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.convertCurrency() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Convert")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.result > 0) {
            Text(
                text = "Result: ${String.format("%.2f", uiState.result)} ${uiState.toCurrency}",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        uiState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    label: String,
    selectedCurrency: String,
    currencies: List<String>,
    onCurrencySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(text = label)
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(selectedCurrency)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency) },
                    onClick = {
                        onCurrencySelected(currency)
                        expanded = false
                    }
                )
            }
        }
    }
}