package com.exposiguard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.exposiguard.app.databinding.ActivityMainBinding
import com.exposiguard.app.managers.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import androidx.appcompat.app.AppCompatDelegate

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var permissionManager: PermissionManager

    // Launcher para solicitud de permisos múltiples (para compatibilidad)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        android.util.Log.d("MainActivity", getString(R.string.log_permission_result_received, permissions))
        android.util.Log.d("MainActivity", getString(R.string.log_processing_results))
        handlePermissionResults(permissions)
    }

    // Launcher para permisos individuales (mejor práctica)
    private val requestSinglePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("MainActivity", getString(R.string.log_single_permission_result, isGranted))
        handleSinglePermissionResult(isGranted)
    }

    // Sistema de permisos secuenciales mejorado - inicializado de forma lazy
    private val permissionGroups by lazy {
        listOf(
            PermissionGroup(
                name = getString(R.string.permission_group_basic_location),
                permissions = arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                explanation = getString(R.string.permission_explanation_basic_location),
                icon = "📍"
            ),
            PermissionGroup(
                name = getString(R.string.permission_group_background_location),
                permissions = arrayOf(
                    android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                explanation = getString(R.string.permission_explanation_background_location),
                icon = "🌍",
                requiresPreviousPermissions = true,
                previousPermissionGroup = 0 // Requiere el grupo 0 (Ubicación Básica)
            ),
            PermissionGroup(
                name = getString(R.string.permission_group_bluetooth),
                permissions = arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ),
                explanation = getString(R.string.permission_explanation_bluetooth),
                icon = "📶"
            ),
            PermissionGroup(
                name = getString(R.string.permission_group_phone),
                permissions = arrayOf(
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.READ_PHONE_NUMBERS
                ),
                explanation = getString(R.string.permission_explanation_phone),
                icon = "📱"
            ),
            PermissionGroup(
                name = getString(R.string.permission_group_body_sensors),
                permissions = arrayOf(
                    android.Manifest.permission.BODY_SENSORS,
                    android.Manifest.permission.ACTIVITY_RECOGNITION
                ),
                explanation = getString(R.string.permission_explanation_body_sensors),
                icon = "🏃"
            ),
            PermissionGroup(
                name = getString(R.string.permission_group_audio),
                permissions = arrayOf(
                    android.Manifest.permission.RECORD_AUDIO
                ),
                explanation = getString(R.string.permission_explanation_audio),
                icon = "🎤"
            )
        )
    }

    // Permisos de salud se manejan por separado (requieren Health Connect)
    private val healthPermissions = arrayOf(
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_SLEEP"
    )

    private var currentPermissionGroupIndex = 0
    private var isRequestingPermissions = false

    // Clase para representar grupos de permisos
    private data class PermissionGroup(
        val name: String,
        val permissions: Array<String>,
        val explanation: String,
        val icon: String,
        val requiresPreviousPermissions: Boolean = false,
        val previousPermissionGroup: Int = -1
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("MainActivity", getString(R.string.log_main_activity_init))

        // Aplicar el tema antes de setContentView
        setTheme(R.style.Theme_ExposiGuard)

        // Aplicar el idioma guardado antes de inflar las vistas
        val languageCode = com.exposiguard.app.utils.LocaleHelper.getPersistedLanguage(this)
        com.exposiguard.app.utils.LocaleHelper.setLocale(this, languageCode)

    // Forzar modo oscuro en toda la app
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Usar ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // getString(R.string.comment_initialize_permission_manager)
        permissionManager = PermissionManager(this)
        android.util.Log.d("MainActivity", getString(R.string.log_permission_manager_init))

        // Verificar permisos al iniciar (movido a onPostCreate para mejor timing)
        // checkAndRequestPermissions()

        // Configurar la toolbar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Configurar el DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            binding.appBarMain.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        // getString(R.string.comment_configure_navcontroller_error_handling)
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

            if (navHostFragment == null) {
                // Log para depuración
                android.util.Log.e("MainActivity", getString(R.string.log_nav_host_not_found))
                // Crear el fragmento manualmente si no se encuentra
                val fragment = NavHostFragment.create(R.navigation.mobile_navigation)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, fragment)
                    .setPrimaryNavigationFragment(fragment)
                    .commit()
                val navController = fragment.navController
                setupNavigation(navController)
            } else {
                val navController = navHostFragment.navController
                setupNavigation(navController)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", getString(R.string.error_navigation_setup), e)
            // getString(R.string.comment_basic_error_handling)
            finish()
        }
    }

    private fun setupNavigation(navController: androidx.navigation.NavController) {
        // Configurar AppBarConfiguration con los top-level destinations
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_wifi,
                R.id.nav_bluetooth,
                R.id.nav_sar,
                R.id.nav_emf,
                R.id.nav_trends,
                R.id.nav_settings
            ),
            drawerLayout
        )

        // Configurar la toolbar con el NavController
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Configurar NavigationView con el NavController
        val navView = findViewById<com.google.android.material.navigation.NavigationView>(R.id.nav_view)
        navView.setupWithNavController(navController)

        // Workaround: manejar selección manual para evitar que algunos destinos se re-seleccionen y no naveguen
        navView.setNavigationItemSelectedListener { menuItem ->
            val handled = try {
                val destinationId = menuItem.itemId
                // Si ya estamos en el destino, cerrar drawer y no hacer nada
                val currentDestId = navController.currentDestination?.id
                if (currentDestId == destinationId) {
                    drawerLayout.closeDrawers()
                    return@setNavigationItemSelectedListener true
                }

                // Navegar al destino seleccionado con opciones seguras de back stack
                val options = androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(R.id.nav_home, false)
                    .build()

                navController.navigate(destinationId, null, options)
                // Marcar y cerrar el drawer
                menuItem.isChecked = true
                drawerLayout.closeDrawers()
                true
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", getString(R.string.error_navigation_item_handling), e)
                false
            }
            handled
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val navController = navHostFragment?.navController
            navController?.navigateUp(appBarConfiguration) ?: false || super.onSupportNavigateUp()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", getString(R.string.error_support_navigate_up), e)
            super.onSupportNavigateUp()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()

        // Verificar permisos después de que todo esté inicializado
        android.util.Log.d("MainActivity", getString(R.string.log_post_create_verifying_permissions))
        checkAndRequestPermissions()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_all -> {
                try { com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.DataChanged) } catch (_: Exception) {}
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Verifica y solicita permisos al iniciar la aplicación
     */
    private fun checkAndRequestPermissions() {
        if (!permissionManager.hasAllRequiredPermissions()) {
            showPermissionDialog()
        }
    }

    /**
     * Muestra diálogo explicativo de permisos
     */
    private fun showPermissionDialog() {
        val missingPermissions = permissionManager.getAllMissingPermissions()
        val requiredCount = missingPermissions["required"]?.size ?: 0
        val optionalCount = missingPermissions["optional"]?.size ?: 0
        val healthCount = missingPermissions["health"]?.size ?: 0

        val message = buildString {
            append(getString(R.string.permissions_required_message))
            append("\n\n")

            if (requiredCount > 0) {
                append(getString(R.string.permission_count_required, requiredCount))
            }
            if (optionalCount > 0) {
                append(getString(R.string.permission_count_optional, optionalCount))
            }
            if (healthCount > 0) {
                append(getString(R.string.permission_count_health, healthCount))
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.permissions_required_title)
            .setMessage(message)
            .setPositiveButton(R.string.permissions_grant) { _, _ ->
                android.util.Log.d("MainActivity", getString(R.string.log_user_grant_permissions))
                requestMissingPermissions()
            }
            .setNegativeButton(R.string.permissions_later) { _, _ ->
                // Continuar sin permisos, mostrar mensaje de limitación
                Toast.makeText(
                    this,
                    getString(R.string.permissions_limited_features),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Solicita permisos faltantes
     */
    /**
     * Solicita permisos faltantes usando el nuevo sistema secuencial
     */
    private fun requestMissingPermissions() {
        if (isRequestingPermissions) {
            android.util.Log.d("MainActivity", getString(R.string.log_already_requesting_permissions))
            return
        }

        android.util.Log.d("MainActivity", getString(R.string.log_starting_sequential_permissions))

        // Reiniciar el proceso secuencial
        currentPermissionGroupIndex = 0
        isRequestingPermissions = true

        // Iniciar con el primer grupo de permisos
        requestNextPermissionGroup()
    }

    /**
     * Solicita el siguiente grupo de permisos en secuencia
     */
    private fun requestNextPermissionGroup() {
        if (currentPermissionGroupIndex >= permissionGroups.size) {
            // Todos los grupos han sido procesados, ahora manejar salud
            android.util.Log.d("MainActivity", getString(R.string.log_group_completed))
            requestHealthPermissions()
            return
        }

        val currentGroup = permissionGroups[currentPermissionGroupIndex]

        // Verificar si este grupo requiere permisos previos
        if (currentGroup.requiresPreviousPermissions && currentGroup.previousPermissionGroup >= 0) {
            val previousGroup = permissionGroups[currentGroup.previousPermissionGroup]
            val hasPreviousPermissions = previousGroup.permissions.all { permission ->
                permissionManager.hasPermission(permission)
            }

            if (!hasPreviousPermissions) {
                android.util.Log.d("MainActivity", getString(R.string.log_skipping_group, currentGroup.name))
                currentPermissionGroupIndex++
                requestNextPermissionGroup()
                return
            }
        }

        val missingPermissionsInGroup = currentGroup.permissions.filter { permission ->
            !permissionManager.hasPermission(permission)
        }

        if (missingPermissionsInGroup.isEmpty()) {
            android.util.Log.d("MainActivity", getString(R.string.log_group_already_has_permissions, currentGroup.name))
            currentPermissionGroupIndex++
            requestNextPermissionGroup()
            return
        }

        android.util.Log.d("MainActivity", getString(R.string.log_processing_group, currentGroup.name, missingPermissionsInGroup))

        // Mostrar explicación del grupo actual
        showPermissionGroupDialog(currentGroup, missingPermissionsInGroup.toTypedArray())
    }

    /**
     * Muestra diálogo explicativo para un grupo de permisos
     */
    private fun showPermissionGroupDialog(group: PermissionGroup, permissions: Array<String>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("${group.icon} ${group.name}")
            .setMessage(group.explanation)
            .setPositiveButton("Continuar") { _, _ ->
                // Solicitar permisos del grupo actual
                requestPermissionsForGroup(permissions)
            }
            .setNegativeButton("Omitir") { _, _ ->
                android.util.Log.d("MainActivity", "⏭️ Usuario omitió grupo '${group.name}'")
                currentPermissionGroupIndex++
                requestNextPermissionGroup()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    /**
     * Solicita permisos para un grupo específico
     */
    private fun requestPermissionsForGroup(permissions: Array<String>) {
        when (permissions.size) {
            0 -> {
                // No hay permisos que solicitar
                currentPermissionGroupIndex++
                requestNextPermissionGroup()
            }
            1 -> {
                // Un solo permiso - usar RequestPermission (mejor práctica)
                android.util.Log.d("MainActivity", "� Solicitando 1 permiso: ${permissions[0]}")
                requestSinglePermissionLauncher.launch(permissions[0])
            }
            else -> {
                // Múltiples permisos - usar RequestMultiplePermissions
                android.util.Log.d("MainActivity", "📋 Solicitando ${permissions.size} permisos: ${permissions.joinToString()}")
                requestPermissionLauncher.launch(permissions)
            }
        }
    }

    /**
     * Maneja el resultado de un permiso individual
     */
    private fun handleSinglePermissionResult(isGranted: Boolean) {
        val currentGroup = permissionGroups[currentPermissionGroupIndex]
        android.util.Log.d("MainActivity", "📨 Resultado permiso individual para '${currentGroup.name}': $isGranted")

        if (isGranted) {
            Toast.makeText(this, "✅ Permiso de ${currentGroup.name} otorgado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Permiso de ${currentGroup.name} denegado", Toast.LENGTH_SHORT).show()
        }

        // Pasar al siguiente grupo
        currentPermissionGroupIndex++
        requestNextPermissionGroup()
    }

    /**
     * Maneja permisos de salud (requieren Health Connect)
     */
    private fun requestHealthPermissions() {
        val missingHealthPermissions = healthPermissions.filter { permission ->
            !permissionManager.hasPermission(permission)
        }

        if (missingHealthPermissions.isEmpty()) {
            android.util.Log.d("MainActivity", "✅ Todos los permisos de salud ya están otorgados")
            finishPermissionProcess()
            return
        }

        android.util.Log.d("MainActivity", "🔄 Procesando permisos de salud faltantes: $missingHealthPermissions")

        // Mostrar diálogo especial para permisos de salud
        showHealthPermissionDialog(missingHealthPermissions)
    }

    /**
     * Muestra diálogo especial para permisos de salud
     */
    private fun showHealthPermissionDialog(@Suppress("UNUSED_PARAMETER") _missingPermissions: List<String>) {
        val healthDialog = AlertDialog.Builder(this)
            .setTitle("❤️ Datos de Salud")
            .setMessage(getString(R.string.main_health_permissions_message))
            .setPositiveButton("Configurar Health Connect") { _, _ ->
                // Abrir Health Connect para configuración manual
                openHealthConnectSettings()
            }
            .setNegativeButton("Omitir") { _, _ ->
                android.util.Log.d("MainActivity", "⏭️ Usuario omitió permisos de salud")
                finishPermissionProcess()
            }
            .setCancelable(false)
            .create()

        healthDialog.show()
    }

    /**
     * Abre configuración de Health Connect
     */
    private fun openHealthConnectSettings() {
        try {
            // Método correcto para abrir Health Connect
            val intent = android.content.Intent()
            intent.action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
            intent.addCategory(android.content.Intent.CATEGORY_DEFAULT)

            // Si no funciona, intentar con el package name directo
            if (intent.resolveActivity(packageManager) == null) {
                intent.setPackage("com.google.android.apps.healthdata")
            }

            // Si aún no funciona, intentar con el método alternativo
            if (intent.resolveActivity(packageManager) == null) {
                val altIntent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                altIntent.data = android.net.Uri.parse("package:com.google.android.apps.healthdata")
                startActivity(altIntent)
                android.util.Log.d("MainActivity", "📱 Abriendo configuración de apps para Health Connect")
            } else {
                startActivity(intent)
                android.util.Log.d("MainActivity", "📱 Abriendo Health Connect directamente")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", getString(R.string.comment_error_opening_health_connect) + ": ${e.message}")
            // Mostrar mensaje más específico
            val message = when {
                e.message?.contains("No Activity found") == true ->
                    "Health Connect no está instalado. Instálalo desde tu tienda de aplicaciones."
                else ->
                    "No se pudo abrir Health Connect. Ve a Configuración > Apps > Health Connect > Permisos."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        finishPermissionProcess()
    }

    /**
     * Finaliza el proceso de permisos
     */
    private fun finishPermissionProcess() {
        android.util.Log.d("MainActivity", "🎉 Proceso de permisos completado")
        isRequestingPermissions = false
        showPermissionSummary()
    }

    /**
     * Muestra resumen final de permisos
     */
    private fun showPermissionSummary() {
        val allMissingPermissions = permissionManager.getMissingRequiredPermissions() +
                                   permissionManager.getMissingOptionalPermissions()

        val healthMissing = healthPermissions.filter { !permissionManager.hasPermission(it) }

        val totalMissing = allMissingPermissions.size + healthMissing.size

        when {
            totalMissing == 0 -> {
                Toast.makeText(this, "🎉 ¡Todos los permisos han sido configurados!", Toast.LENGTH_LONG).show()
            }
            healthMissing.isNotEmpty() -> {
                val message = "Configuración completada. ${allMissingPermissions.size} permisos pendientes. ${healthMissing.size} permisos de salud requieren configuración manual en Health Connect."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            else -> {
                val message = "Configuración completada. ${allMissingPermissions.size} permisos opcionales pendientes."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Maneja los resultados de la solicitud de permisos múltiples (para grupos)
     */
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        if (!isRequestingPermissions || currentPermissionGroupIndex >= permissionGroups.size) {
            android.util.Log.d("MainActivity", "handlePermissionResults: No se está solicitando permisos o índice inválido")
            return
        }

        val currentGroup = permissionGroups[currentPermissionGroupIndex]
        android.util.Log.d("MainActivity", "📋 Resultados para grupo '${currentGroup.name}': $permissions")

        val grantedPermissions = permissions.filter { it.value }.keys
        val deniedPermissions = permissions.filter { !it.value }.keys

        val totalRequested = permissions.size
        val totalGranted = grantedPermissions.size
        val totalDenied = deniedPermissions.size

        android.util.Log.d("MainActivity", "Grupo '${currentGroup.name}' - Solicitados: $totalRequested, Otorgados: $totalGranted, Denegados: $totalDenied")

        // Mostrar feedback al usuario
        when {
            totalGranted == totalRequested -> {
                Toast.makeText(this, "✅ ${currentGroup.name}: Todos los permisos otorgados", Toast.LENGTH_SHORT).show()
            }
            totalGranted > 0 -> {
                Toast.makeText(this, "⚠️ ${currentGroup.name}: $totalGranted/$totalRequested permisos otorgados", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "❌ ${currentGroup.name}: Permisos denegados", Toast.LENGTH_SHORT).show()
            }
        }

        // Pasar al siguiente grupo
        currentPermissionGroupIndex++
        requestNextPermissionGroup()
    }

    /**
     * Método legacy para compatibilidad con versiones anteriores
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            val results = permissions.zip(grantResults.toTypedArray()).toMap()
            handlePermissionResults(results.mapValues { it.value == PackageManager.PERMISSION_GRANTED })
        }
    }
}
