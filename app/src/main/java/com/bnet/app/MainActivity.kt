package com.bnet.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val number=BnetNumber.getOrCreate(this);val mesh=MeshManager(this,number);setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF22C55E))){BnetScreen(number,mesh)}}}
}

@Composable fun BnetScreen(myNumber:String,mesh:MeshManager){
 var dial by remember{mutableStateOf("")};val status by mesh.status.collectAsState();val peers by mesh.peers.collectAsState();var message by remember{mutableStateOf("")}
 val permissions=buildList{if(Build.VERSION.SDK_INT>=31){add(Manifest.permission.BLUETOOTH_SCAN);add(Manifest.permission.BLUETOOTH_ADVERTISE);add(Manifest.permission.BLUETOOTH_CONNECT)};if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.NEARBY_WIFI_DEVICES);if(Build.VERSION.SDK_INT<32)add(Manifest.permission.ACCESS_FINE_LOCATION)}.toTypedArray()
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){granted->if(granted.values.all{it})mesh.start()else message="Permissions refusées"}
 DisposableEffect(Unit){onDispose{mesh.stop()}}
 Surface(Modifier.fillMaxSize(),color=Color(0xFF07110B)){Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
  Text("BNET",fontSize=32.sp,fontWeight=FontWeight.Black,color=Color(0xFF22C55E));Text(myNumber,color=Color.White);Spacer(Modifier.height(16.dp));Text(status,color=Color.LightGray);Text("${peers.size} téléphone(s) détecté(s)",color=Color.Gray)
  Spacer(Modifier.height(18.dp));OutlinedTextField(dial,{dial=it.take(16)},label={Text("Numéro BNET (+AAMMJJ + 8 chiffres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
  val keys=listOf("1","2","3","4","5","6","7","8","9","+","0","⌫");keys.chunked(3).forEach{row->Row{row.forEach{k->TextButton(onClick={if(k=="⌫")dial=dial.dropLast(1)else dial+=k},modifier=Modifier.size(90.dp,62.dp)){Text(k,fontSize=25.sp)}}}}
  Button(onClick={if(!mesh.call(dial))message="Ce numéro n’est pas encore visible sur BNET"},modifier=Modifier.fillMaxWidth()){Text("Appeler sur BNET")}
  OutlinedButton(onClick={launcher.launch(permissions)},modifier=Modifier.fillMaxWidth()){Text("Activer le radar BNET")}
  if(message.isNotBlank())Text(message,color=Color(0xFFFFB4AB),modifier=Modifier.padding(12.dp))
  Text("Prototype local: garde BNET ouvert sur les deux téléphones.",color=Color.Gray,fontSize=12.sp)
 }}
}
