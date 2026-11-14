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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.io.FileOutputStream

/**
 * Aïn Souk v2 — application de l'auxiliaire d'autorité.
 *
 * - L'interface (HTML/JavaScript, en arabe) est embarquée dans l'APK (assets/www) et servie
 *   par WebViewAssetLoader sur une adresse locale sécurisée : le chiffrement (Web Crypto) y est disponible.
 * - Aucune permission Internet : l'application ne communique avec aucun serveur.
 * - Transmission vers l'annexe : Bluetooth, partage à proximité (Quick Share / Wi-Fi Direct),
 *   code QR (affiché à l'écran) ou câble USB (fichier dans Téléchargements/AinSouk).
 * - Les fichiers transmis sont chiffrés avec la clé publique de l'annexe.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var photoUri: Uri? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var geoOrigin: String? = null

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
    }

    // Résultat de la caméra / de la galerie / du sélecteur de fichiers
    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        if (res.resultCode != RESULT_OK) { cb.onReceiveValue(null); return@registerForActivityResult }
        val picked = res.data?.data ?: photoUri
        cb.onReceiveValue(if (picked != null) arrayOf(picked) else null)
    }

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> openPhotoChooser(granted) }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        geoCallback?.invoke(geoOrigin, granted.values.any { it }, false)
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
            allowFileAccess = false
            allowContentAccess = true
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = 100
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)
            // Aucune navigation vers l'extérieur
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != "appassets.androidplatform.net"
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val wantsImage = params?.acceptTypes?.any { it.startsWith("image") } == true
                if (!wantsImage) {
                    // Fichier de configuration envoyé par l'annexe
                    val doc = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
                    photoUri = null
                    pickFile.launch(Intent.createChooser(doc, "ملف الإعدادات"))
                } else if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    askCamera.launch(Manifest.permission.CAMERA)
                } else openPhotoChooser()
                return true
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                geoOrigin = origin; geoCallback = callback
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        // Bouton « retour » : revenir à l'accueil avant de quitter
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(function(){if(typeof VIEW!=='undefined'&&VIEW!=='home'&&VIEW!=='login'){go('home');return 'ok'}return 'exit'})()") {
                    if (it.contains("exit")) finish()
                }
            }
        })

        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    /** Caméra (photo enregistrée dans le cache de l'application), avec la galerie en option. */
    private fun openPhotoChooser(withCamera: Boolean = true) {
        val gallery = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        if (!withCamera) { photoUri = null; pickFile.launch(Intent.createChooser(gallery, "الصورة")); return }
        val dir = File(cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(gallery, "الصورة").apply { putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera)) }
        pickFile.launch(chooser)
    }

    private fun toast(t: String) = runOnUiThread { Toast.makeText(this, t, Toast.LENGTH_LONG).show() }

    /** Pont JavaScript → Android. */
    inner class Bridge {
        /** Câble USB : enregistre le fichier chiffré dans « Téléchargements/AinSouk ». */
        @JavascriptInterface
        fun saveFile(name: String, content: String): Boolean = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AinSouk")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AinSouk").apply { mkdirs() }
                FileOutputStream(File(dir, name)).use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }
            toast("تم حفظ الملف في Download/AinSouk")
            true
        } catch (e: Exception) { toast("خطأ: ${e.message}"); false }

        /**
         * Bluetooth ou partage à proximité : le fichier chiffré est partagé
         * - mode "bluetooth" : directement avec l'application Bluetooth du téléphone (OBEX) ;
         * - mode "proximite" : via le menu de partage (Quick Share / Wi-Fi Direct / Bluetooth).
         * Aucune connexion Internet n'est utilisée.
         */
        @JavascriptInterface
        fun shareFile(name: String, content: String, mode: String): Boolean = try {
            val dir = File(cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, name).apply { writeText(content, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val bt = Intent(send).apply { setPackage("com.android.bluetooth") }
            runOnUiThread {
                if (mode == "bluetooth" && bt.resolveActivity(packageManager) != null) startActivity(bt)
                else startActivity(Intent.createChooser(send, if (mode == "bluetooth") "الإرسال بالبلوتوث" else "المشاركة القريبة"))
            }
            true
        } catch (e: Exception) { toast("خطأ: ${e.message}"); false }
    }
}
