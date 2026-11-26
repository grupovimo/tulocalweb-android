package ar.com.tulocalweb.app.pantallas.inicio

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.com.tulocalweb.app.app.LocalEstado
import ar.com.tulocalweb.app.nucleo.Formato
import ar.com.tulocalweb.app.nucleo.Sugerencia
import ar.com.tulocalweb.app.nucleo.abrirEnNavegador
import ar.com.tulocalweb.app.nucleo.intentar
import ar.com.tulocalweb.app.tema.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Clon de Pantallas/Inicio/TarjetaSugerencia.swift.

/** La sugerencia con su imagen ya bajada: lo que guarda el Inicio (SugerenciaLista en iOS). */
data class SugerenciaLista(val sugerencia: Sugerencia, val imagen: ImageBitmap)

/** La sugerencia de la casa del Inicio (docs/laminas/sugerencia.html): otra app de Grupo Vimo según el rubro de la
 *  tienda, debajo de las acciones y de «Para hacer hoy». Viene de /v1/panel/sugerencia, que las apps viejas no llaman.
 *  Inicio la pide aparte del tablero, una vez por tienda, y trae la imagen antes: si no hay sugerencia, si falla o si
 *  la imagen no baja, no se dibuja nada. La cruz abre «Ocultar por 30 días» y «Ya la uso / No me interesa» (esta
 *  marca, para siempre), en todos los planes; al elegir se va en el acto y el pedido sale atrás. Apagarlas todas es el
 *  interruptor de Cuenta, que sólo está con un plan pago. */
@Composable
fun TarjetaSugerencia(sugerencia: Sugerencia, imagen: ImageBitmap, paleta: PaletaTienda, cerrar: (hasta: String) -> Unit) {
    val estado = LocalEstado.current
    val ctx = LocalContext.current
    val forma = RoundedCornerShape(16.dp)
    // Se dibujó (con la imagen ya bajada): cuenta la vista, una por tienda y día (el GET no cuenta).
    LaunchedEffect(sugerencia.id) { intentar { estado.api.hacer("POST", "/v1/panel/sugerencia/${sugerencia.id}/vista") } }
    Box(Modifier.fillMaxWidth().relieve()) {
        Row(
            Modifier.fillMaxWidth()
                .clickable {
                    val id = sugerencia.id
                    estado.tarea { runCatching { estado.api.hacer("POST", "/v1/panel/sugerencia/$id/clic") } }
                    abrirEnNavegador(ctx, sugerencia.url)
                }
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(14.dp).padding(end = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(imagen, null, Modifier.size(72.dp).clip(forma).background(hex(PaletaTienda.leer(sugerencia.color) ?: 0x15131CL))
                .border(1.dp, C.linea, forma), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Texto(sugerencia.sello, Estilo.footnoteFuerte, C.texto2)
                // Como iOS y la web (text-wrap: balance): «Sumá gente nueva / con tus eventos», sin viudas.
                Texto(Formato.sinViudas(sugerencia.titulo), Estilo(15f, Peso.semibold), C.texto)
                // La flecha es un dibujo: TalkBack lee sólo el texto (como el ícono de la web).
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Texto(sugerencia.cta, Estilo(14f, Peso.bold), paleta.tinta)
                    Icono("arrow.right", 12f, paleta.tinta, Modifier.clearAndSetSemantics {})
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            MenuBoton(listOf(
                OpcionDeMenu("Ocultar por 30 días", "") { cerrar("30d") },
                OpcionDeMenu("Ya la uso / No me interesa", "") { cerrar("siempre") },
            ), "Ocultar sugerencia") {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icono("xmark", 13f, C.texto3) }
            }
        }
    }
}

/** La imagen de la sugerencia, bajada antes de dibujar la tarjeta con el cliente de la API y un tope de 10 s en total
 *  (null si falla o tarda). */
suspend fun bajarImagenSugerencia(http: OkHttpClient, url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }?.asImageBitmap() else null
            }
    }.getOrNull()
}
