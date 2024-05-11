package com.example.sismocontrol

import SismoAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.sismocontrol.databinding.ActivityMainBinding
import com.example.sismocontrol.entities.Sismo
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * MainActivity es la actividad principal que muestra una lista de sismos cargados desde una API.
 * Permite aplicar filtros por país y actualizar la lista de sismos.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sismoAdapter: SismoAdapter
    private val tag = "MainActivity"
    private lateinit var vibrator: Vibrator // Instancia de Vibrator para manejar la vibración

    /**
     * Función que se llama cuando se crea la actividad.
     * Se configura la interfaz de usuario y se inicia la carga de sismos.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recyclerSismos.layoutManager = LinearLayoutManager(this)
        sismoAdapter = SismoAdapter()
        binding.recyclerSismos.adapter = sismoAdapter

        // Configurar el botón de aplicar filtro
        val btnAplicarFiltro = binding.btnAplicarFiltro
        btnAplicarFiltro.setOnClickListener {
            applyFilter()
        }

        // Configurar el campo de texto para que aplique el filtro al presionar "Done" en el teclado
        binding.editTextCountryFilter.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyFilter()
                true
            } else {
                false
            }
        }

        // Solicitar permiso de Internet si no está otorgado
        if (isInternetPermissionGranted()) {
            cargarSismos()
        } else {
            requestInternetPermission()
        }

        // Inicializar Vibrator para manejar la vibración
       //

        // Configurar el botón de actualizar
        val btnActualizar: Button = findViewById(R.id.btnActualizar)
        btnActualizar.setOnClickListener {
            // Actualizar la lista de sismos
            cargarSismos()
            // Vibrar al hacer clic en el botón
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(100)
            }
        }

        // Configurar el SwipeRefreshLayout para "swipe-to-refresh"
        val swipeRefreshLayout: SwipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            // Llama a la función para cargar los sismos nuevamente
            cargarSismos()

            // Detén la animación de "swipe-to-refresh" una vez que los datos se han cargado
            swipeRefreshLayout.isRefreshing = false
        }
    }

    /**
     * Verifica si el permiso de Internet está otorgado.
     */
    private fun isInternetPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Solicita el permiso de Internet al usuario.
     */
    private fun requestInternetPermission() {
        requestPermissionLauncher.launch(Manifest.permission.INTERNET)
    }

    /**
     * Maneja la respuesta del usuario al solicitar el permiso de Internet.
     */
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                cargarSismos()
            } else {
                Toast.makeText(
                    this,
                    "El permiso de Internet es necesario para cargar datos desde la API",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    /**
     * Carga la lista de sismos desde una API en segundo plano.
     */
    private fun cargarSismos() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val urlString = "https://earthquake.usgs.gov/fdsnws/event/1/query"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                // Obtener las fechas para el rango de consulta
                val startDate = getFormattedDate(6) // Fecha de 6 meses atrás
                val endDate = getCurrentFormattedDate() // Fecha actual

                val minLatitude = "-56"
                val maxLatitude = "-17"
                val minLongitude = "-76"
                val maxLongitude = "-66"

                val queryParams = "format=geojson" +
                        "&starttime=$startDate" +
                        "&endtime=$endDate" +
                        "&minlatitude=$minLatitude" +
                        "&maxlatitude=$maxLatitude" +
                        "&minlongitude=$minLongitude" +
                        "&maxlongitude=$maxLongitude"

                val urlWithParams = "$urlString?$queryParams"
                val urlWithParamsObj = URL(urlWithParams)
                val connectionWithParams = urlWithParamsObj.openConnection() as HttpURLConnection
                connectionWithParams.requestMethod = "GET"

                val inputStream = connectionWithParams.inputStream
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))

                val response = StringBuilder()
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                bufferedReader.close()

                // Parsear la respuesta JSON
                val jsonObject = JSONObject(response.toString())
                val jsonArray = jsonObject.getJSONArray("features")
                val sismos = mutableListOf<Sismo>()
                for (i in 0 until jsonArray.length()) {
                    val feature = jsonArray.getJSONObject(i)
                    val properties = feature.getJSONObject("properties")
                    val geometry = feature.getJSONObject("geometry")
                    val lugar = properties.getString("place")
                    val magnitud = properties.getDouble("mag")
                    val latitud = geometry.getJSONArray("coordinates").getDouble(1)
                    val longitud = geometry.getJSONArray("coordinates").getDouble(0)
                    val tiempo = properties.getLong("time")
                    val sismo = Sismo(lugar, magnitud, latitud, longitud, tiempo)
                    sismos.add(sismo)
                }

                withContext(Dispatchers.Main) {
                    sismoAdapter.sismos = sismos
                    if (sismos.isEmpty()) {
                        binding.empty.visibility = View.VISIBLE
                    } else {
                        binding.empty.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error al cargar los sismos: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error al cargar los sismos: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    /**
     * Aplica un filtro a la lista de sismos basado en el país ingresado por el usuario.
     */
    private fun applyFilter() {
        val filterText = binding.editTextCountryFilter.text.toString().trim()
            .lowercase(Locale.getDefault())

        // Filtrar los sismos que contienen el texto ingresado
        val filteredSismos = sismoAdapter.sismos.filter { sismo ->
            sismo.lugar.lowercase(Locale.getDefault()).contains(filterText) || sismo.magnitud.toString().contains(filterText)
        }

        // Actualizar la lista mostrada en la pantalla con los sismos filtrados
        sismoAdapter.sismos = filteredSismos.toMutableList()
    }

    // Función para obtener la fecha actual formateada en el formato esperado
    private fun getCurrentFormattedDate(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    // Función para obtener la fecha hace n meses formateada en el formato esperado
    private fun getFormattedDate(monthsAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -monthsAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }
}
