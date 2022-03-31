import androidx.compose.desktop.LocalAppWindow
import androidx.compose.desktop.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import org.jetbrains.skija.Image
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.awt.image.WritableRaster
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.application
import java.awt.FileDialog
import java.awt.Frame


fun imageFromFile(file: File): ImageBitmap {
    return Image.makeFromEncoded(file.readBytes()).asImageBitmap()
}

typealias Pixels = Array<Array<Vector>>
typealias Matrix = Array<Array<Float>>

fun ImageBitmap.toPixels(): Pixels {
    val pixelMap = toPixelMap()
    return Array(height) { x ->
        Array(width) { y ->
            val color = pixelMap[y, x]
            Vector(color.red, color.green, color.blue)
        }
    }
}

fun BufferedImage.toImageBitmap(): ImageBitmap {
    val stream = ByteArrayOutputStream()
    ImageIO.write(this, "png", stream)
    val byteArray = stream.toByteArray()
    return Image.makeFromEncoded(byteArray).asImageBitmap()
}

fun Matrix.toImageBitmap(): ImageBitmap {
    val height = size
    val width = this[0].size
    val image = BufferedImage(height, width, BufferedImage.TYPE_BYTE_GRAY)
    val raster = image.data as WritableRaster
    val minValue = flatten().minOrNull()!!
    val maxValue = flatten().maxOrNull()!!
    val pixels = flatMap { it.map { color -> (color - minValue) / (maxValue - minValue) } }.toFloatArray()
    raster.setPixels(0, 0, width, height, pixels)
    return image.toImageBitmap()
}

fun Pixels.toImageBitmap(): ImageBitmap {
    val height = size
    val width = this[0].size
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val values = flatten().flatMap { listOf(it.x, it.y, it.z) }
    val minValue = values.minOrNull()!!
    val maxValue = values.maxOrNull()!!
    val pixels = values.map { 255 * (it - minValue) / (maxValue - minValue) }.toFloatArray()
    image.raster.setPixels(0, 0, width, height, pixels)
    return image.toImageBitmap()
}

fun Pixels.applyConvolution(matrix: Array<Array<Float>>): Pixels {
    val height = size - matrix.size + 1
    val width = this[0].size - matrix[0].size + 1
    return Array(height) { x ->
        Array(width) { y ->
            var value = Vector()
            for (dx in matrix.indices) {
                for ((dy, coefficient) in matrix[dx].withIndex()) {
                    value += this[x + dx][y + dy] * coefficient
                }
            }
            value
        }
    }
}

fun Pixels.downscale(factor: Int): Pixels {
    val height = size / factor
    val width = this[0].size / factor
    val coefficient = 1F / factor / factor
    return Array(height) { x ->
        Array(width) { y ->
            var value = Vector()
            for (dx in 0 until factor)
                for (dy in 0 until factor)
                    value += this[x * factor + dx][y * factor + dy]
            value * coefficient
        }
    }
}

fun getClosestSmaller(values: List<Int>): List<Int> {
    val prefixMaximums = arrayListOf<Int>()
    val result = MutableList(values.size) {0}
    for ((i, value) in values.withIndex()) {
        while (prefixMaximums.isNotEmpty() && values[prefixMaximums.last()] > value)
            prefixMaximums.removeLast()
        result[i] = prefixMaximums.lastOrNull() ?: -1
        prefixMaximums.add(i)
    }
    return result
}

fun findMaxRectangle(values: Array<Array<Boolean>>): Rectangle {
    val height = values.size
    val width = values[0].size
    val pixelsBefore = Array(height) { Array(width) { 0 } }
    for (x in 0 until width)
        for (y in 0 until height)
            if (values[x][y])
                pixelsBefore[x][y] = 1 + if (x == 0) 0 else pixelsBefore[x - 1][y]
    var result = Rectangle(0, 0, 0, 0)
    var bestSquare = 0
    for ((x, row) in pixelsBefore.withIndex()) {
        val smallerLeft = getClosestSmaller(row.toList())
        val smallerRight = getClosestSmaller(row.reversed()).reversed()
        for (y in 0 until width) {
            val left = smallerLeft[y]
            val right = width - 1 - smallerRight[y]
            val up = x - pixelsBefore[x][y] + 1
            val rectWidth = right - left
            val rectHeight = x - up
            val square = rectHeight * rectWidth
            if (square > bestSquare) {
                bestSquare = square
                result = Rectangle(y, x, width, height)
            }
        }
    }
    return result
}

fun getFiltered(image: ImageBitmap): Pixels {
    val laplacianFilter = arrayOf(
        arrayOf(0F, 1F, 0F),
        arrayOf(1F, -4F, 1F),
        arrayOf(0F, 1F, 0F)
    )

    val pixels = image.toPixels()
    val filtered = pixels.downscale(2).applyConvolution(laplacianFilter)
    return filtered
}

fun main() = Window {
    val a = Color
    var text by remember { mutableStateOf("Hello, World!") }
    val file = File("/home/receed/Downloads/gradients.png")
    var image by remember { mutableStateOf(imageFromFile(file)) }
    var filtered by remember { mutableStateOf(getFiltered(image)) }

    val window = LocalAppWindow.current
    MaterialTheme {
        Row {
            Column {
                Image(image, "image", modifier = Modifier.requiredHeight(240.dp).requiredWidth(600.dp).fillMaxHeight())
                Image(
                    filtered.toImageBitmap(),
                    "filtered",
                    modifier = Modifier.requiredHeight(240.dp).requiredWidth(600.dp).fillMaxHeight()
                )
            }
            Button(onClick = {
                FileDialog(
                    onCloseRequest = {
                        val file = File(it)
                        image = imageFromFile(file)
                        filtered = imageFromFile(file)
                    }
                )
            }) {
                
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FileDialog(
    parent: Frame? = null,
    onCloseRequest: (result: String?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, "Choose a file", LOAD) {
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onCloseRequest(file)
                }
            }
        }
    },
    dispose = FileDialog::dispose
)