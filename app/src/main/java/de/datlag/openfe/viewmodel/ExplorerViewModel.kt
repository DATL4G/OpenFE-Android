package de.datlag.openfe.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.datlag.openfe.commons.copyOf
import de.datlag.openfe.commons.getRootOfStorage
import de.datlag.openfe.commons.isInternal
import de.datlag.openfe.commons.isNotCleared
import de.datlag.openfe.commons.matchWithApps
import de.datlag.openfe.commons.mutableCopyOf
import de.datlag.openfe.commons.parentDir
import de.datlag.openfe.fragments.ExplorerFragmentArgs
import de.datlag.openfe.recycler.data.ExplorerItem
import de.datlag.openfe.recycler.data.FileItem
import io.michaelrocks.paranoid.Obfuscate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
@Obfuscate
class ExplorerViewModel constructor(
    private val explorerFragmentArgs: ExplorerFragmentArgs,
    private val appsViewModel: AppsViewModel
) : ViewModel() {

    val startDirectory: File = getStartDirectory(explorerFragmentArgs)

    val currentDirectory: MutableLiveData<File> = MutableLiveData(startDirectory)
    val currentSubDirectories: MutableLiveData<List<ExplorerItem>> = MutableLiveData(listOf())

    val selectedItems = MutableLiveData<List<ExplorerItem>>()

    val searchShown: MutableLiveData<Boolean> = MutableLiveData(false)
    var searched: Boolean = false
    private var searchDirectoriesCopy: List<ExplorerItem> = listOf()
    private var searchJob: Job? = null
    private var previousSearchText: String? = null

    private val systemAppsObserver = Observer<AppList> { list ->
        viewModelScope.launch(Dispatchers.IO) {
            val matchedAppItems = matchNewAppsToDirectories(list)
            withContext(Dispatchers.Main) {
                try {
                    currentSubDirectories.value = matchedAppItems
                } catch (exception: Exception) {
                    currentSubDirectories.postValue(matchedAppItems)
                }
            }
        }
    }

    private val currentDirectoryObserver = Observer<File> { dir ->
        currentSubDirectories.value = listOf()

        val fileList = dir.listFiles()?.toMutableList() ?: mutableListOf()
        val startDirParent = File(startDirectory.getRootOfStorage()).parentDir

        if (dir == startDirParent) {
            for (item in explorerFragmentArgs.storage.list) {
                if (!fileList.contains(item.rootFile)) {
                    fileList.add(item.rootFile)
                }
            }
        }

        if (dir == File("/")) {
            val dataPath = File("/data")
            if (!fileList.contains(dataPath)) {
                fileList.add(dataPath)
            }

            val storagePath = File("/storage")
            if (!fileList.contains(storagePath)) {
                fileList.add(storagePath)
            }

            val systemPath = File("/system")
            if (!fileList.contains(systemPath)) {
                fileList.add(systemPath)
            }
        } else {
            val parentFile = ExplorerItem(
                FileItem(
                    dir.parentDir,
                    ".."
                ),
                null, false, false
            )
            currentSubDirectories.value = listOf(parentFile)
        }
        createSubDirectories(fileList)
    }

    private val selectedItemsObserver = Observer<List<ExplorerItem>> {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSubDirs = matchSelectedWithCurrentSubDirs()
            withContext(Dispatchers.Main) {
                currentSubDirectories.value = currentSubDirs
            }
        }
    }

    init {
        appsViewModel.systemApps.observeForever(systemAppsObserver)
        currentDirectory.observeForever(currentDirectoryObserver)
        selectedItems.observeForever(selectedItemsObserver)
    }

    private fun getStartDirectory(args: ExplorerFragmentArgs = explorerFragmentArgs): File {
        val file = File(args.storage.list[args.storage.selected].usage.file.absolutePath)
        return if (file.isDirectory) file else file.parentDir
    }

    private fun createSubDirectories(fileList: MutableList<File>) = viewModelScope.launch(Dispatchers.IO) {
        fileList.sortWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
        val iterator = fileList.listIterator()

        while (iterator.hasNext()) {
            val file = iterator.next()

            if (!file.isHidden) {
                val explorerItem = ExplorerItem.from(file, appsViewModel.systemApps.value ?: listOf())
                val copy = currentSubDirectories.value?.mutableCopyOf() ?: mutableListOf()
                copy.add(explorerItem)

                withContext(Dispatchers.Main) {
                    currentSubDirectories.value = copy
                }
            }
        }
    }

    fun moveToPath(path: File, force: Boolean = false) {
        val newPath = if (path.isInternal() && !force) startDirectory else path

        currentDirectory.value = if (newPath.isDirectory) {
            newPath
        } else {
            newPath.parentDir
        }
    }

    fun searchCurrentDirectories(text: String?, recursively: Boolean) {
        if (previousSearchText == text || (currentSubDirectories.value.isNullOrEmpty() && searchDirectoriesCopy.isEmpty())) {
            return
        }
        previousSearchText = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            if (text.isNotCleared()) {
                if (searchDirectoriesCopy.isEmpty()) {
                    searchDirectoriesCopy = currentSubDirectories.value!!.copyOf()
                }
                withContext(Dispatchers.Main) {
                    currentSubDirectories.value = listOf()
                }

                for (explorerItem in searchDirectoriesCopy) {
                    if (recursively && selectedItems.value.isNullOrEmpty()) {
                        explorerItem.fileItem.file.walkTopDown().fold(true) { res, file ->
                            val fileMatches = file.name.contains(text, true) && !file.isHidden
                            if (fileMatches) {
                                withContext(Dispatchers.Main) {
                                    currentSubDirectories.value =
                                        (
                                            currentSubDirectories.value?.mutableCopyOf()
                                                ?: mutableListOf()
                                            ).apply {
                                            add(
                                                ExplorerItem.from(
                                                    file,
                                                    appsViewModel.systemApps.value ?: listOf()
                                                )
                                            )
                                        }
                                }
                            }
                            (file.exists() && fileMatches) && res
                        }
                    } else {
                        if (explorerItem.fileItem.name?.contains(
                                text,
                                true
                            ) == true || explorerItem.fileItem.file.name.contains(text, true)
                        ) {
                            withContext(Dispatchers.Main) {
                                currentSubDirectories.value =
                                    (
                                        currentSubDirectories.value?.toMutableList()
                                            ?: mutableListOf()
                                        ).apply { add(explorerItem) }
                            }
                        }
                    }
                }
            } else {
                if (searchDirectoriesCopy.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        currentDirectory.value = currentDirectory.value
                    }
                }
            }

            val currentSubDirs = matchSelectedWithCurrentSubDirs()
            withContext(Dispatchers.Main) {
                currentSubDirectories.value = currentSubDirs
            }
        }
    }

    private fun matchSelectedWithCurrentSubDirs(): List<ExplorerItem> {
        val selectedList = selectedItems.value ?: listOf()
        val currentSubDirs = currentSubDirectories.value?.mutableCopyOf() ?: mutableListOf()
        for (selected in selectedList) {
            for (pos in 0 until currentSubDirs.size) {
                if (currentSubDirs[pos].fileItem == selected.fileItem) {
                    currentSubDirs.removeAt(pos)
                    currentSubDirs.add(pos, selected)
                }
            }
        }
        return currentSubDirs
    }

    private fun matchNewAppsToDirectories(list: AppList): List<ExplorerItem> {
        val copy = currentSubDirectories.value?.mutableCopyOf() ?: mutableListOf()

        for (explorerItem in copy) {
            explorerItem.matchWithApps(list)
        }
        return copy
    }

    fun selectItem(explorerItem: ExplorerItem): Boolean {
        val selectedItemsList = selectedItems.value?.mutableCopyOf() ?: mutableListOf()

        if (selectedItemsList.contains(explorerItem)) {
            selectedItemsList.remove(explorerItem)
        }

        if (explorerItem.selectable) {
            explorerItem.selected = !explorerItem.selected
        } else {
            explorerItem.selected = false
        }

        if (explorerItem.selected) {
            selectedItemsList.add(explorerItem)
        }
        selectedItems.value = selectedItemsList
        return explorerItem.selected
    }

    fun clearAllSelectedItems() = viewModelScope.launch(Dispatchers.IO) {
        val copy = currentSubDirectories.value?.mutableCopyOf() ?: mutableListOf()
        for (item in copy) {
            item.selected = false
        }

        withContext(Dispatchers.Main) {
            selectedItems.value = listOf()
            currentSubDirectories.value = copy
        }
    }

    override fun onCleared() {
        super.onCleared()
        appsViewModel.systemApps.removeObserver(systemAppsObserver)
        currentDirectory.removeObserver(currentDirectoryObserver)
        selectedItems.removeObserver(selectedItemsObserver)
    }
}
