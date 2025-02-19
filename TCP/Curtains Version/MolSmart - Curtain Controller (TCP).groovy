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
 */

metadata {
  definition (name: "MolSmart - Curtain Controller (TCP)", namespace: "VH", author: "VH", vid: "generic-contact", singleThreaded: true) {
    capability "Switch"  
    capability "Initialize"
    capability "Refresh"       
  }
}

import groovy.json.JsonSlurper
import groovy.transform.Field

command "queryBoardStatus"
command "masteron"
command "masteroff"
command "stop"


@Field static final String DRIVER = "by TRATO"
@Field static final String USER_GUIDE = "https://github.com/hhorigian/"

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

    attribute "powerstatus", "string"
    attribute "boardstatus", "string"
}

@Field static String partialMessage = ''

def installed() {
    logTrace('installed()')
    state.childscreated = 0
    state.boardstatus = "offline"    
    boardstatus = "offline"
    runIn(1800, logsOff)
}

def uninstalled() {
    logTrace('uninstalled()')
    unschedule()
    interfaces.rawSocket.close()
}

def updated() {
    logTrace('updated()')   
    refresh()
}

def initialize() {
//  state.childscreated = 0
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

    //setBoardStatus("connecting")

    //Llama la busca de count de inputs+outputs via HTTP
    buscainputcount()
    
    // Query the board for its current relay status
    queryBoardStatus()
    
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
    runIn(10, "refresh")
}




def createchilds() {
    String thisId = device.id
    def cd = getChildDevice("${thisId}-Curtain")
    state.netids = "${thisId}-Curtain-"
    
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


def queryBoardStatus() {
    try {
        httpGet("http://${settings.device_IP_address}/relay_cgi_load.cgi") { resp ->
            if (resp.success) {
                logDebug "QueryBoardStatus: Fetched relay status: ${resp.data}"
                
                // Convert the response to a string
                def responseText = resp.data.toString()
                
                // Split the response by the delimiter '&'
                def relayStatus = responseText.split('&')
                
                if (relayStatus.size() > 2) {
                    def relayCount = relayStatus[2] as int
                    for (int i = 1; i <= relayCount/2; i++) {
                        def curtainNumber = i < 10 ? "0${i}" : "${i}"
                        def chdid = "${state.netids}${curtainNumber}"
                        def cd = getChildDevice(chdid)
                        if (cd) {
                            def upRelayStatus = relayStatus[(i*2)-1] as int
                            def downRelayStatus = relayStatus[i*2] as int
                            if (upRelayStatus == 1) {
                                cd.parse([[name: "switch", value: "up", descriptionText: "${cd.displayName} was moved up"]])
                            } else if (downRelayStatus == 1) {
                                cd.parse([[name: "switch", value: "down", descriptionText: "${cd.displayName} was moved down"]])
                            } else {
                                cd.parse([[name: "switch", value: "off", descriptionText: "${cd.displayName} was stopped"]])
                            }
                        }
                    }
                }
            } else {
                logError("Failed to fetch relay status: ${resp.status}")
            }
        }
    } catch (Exception e) {
        logError("Error in queryBoardStatus: ${e.message}")
    }
}



def refresh() {
    logInfo('Refresh()')
    def msg = "REFRESH"    
    sendCommand(msg)
}

def parse(String msg) {
    String thisId = device.id
    state.netids = "${thisId}-Curtain-"
    
    state.lastMessageReceived = new Date(now()).toString();
    state.lastMessageReceivedAt = now();
    
    def newmsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) //na Mol, o resultado vem em HEX, então preciso converter para Array
    def newmsg2 = new String(newmsg) // Array para String    
    state.lastmessage = newmsg2
    
    logDebug("****** New Block LOG Parse ********")
    logDebug("Last Msg: ${newmsg2}")
    logDebug("Qde chars = ${newmsg2.length()}")

    if (state.inputcount == 2) {
        state.channels = 2
        parse2CH(newmsg2)
    } else if (state.inputcount == 4) {
        state.channels = 4        
        parse4CH(newmsg2)
    } else if (state.inputcount == 8) {
        state.channels = 8
        //parse8CH(newmsg2)
    } else if (state.inputcount == 16) {
        state.channels = 16
        parse16CH(newmsg2)
    } else if (state.inputcount == 32) {
        state.channels = 32
        parse32CH(newmsg2)
    } else {
        log.warn "Unknown input count: ${state.inputcount}"
    }
}

def on() {
    logDebug("Master Cortinas UP()")
    masteron()
}

def off() {
    logDebug("Master Cortinas DOWN()")
    masteroff()
}

def masteron() {
    logInfo("MasterUP() Executado ")
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
    logInfo("MasterDOWN() Executado ")
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
        
        if (boardstatus != "offline") {
            setBoardStatus("offline")
        }

        initialize()

        def retryCount = state.retryCount ?: 0
        def retryDelay = Math.min(300, (retryCount + 1) * 60)
        state.retryCount = retryCount + 1
        runIn(retryDelay, "connectionCheck")
    } else {
        logInfo("Connection Check: Status OK - Board Online")
        
        state.retryCount = 0

        if (boardstatus != "online") {
            setBoardStatus("online")
        }

        runIn(checkInterval, "connectionCheck")
    }
}

def setBoardStatus(String status) {
    if (state.boardstatus != status) {
        boardstatus = status
        state.boardstatus = status
        if (status == "online" || status == "offline") {
            sendEvent(name: "boardstatus", value: status, isStateChange: true)
        }
        
        if (state.lastNotificationStatus != status) {
            state.lastNotificationStatus = status
            
            if (status == "online") {
                logInfo("MolSmart Board is now online.")
                if (settings.enableNotifications) {
                    sendPush("MolSmart Board is now online.")
                }
            } else if (status == "offline") {
                logInfo("MolSmart Board is now offline.")
                if (settings.enableNotifications) {
                    sendPush("MolSmart Board is now offline.")
                }
            }
        }
    }
}

void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from ${cd.displayName}"
    refresh()
}

void componentOn(cd) {
    if (logEnable) log.info "received on request from ${cd.displayName}"
    on(cd)
}

void componentOff(cd) {
    if (logEnable) log.info "received off request from ${cd.displayName}"
    off(cd)
}

void componentStop(cd) {
    if (logEnable) log.info "received stop request from ${cd.displayName}"
    stop(cd)
}

void on(cd) {
    sendEvent(name: "switch", value: "up", isStateChange: true)
    ipdomodulo  = state.ipaddress
    lengthvar =  (cd.deviceNetworkId.length())
    int relay = 0
    def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1);
    def result01 = lengthvar - substr1 
    if (result01 > 2  ) {
        def  substr2a = substr1 + 1
        def  substr2b = substr1 + 2
        def substr3 = cd.deviceNetworkId[substr2a..substr2b]
        numervalue1 = substr3
    } else {
        def substr3 = cd.deviceNetworkId[substr1+1]
        numervalue1 = substr3
    }

    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   

    // Calculate the "up" relay (e.g., Curtain 1 -> Relay1, Curtain 2 -> Relay3, etc.)
    def upRelay = (relay * 2) - 1

    // Calculate the "down" relay (e.g., Curtain 1 -> Relay2, Curtain 2 -> Relay4, etc.)
    def downRelay = relay * 2

    // Send command to activate the "up" relay
    def comandoUp = "1" + upRelay
    interfaces.rawSocket.sendMessage(comandoUp)
    log.info "Send command UP to relay ${upRelay} = " + comandoUp

    // Send command to deactivate the "down" relay
    def comandoDown = "2" + downRelay
    interfaces.rawSocket.sendMessage(comandoDown)
    log.info "Send command DOWN to relay ${downRelay} = " + comandoDown

    // Schedule the stop command after the specified stopTime
    runIn(settings.stopTime, "stop", [data: cd])
}



void componentStop(cd) {
    if (logEnable) log.info "received stop request from ${cd.displayName}"
    stop(cd)
}


void stop(cd) {
    sendEvent(name: "switch", value: "off", isStateChange: true)
    ipdomodulo  = state.ipaddress
    lengthvar =  (cd.deviceNetworkId.length())
    int relay = 0
    def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1);
    def result01 = lengthvar - substr1 
    if (result01 > 2  ) {
        def  substr2a = substr1 + 1
        def  substr2b = substr1 + 2
        def substr3 = cd.deviceNetworkId[substr2a..substr2b]
        numervalue1 = substr3
    } else {
        def substr3 = cd.deviceNetworkId[substr1+1]
        numervalue1 = substr3
    }

    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   

    // Calculate the "up" relay (e.g., Curtain 1 -> Relay1, Curtain 2 -> Relay3, etc.)
    def upRelay = (relay * 2) - 1

    // Calculate the "down" relay (e.g., Curtain 1 -> Relay2, Curtain 2 -> Relay4, etc.)
    def downRelay = relay * 2

    // Send command to deactivate the "up" relay
    def comandoUp = "2" + upRelay
    interfaces.rawSocket.sendMessage(comandoUp)
    log.info "Send command STOP to relay ${upRelay} = " + comandoUp

    // Send command to deactivate the "down" relay
    def comandoDown = "2" + downRelay
    interfaces.rawSocket.sendMessage(comandoDown)
    log.info "Send command STOP to relay ${downRelay} = " + comandoDown
}




void off(cd) {
    sendEvent(name: "switch", value: "down", isStateChange: true)
    ipdomodulo  = state.ipaddress
    lengthvar =  (cd.deviceNetworkId.length())
    int relay = 0
    def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1);
    def result01 = lengthvar - substr1 
    if (result01 > 2  ) {
        def  substr2a = substr1 + 1
        def  substr2b = substr1 + 2
        def substr3 = cd.deviceNetworkId[substr2a..substr2b]
        numervalue1 = substr3
    } else {
        def substr3 = cd.deviceNetworkId[substr1+1]
        numervalue1 = substr3
    }

    def valor = ""
    valor =   numervalue1 as Integer
    relay = valor   

    // Calculate the "up" relay (e.g., Curtain 1 -> Relay1, Curtain 2 -> Relay3, etc.)
    def upRelay = (relay * 2) - 1

    // Calculate the "down" relay (e.g., Curtain 1 -> Relay2, Curtain 2 -> Relay4, etc.)
    def downRelay = relay * 2

    // Send command to activate the "down" relay
    def comandoDown = "1" + downRelay
    interfaces.rawSocket.sendMessage(comandoDown)
    log.info "Send command DOWN to relay ${downRelay} = " + comandoDown

    // Send command to deactivate the "up" relay
    def comandoUp = "2" + upRelay
    interfaces.rawSocket.sendMessage(comandoUp)
    log.info "Send command UP to relay ${upRelay} = " + comandoUp

    // Schedule the stop command after the specified stopTime
    runIn(settings.stopTime, "stop", [data: cd])
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
