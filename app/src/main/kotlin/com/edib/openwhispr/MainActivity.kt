// Modified in OpenWispr-S1 from EdiBianco/OpenWhispr. See ATTRIBUTION.md.
package com.edib.openwhispr

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.tabs.TabLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var statusSubtitle: TextView
    private lateinit var audioRow: LinearLayout
    private lateinit var audioRowSub: TextView
    private lateinit var audioDot: View
    private lateinit var accRow: LinearLayout
    private lateinit var accRowSub: TextView
    private lateinit var accDot: View
    private lateinit var accCaption: TextView
    private lateinit var batteryRow: LinearLayout
    private lateinit var batteryRowSub: TextView
    private lateinit var batteryDot: View
    private lateinit var setupCollapsedRow: LinearLayout
    private lateinit var setupCollapsedRowSub: TextView
    private lateinit var setupDoneSummary: TextView
    private lateinit var keyRowSub: TextView
    private lateinit var customInstructionsRowSub: TextView
    private lateinit var customInstructionsRow: LinearLayout
    private lateinit var writingProfileRowSub: TextView
    private lateinit var historyRowSub: TextView
    private lateinit var overlayColorRowSub: TextView
    private lateinit var modelContainer: LinearLayout
    private lateinit var voiceCommandsDetailContainer: LinearLayout
    private lateinit var triggerPhraseRowSub: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var statusContainer: LinearLayout
    private lateinit var dictationContainer: LinearLayout
    private lateinit var settingsContainer: LinearLayout

    private val modelRows = mutableMapOf<String, ModelRowViews>()
    private var batteryWarningShown = false
    private var setupExpanded = false

    private data class ModelRowViews(
        val radio: MaterialRadioButton,
        val progress: LinearProgressIndicator,
        val subtitle: TextView,
        val dlBtn: MaterialButton
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Best-effort: lets the background service show its "still running"
        // notification (Android 13+ requires this permission for any
        // notification, including the foreground-service one). Not gated on
        // anything -- dictation works fine without it, this just makes the
        // service more likely to survive being swiped from Recents.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        checkForUpdate()

        val outer = vertical(0, 0)

        // Top large header (like "Connected devices"), with the app icon
        // alongside the name.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(64), dp(24), dp(24))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
        })
        header.addView(TextView(this).apply {
            text = "OpenWispr"
            textSize = 32f
        })
        outer.addView(header)

        tabLayout = TabLayout(this).apply {
            addTab(newTab().setText("Estado"))
            addTab(newTab().setText("Dictado"))
            addTab(newTab().setText("Ajustes"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) { showTab(tab.position) }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
        outer.addView(tabLayout)

        statusContainer = vertical(0)
        dictationContainer = vertical(0)
        settingsContainer = vertical(0)

        // ================= Status tab =================

        val statusRow = settingsRow("Estado", "Comprobando...")
        statusSubtitle = statusRow.findViewWithTag("subtitle")
        statusContainer.addView(statusRow)

        // --- Setup checklist card ---
        setupCollapsedRow = settingsRow("Configuración", "Comprobando...") {
            setupExpanded = !setupExpanded
            refresh()
        }
        setupCollapsedRowSub = setupCollapsedRow.findViewWithTag("subtitle")
        statusContainer.addView(setupCollapsedRow)

        setupDoneSummary = TextView(this).apply {
            textSize = 14f
            setTextColor(DOT_GREEN)
            setPadding(dp(24), 0, dp(24), dp(8))
        }
        statusContainer.addView(setupDoneSummary)

        audioDot = statusDot()
        audioRow = settingsRow("Permiso de micrófono", "Comprobando...", leading = audioDot) {
            if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        audioRowSub = audioRow.findViewWithTag("subtitle")
        statusContainer.addView(audioRow)

        accDot = statusDot()
        accRow = settingsRow("Servicio de accesibilidad", "Comprobando...", leading = accDot) {
            val alreadyEnabled = WhisperAccessibilityService.instance != null
            if (!alreadyEnabled && android.os.Build.VERSION.SDK_INT >= 33) {
                showRestrictedSettingsHelp()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        accRowSub = accRow.findViewWithTag("subtitle")
        statusContainer.addView(accRow)

        accCaption = TextView(this).apply {
            text = "Necesario para detectar el campo de texto activo e insertar ahí el texto procesado."
            textSize = 12f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            alpha = 0.8f
            setPadding(dp(24), 0, dp(24), dp(12))
        }
        statusContainer.addView(accCaption)

        batteryDot = statusDot()
        batteryRow = settingsRow("Optimización de batería", "Comprobando...", leading = batteryDot) {
            requestBatteryExemption()
        }
        batteryRowSub = batteryRow.findViewWithTag("subtitle")
        statusContainer.addView(batteryRow)

        // --- Background service ---
        val serviceEnabled = prefs().getBoolean("service_master_enabled", true)
        val serviceSwitch = MaterialSwitch(this).apply {
            isChecked = serviceEnabled
            isClickable = false
        }
        val serviceRow = settingsRow(
            "Servicio en segundo plano",
            "Pausa la burbuja sin desactivar Accesibilidad",
            serviceSwitch
        ) {
            val newVal = !serviceSwitch.isChecked
            prefs().edit().putBoolean("service_master_enabled", newVal).apply()
            serviceSwitch.isChecked = newVal
            WhisperAccessibilityService.instance?.refreshMasterEnabled()
        }
        statusContainer.addView(serviceRow)

        // ================= Dictation tab =================

        dictationContainer.addView(sectionHeader("Motor"))

        val isCloud = !prefs().getBoolean("use_local", true)
        val cloudSwitch = MaterialSwitch(this).apply {
            isChecked = isCloud
            isClickable = false
        }
        val cloudRow = settingsRow("Usar transcripción en la nube", "Requiere una clave API de Groq", cloudSwitch) {
            val newCloud = !cloudSwitch.isChecked
            prefs().edit().putBoolean("use_local", !newCloud).apply()
            cloudSwitch.isChecked = newCloud
            refresh()
        }
        dictationContainer.addView(cloudRow)

        modelContainer = vertical(0)
        modelContainer.addView(sectionHeader("Modelos locales"))
        for (m in MODEL_CATALOG) modelContainer.addView(buildModelRow(m))
        dictationContainer.addView(modelContainer)

        dictationContainer.addView(sectionHeader("Postprocesado"))

        val isPostProcessing = prefs().getBoolean("use_post_processing", false)
        val postProcessSwitch = MaterialSwitch(this).apply {
            isChecked = isPostProcessing
            isClickable = false
        }
        val postProcessRow = settingsRow("Limpiar transcripción", "Usa Groq para corregir gramática y puntuación", postProcessSwitch) {
            val newVal = !postProcessSwitch.isChecked
            prefs().edit().putBoolean("use_post_processing", newVal).apply()
            postProcessSwitch.isChecked = newVal
            refresh()
        }
        dictationContainer.addView(postProcessRow)

        customInstructionsRow = settingsRow("Instrucciones globales", "Se aplican además del perfil de escritura") {
            promptCustomInstructions()
        }
        customInstructionsRowSub = customInstructionsRow.findViewWithTag("subtitle")
        customInstructionsRowSub.maxLines = 2
        customInstructionsRowSub.ellipsize = android.text.TextUtils.TruncateAt.END
        dictationContainer.addView(customInstructionsRow)

        val writingProfileRow = settingsRow(
            "Perfil de escritura",
            WritingProfiles.label(prefs().getString("writing_profile", WritingProfiles.AUTO) ?: WritingProfiles.AUTO)
        ) { showWritingProfileDialog() }
        writingProfileRowSub = writingProfileRow.findViewWithTag("subtitle")
        writingProfileRowSub.maxLines = 2
        dictationContainer.addView(writingProfileRow)

        dictationContainer.addView(settingsRow(
            "Cambio temporal de perfil",
            "Mantén pulsada la burbuja o el punto; se mantiene hasta salir de la app activa"
        ))

        dictationContainer.addView(settingsRow(
            "Duración máxima por dictado",
            "5 minutos · aviso a los 4:30 y procesamiento automático al llegar al límite"
        ))

        val historyRow = settingsRow(
            "Historial de dictados",
            "Hasta ${DictationHistory.MAX_ITEMS} textos guardados solo en este dispositivo"
        ) { showHistoryDialog() }
        historyRowSub = historyRow.findViewWithTag("subtitle")
        dictationContainer.addView(historyRow)

        dictationContainer.addView(sectionHeader("Comandos de voz"))

        val isVoiceCommands = prefs().getBoolean("voice_commands_enabled", false)
        val voiceCommandsSwitch = MaterialSwitch(this).apply {
            isChecked = isVoiceCommands
            isClickable = false
        }
        val voiceCommandsRow = settingsRow(
            "Comandos de voz",
            "Usa una frase de activación para resumir, traducir y más",
            voiceCommandsSwitch
        ) {
            val newVal = !voiceCommandsSwitch.isChecked
            prefs().edit().putBoolean("voice_commands_enabled", newVal).apply()
            voiceCommandsSwitch.isChecked = newVal
            refresh()
        }
        dictationContainer.addView(voiceCommandsRow)

        voiceCommandsDetailContainer = vertical(0)

        val triggerPhraseRow = settingsRow("Frase de activación", "Toca para cambiarla") { promptTriggerPhrase() }
        triggerPhraseRowSub = triggerPhraseRow.findViewWithTag("subtitle")
        voiceCommandsDetailContainer.addView(triggerPhraseRow)

        val examplesRow = settingsRow("Ejemplos de comandos", "Consulta qué puedes decir") { showCommandExamples() }
        voiceCommandsDetailContainer.addView(examplesRow)

        dictationContainer.addView(voiceCommandsDetailContainer)

        // ================= Settings tab =================

        settingsContainer.addView(sectionHeader("Ajustes"))

        val keyRow = settingsRow("Clave API de Groq", "Toca para configurarla") { promptApiKey() }
        keyRowSub = keyRow.findViewWithTag("subtitle")
        settingsContainer.addView(keyRow)

        settingsContainer.addView(sectionHeader("Burbuja flotante"))

        val minimizeSwitch = MaterialSwitch(this).apply {
            isChecked = prefs().getBoolean("minimize_to_dot", false)
            isClickable = false
        }
        settingsContainer.addView(settingsRow(
            "Minimizar a punto",
            "Muestra un punto pequeño en lugar de ocultarse por completo",
            minimizeSwitch
        ) {
            val enabled = !minimizeSwitch.isChecked
            minimizeSwitch.isChecked = enabled
            prefs().edit().putBoolean("minimize_to_dot", enabled).apply()
            WhisperAccessibilityService.instance?.refreshOverlaySettings()
        })

        lateinit var bubbleSizeSub: TextView
        val bubbleSizeRow = settingsRow(
            "Tamaño de burbuja",
            "${prefs().getInt("bubble_size_dp", 44)} dp"
        ) {
            showIntSlider("Tamaño de burbuja", "bubble_size_dp", 32, 72, 44, " dp") { value ->
                bubbleSizeSub.text = "$value dp"
            }
        }
        bubbleSizeSub = bubbleSizeRow.findViewWithTag("subtitle")
        settingsContainer.addView(bubbleSizeRow)

        lateinit var dotSizeSub: TextView
        val dotSizeRow = settingsRow(
            "Tamaño del punto",
            "${prefs().getInt("dot_size_dp", 10)} dp"
        ) {
            showIntSlider("Tamaño del punto", "dot_size_dp", 6, 32, 10, " dp") { value ->
                dotSizeSub.text = "$value dp"
            }
        }
        dotSizeSub = dotSizeRow.findViewWithTag("subtitle")
        settingsContainer.addView(dotSizeRow)

        lateinit var dotTimeoutSub: TextView
        val dotTimeoutSeconds = prefs().getInt("dot_timeout_seconds", 3).coerceIn(0, 10)
        val dotTimeoutRow = settingsRow(
            "Tiempo visible del punto",
            if (dotTimeoutSeconds == 0) "Siempre" else "$dotTimeoutSeconds s"
        ) {
            showDotTimeoutDialog { seconds ->
                dotTimeoutSub.text = if (seconds == 0) "Siempre" else "$seconds s"
            }
        }
        dotTimeoutSub = dotTimeoutRow.findViewWithTag("subtitle")
        settingsContainer.addView(dotTimeoutRow)

        lateinit var inactiveAlphaSub: TextView
        val inactiveAlphaRow = settingsRow(
            "Transparencia en reposo",
            "${prefs().getInt("overlay_alpha_percent", 70)}%"
        ) {
            showIntSlider("Transparencia en reposo", "overlay_alpha_percent", 20, 100, 70, "%") { value ->
                inactiveAlphaSub.text = "$value%"
            }
        }
        inactiveAlphaSub = inactiveAlphaRow.findViewWithTag("subtitle")
        settingsContainer.addView(inactiveAlphaRow)

        val overlayColorRow = settingsRow(
            "Color en reposo",
            overlayColorLabel(prefs().getInt("overlay_idle_color", 0xDD1C1C1E.toInt()))
        ) { showOverlayColorDialog() }
        overlayColorRowSub = overlayColorRow.findViewWithTag("subtitle")
        settingsContainer.addView(overlayColorRow)

        val systemOverlaySwitch = MaterialSwitch(this).apply {
            isChecked = prefs().getBoolean("system_overlay_enabled", false) && Settings.canDrawOverlays(this@MainActivity)
            isClickable = false
        }
        settingsContainer.addView(settingsRow(
            "Capa del sistema opcional",
            "Usa «Mostrar sobre otras apps» como respaldo adicional",
            systemOverlaySwitch
        ) {
            if (systemOverlaySwitch.isChecked) {
                prefs().edit().putBoolean("system_overlay_enabled", false).apply()
                systemOverlaySwitch.isChecked = false
                WhisperAccessibilityService.instance?.rebuildOverlayForTypeChange()
            } else if (Settings.canDrawOverlays(this)) {
                prefs().edit().putBoolean("system_overlay_enabled", true).apply()
                systemOverlaySwitch.isChecked = true
                WhisperAccessibilityService.instance?.rebuildOverlayForTypeChange()
            } else {
                prefs().edit().putBoolean("system_overlay_enabled", true).apply()
                try {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) {
                    toast("No se pudo abrir el permiso de superposición: ${e.message}")
                }
            }
        })

        settingsContainer.addView(settingsRow(
            "Restaurar burbuja",
            "Recrea ahora la burbuja o el punto"
        ) {
            WhisperAccessibilityService.instance?.restoreOverlay()
                ?: toast("El servicio de accesibilidad no está activo")
        })

        settingsContainer.addView(sectionHeader("Acerca de"))

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "desconocida"
        } catch (e: Exception) {
            "desconocida"
        }
        settingsContainer.addView(settingsRow("Versión", versionName))
        settingsContainer.addView(settingsRow("Canal", "Canary 5 · desarrollo"))
        settingsContainer.addView(settingsRow("Proyecto / mantenedor", "@sromero78"))

        settingsContainer.addView(settingsRow("GitHub", "Ver código fuente y versiones") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sromero78/OpenWhispr-S1")))
            } catch (e: Exception) {
                toast("No se pudo abrir el navegador: ${e.message}")
            }
        })

        settingsContainer.addView(settingsRow("Buscar actualizaciones", "Comprobar ahora") {
            checkForUpdate(force = true)
        })

        outer.addView(statusContainer)
        outer.addView(dictationContainer)
        outer.addView(settingsContainer)
        showTab(0)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(attrColor(android.R.attr.colorBackground))
            addView(outer)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                nav.bottom + dp(16)
            )
            insets
        }
        setContentView(scrollView)

        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); refresh()
    }

    private fun showTab(index: Int) {
        statusContainer.visibility = if (index == 0) View.VISIBLE else View.GONE
        dictationContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsContainer.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    // --- Model Rows ---

    private fun buildModelRow(model: Model): View {
        val radio = MaterialRadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(attrColor(androidx.appcompat.R.attr.colorPrimary))
        }
        val dlBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            text = "↓"
            textSize = 18f
            setTextColor(attrColor(androidx.appcompat.R.attr.colorPrimary))
        }

        val progress = LinearProgressIndicator(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(4)).apply {
                topMargin = dp(8)
            }
        }

        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dlBtn)
            addView(radio)
        }

        val row = settingsRow(
            if (model.recommended) model.name else model.name,
            "${model.quality} · ${model.sizeMb} MB",
            rightContainer
        ) {
            onModelAction(model)
        }

        val textContainer = row.getChildAt(0) as LinearLayout
        textContainer.addView(progress)

        modelRows[model.archive] = ModelRowViews(
            radio, progress, textContainer.findViewWithTag("subtitle"), dlBtn
        )
        refreshCard(model)

        return row
    }

    private fun onModelAction(model: Model) {
        val views = modelRows[model.archive] ?: return

        if (ModelDownloader.isInstalled(this, model)) {
            selectModel(model.archive)
            return
        }

        views.dlBtn.isEnabled = false
        views.progress.visibility = View.VISIBLE
        views.progress.isIndeterminate = false
        views.subtitle.text = "Iniciando descarga..."

        ModelDownloader.download(this, model) { state ->
            runOnUiThread {
                when (state) {
                    is DownloadState.Downloading -> {
                        views.progress.progress = (state.progress * 100).toInt()
                        views.subtitle.text = "Downloading: ${(state.progress * 100).toInt()}%"
                    }
                    is DownloadState.Extracting -> {
                        views.progress.isIndeterminate = true
                        views.subtitle.text = "Extrayendo..."
                    }
                    is DownloadState.Done -> {
                        views.progress.visibility = View.GONE
                        selectModel(model.archive)
                        toast("${model.name} listo")
                    }
                    is DownloadState.Error -> {
                        views.progress.visibility = View.GONE
                        views.subtitle.text = "Error: ${state.message}"
                        views.dlBtn.isEnabled = true
                    }
                }
            }
        }
    }

    private fun selectModel(archive: String) {
        prefs().edit().putString("model_name", archive).apply()
        WhisperAccessibilityService.instance?.reloadModel()
        refreshAllCards(); refresh()
    }

    private fun refreshCard(model: Model) {
        val views = modelRows[model.archive] ?: return
        val active = prefs().getString("model_name", "") == model.archive
        val installed = ModelDownloader.isInstalled(this, model)

        views.radio.isChecked = active
        views.radio.visibility = if (installed) View.VISIBLE else View.GONE
        views.dlBtn.visibility = if (installed) View.GONE else View.VISIBLE

        if (views.progress.visibility == View.GONE) {
            views.subtitle.text = "${model.quality} · ${model.sizeMb} MB"
        }
    }

    private fun refreshAllCards() = MODEL_CATALOG.forEach { refreshCard(it) }

    // --- State Updates ---

    private fun refresh() {
        val audio = hasPerm(Manifest.permission.RECORD_AUDIO)
        val acc = WhisperAccessibilityService.instance != null
        val useLocal = prefs().getBoolean("use_local", true)
        val usePostProcessing = prefs().getBoolean("use_post_processing", false)
        val hasKey = !prefs().getString("api_key", "").isNullOrBlank()
        val hasModel = LocalTranscriber.availableModels(this).isNotEmpty()
        val unrestricted = isIgnoringBatteryOptimizations()

        audioRowSub.text = if (audio) "Concedido" else "Toca para conceder el permiso"
        accRowSub.text = if (acc) "Activado" else "Toca para activarlo en Ajustes"
        batteryRowSub.text = if (unrestricted)
            "Sin restricciones — Android no debería cerrarlo para ahorrar batería"
        else
            "Toca para permitir actividad en segundo plano (recomendado)"

        // --- Setup checklist card ---
        val allOk = audio && acc && unrestricted
        val doneCount = listOf(audio, acc, unrestricted).count { it }

        setupCollapsedRow.visibility = if (allOk) View.VISIBLE else View.GONE
        setupCollapsedRowSub.text = if (setupExpanded) "Toca para contraer" else "Toca para revisar"

        setupDoneSummary.visibility = if (!allOk && doneCount > 0) View.VISIBLE else View.GONE
        setupDoneSummary.text = "✓ $doneCount de 3 pasos listos"

        fun rowVisibility(ok: Boolean) =
            if (!ok || (allOk && setupExpanded)) View.VISIBLE else View.GONE

        audioRow.visibility = rowVisibility(audio)
        accRow.visibility = rowVisibility(acc)
        accCaption.visibility = accRow.visibility
        batteryRow.visibility = rowVisibility(unrestricted)

        audioDot.background = dotDrawable(if (audio) DOT_GREEN else DOT_RED)
        accDot.background = dotDrawable(if (acc) DOT_GREEN else DOT_RED)
        batteryDot.background = dotDrawable(if (unrestricted) DOT_GREEN else DOT_RED)

        modelContainer.visibility = if (useLocal) View.VISIBLE else View.GONE
        customInstructionsRow.visibility = if (usePostProcessing) View.VISIBLE else View.GONE

        val voiceCommandsEnabled = prefs().getBoolean("voice_commands_enabled", false)
        voiceCommandsDetailContainer.visibility = if (voiceCommandsEnabled) View.VISIBLE else View.GONE
        triggerPhraseRowSub.text = "\"${prefs().getString("command_trigger_phrase", "Comando Whisper")}\""

        val apiKey = prefs().getString("api_key", "") ?: ""
        keyRowSub.text = if (apiKey.isBlank()) "Toca para configurarla"
                         else if (apiKey.length > 7) "gsk_...${apiKey.takeLast(4)}"
                         else "gsk_...***"

        val customInstructions = prefs().getString("custom_instructions", "") ?: ""
        customInstructionsRowSub.text = if (customInstructions.isBlank())
            "Toca para añadir reglas adicionales"
        else
            customInstructions.replace("\n", " ")

        val writingProfile = prefs().getString("writing_profile", WritingProfiles.AUTO) ?: WritingProfiles.AUTO
        writingProfileRowSub.text = if (writingProfile == WritingProfiles.AUTO)
            "Automático · según la app activa"
        else
            WritingProfiles.label(writingProfile)
        val historyCount = DictationHistory.load(prefs()).size
        historyRowSub.text = if (historyCount == 0)
            "Sin dictados guardados"
        else
            "$historyCount de ${DictationHistory.MAX_ITEMS} dictados guardados"
        overlayColorRowSub.text = overlayColorLabel(
            prefs().getInt("overlay_idle_color", 0xDD1C1C1E.toInt())
        )

        val cur = prefs().getString("model_name", "") ?: ""
        if (cur.isBlank() || !File(filesDir, "models/$cur").exists()) {
            MODEL_CATALOG.firstOrNull { ModelDownloader.isInstalled(this, it) }
                ?.let { selectModel(it.archive) }
        }

        // Ready logic
        val localReady = useLocal && hasModel
        val cloudReady = !useLocal && hasKey
        val postReady = !usePostProcessing || hasKey
        val ready = audio && acc && (localReady || cloudReady) && postReady

        statusSubtitle.text = if (ready) "Listo — toca el punto para dictar" else "Configuración pendiente"
        statusSubtitle.setTextColor(if (ready) attrColor(androidx.appcompat.R.attr.colorPrimary) else attrColor(android.R.attr.textColorSecondary))

        refreshAllCards()
        maybeShowBatteryWarning(acc, unrestricted)
    }

    /** Android 13+ silently disables the Accessibility toggle for apps
     * installed outside the Play Store ("Ajustes restringidos"), with no
     * explanation in the Settings UI itself -- it just looks broken. Walks
     * the user through unlocking it before sending them to the system
     * screen, instead of letting them hit a dead end and assume the app
     * doesn't work. */
    private fun showRestrictedSettingsHelp() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Un paso adicional en Android 13+")
            .setMessage(
                "Android blocks this permission by default for apps installed outside the Play Store -- that's normal, not a bug.\n\n" +
                "If the Accessibility toggle looks greyed out or won't switch on:\n" +
                "1. Long-press the OpenWispr icon -> App info\n" +
                "2. Tap the \u22ee menu (top right) -> \"Allow restricted settings\"\n" +
                "3. Come back and enable Accessibility as usual"
            )
            .setPositiveButton("Abrir ajustes de Accesibilidad") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- Battery optimization ---

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        if (isIgnoringBatteryOptimizations()) { toast("Ya está sin restricciones"); return }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: Exception) {
            // Some OEMs block the direct per-app request intent -- fall back
            // to the general battery-optimization list where the user can
            // find OpenWispr and exempt it manually.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                toast("No se pudieron abrir los ajustes de batería: ${e2.message}")
            }
        }
    }

    /** Checks this repo's GitHub Releases. No backend involved. Shows a
     * dialog linking to the release page when a newer version is
     * available. Runs automatically (and silently, when nothing's new)
     * once per app-open; [force] bypasses the cache interval and always
     * gives feedback, for the manual "Check for updates" row. */
    private fun checkForUpdate(force: Boolean = false) {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        } ?: return

        UpdateChecker.checkForUpdate(prefs(), currentVersion, force) { info ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (info != null) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Actualización disponible")
                        .setMessage(
                            buildString {
                                append("OpenWispr ${info.version} está disponible. Tienes $currentVersion.")
                                if (!info.notes.isNullOrBlank()) {
                                    append("\n\nWhat's new:\n")
                                    append(info.notes)
                                }
                            }
                        )
                        .setPositiveButton("Actualizar") { _, _ -> downloadAndInstallUpdate(info) }
                        .setNegativeButton("Más tarde", null)
                        .show()
                } else if (force) {
                    toast("Ya tienes la última versión (v$currentVersion)")
                }
            }
        }
    }

    /** Downloads the release's .apk directly in-app and hands it to the
     * system installer -- no browser tab, no external navigation. The final
     * "install this app?" confirmation is a mandatory Android system dialog
     * for a sideloaded APK and can't be skipped, but everything up to that
     * point (download, progress) happens invisibly inside OpenWispr. */
    private fun downloadAndInstallUpdate(info: UpdateChecker.UpdateInfo) {
        val apkUrl = info.apkUrl
        if (apkUrl == null) {
            // Release has no .apk asset (shouldn't normally happen) -- fall
            // back to the release page rather than doing nothing.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
            } catch (e: Exception) {
                toast("No se pudo abrir el navegador: ${e.message}")
            }
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permitir instalar actualizaciones")
                .setMessage("Para instalar actualizaciones desde la app, permite que OpenWispr instale aplicaciones desconocidas en la siguiente pantalla. Después vuelve y pulsa Actualizar de nuevo.")
                .setPositiveButton("Continuar") { _, _ ->
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (e: Exception) {
                        toast("No se pudieron abrir los ajustes: ${e.message}")
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        toast("Descargando actualización…")
        UpdateChecker.downloadApk(this, apkUrl) { file, error ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (file == null) {
                    toast("Download failed: ${error ?: "desconocida error"}")
                    return@runOnUiThread
                }
                installApk(file)
            }
        }
    }

    private fun installApk(file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("No se pudo iniciar el instalador: ${e.message}")
        }
    }

    /** Nags the user, once per app-open, if the accessibility service is on
     * but Android is still free to kill it to save battery -- this is the
     * single biggest cause of the overlay silently disappearing until the
     * user re-opens the app. */
    private fun maybeShowBatteryWarning(accessibilityEnabled: Boolean, unrestricted: Boolean) {
        if (!accessibilityEnabled || unrestricted || batteryWarningShown) return
        batteryWarningShown = true
        android.app.AlertDialog.Builder(this)
            .setTitle("Mantener el dictado activo")
            .setMessage(
                "El ahorro de batería de Android puede cerrar el servicio en segundo plano de OpenWispr " +
                "y hacer desaparecer la burbuja hasta que vuelvas a abrir la app.\n\n" +
                "Permite que funcione sin restricciones para mantenerla disponible.\n\n" +
                "En algunos móviles (Samsung, Xiaomi, OnePlus y otros) también puede ser necesario " +
                "permitir el inicio automático o excluir OpenWispr de los sistemas de suspensión " +
                "de aplicaciones del fabricante."
            )
            .setPositiveButton("Quitar restricciones") { _, _ -> requestBatteryExemption() }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun promptApiKey() {
        val link = TextView(this).apply {
            text = android.text.Html.fromHtml(
                "¿No tienes una? Consigue una clave gratuita en <a href=\"https://console.groq.com/keys\">console.groq.com/keys</a>",
                android.text.Html.FROM_HTML_MODE_LEGACY
            )
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        }
        val input = EditText(this).apply {
            hint = "gsk_..."
            setText(prefs().getString("api_key", ""))
        }
        val container = vertical(dp(24), dp(8)).apply {
            addView(link)
            addView(input)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Clave API de Groq")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                prefs().edit().putString("api_key", input.text.toString().trim()).apply()
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptCustomInstructions() {
        // The base cleanup prompt itself is fixed in PostProcessor and never
        // shown here -- this only lets the user append their own extra
        // refinements on top of it (see PostProcessor.effectivePrompt).
        val input = EditText(this).apply {
            hint = "Ej.: escribe siempre las siglas CNC en mayúsculas"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setText(prefs().getString("custom_instructions", ""))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Instrucciones globales")
            .setMessage("Se aplican a todos los perfiles de escritura, además de las reglas internas de limpieza de OpenWispr. No sustituyen las reglas de seguridad, formato ni autocorrección.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Guardar") { _, _ ->
                prefs().edit().putString("custom_instructions", input.text.toString().trim()).apply()
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptTriggerPhrase() {
        val input = EditText(this).apply {
            hint = "Comando Whisper"
            setText(prefs().getString("command_trigger_phrase", "Comando Whisper"))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Frase de activación")
            .setMessage("Di esta frase al principio de una grabación para entrar en modo comando en lugar de dictado normal.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Guardar") { _, _ ->
                val phrase = input.text.toString().trim()
                prefs().edit()
                    .putString("command_trigger_phrase", if (phrase.isBlank()) "Comando Whisper" else phrase)
                    .apply()
                refresh()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCommandExamples() {
        val trigger = prefs().getString("command_trigger_phrase", "Comando Whisper") ?: "Comando Whisper"
        val message = """
            Di la frase de activación y después la orden. Se aplicará al texto que ya esté en el campo o al texto que dictes dentro del propio comando:

            • "$trigger, resume esto en dos frases"
            • "$trigger, mejora la fluidez"
            • "$trigger, tradúcelo al italiano"
            • "$trigger, ponlo en un tono más formal"
            • "$trigger, conviértelo en una lista"

            Puedes encadenar varias: "$trigger, tradúcelo al italiano y conviértelo en una lista".
        """.trimIndent()
        android.app.AlertDialog.Builder(this)
            .setTitle("Ejemplos de comandos")
            .setMessage(message)
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showWritingProfileDialog() {
        val current = prefs().getString("writing_profile", WritingProfiles.AUTO) ?: WritingProfiles.AUTO
        val checked = WritingProfiles.keys.indexOf(current).coerceAtLeast(0)

        android.app.AlertDialog.Builder(this)
            .setTitle("Perfil de escritura")
            .setMessage(WritingProfiles.examplesText() + "\n\nEn Automático, OpenWispr usa un perfil conversacional en mensajería y Formal en las principales apps de correo. Mantén pulsada la burbuja para forzar otro perfil solo durante la sesión en la app actual.")
            .setSingleChoiceItems(WritingProfiles.labels, checked) { dialog, which ->
                val key = WritingProfiles.keys[which]
                prefs().edit().putString("writing_profile", key).apply()
                writingProfileRowSub.text = WritingProfiles.label(key)
                dialog.dismiss()
                if (key == WritingProfiles.CUSTOM) promptCustomProfileInstructions()
                else if (key != WritingProfiles.AUTO) promptProfileExample(key)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptProfileExample(key: String) {
        val prefKey = WritingProfiles.examplePreferenceKey(key)
        val current = prefs().getString(prefKey, "")?.takeIf { it.isNotBlank() }
            ?: WritingProfiles.defaultExample(key)
        val input = EditText(this).apply {
            hint = "Dictado: ...\nResultado: ..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            setText(current)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Ejemplo · " + WritingProfiles.label(key))
            .setMessage("Puedes adaptar este ejemplo a tu forma de escribir. El modelo imitará el registro y el grado de reescritura, no los hechos del ejemplo.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Guardar") { _, _ ->
                prefs().edit().putString(prefKey, input.text.toString().trim()).apply()
            }
            .setNeutralButton("Restaurar") { _, _ ->
                prefs().edit().remove(prefKey).apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptCustomProfileInstructions() {
        val input = EditText(this).apply {
            hint = "Ej.: conserva un tono breve y directo, sin fórmulas de cortesía añadidas"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setText(prefs().getString("profile_custom_instructions", ""))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Perfil personalizado")
            .setMessage("Estas reglas solo se aplican cuando está seleccionado el perfil Personalizado.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Guardar") { _, _ ->
                prefs().edit().putString("profile_custom_instructions", input.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showHistoryDialog() {
        val items = DictationHistory.load(prefs())
        if (items.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Historial de dictados")
                .setMessage("Todavía no hay dictados guardados. El historial es local y solo conserva texto, nunca audio.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

        val labels = items.mapIndexed { index, text ->
            val compact = text.replace("\n", " ")
            val shown = if (compact.length > 120) compact.take(117) + "…" else compact
            "${index + 1}. $shown"
        }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Historial de dictados")
            .setMessage("Toca un texto para copiarlo. Se guardan como máximo ${DictationHistory.MAX_ITEMS} y solo en este dispositivo.")
            .setItems(labels) { _, which ->
                val clip = ClipData.newPlainText("OpenWispr historial", items[which])
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                toast("Texto copiado")
            }
            .setNeutralButton("Borrar historial") { _, _ ->
                DictationHistory.clear(prefs())
                refresh()
                toast("Historial borrado")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun overlayColorLabel(color: Int): String = when (color) {
        0xDD1C1C1E.toInt() -> "Grafito"
        0xFF1565C0.toInt() -> "Azul"
        0xFF00897B.toInt() -> "Verde azulado"
        0xFFFF8F00.toInt() -> "Ámbar"
        0xFF7B1FA2.toInt() -> "Violeta"
        else -> String.format("#%06X", 0xFFFFFF and color)
    }

    private fun showOverlayColorDialog() {
        val names = arrayOf("Grafito", "Azul", "Verde azulado", "Ámbar", "Violeta", "Personalizado…")
        val colors = intArrayOf(
            0xDD1C1C1E.toInt(),
            0xFF1565C0.toInt(),
            0xFF00897B.toInt(),
            0xFFFF8F00.toInt(),
            0xFF7B1FA2.toInt()
        )
        val current = prefs().getInt("overlay_idle_color", 0xDD1C1C1E.toInt())
        val checked = colors.indexOf(current)

        android.app.AlertDialog.Builder(this)
            .setTitle("Color en reposo")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                if (which == names.lastIndex) {
                    promptCustomOverlayColor()
                } else {
                    val color = colors[which]
                    prefs().edit().putInt("overlay_idle_color", color).apply()
                    overlayColorRowSub.text = overlayColorLabel(color)
                    WhisperAccessibilityService.instance?.refreshOverlaySettings()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptCustomOverlayColor() {
        val input = EditText(this).apply {
            hint = "#3F51B5"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(String.format("#%06X", 0xFFFFFF and prefs().getInt("overlay_idle_color", 0xDD1C1C1E.toInt())))
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Color personalizado")
            .setMessage("Introduce un color hexadecimal, por ejemplo #3F51B5.")
            .setView(input.apply { setPadding(dp(24), dp(8), dp(24), dp(8)) })
            .setPositiveButton("Guardar") { _, _ ->
                try {
                    val color = Color.parseColor(input.text.toString().trim())
                    prefs().edit().putInt("overlay_idle_color", color).apply()
                    overlayColorRowSub.text = overlayColorLabel(color)
                    WhisperAccessibilityService.instance?.refreshOverlaySettings()
                } catch (_: IllegalArgumentException) {
                    toast("Color no válido")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showIntSlider(
        title: String,
        prefKey: String,
        min: Int,
        max: Int,
        defaultValue: Int,
        suffix: String,
        onSaved: ((Int) -> Unit)? = null
    ) {
        val current = prefs().getInt(prefKey, defaultValue).coerceIn(min, max)
        val valueLabel = TextView(this).apply {
            text = "$current$suffix"
            textSize = 18f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            setPadding(dp(24), 0, dp(24), 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    valueLabel.text = "${min + progress}$suffix"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val content = vertical(0).apply {
            addView(valueLabel)
            addView(seek)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("Guardar") { _, _ ->
                val value = min + seek.progress
                prefs().edit().putInt(prefKey, value).apply()
                onSaved?.invoke(value)
                WhisperAccessibilityService.instance?.refreshOverlaySettings()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDotTimeoutDialog(onSaved: (Int) -> Unit) {
        val labels = Array(11) { index ->
            if (index == 10) "Siempre" else "${index + 1} s"
        }
        val current = prefs().getInt("dot_timeout_seconds", 3).coerceIn(0, 10)
        val checked = if (current == 0) 10 else current - 1

        android.app.AlertDialog.Builder(this)
            .setTitle("Tiempo visible del punto")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val seconds = if (which == 10) 0 else which + 1
                prefs().edit().putInt("dot_timeout_seconds", seconds).apply()
                onSaved(seconds)
                WhisperAccessibilityService.instance?.refreshOverlaySettings()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- UI Helpers ---

    private fun settingsRow(
        title: String,
        subtitle: String,
        widget: View? = null,
        leading: View? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            isClickable = onClick != null
            isFocusable = onClick != null
            if (onClick != null) {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { onClick() }
            }
        }

        if (leading != null) row.addView(leading)

        val textContainer = vertical(0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LP_WRAP, 1f)
        }

        textContainer.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(attrColor(android.R.attr.textColorPrimary))
        })

        textContainer.addView(TextView(this).apply {
            tag = "subtitle"
            text = subtitle
            textSize = 14f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(textContainer)
        if (widget != null) row.addView(widget)

        return row
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(attrColor(androidx.appcompat.R.attr.colorPrimary)) // Neutral Android-like blue
        setPadding(dp(24), dp(24), dp(24), dp(8))
    }

    private fun vertical(padH: Int, padV: Int = padH) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, padV, padH, padV)
    }

    private fun dotDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun statusDot(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
            marginEnd = dp(12)
        }
        background = dotDrawable(DOT_RED)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun attrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
    private fun prefs() = getSharedPreferences("openwhispr", MODE_PRIVATE)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val LP_MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val DOT_GREEN = 0xFF34C759.toInt()
        private const val DOT_RED = 0xFFEF4444.toInt()
    }
}
