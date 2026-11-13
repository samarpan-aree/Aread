package com.aread.app.tutor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.Scene
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import kotlin.math.sin

@Composable
fun AvatarView(mouth: Mouth, speaking: Boolean, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) { position = io.github.sceneview.math.Position(z = 0.9f) }
    val childNodes = rememberNodes()

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
            kotlinx.coroutines.delay(33)
            t += 0.033f
            node.rotation = io.github.sceneview.math.Rotation(y = sin(t * 0.6f) * 4f, x = sin(t * 0.9f) * 1.5f)
            val blink = if ((t % 4f) < 0.12f) 1f else 0f
            node.setMorph("eyeBlinkLeft", blink)
            node.setMorph("eyeBlinkRight", blink)
        }
    }

    LaunchedEffect(mouth, model) {
        val node = model ?: return@LaunchedEffect
        node.setMorph("jawOpen", mouth.jawOpen)
        node.setMorph("mouthFunnel", mouth.funnel)
        node.setMorph("mouthPucker", mouth.pucker)
        node.setMorph("mouthSmileLeft", mouth.smile)
        node.setMorph("mouthSmileRight", mouth.smile)
        node.setMorph("mouthClose", mouth.closed * 0.3f)
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

private fun ModelNode.setMorph(name: String, weight: Float) {
    runCatching {
        val index = morphTargetNames.indexOf(name)
        if (index >= 0) setMorphTargetWeight(index, weight)
    }
}
