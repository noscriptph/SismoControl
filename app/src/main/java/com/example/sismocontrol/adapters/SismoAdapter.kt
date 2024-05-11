import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.example.sismocontrol.databinding.SismoItemBinding
import com.example.sismocontrol.entities.Sismo

/**
 * Adaptador para manejar la lista de sismos en un RecyclerView.
 * Este adaptador se encarga de mostrar los datos de los sismos en las vistas correspondientes.
 * También proporciona funcionalidad para escuchar clics en los elementos de la lista.
 */
private val TAG = SismoAdapter::class.java.simpleName

class SismoAdapter : RecyclerView.Adapter<SismoAdapter.SismoViewHolder>() {
    lateinit var onItemClickListener: (Sismo) -> Unit
    private lateinit var vibrator: Vibrator // Inicializar Vibrator

    // Atributo de la clase que almacena la lista de sismos
    var sismos = mutableListOf<Sismo>()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            this.notifyDataSetChanged()
        }

    /**
     * Crea una nueva vista de elemento de sismo.
     * Este método es llamado por RecyclerView para crear una nueva vista para mostrar un elemento.
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SismoAdapter.SismoViewHolder {

        val bindingItem =
            SismoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SismoViewHolder(bindingItem)

    }

    /**
     * Actualiza el contenido de la vista de un elemento de sismo.
     * Este método es llamado por RecyclerView para reemplazar el contenido de una vista de elemento de sismo.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: SismoAdapter.SismoViewHolder, position: Int) {
        val sismo: Sismo = sismos[position]
        holder.bind(sismo)
    }

    /**
     * Obtiene el número total de elementos de sismo en la lista.
     */
    override fun getItemCount(): Int {
        return sismos.size
    }

    /**
     * Clase interna que representa una vista de elemento de sismo.
     */
    inner class SismoViewHolder(private var bindingItem: SismoItemBinding) :
        RecyclerView.ViewHolder(bindingItem.root) {

        /**
         * Vincula los datos de un sismo a la vista de elemento de sismo.
         */
        @RequiresApi(Build.VERSION_CODES.O)
        fun bind(sismo: Sismo) {
            with(sismo) {
                bindingItem.magnitudTxt.text = magnitud.toString()
                bindingItem.locacionTxt.text = lugar
                bindingItem.fechaTxt.text = getElapsedTime(sismo.tiempo)
            }

            bindingItem.root.setOnClickListener {
                if (::onItemClickListener.isInitialized)
                    onItemClickListener(sismo)
                else
                    Log.e(TAG, "Listener not initialized")

                // Enviar sismo por WhatsApp
                compartirMensaje(bindingItem.root.context, sismo)
            }
        }

        /**
         * Obtiene el tiempo transcurrido desde el evento de sismo.
         */
        private fun getElapsedTime(eventTime: Long): String {
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime - eventTime

            // Convertir milisegundos a días, horas, minutos y segundos
            val seconds = (elapsedTime / 1000) % 60
            val minutes = (elapsedTime / (1000 * 60)) % 60
            val hours = (elapsedTime / (1000 * 60 * 60)) % 24
            val days = (elapsedTime / (1000 * 60 * 60 * 24))

            // Formatear la cadena de tiempo transcurrido
            val formattedTime = when {
                days > 0 -> "$days día(s) $hours hora(s)"
                hours > 0 -> "$hours hora(s) $minutes minuto(s)"
                minutes > 0 -> "$minutes minuto(s) $seconds segundo(s)"
                else -> "$seconds segundo(s)"
            }

            return "Hace $formattedTime"
        }
    }

    /**
     * Comparte los detalles de un sismo a través de una aplicación externa como WhatsApp.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun compartirMensaje(context: Context, sismo: Sismo) {
        val mensaje =
            "Hola, acaba de haber un temblor en: ${sismo.lugar} de magnitud ${sismo.magnitud}. " +
                    "Aquí tienes la ubicación en Google Maps: " +
                    "https://www.google.com/maps?q=${sismo.latitud},${sismo.longitud}\n\n" +
                    "Para más detalles sobre este temblor, visita: " +
                    "https://earthquake.usgs.gov/earthquakes/"

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, mensaje)

        // Inicializar Vibrator
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, "Compartir mensaje"))
            // Vibrar después de enviar el mensaje
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        100,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                vibrator.vibrate(100)
            }
        } else {
            Toast.makeText(context, "No se puede compartir el mensaje", Toast.LENGTH_LONG).show()
        }
    }
}
