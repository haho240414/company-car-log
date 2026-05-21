package kr.co.hwacheon.carmileage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import kr.co.hwacheon.carmileage.ui.CarLogApp
import kr.co.hwacheon.carmileage.ui.theme.CompanyCarLogTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CarLogViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompanyCarLogTheme {
                CarLogApp(viewModel = viewModel)
            }
        }
    }
}
