/*
 * Copyright 2026 Google LLC
 * Licensed under the Apache License, Version 2.0
 * You may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 */
package com.google.threelive.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.threelive.ThreeCompiler
import com.google.threelive.ThreeLiveCameraOptions
import com.google.threelive.filament.ThreeLiveFilamentRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StudioBg = Color(0xFF0B1020)
private val StudioPanel = Color(0xFF151D31)
private val StudioCyan = Color(0xFF43D9E8)

class GameStudioActivity : ComponentActivity() {
  private var renderer: ThreeLiveFilamentRenderer? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme(colorScheme = darkColorScheme(primary = StudioCyan, background = StudioBg, surface = StudioPanel)) {
        GameStudioScreen { renderer = it }
      }
    }
  }
  override fun onDestroy() {
    renderer?.dispose()
    super.onDestroy()
  }
}

private data class StudioObject(val name: String, val code: String)
private val objects = listOf(
  StudioObject("House", """const h = new THREE.Group(); const b = new THREE.Mesh(new THREE.BoxGeometry(1.3,0.9,1.1),new THREE.MeshStandardMaterial({color:0xd8a16c})); b.position.y=.48; h.add(b); const r = new THREE.Mesh(new THREE.ConeGeometry(1,.65,4),new THREE.MeshStandardMaterial({color:0x8b3d32})); r.position.y=1.25; r.rotation.y=Math.PI/4; h.add(r); scene.add(h);"""),
  StudioObject("Tree", """const t = new THREE.Group(); const trunk = new THREE.Mesh(new THREE.CylinderGeometry(.12,.16,.8,8),new THREE.MeshStandardMaterial({color:0x79502e})); trunk.position.y=.4; t.add(trunk); const crown = new THREE.Mesh(new THREE.ConeGeometry(.65,1.5,8),new THREE.MeshStandardMaterial({color:0x26945b})); crown.position.y=1.45; t.add(crown); t.position.x=2; scene.add(t);"""),
  StudioObject("Platform", """const p = new THREE.Mesh(new THREE.BoxGeometry(2.2,.25,2.2),new THREE.MeshStandardMaterial({color:0x3686d9})); p.position.set(-2,.25,0); scene.add(p);"""),
  StudioObject("Coin", """const c = new THREE.Mesh(new THREE.TorusGeometry(.35,.1,12,24),new THREE.MeshStandardMaterial({color:0xffcc33,metalness:.7})); c.position.set(0,1.1,2); scene.add(c);""")
)

private fun sceneCode(parts: List<String>) = """
scene.background = new THREE.Color(0x9bc9e8);
const hemi = new THREE.HemisphereLight(0xffffff,0x657080,2.2); scene.add(hemi);
const sun = new THREE.DirectionalLight(0xffffff,2.5); sun.position.set(5,9,6); scene.add(sun);
const ground = new THREE.Mesh(new THREE.PlaneGeometry(18,18),new THREE.MeshStandardMaterial({color:0x648b62})); ground.rotation.x=-Math.PI/2; scene.add(ground);
const road = new THREE.Mesh(new THREE.BoxGeometry(18,.04,2.2),new THREE.MeshStandardMaterial({color:0x343b49})); road.position.set(0,.02,-3); scene.add(road);
${parts.join("\n")}
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameStudioScreen(onRenderer: (ThreeLiveFilamentRenderer) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val prefs = remember { context.getSharedPreferences("nexcraft_studio", Context.MODE_PRIVATE) }
  var parts by remember { mutableStateOf(listOf<String>()) }
  var template by remember { mutableStateOf("City") }
  var message by remember { mutableStateOf("Choose a template or add objects, then build.") }
  var busy by remember { mutableStateOf(false) }
  val renderer = remember {
    ThreeLiveFilamentRenderer(context).apply {
      setCameraOptions(ThreeLiveCameraOptions(cameraOrbit = "35deg 55deg", autoRotate = false))
    }.also(onRenderer)
  }
  fun build() {
    busy = true
    message = "Compiling 3D scene…"
    val source = sceneCode(parts)
    scope.launch {
      try {
        val glb = withContext(Dispatchers.Default) {
          ThreeCompiler.compile(source).use { it.toByteArray() }
        }
        renderer.load(java.nio.ByteBuffer.allocateDirect(glb.size).apply { put(glb); flip() })
        message = "3D scene ready • ${parts.size} object(s)"
      } catch (e: Exception) {
        message = "Build error: ${e.localizedMessage ?: "unknown error"}"
      } finally { busy = false }
    }
  }
  Scaffold(
    containerColor = StudioBg,
    topBar = { TopAppBar(title = { Column {
      Text("NEXCRAFT 3D STUDIO", fontSize = 17.sp)
      Text("Mobile Game Builder • Three-Live Engine", fontSize = 10.sp, color = Color.LightGray)
    } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioPanel, titleContentColor = Color.White)) }
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).background(StudioBg)) {
      Box(Modifier.fillMaxWidth().weight(.43f).padding(10.dp).background(Color(0xFF080D18), RoundedCornerShape(18.dp))) {
        AndroidView(factory = { renderer.createView(it) }, modifier = Modifier.fillMaxSize())
        Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color(0xCC101A2C), shape = RoundedCornerShape(20.dp)) {
          Text("3D LIVE VIEWPORT", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = StudioCyan, fontSize = 10.sp)
        }
      }
      Column(Modifier.weight(.57f).fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("START PROJECT", color = Color.White, style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = { parts = emptyList(); template = "City"; message = "New project created." }) { Text("New") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("City", "Platformer", "Sandbox").forEach { name ->
            FilterChip(selected = template == name, onClick = {
              template = name
              parts = when (name) {
                "Platformer" -> listOf(objects[2].code, objects[3].code)
                "Sandbox" -> listOf(objects[1].code, objects[3].code)
                else -> listOf(objects[0].code, objects[1].code)
              }
              message = "$name starter loaded. Tap Build."
            }, label = { Text(name) })
          }
        }
        Spacer(Modifier.height(8.dp))
        Text("ADD OBJECTS", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          objects.forEach { item -> OutlinedButton(onClick = { parts = parts + item.code; message = "${item.name} added." }) { Text("+ ${item.name}") } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = { build() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "Building…" else "▶ Build 3D Scene") }
          OutlinedButton(onClick = { prefs.edit().putString("scene", sceneCode(parts)).apply(); message = "Project saved on this phone." }) { Text("Save") }
        }
        Row {
          TextButton(onClick = {
            val saved = prefs.getString("scene", null)
            message = if (saved == null) "No saved project yet." else "Saved project exists on this phone."
          }) { Text("Check Save") }
          TextButton(onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "My 3D scene"); putExtra(Intent.EXTRA_TEXT, sceneCode(parts))
            }
            context.startActivity(Intent.createChooser(intent, "Export scene script"))
          }) { Text("Export Script") }
        }
        Text(message, color = if (message.startsWith("Build error")) Color(0xFFFF8A80) else Color(0xFFB8C7D9), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text("First MVP: 3D viewport, starter templates, object placement, local save and script export. Terrain tools, gameplay systems and standalone game APK export are later milestones.", color = Color(0xFF8493A8), fontSize = 10.sp)
      }
    }
  }
}
