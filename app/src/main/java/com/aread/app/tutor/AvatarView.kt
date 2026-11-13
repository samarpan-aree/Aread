package com.aread.app.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun AvatarView(mouth: Mouth, speaking: Boolean, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) { position = Position(z = 0.9f) }
    val childNodes = rememberNodes()
    val currentMouth by rememberUpdatedState(mouth)

    var model by remember { mutableStateOf<ModelNode?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            val instance = modelLoader.createModelInstance("medha.glb")
            val node = ModelNode(modelInstance = instance, scaleToUnits = 0.6f)
            childNodes.add(node)
            model = node
        }
    }

    LaunchedEffect(model) {
        val node = model ?: return@LaunchedEffect
        var t = 0f
        while (true) {
            delay(33)
            t += 0.033f
            node.rotation = Rotation(y = sin(t * 0.6f) * 4f, x = sin(t * 0.9f) * 1.5f)
            val blink = if ((t % 4f) < 0.12f) 1f else 0f
            val m = currentMouth
            node.applyMorphs(
                mapOf(
                    "eyeBlinkLeft" to blink,
                    "eyeBlinkRight" to blink,
                    "jawOpen" to m.jawOpen,
                    "mouthFunnel" to m.funnel,
                    "mouthPucker" to m.pucker,
                    "mouthSmileLeft" to m.smile,
                    "mouthSmileRight" to m.smile,
                    "mouthClose" to m.closed * 0.3f,
                )
            )
        }
    }

    Box(modifier.background(Color.Transparent)) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            childNodes = childNodes,
            onGestureListener = rememberOnGestureListener(),
        )
    }
}

private fun ModelNode.applyMorphs(values: Map<String, Float>) {
    runCatching {
        val names = renderableNodes.firstOrNull { it.morphTargetNames.isNotEmpty() }?.morphTargetNames ?: return
        val weights = FloatArray(names.size)
        values.forEach { (name, value) ->
            val index = names.indexOf(name)
            if (index >= 0) weights[index] = value
        }
        setMorphWeights(weights, 0)
    }
}
