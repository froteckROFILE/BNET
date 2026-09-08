package com.bnet.app

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.charset.StandardCharsets

class MeshManager(context: Context, val myNumber: String) {
    private val client=Nearby.getConnectionsClient(context)
    val status=MutableStateFlow("Réseau arrêté")
    val peers=MutableStateFlow<Map<String,String>>(emptyMap())
    private val endpoints=mutableMapOf<String,String>()
    private val pendingNames=mutableMapOf<String,String>()
    private val strategy=Strategy.P2P_CLUSTER

    private val payloadCallback=object:PayloadCallback(){
        override fun onPayloadReceived(endpointId:String,payload:Payload){
            val msg=payload.asBytes()?.toString(StandardCharsets.UTF_8)?:return
            if(msg.startsWith("CALL|")) status.value="Appel entrant de ${msg.removePrefix("CALL|")}" 
            if(msg=="END") status.value="Appel terminé"
        }
        override fun onPayloadTransferUpdate(endpointId:String,update:PayloadTransferUpdate){}
    }
    private val lifecycle=object:ConnectionLifecycleCallback(){
        override fun onConnectionInitiated(id:String,info:ConnectionInfo){
            pendingNames[id]=info.endpointName
            peers.value=peers.value+(id to info.endpointName)
            client.acceptConnection(id,payloadCallback)
        }
        override fun onConnectionResult(id:String,res:ConnectionResolution){
            if(res.status.isSuccess){
                endpoints[id]=pendingNames[id]?:peers.value[id]?:"?"
                status.value="BNET connecté: ${endpoints.size} téléphone(s)"
            }
        }
        override fun onDisconnected(id:String){ endpoints.remove(id);pendingNames.remove(id);status.value="Téléphone déconnecté" }
    }
    private val discovery=object:EndpointDiscoveryCallback(){
        override fun onEndpointFound(id:String,info:DiscoveredEndpointInfo){
            peers.value=peers.value+(id to info.endpointName)
            pendingNames[id]=info.endpointName
            // Un seul des deux appareils initie la connexion, ce qui évite une course.
            if(myNumber < info.endpointName) client.requestConnection(myNumber,id,lifecycle)
        }
        override fun onEndpointLost(id:String){ peers.value=peers.value-id }
    }
    fun start(){
        status.value="Recherche du réseau BNET…"
        client.startAdvertising(myNumber,"com.bnet.mesh",lifecycle,AdvertisingOptions.Builder().setStrategy(strategy).build())
        client.startDiscovery("com.bnet.mesh",discovery,DiscoveryOptions.Builder().setStrategy(strategy).build())
    }
    fun call(number:String):Boolean{
        val wanted=number.filter{it.isDigit()}
        val target=endpoints.entries.firstOrNull{it.value.filter(Char::isDigit)==wanted}?.key?:return false
        client.sendPayload(target,Payload.fromBytes("CALL|$myNumber".toByteArray()))
        status.value="Appel vers $number…"; return true
    }
    fun stop(){client.stopAllEndpoints();client.stopAdvertising();client.stopDiscovery()}
}
