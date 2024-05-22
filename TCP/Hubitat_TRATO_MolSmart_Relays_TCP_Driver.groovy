/**
 *  Hubitat - TCP MolSmart Relay Drivers by VH - 
 *
 *  Copyright 2024 VH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *        
 *        1.0 25/4/2024  - V.BETA 1
 *	      1.1 30/4/2024  - correção do indexof para verificar os digitos de Network ID. Lineas 449 e 494, precisa ver quantos digitos vai ter o network id. Agora ficou para começar
 * 	      depois do "5" digito para ver depspues del "-". 
 *        1.2 05/10/2024 - Adição do Check cada 5 minutos para keepalive. Adição de botão manual para KeepAlive. 
 *        1.3 05/12/2024 - Added BoardStatus Attribute (online/offline)
 *        1.4 05/22/2024 - Fix Scenes run
 */
metadata {
  definition (name: "MolSmart - Relay 4/8/16/32CH (TCP)", namespace: "TRATO", author: "VH", vid: "generic-contact") {
        capability "Switch"  
        capability "Configuration"
        capability "Initialize"
        capability "Refresh"
     
      
  }
      
  }

import groovy.json.JsonSlurper
import groovy.transform.Field
command "buscainputcount"
command "createchilds"
command "connectionCheck"
command "keepalivemol"
//command "clearAllvalues"

  preferences {
        input "device_IP_address", "text", title: "MolSmart IP Address", required: true, defaultValue: "192.168.7.1"   
        input "device_port", "number", title: "IP Port of Device", required: true, defaultValue: 502
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        //input name: "powerstatus", type: "string", title: "Power Status" 

    input 'logInfo', 'bool', title: 'Show Info Logs?',  required: false, defaultValue: true
    input 'logWarn', 'bool', title: 'Show Warning Logs?', required: false, defaultValue: true
    input 'logDebug', 'bool', title: 'Show Debug Logs?', description: 'Only leave on when required', required: false, defaultValue: true
    input 'logTrace', 'bool', title: 'Show Detailed Logs?', description: 'Only leave on when required', required: false, defaultValue: true

    attribute "powerstatus", "string"
    attribute "boardstatus", "string"
    
      
  }   


@Field static String partialMessage = ''
@Field static Integer checkInterval = 300


def installed() {
    logTrace('installed()')
    //buscainputcount()
    updated()
    def novaprimeira = ""
    def oldprimeira = ""
    runIn(1800, logsOff)
}

def uninstalled() {
    logTrace('uninstalled()')
    unschedule()
    interfaces.rawSocket.close()
}

def updated() {
    logTrace('updated()')
    initialize()
    refresh()
}

def configure() {
    logTrace('configure()')
    unschedule()

}

def clearAllvalues() {
    logTrace('clearAllvalues()')
    unschedule()
    state.clear()
    interfaces.rawSocket.close();

}

def keepalivemol (){

   logTrace('keepalivemol()')
    unschedule()
    interfaces.rawSocket.close();
    state.clear()
    def novaprimeira = ""
    def oldprimeira = ""
    partialMessage = '';

    //Llama la busca de count de inputs+outputs
    buscainputcount()
    
    try {
        logTrace("tentando conexão com o device no ${device_IP_address}...na porta ${device_port}");
        int i = 502
        interfaces.rawSocket.connect(device_IP_address, (int) device_port);
        state.lastMessageReceivedAt = now();        
        runIn(checkInterval, "connectionCheck");
        refresh();  // se estava offline, preciso fazer um refresh
        
    }
    catch (e) {
        logError( "${device_IP_address} initialize error: ${e.message}" )
        sendEvent(name: "boardstatus", value: "offline", isStateChange: true)        

        //runIn(60, "initialize");
    }    
}


def initialize() {
    logTrace('initialize()')
    unschedule()
    interfaces.rawSocket.close();
    state.clear()
    def novaprimeira = ""
    def oldprimeira = ""
    partialMessage = '';

    if (!device_IP_address) {
        logError 'IP do Device not configured'
        return
    }

    if (!device_port) {
        logError 'Porta do Device não configurada.'
        return
    }
    //Llama la busca de count de inputs+outputs
    buscainputcount()
    
    try {
        logTrace("tentando conexão com o device no ${device_IP_address}...na porta ${device_port}");
        int i = 502
        interfaces.rawSocket.connect(device_IP_address, (int) device_port);
        state.lastMessageReceivedAt = now();        
        runIn(checkInterval, "connectionCheck");
        refresh();  // se estava offline, preciso fazer um refresh
    }
    catch (e) {
        logError( "${device_IP_address} initialize error: ${e.message}" )
        runIn(60, "initialize");
    }
    
    try{
         logTrace("criando childs")
          createchilds()       
    }
    catch (e) {
        logError( "initialize error: ${e.message}" )
    }    
}


def createchilds() {

    String thisId = device.id
	//log.info "info thisid " + thisId
	def cd = getChildDevice("${thisId}-Switch")
    state.netids = "${thisId}-Switch-"
	if (!cd) {
        log.info "inputcount = " + state.inputcount 
        for(int i = 1; i<=state.inputcount; i++) {        
        cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-Switch-" + Integer.toString(i), [name: "${device.displayName} Switch-" + Integer.toString(i) , isComponent: true])
        log.info "added switch # " + i + " from " + state.inputcount            
        
    }
    }         
}

def buscainputcount(){
    try {
        httpGet("http://" + settings.device_IP_address + "/relay_cgi_load.cgi") { resp ->
            if (resp.success) {
                logDebug "Busquei o numero de relays(): " + resp.data
                //logDebug "initialize(): Response[3] = " + (resp.data as String)[3]
                 
                //Verifica a quantidade de Relays que tem a placa, usando o primeiro digito do 3 caracter do response. (2 ou 4 ou 8 ou 1 ou 3)
                inputCountV = (resp.data as String)[3] as int
                    
                if (inputCountV == 4) {
                    inputCount = 4
                }
                if (inputCountV == 8) {
                    inputCount = 8
                }    
                if (inputCountV == 1) {
                    inputCount = 16
                }
                if (inputCountV == 3) {
                    inputCount = 32
                }
                               
            state.inputcount = inputCount  // deixar o numero de relays na memoria
       
            }
            else {
                if (resp.data) logDebug "initialize(): Falha para obter o # de relays ${resp.data}"
            }
  
        }
    } catch (Exception e) {
        log.warn "initialize(): Call failed: ${e.message}"
       
    }
         def ipmolsmart = settings.device_IP_address
         state.ipaddress = settings.device_IP_address
}



def refresh() {
    logTrace('refresh()')
    def msg = "00"    
    sendCommand(msg)
}



//Feedback e o tratamento 

 def fetchChild(String type, String name){
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${type}_${name}")
    if (!cd) {
        cd = addChildDevice("hubitat", "Generic Component ${type}", "${thisId}-${type}_${name}", [name: "${name}", isComponent: true])
        cd.parse([[name:"switch", value:"off", descriptionText:"set initial switch value"]]) //TEST!!
    }
    return cd 
}


def parse(msg) {
    state.lastMessageReceived = new Date(now()).toString();
    state.lastMessageReceivedAt = now();

    def oldmessage = state.lastmessage
    //log.info "oldmessage = " + oldmessage
    
    def oldprimeira = state.primeira
    //log.info "oldprimeira = " + oldprimeira
    state.lastprimeira =  state.primeira
    
    def newmsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) //na Mol, o resultado vem em HEX, então preciso converter para Array
    def newmsg2 = new String(newmsg) // Array para String
    
    state.lastmessage = newmsg2
    

    //(Doing a search with  "Contain" to see the board model, and qty of relays inside of the model MolSmart 4/8/16/32 relays). 

    if (newmsg2.contains("32")) {
        state.primeira = newmsg2[0..31]
        state.channels = 32
        novaprimeira = newmsg2[0..31]     
        log.debug "Placa MolSmart de 32CH"
        sendEvent(name: "boardstatus", value: "online", isStateChange: true)
        
    }    
    
    if (newmsg2.contains("16")) {
        state.primeira = newmsg2[0..15]
        state.channels = 16
        novaprimeira = newmsg2[0..15]
        log.debug "Placa MolSmart de 16CH"
        sendEvent(name: "boardstatus", value: "online", isStateChange: true)
        
    }   
    
    if (newmsg2.contains("8")) {
        state.primeira = newmsg2[0..7]
        state.channels = 8
        novaprimeira = newmsg2[0..7]
        log.debug "Placa MolSmart de 8CH"
        sendEvent(name: "boardstatus", value: "online", isStateChange: true)

    }      

    if (newmsg2.contains("4")) {
        state.primeira = newmsg2[0..3]
        state.channels = 4
        novaprimeira = newmsg2[0..3]
        sendEvent(name: "boardstatus", value: "online", isStateChange: true)        
        log.debug "Placa MolSmart de 4CH"
    }       
    
    //compare last parse result with current, and if different, compare changes. 
    if ((novaprimeira)&&(oldprimeira)) {  //if not empty in first run

    if (novaprimeira.compareToIgnoreCase(oldprimeira) == 0){
        log.info "No changes in relay status"
    }
    else{
        
        for(int f = 0; f <state.inputcount; f++) {  
        def valprimeira = state.primeira[f]
        def valold = oldprimeira[f]
        def diferenca = valold.compareToIgnoreCase(valprimeira)
         
            switch(diferenca) { 
               case 0: 
               log.info "no changes in ch#" + (f+1) ;
               break                     
               case -1:  //  -1 is when light was turned ON                   
                if (state.update == 1){     //no hace nada porque fue el switch
                log.info "(NOTHING) - ON changes in ch#" + (f+1) ;
                    
                }
                else {
                   log.info "ON changes in ch#" + (f+1) ; 
                    chdid = state.netids + (f+1) 
                    def cd = getChildDevice(chdid)
                    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])
                    //buscar y cambiar el status a ON del switch
                    
                }
               break                     
               case 1: // 1 is when light was turned OFF
                    log.info "OFF changes in ch#" + (f+1) ;
                    chdid = state.netids + (f+1) 
                    def cd = getChildDevice(chdid) 
                    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                   
                    //buscar y cambiar el status a OFF del switch
    
                break                     

                default:
                log.info "changes in ch#" + (f+1) + " with dif " + diferenca;
                break
            }
        }        
        state.update = 0  
    }}

        for(int f = 0; f <state.inputcount; f++) {  
        val = state.primeira[f]
        log.info "posição relay = " + f + ",  status = " + val  + "  (1=on / 2=off)"
        } 
        //log.info "Status do update = " + state.update
}


////////////////
////Commands 
////////////////

def on()
{
    logDebug("Master Power ON()")
    def msg = "1X"
    sendCommand(msg)
}

def off()
{
    logDebug("Master Power OFF()")
    def msg = "2X"
    sendCommand(msg)
}


private sendCommand(s) {
    logDebug("sendCommand ${s}")
    interfaces.rawSocket.sendMessage(s)    
}


////////////////////////////
//// Connections Checks ////
////////////////////////////

def connectionCheck() {
    def now = now();
    
    if ( now - state.lastMessageReceivedAt > (checkInterval * 1000)) { 
        logError("sem mensagens desde ${(now - state.lastMessageReceivedAt)/60000} minutos, reconectando ...");
        sendEvent(name: "boardstatus", value: "offline", isStateChange: true)        
        initialize();
    }
    else {
        logDebug("Connection Check ok");
        sendEvent(name: "boardstatus", value: "online", isStateChange: true)
        runIn(checkInterval, "connectionCheck");
    }
}




def socketStatus(String message) {
    if (message == "receive error: String index out of range: -1") {
        // This is some error condition that repeats every 15ms.

        interfaces.rawSocket.close();       
        logError( "socketStatus: ${message}");
        logError( "Closing connection to device" );
    }
    else if (message != "receive error: Read timed out") {
        logError( "socketStatus: ${message}")
    }
}




/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Component Child
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

void componentRefresh(cd){
	if (logEnable) log.info "received refresh request from ${cd.displayName}"
	refresh()
    
    
}

def componentOn(cd){
	if (logEnable) log.info "received on request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])       
    on(cd)  
    pauseExecution(250)
    
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])    
	off(cd)
    pauseExecution(250)


}


////// Driver Commands /////////


//SEND ON COMMAND IN CHILD BUTTON
void on(cd) {
//if (logEnable) log.debug "Turn device ON "	 + cd.deviceNetworkId	
sendEvent(name: "switch", value: "on", isStateChange: true)
cd.updateDataValue("Power","On")    
//cd.parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])

ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0
/// Inicio verificación del length    
      def substr1 = (cd.deviceNetworkId.indexOf("-",5))
      def result01 = lengthvar - substr1 
      if (result01 > 2  ) {
           def  substr2a = substr1 + 1
           def  substr2b = substr1 + 2
           def substr3 = cd.deviceNetworkId[substr2a..substr2b]
           numervalue1 = substr3

           
          
      }
      else {
          def substr3 = cd.deviceNetworkId[substr1+1]
          numervalue1 = substr3
        
           }

    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   

 ////
     def stringrelay = relay
     def comando = "1" + stringrelay
     interfaces.rawSocket.sendMessage(comando)
     //log.info "Foi Ligado o Relay " + relay + " via TCP " + comando 
     sendEvent(name: "power", value: "on")
     state.update = 1  //variable to control update with board on parse
    
}


//SEND OFF COMMAND IN CHILD BUTTON 
void off(cd) {
//if (logEnable) log.debug "Turn device OFF "	 + cd.deviceNetworkId
sendEvent(name: "switch", value: "off", isStateChange: true)
cd.updateDataValue("Power","Off")
//cd.parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
    
ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0
/// Inicio verificación del length    
      def substr1 = (cd.deviceNetworkId.indexOf("-",5))
      def result01 = lengthvar - substr1 
      if (result01 > 2  ) {
           def  substr2a = substr1 + 1
           def  substr2b = substr1 + 2
           def substr3 = cd.deviceNetworkId[substr2a..substr2b]
           numervalue1 = substr3
          
      }
      else {
          def substr3 = cd.deviceNetworkId[substr1+1]
          numervalue1 = substr3
           }

    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   

 ////
     def stringrelay = relay
     def comando = "2" + stringrelay
     interfaces.rawSocket.sendMessage(comando)
     //log.info "Foi Desligado o Relay " + relay + " via TCP " + comando 
     state.update = 1    //variable to control update with board on parse
    
}




////////////////////////////////////////////////
////////LOGGING
///////////////////////////////////////////////


private processEvent( Variable, Value ) {
    if ( state."${ Variable }" != Value ) {
        state."${ Variable }" = Value
        logDebug( "Event: ${ Variable } = ${ Value }" )
        sendEvent( name: "${ Variable }", value: Value, isStateChanged: true )
    }
}



def logsOff() {
    log.warn 'logging disabled...'
    device.updateSetting('logInfo', [value:'false', type:'bool'])
    device.updateSetting('logWarn', [value:'false', type:'bool'])
    device.updateSetting('logDebug', [value:'false', type:'bool'])
    device.updateSetting('logTrace', [value:'false', type:'bool'])
}

void logDebug(String msg) {
    if ((Boolean)settings.logDebug != false) {
        log.debug "${drvThis}: ${msg}"
    }
}

void logInfo(String msg) {
    if ((Boolean)settings.logInfo != false) {
        log.info "${drvThis}: ${msg}"
    }
}

void logTrace(String msg) {
    if ((Boolean)settings.logTrace != false) {
        log.trace "${drvThis}: ${msg}"
    }
}

void logWarn(String msg, boolean force = false) {
    if (force || (Boolean)settings.logWarn != false) {
        log.warn "${drvThis}: ${msg}"
    }
}

void logError(String msg) {
    log.error "${drvThis}: ${msg}"
}
