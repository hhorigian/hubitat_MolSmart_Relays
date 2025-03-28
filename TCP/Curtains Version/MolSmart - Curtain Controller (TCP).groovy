/**
 *  Hubitat - TCP MolSmart Curtain Controller Drivers by VH - 
 *
 *  Copyright 2024 VH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *		Driver para utilizar os módulos da MolSmart em Modo de Cortina. (Relay 1 + Relay 2 = Cortina 1.  Relay 3 + Relay 4 = Cortina 2, etc. )
 *
 * 	
 *
 *      1.0 21/2/2025  - V.BETA 1. Com Variável na configuração para Tempo para automaticamente parar o UP/DOWN. 
 * 		
 */

metadata {
  definition (name: "MolSmart - Curtain Controller", namespace: "TRATO", author: "VH", vid: "generic-contact", singleThreaded: true) {
    capability "Switch"  
    capability "Configuration"
    capability "Initialize"
    capability "Refresh"       
  }
}

import groovy.json.JsonSlurper
import groovy.transform.Field


command "masteron"
command "masteroff"
command "stop"
command "createchilds"

@Field static final String DRIVER = "by TRATO"
@Field static final String USER_GUIDE = "https://github.com/hhorigian/hubitat_MolSmart_Relays/tree/main/TCP"

String fmtHelpInfo(String str) {
    String prefLink = "<a href='${USER_GUIDE}' target='_blank'>${str}<br><div style='font-size: 70%;'>${DRIVER}</div></a>"
    return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>"
}

preferences {
    input "device_IP_address", "text", title: "MolSmart IP Address", required: true 
    input "device_port", "number", title: "IP Port of Device", required: true, defaultValue: 502
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input 'logInfo', 'bool', title: 'Show Info Logs?',  required: false, defaultValue: false
    input 'logDebug', 'bool', title: 'Show Debug Logs?', description: 'Only leave on when required', required: false, defaultValue: false
    input "checkInterval", "number", title: "Connection Check Interval (seconds)", defaultValue: 90, required: true
    input "enableNotifications", "bool", title: "Enable Connection Status Notifications", defaultValue: false      
    input "stopTime", "number", title: "Stop Time (seconds)", description: "Time in seconds before stopping the curtains", defaultValue: 10, required: true
    
    //help guide
    input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver") 

    attribute "boardstatus", "string"
	attribute "curtainStatus", "string"
    
    
}

@Field static String partialMessage = ''

def installed() {
    logTrace('installed()')
    state.childscreated = 0
    state.boardstatus = "offline"    
    boardstatus = "offline"
    //runIn(1800, logsOff)
}

def uninstalled() {
    logTrace('uninstalled()')
    unschedule()
    interfaces.rawSocket.close()
}

def updated() {
    logTrace('updated()')   
    //state.childscreated= null 
     refresh()
}

def initialize() {
  
    unschedule()
    logTrace('Run Initialize()')
    interfaces.rawSocket.close() // Ensure the socket is closed before reconnecting
    if (!device_IP_address) {
        logError 'IP do Device not configured'
        return
    }

    if (!device_port) {
        logError 'Porta do Device não configurada.'
        return
    }

    // Reset notification status on initialization
    //state.lastNotificationStatus = null
    //state.boardstatus = null

    //Llama la busca de count de inputs+outputs via HTTP
    buscainputcount()
    
    // Query the board for its current relay status
    //queryBoardStatus()
    
    try {
        logTrace("Initialize: Tentando conexão com a MolSmart no IP:  ${device_IP_address}...na porta configurada: ${device_port}")
        interfaces.rawSocket.connect(device_IP_address, (int) device_port)
        state.lastMessageReceivedAt = now()
        setBoardStatus("online")
        runIn(checkInterval, "connectionCheck")
    } catch (e) {
        logError("Initialize: com ${device_IP_address} com um error: ${e.message}")
        logError("Problemas na Rede ou Conexão com o Board MolSmart - Verificar IP/PORTA/Rede/Energia")
        setBoardStatus("offline")
        def retryDelay = Math.min(300, (state.retryCount ?: 0) * 60) // Exponential backoff, max 5 minutes
        state.retryCount = (state.retryCount ?: 0) + 1
        runIn(retryDelay, "initialize") // Retry after calculated delay
    }

    try {
        if  (boardstatus == "online") {
                 logTrace("Go to-> Creating Childs")
       			 createchilds()
        }

    } catch (e) {
        logError("Error de Initialize: ${e.message}")
    }
    // Create child devices if not already created
    
    
    
    runIn(10, "refresh")
}

def createchilds() {
     // state.childscreated = 0
    String thisId = device.id
    def cd = getChildDevice("${thisId}-Curtain")
    state.netids = "${thisId}-Curtain-"
    log.debug "Dentro do CreateChilds. Childscreated = " + state.childscreated
    if (state.childscreated == 0) {
        if (!cd) {
            log.info "Inputcount = " + state.inputcount 
            for(int i = 1; i<=state.inputcount/2; i++) {        
                if (i < 10) {     //Verify to add double digits to curtain name. 
                    numerocurtain = "0" + Integer.toString(i)
                } else {
                    numerocurtain = Integer.toString(i)
                }             
                cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-Curtain-" + numerocurtain, [name: "${device.displayName} Curtain-" + numerocurtain , isComponent: true])
                // Add the stop command capability to the child device
                cd.sendEvent(name: "supportedCommands", value: ["on", "off", "stop"], isStateChange: true)                             
                log.info "added curtain # " + i + " from " + state.inputcount/2            
            }
        }
        state.childscreated = 1   
    } else {
        log.info "Childs previously created. Nothing done"
    }
}

//Conta os inputs/relays, via HTTP. 
def buscainputcount(){
    try {
        httpGet("http://" + settings.device_IP_address + "/relay_cgi_load.cgi") { resp ->
            if (resp.success) {
                logDebug "Buscainputcount: Busquei o numero de relays(): " + resp.data
               
                //Verifica a quantidade de Relays que tem a placa, usando o primeiro digito do 3 caracter do response. (2 ou 4 ou 8 ou 1 ou 3)
                inputCountV = (resp.data as String)[3] as int
                    
                if (inputCountV == 2) {
                    inputCount = 2
                }
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
               logDebug "Numero de Relays: " + inputCount
            }
            else {
                if (resp.data) logDebug "initialize(): Falha para obter o # de relays ${resp.data}"
                logError("Failed to fetch relay count: ${resp.status}")
                runIn(60, "buscainputcount") // Retry after 60 seconds                
            }
  
        }
    } catch (Exception e) {
        log.warn "BuscaInputs-Initialize(): Erro no BuscaInputs: ${e.message}"
        logError("Error in buscainputcount: ${e.message}")
        runIn(60, "buscainputcount") // Retry after 60 seconds        
       
    }
         def ipmolsmart = settings.device_IP_address
         state.ipaddress = settings.device_IP_address
}


def refresh() {
    logInfo('Refresh()')
    def msg = "REFRESH"    
    sendCommand(msg)
}

def parse(String msg) {

    state.lastMessageReceived = new Date(now()).toString();
    state.lastMessageReceivedAt = now();
    
    def newmsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) //na Mol, o resultado vem em HEX, então preciso converter para Array
    def newmsg2 = new String(newmsg) // Array para String    
 
    state.lastmessage = newmsg2
 	def parts = state.lastmessage
    
	parts = parts.split(':')
	//log.debug "parts.size= " + parts.size()
    if (parts.size() >= 5) {
        def relayStatus = parts[0] // Relay status part
        def inputStatus = parts[1] // Input status part (not used here)
        def channelCount = parts[2] as int // Number of channels
        def lastUpdates = parts[3] // Last updates (not used here)
        def relayStatus2 = parts[4] // Relay status part (not used here)

       
        if (lastUpdates.contains("1")) {
        relaychanged =lastUpdates.indexOf('1'); 
        
        
        
        // Update child devices based on relay status
        for (int i = 1; i <= channelCount; i++) {
            def curtainNumber = i < 10 ? "0${i}" : "${i}"
            def chdid = "${state.netids}${curtainNumber}"
            def cd = getChildDevice(chdid)
            if (cd) {
                def upRelayStatus = relayStatus[(i*2)-2] as int // Relay for "up" (odd relay)
                def downRelayStatus = relayStatus[(i*2)-1] as int // Relay for "down" (even relay)
                ///log.debug "upRelayStatus = " + upRelayStatus
                //log.debug "downRelayStatus = " + downRelayStatus    
                    
                 if (upRelayStatus == 1) {
                        cd.parse([[name: "switch", value: "up", descriptionText: "${cd.displayName} was moved up"]])
                        cd.sendEvent(name: "curtainStatus", value: "up", isStateChange: true)
                    } else if (downRelayStatus == 1) {
                        cd.parse([[name: "switch", value: "down", descriptionText: "${cd.displayName} was moved down"]])
                        cd.sendEvent(name: "curtainStatus", value: "down", isStateChange: true)
                    } else {
                        cd.parse([[name: "switch", value: "stop", descriptionText: "${cd.displayName} was stopped"]])
                        cd.sendEvent(name: "curtainStatus", value: "stop", isStateChange: true)
                    }
                }
            }
        } else {
        logInfo("Curtain: No Changes - Nothing to update")
    }
    
        }
    
    
}



def updateChildStatus(int curtainNumber, String upRelayStatus, String downRelayStatus) {
    def chdid = "${state.netids}${curtainNumber < 10 ? "0${curtainNumber}" : "${curtainNumber}"}"
    def cd = getChildDevice(chdid)
    if (cd) {
        if (upRelayStatus == "1") {
            cd.parse([[name: "switch", value: "up", descriptionText: "${cd.displayName} was moved up by switch0"]])
        } else if (downRelayStatus == "1") {
            cd.parse([[name: "switch", value: "down", descriptionText: "${cd.displayName} was moved down by switch0"]])
        } else {
            cd.parse([[name: "switch", value: "stop", descriptionText: "${cd.displayName} was stopped by switch0"]])
        }
    }
}


def on() {
    logDebug("Master Power ON()")
    masteron()
}

def off() {
    logDebug("Master Power OFF()")
    masteroff()
}

def masteron() {
    logInfo("MasterON() Executed (new)")
    for (int i = 1; i <= state.inputcount/2; i++) {
        def curtainNumber = i < 10 ? "0${i}" : "${i}"
        def chdid = "${state.netids}${curtainNumber}"
        def cd = getChildDevice(chdid)
        if (cd) {
            cd.parse([[name: "switch", value: "up", descriptionText: "${cd.displayName} was moved up"]])
            logInfo("Curtain ${cd.displayName} moved UP")
            on(cd)
            pauseExecution(200) // Reduced delay
        }
    }
}

def masteroff() {
    logInfo("MasterOFF() Executed (new)")
    for (int i = 1; i <= state.inputcount/2; i++) {
        def curtainNumber = i < 10 ? "0${i}" : "${i}"
        def chdid = "${state.netids}${curtainNumber}"
        def cd = getChildDevice(chdid)
        if (cd) {
            cd.parse([[name: "switch", value: "down", descriptionText: "${cd.displayName} was moved down"]])
            logInfo("Curtain ${cd.displayName} moved DOWN")
            off(cd)
            pauseExecution(200) // Reduced delay
        }
    }
}

private sendCommand(s) {
    logDebug("sendCommand ${s}")
    interfaces.rawSocket.sendMessage(s)    
}

def sendNotification(String message) {
    if (settings.enableNotifications) {
        sendPush(message)
    }
}

def connectionCheck() {
    def now = now()
    def timeSinceLastMessage = now - state.lastMessageReceivedAt
    if (timeSinceLastMessage > (checkInterval * 1000)) {
        logError("ConnectionCheck: No messages received for ${timeSinceLastMessage / 1000} seconds. Attempting to reconnect...")
        
        // Set board status to offline
        if (boardstatus != "offline") {
            setBoardStatus("offline")  // This will handle the notification
        }

        // Attempt immediate reconnection
        initialize()

        // Schedule the next connection check with exponential backoff
        def retryCount = state.retryCount ?: 0
        def retryDelay = Math.min(300, (retryCount + 1) * 60) // Exponential backoff, max 5 minutes
        state.retryCount = retryCount + 1
        runIn(retryDelay, "connectionCheck")
    } else {
        logInfo("Connection Check: Status OK - Board Online")
        
        // Reset retry count if connection is stable
        state.retryCount = 0

        if (boardstatus != "online") {
            setBoardStatus("online")  // This will handle the notification
        }

        // Schedule the next connection check
        runIn(checkInterval, "connectionCheck")
    }
}

def setBoardStatus(String status) {
    if (state.boardstatus != status) {

        sendEvent(name: "boardstatus", value: status, isStateChange: true)
        boardstatus = status
        state.boardstatus = status
        
        // Check if a notification has already been sent for this status
        if (state.lastNotificationStatus != status) {
            state.lastNotificationStatus = status  // Update the last notification status
            if (status == "online") {
                logInfo("MolSmart Board is now online.")  // Log message for online
                if (settings.enableNotifications) {
                    sendPush("MolSmart Board is now online.")  // Send notification
                }
            } else if (status == "offline") {
                logInfo("MolSmart Board is now offline.") // Log message for offline
                if (settings.enableNotifications) {
                    sendPush("MolSmart Board is now offline.") // Send notification
                }
            }
        }
    }
}


void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from ${cd.displayName}"
    refresh()
}

void componentOn(cd){
    if (logEnable) log.info "received on request from ${cd.displayName}"
    on(cd)
}

void componentOff(cd){
    if (logEnable) log.info "received off request from ${cd.displayName}"
    off(cd)
}

void componentStop(cd){
    if (logEnable) log.info "received stop request from ${cd.displayName}"
    stop(cd)
}



void on(cd) {
    def relay = getRelayNumber(cd)
    def upRelay = (relay * 2) - 1
    sendCommand("1${upRelay}") // Command to turn on the "up" relay
    logInfo("Curtain ${cd.displayName} moved UP")
    runIn(settings.stopTime, "stop", [data: cd]) // Schedule stop after stopTime
}

void off(cd) {
   def relay = getRelayNumber(cd)
    def downRelay = relay * 2
    sendCommand("1${downRelay}") // Command to turn on the "down" relay
    logInfo("Curtain ${cd.displayName} moved DOWN")
    //sendEvent(name: "switch", value: "off")
    runIn(settings.stopTime, "stop", [data: cd]) // Schedule stop after stopTime

}

void stop(cd) {
    def relay = getRelayNumber(cd)
    def upRelay = (relay * 2) - 1
    def downRelay = relay * 2
    sendCommand("2${upRelay}") // Command to turn off the "up" relay
    sendCommand("2${downRelay}") // Command to turn off the "down" relay
    logInfo("Curtain ${cd.displayName} was stopped")  
    curtainStatus = "stop" 
    log.info "STOP was executed for " + cd.displayName  
    sendEvent(name: "vaicagarsupportedCommands", value: "stop")
    
    
}

private getRelayNumber(cd) {
    def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1)
    def result01 = cd.deviceNetworkId.length() - substr1
    def numervalue1 = result01 > 2 ? cd.deviceNetworkId[substr1+1..substr1+2] : cd.deviceNetworkId[substr1+1]
    log.info "getrelaynumber " + numervalue1
    return numervalue1 as Integer
}

private processEvent( Variable, Value ) {
    if ( state."${ Variable }" != Value ) {
        state."${ Variable }" = Value
        logDebug( "Event: ${ Variable } = ${ Value }" )
        sendEvent( name: "${ Variable }", value: Value, isStateChanged: true )
    }
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
