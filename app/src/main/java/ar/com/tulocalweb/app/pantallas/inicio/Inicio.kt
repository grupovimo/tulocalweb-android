package ar.com.tulocalweb.app.pantallas.inicio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ar.com.tulocalweb.app.app.Destino
import ar.com.tulocalweb.app.app.LocalEstado
import ar.com.tulocalweb.app.app.LocalNavegacion
import ar.com.tulocalweb.app.app.Pestana
import ar.com.tulocalweb.app.nucleo.ErrorApi
import ar.com.tulocalweb.app.nucleo.Formato
import ar.com.tulocalweb.app.nucleo.Guardado
import ar.com.tulocalweb.app.nucleo.PaginaPedidos
import ar.com.tulocalweb.app.nucleo.Preferencias
import ar.com.tulocalweb.app.nucleo.RespuestaSugerencia
import ar.com.tulocalweb.app.nucleo.Sugerencia
import ar.com.tulocalweb.app.nucleo.Tablero
import ar.com.tulocalweb.app.pantallas.mas.AjusteSugerencias
import ar.com.tulocalweb.app.nucleo.errorDe
import ar.com.tulocalweb.app.nucleo.intentar
import ar.com.tulocalweb.app.nucleo.opcional
import ar.com.tulocalweb.app.tema.*
import kotlinx.serialization.Serializable
import java.time.Instant

// Clon de Pantallas/Inicio/Inicio.swift.

/** 2a · Inicio, «La vidriera de tu local» (docs/handoff/inicio-a): arriba la vidriera con el color de la tienda (cuánto
 *  se vendió, el gráfico de hoy contra ayer y «Vender ahora»), la tarjeta de Tincho montada sobre su borde, las acciones
 *  en un bento, lo que hay para hacer hoy, un vistazo de costado, los últimos pedidos, lo más vendido y el resto del
 *  tablero. Al bajar, la vidriera se pliega a una barra. Con /v1/panel/tablero. */
@Composable
fun Inicio() {
    val estado = LocalEstado.current
    val ancho = LocalAnchoPantalla.current
    val oscuro = LocalOscuro.current
    var periodo by remember { mutableStateOf("hoy") }
    var carga by remember { mutableStateOf<Carga<Tablero>>(Carga.cargando) }
    var masViejo by remember { mutableStateOf<Instant?>(null) }
    var sinLeer by remember { mutableStateOf(0) }
    var editarMeta by remember { mutableStateOf(false) }
    var ideasOcultas by remember { mutableStateOf(setOf<String>()) }
    // La sugerencia de la casa con su imagen ya bajada (null: no se dibuja nada). Arranca con la ya pedida para esta
    // tienda y versión del interruptor: volver a la pestaña no la pide de nuevo ni la hace aparecer tarde.
    val claveSugerencia = "${estado.tienda?.id ?: ""}-${estado.versionSugerencias}"
    var sugerencia by remember { mutableStateOf(SugerenciaPedida.lista.takeIf { SugerenciaPedida.clave == claveSugerencia && estado.puede("tienda") }) }
    // El color de la última carga («#RRGGBB|#RRGGBB»), para que el esqueleto ya salga con el de la tienda.
    var colores by remember { mutableStateOf(Preferencias.texto("inicio.colores") ?: "") }
    val se = ancho <= 375f
    val t = carga.valor
    val paleta = remember(t?.acento, t?.sobreAcento, colores, oscuro) {
        val guardados = colores.split("|").filter { it.isNotEmpty() }
        PaletaTienda(acento = t?.acento ?: guardados.firstOrNull(), sobre = t?.sobreAcento ?: guardados.lastOrNull(), oscuro = oscuro)
    }
    // La barra de estado toma el color de la vidriera: íconos claros sobre un acento oscuro. Sólo
    // mientras Inicio está a la vista (las pestañas quedan compuestas aunque no se vean).
    val nav = LocalNavegacion.current
    val vista = LocalView.current
    val aLaVista = nav.pestana == Pestana.inicio && nav.inicio.isEmpty()
    DisposableEffect(aLaVista, paleta.claroEncima, oscuro) {
        val ventana = (vista.context as? android.app.Activity)?.window
        val control = ventana?.let { WindowCompat.getInsetsController(it, vista) }
        if (aLaVista && control != null) control.isAppearanceLightStatusBars = !paleta.claroEncima && !oscuro
        onDispose { if (aLaVista && control != null) control.isAppearanceLightStatusBars = !oscuro }
    }
    val scroll = rememberScrollState()
    var altoVidriera by remember { mutableIntStateOf(0) }
    val umbral = with(LocalDensity.current) { 140.dp.toPx() }
    val plegada by remember { derivedStateOf { altoVidriera > 0 && scroll.value > altoVidriera - umbral } }

    @Serializable data class SinLeer(val sinLeer: Int = 0)

    // Uno detrás del otro, como en iOS, con la imagen antes de dibujar y un tope de 10 s. Cualquier falla: sin tarjeta.
    suspend fun cargarSugerencia(clave: String) {
        // Ya pedida (y la sesión sigue pudiendo verla: otra cuenta en la misma tienda no hereda la tarjeta).
        if (clave == SugerenciaPedida.clave) { sugerencia = SugerenciaPedida.lista.takeIf { estado.puede("tienda") }; return }
        // Otra tienda: la tarjeta de la anterior se va en el acto (y su cruz no cierra la de esta).
        sugerencia = null
        SugerenciaPedida.lista = null
        if (!estado.puede("tienda")) return
        val r = estado.api.opcional<RespuestaSugerencia>("/v1/panel/sugerencia") ?: return
        SugerenciaPedida.clave = clave
        val s = r.sugerencia ?: return
        val imagen = bajarImagenSugerencia(estado.api.http, s.imagen) ?: return
        val l = SugerenciaLista(s, imagen)
        SugerenciaPedida.lista = l
        sugerencia = l
    }

    fun cerrarSugerencia(s: Sugerencia, hasta: String) {
        sugerencia = null
        SugerenciaPedida.lista = null
        // El aviso dice dónde se deshace (el interruptor «Sugerencias» de Cuenta).
        estado.avisar(if (hasta == "siempre") "No te la mostramos más. Se vuelve a prender en Más › Perfil, seguridad y tema"
                      else "La ocultamos por 30 días")
        @Serializable data class Cuerpo(val hasta: String)
        estado.tarea { runCatching { estado.api.hacer("POST", "/v1/panel/sugerencia/${s.id}/cerrar", Cuerpo(hasta)) } }
    }

    // Todas, como el interruptor de Cuenta. Si no se guarda, la tarjeta vuelve.
    fun apagarSugerencias(l: SugerenciaLista) {
        sugerencia = null
        SugerenciaPedida.lista = null
        estado.tarea {
            if (intentar { estado.api.hacer("PUT", "/v1/panel/sugerencias/ajuste", AjusteSugerencias(false)) }) {
                estado.avisar("Sugerencias apagadas. Las prendés de nuevo en Más › Perfil, seguridad y tema")
            } else {
                sugerencia = l
                SugerenciaPedida.lista = l
                estado.avisar("No pudimos apagar las sugerencias", ok = false)
            }
        }
    }

    suspend fun cargar() {
        try {
            val r: Tablero = estado.api.get("/v1/panel/tablero", listOf("periodo" to periodo))
            carga = Carga.listo(r)
            Guardado.guardar(r, "tablero-$periodo")
            val a = r.acento
            val s = r.sobreAcento
            if (a != null && s != null) {
                colores = "$a|$s"
                Preferencias.ponerTexto("inicio.colores", colores)
            }
            if (r.paraHacer.preparar > 0) {
                val l = estado.api.opcional<PaginaPedidos>("/v1/panel/pedidos", listOf("estado" to "Para preparar", "orden" to "viejos", "cuantos" to "1"))
                masViejo = l?.filas?.firstOrNull()?.creado
            }
            estado.api.opcional<SinLeer>("/v1/panel/notificaciones/sin-leer")?.let { sinLeer = it.sinLeer }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val x = errorDe(e)
            val guardado = if (x.sinConexion) Guardado.leer<Tablero>("tablero-$periodo") else null
            if (guardado != null) carga = Carga.listo(guardado.first)
            else if (carga.valor == null || e !is ErrorApi) carga = Carga.error(x)
        }
    }

    Box(Modifier.fillMaxSize().grano(0.06f, BlendMode.Multiply)) {
        Refrescable({ cargar() }) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 32.dp)) {
                Vidriera(carga, periodo, { periodo = it }, sinLeer, paleta, se, Modifier.onSizeChanged { altoVidriera = it.height })
                when (val c = carga) {
                    Carga.cargando -> EsqueletoInicio(paleta)
                    is Carga.error -> Box(Modifier.padding(top = 30.dp).margenPantalla()) { EstadoError("cómo viene el día", c.e) { estado.tarea { cargar() } } }
                    is Carga.listo -> Contenido(c.v, periodo, masViejo, paleta, ideasOcultas, { ideasOcultas = ideasOcultas + it }, { editarMeta = true },
                        sugerencia, { s, h -> cerrarSugerencia(s, h) }, { apagarSugerencias(it) })
                }
            }
        }
        AnimatedVisibility(plegada && t != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            t?.let { BarraVidriera(it, periodo, paleta) }
        }
    }
    LaunchedEffect(periodo, estado.version) { cargar() }
    // La sugerencia va aparte del tablero: no frena el refresco ni se vuelve a pedir con cada venta, período o
    // recarga; sólo al cambiar de tienda o el interruptor.
    LaunchedEffect(claveSugerencia) { cargarSugerencia(claveSugerencia) }
    Hoja(editarMeta, { editarMeta = false }, altoFijo = 470.dp) {
        carga.valor?.let { v -> EditarMeta(v.meta?.objetivo ?: 0, v.meta?.actual ?: 0) { cargar() } }
    }
}

/** La sugerencia ya pedida y para qué tienda y versión del interruptor (sugerenciaPedida en iOS). Vive fuera del
 *  Inicio porque Compose lo descarta al cambiar de pestaña; en iOS el @State sobrevive. */
private object SugerenciaPedida {
    var clave: String? = null
    var lista: SugerenciaLista? = null
}

// MARK: Contenido

@Composable
private fun Contenido(t: Tablero, periodo: String, masViejo: Instant?, paleta: PaletaTienda, ocultas: Set<String>, ocultar: (String) -> Unit, editarMeta: () -> Unit,
                      sugerencia: SugerenciaLista?, cerrarSugerencia: (Sugerencia, String) -> Unit, apagarSugerencias: (SugerenciaLista) -> Unit) {
    val estado = LocalEstado.current
    val cero = Vidriera.enCero(t)
    if (!cero) ideaPrincipal(t, ocultas, ocultar)?.let { TarjetaTincho(it, paleta, Modifier.subir(28.dp).padding(horizontal = 16.dp)) }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp).margenPantalla(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        if (cero) {
            AccionesCero(paleta)
            if (!PrimerosPasos.ocultos(estado.tienda?.id)) PasosPrimeraVenta(paleta)
        } else {
            AccionesInicio(t, paleta)
        }
        ParaHacerHoy(t, masViejo, paleta, cero)
        sugerencia?.let { l -> TarjetaSugerencia(l.sugerencia, l.imagen, paleta, { hasta -> cerrarSugerencia(l.sugerencia, hasta) }, { apagarSugerencias(l) }) }
        DeUnVistazo(t, periodo, paleta, editarMeta)
        if (t.ultimosPedidos.isNotEmpty()) UltimosPedidos(t, paleta)
        if (t.masVendidos.isNotEmpty()) LoMasVendido(t)
        t.visitas?.let { if (it.masVistos.isNotEmpty()) TarjetaMasVistos(it, paleta) }
        t.resenas?.let { if (it.nuevas.isNotEmpty()) TarjetaResenas(it, paleta) }
        t.envios?.let { if (it.paquetes > 0 || it.retiros.isNotEmpty()) TarjetaEnvios(it, paleta) }
        if (estado.puede("cuenta")) TarjetaIntegraciones(paleta)
    }
}

// MARK: Tincho

/** La idea más relevante de Tincho: carritos por recordar, después stock, después compartir; si no hay ninguna, lo más
 *  vendido o una invitación a preguntarle. */
@Composable
private fun ideaPrincipal(t: Tablero, ocultas: Set<String>, ocultar: (String) -> Unit): IdeaTincho? {
    val estado = LocalEstado.current
    val nav = LocalNavegacion.current

    @Serializable data class R(val enviados: Int = 0)

    suspend fun recordarCarritos() {
        try {
            val r: R = estado.api.enviar("POST", "/v1/panel/carritos/recordar", emptyMap<String, String>())
            Haptico.exito()
            if (r.enviados == 0) {
                // Ya se les mandó en las últimas 24 horas (no se repite para no molestar).
                estado.avisar("Ya les mandamos un mail hace menos de un día. Mañana podés volver a recordarles.")
            } else {
                estado.avisar(if (r.enviados == 1) "Listo: le mandamos un mail a 1 cliente" else "Listo: les mandamos un mail a ${r.enviados} clientes")
            }
            ocultar("carritos")
            estado.version += 1
        } catch (e: ErrorApi) { estado.avisar(e.detalle, ok = false) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {}
    }

    val lista = buildList {
        // Ya recordados: el mail salió y no se repite antes de 24 h, así que la idea no vuelve.
        if (t.hornero.carritos > 0 && "carritos" !in t.hornero.ideasHechas && t.hornero.recordados != true) {
            val s = buildAnnotatedString {
                append(if (t.hornero.carritos == 1) "Hay 1 carrito abandonado" else "Hay ${t.hornero.carritos} carritos abandonados")
                val monto = t.hornero.carritosMonto
                if (monto != null && t.montos) {
                    append(" por ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(Formato.pesos(monto)) }
                }
                append(". ¿Les mando un mail para recordarles lo que dejaron?")
            }
            add(IdeaTincho("carritos", s, "Sí, mandales", "¿Qué dejaron en los carritos abandonados?") { recordarCarritos() })
        }
        if (t.paraHacer.sinStock > 0 && "stock" !in t.hornero.ideasHechas) {
            val n = t.paraHacer.sinStock
            add(IdeaTincho("stock", AnnotatedString(if (n == 1) "Un producto se quedó sin stock y sigue visible en la tienda. ¿Le cargás un ingreso?"
                else "$n productos se quedaron sin stock y siguen visibles en la tienda. ¿Les cargás un ingreso?"), "Reponer", "¿Qué productos tengo que reponer?") {
                nav.segmentoProductos = "out"; nav.tocar(Pestana.productos)
            })
        }
        val v = t.visitas
        if (v != null && "compartir" !in t.hornero.ideasHechas) {
            val n = v.dias7.actual.toInt()
            if (v.desde == null || n < 30) {
                add(IdeaTincho("compartir", AnnotatedString(if (v.desde == null || n == 0) "Tu tienda todavía no tuvo visitas. ¿Te armo una imagen con el QR para tus estados de WhatsApp?"
                    else "En 7 días te visitaron ${if (n == 1) "1 persona" else "$n personas"}. Con una historia o un estado llegás a muchas más. ¿La armamos?"),
                    "Armarla", "¿Cómo consigo más visitas en mi tienda?") { nav.inicio.add(Destino.compartir) })
            }
        }
        t.masVendidos.firstOrNull()?.let { p ->
            val s = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p.nombre) }
                append(" es lo que más sale: ${Formato.cantidad(p.unidades, "unidad", "unidades")}. Compartilo para que llegue a más gente.")
            }
            add(IdeaTincho("estrella", s, "Compartir", "¿Cómo viene ${p.nombre}?") { nav.inicio.add(Destino.compartir) })
        }
        add(IdeaTincho("preguntar", AnnotatedString("Preguntame lo que quieras de tu negocio: cuánto vendiste, qué reponer o cómo vienen tus clientes."),
            null, "¿Cómo vengo esta semana?") {})
    }
    return lista.firstOrNull { it.id !in ocultas }
}

/** Indicador chico (radio 18): título 13, valor 22/700 y delta 12/600 coloreado. Lo usan Clientes y las secciones de
 *  Más. */
@Composable
fun Indicador(titulo: String, valor: String, delta: String?, positivo: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().tarjeta(radio = 18.dp).padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Texto(titulo, Estilo.footnote, C.texto2, lineas = 2)
        Texto(valor, Estilo.indicador, C.texto, lineas = 1, numeros = true, escalaMinima = 0.6f)
        if (delta != null) Texto(delta, Estilo.chip, if (positivo) C.okTinta else C.errorTinta, lineas = 1, escalaMinima = 0.8f)
    }
}
