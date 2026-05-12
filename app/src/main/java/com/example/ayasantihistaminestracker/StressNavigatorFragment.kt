package com.example.ayasantihistaminestracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment

class StressNavigatorFragment : Fragment(), MainActivity.TitleProvider {

    override fun getTitle(): String {
        return getString(R.string.title_stress_navigator)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                StressNavigatorScreen(
                    onBack = {
                        parentFragmentManager.popBackStack()
                    }
                )
            }
        }
    }
}
