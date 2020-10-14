package de.datlag.openfe

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.ferfalk.simplesearchview.SimpleSearchView
import de.datlag.openfe.commons.toggle
import de.datlag.openfe.databinding.ActivityMainBinding
import de.datlag.openfe.extend.AdvancedActivity
import de.datlag.openfe.interfaces.FragmentBackPressed
import de.datlag.openfe.interfaces.FragmentOptionsMenu
import timber.log.Timber

class MainActivity : AdvancedActivity() {

    private lateinit var binding: ActivityMainBinding

    val toolbar: Toolbar
        get() = binding.toolBar

    val searchView: SimpleSearchView
        get() = binding.searchview

    val drawer: DrawerLayout
        get() = binding.drawer

    lateinit var toggle: ActionBarDrawerToggle
    var toggleListener = View.OnClickListener { drawer.toggle() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
    }

    private fun initViews() = with(binding) {
        setSupportActionBar(toolBar)
        toggle = ActionBarDrawerToggle(this@MainActivity, drawer, toolbar, R.string.drawer_open, R.string.drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        toggle.toolbarNavigationClickListener?.let { toggleListener = it }
    }

    private fun getCurrentNavFragment(): Fragment? {
        val navHostFragment = supportFragmentManager.primaryNavigationFragment
        val fragmentList = navHostFragment?.childFragmentManager?.fragments
        return if (!fragmentList.isNullOrEmpty() && fragmentList.size >= 1) fragmentList[0] else null
    }

    override fun onBackPressed() {
        val pressed: Boolean = (getCurrentNavFragment() as? FragmentBackPressed?)?.onBackPressed() ?: true

        if (pressed) {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return (getCurrentNavFragment() as? FragmentOptionsMenu?)?.onCreateMenu(menu, menuInflater) ?: true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Timber.e("activity called")

        when (requestCode) {
            1337 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.e("activity granted")
                } else {
                    Timber.e("activity not granted")
                }
            }
        }
    }
}
