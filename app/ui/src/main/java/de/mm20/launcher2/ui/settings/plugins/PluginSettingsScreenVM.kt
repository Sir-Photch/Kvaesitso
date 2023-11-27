package de.mm20.launcher2.ui.settings.plugins

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.plugin.PluginPackage
import de.mm20.launcher2.plugin.PluginState
import de.mm20.launcher2.plugin.PluginType
import de.mm20.launcher2.plugins.PluginService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PluginSettingsScreenVM : ViewModel(), KoinComponent {
    private val pluginService by inject<PluginService>()

    private var pluginPackageName = MutableStateFlow<String?>(null)

    val pluginPackage: StateFlow<PluginPackage?> = pluginPackageName.flatMapLatest {
        if (it == null) {
            emptyFlow()
        } else {
            pluginService.getPluginPackage(it)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(100), null)

    val icon: Flow<Drawable?> = pluginPackage
        .distinctUntilChangedBy { it?.packageName }
        .map {
            it?.let { pluginService.getPluginPackageIcon(it) }
        }

    val types: Flow<List<PluginType>> = pluginPackage.map {
        it?.plugins?.map { it.type }?.distinct() ?: emptyList()
    }

    val states: Flow<List<PluginState?>> = pluginPackage.map {
        it?.plugins?.map {
            pluginService.getPluginState(it)
        } ?: emptyList()
    }


    fun init(pluginId: String) {
        this.pluginPackageName.value = pluginId
    }

    fun setPluginEnabled(enabled: Boolean) {
        val plugin = pluginPackage.value ?: return
        if (enabled) {
            pluginService.enablePluginPackage(plugin)
        } else {
            pluginService.disablePluginPackage(plugin)
        }
    }

    fun openAppInfo(context: Context) {
        val plugin = pluginPackage.value ?: return
        context.tryStartActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${plugin.packageName}")
        })
    }

    fun uninstall(context: Context) {
        val plugin = pluginPackage.value ?: return
        pluginService.uninstallPluginPackage(context, plugin)
    }
}