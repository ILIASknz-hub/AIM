package ma.irat.ainsouk

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Aïn Souk — application de l'auxiliaire d'autorité.
 * L'interface (HTML/JavaScript, en darija) est embarquée dans l'APK (assets/www).
 * Aucune connexion Internet n'est utilisée : les données restent dans le téléphone
 * (IndexedDB du WebView) jusqu'à leur export dans un fichier pour l'annexe.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var photoUri: Uri? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    // Résultat de la caméra / de la galerie
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        if (res.resultCode != RESULT_OK) { cb.onReceiveValue(null); return@registerForActivityResult }
        val fromGallery = res.data?.data
        cb.onReceiveValue(arrayOf(fromGallery ?: photoUri ?: run { cb.onReceiveValue(null); return@registerForActivityResult }))
    }

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> openChooser(granted) }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val ok = granted.values.any { it }
        geoCallback?.invoke(geoOrigin, ok, false)
        geoCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true          // stockage local
            databaseEnabled = true            // IndexedDB
            allowFileAccess = true
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = 100
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    askCamera.launch(Manifest.permission.CAMERA)
                } else openChooser()
                return true
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                geoOrigin = origin; geoCallback = callback
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        // Bouton « retour » : revenir à l'accueil de l'application avant de quitter
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(function(){if(typeof VIEW!=='undefined'&&VIEW!=='home'&&VIEW!=='setup'){go('home');return 'ok'}return 'exit'})()") {
                    if (it.contains("exit")) finish()
                }
            }
        })

        web.loadUrl("file:///android_asset/www/index.html")
    }

    /** Ouvre la caméra (photo enregistrée dans le cache de l'application) avec la galerie en option. */
    private fun openChooser(withCamera: Boolean = true) {
        val gallery = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        if (!withCamera) { photoUri = null; pickImage.launch(Intent.createChooser(gallery, "التصويرة")); return }
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(gallery, "التصويرة").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        }
        pickImage.launch(chooser)
    }

    /** Pont JavaScript → Android : enregistre le fichier d'export dans « Téléchargements ». */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(name: String, content: String): Boolean = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AinSouk")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AinSouk").apply { mkdirs() }
                FileOutputStream(File(dir, name)).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
            runOnUiThread { Toast.makeText(this@MainActivity, "الملف تحط ف Download/AinSouk", Toast.LENGTH_LONG).show() }
            true
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show() }
            false
        }
    }
}
