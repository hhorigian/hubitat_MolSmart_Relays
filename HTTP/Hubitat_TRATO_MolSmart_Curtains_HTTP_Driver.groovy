/**
 *  Hubitat - HTTP MolSmart Curtain Controller Drivers by VH - 
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
 *      1.1 28/3/2025  - Updated to use HTTP commands instead of TCP
 *      1.2 - Added 600 Seconds channel verification and 1-second delays between commands
 */

metadata {
  definition (name: "MolSmart - HTTP Curtain Controller", namespace: "TRATO", author: "VH", vid: "generic-contact", singleThreaded: true) {
    capability "Switch"  
    capability "Configuration"
    capability "Initialize"
    capability "Refresh"       
  }
}

import groovy.json.JsonSlurper
import groovy.transform.Field
import java.net.URI

command "masteron"
command "masteroff"
command "stop"
command "createchilds"

@Field static final String DRIVER = "by TRATO"
@Field static final String USER_GUIDE = "https://github.com/hhorigian/hubitat_MolSmart_Relays/tree/main/HTTP"


String fmtHelpInfo(String str) {
    String prefLink = "<a href='${USER_GUIDE}' target='_blank'>${str}<br><div style='font-size: 70%;'>${DRIVER}</div></a>"
    return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>"
}

preferences {
    input "device_IP_address", "text", title: "MolSmart IP Address", required: true 
    input "device_port", "number", title: "IP Port of Device", required: true, defaultValue: 80
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input 'logInfo', 'bool', title: 'Show Info Logs?',  required: false, defaultValue: false
    input 'logDebug', 'bool', title: 'Show Debug Logs?', description: 'Only leave on when required', required: false, defaultValue: false
    input "checkInterval", "number", title: "Connection Check Interval (seconds)", defaultValue: 90, required: true
    input "enableNotifications", "bool", title: "Enable Connection Status Notifications", defaultValue: false      
    input "stopTime", "number", title: "Stop Time (seconds)", description: "Time in seconds before stopping the curtains", defaultValue: 10, required: true
    input "devicePassword", "number", title: "Device Password", defaultValue: 0, required: true
    
    //help guide
    input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver") 

    attribute "boardstatus", "string"
    attribute "curtainStatus", "string"
}

def installed() {
    logTrace('installed()')
    state.childscreated = 0
    state.boardstatus = "offline"    
    state.lastMovementCommands = [:]  // Only tracks up/down movements    
    state.currentMovementStatus = [:] // Tracks current movement state    
    boardstatus = "offline"
    runIn(55, "verifyAllChannelsOff") // Start the 30-second verification
}

def uninstalled() {
    logTrace('uninstalled()')
    unschedule()
}

def updated() {
    logTrace('updated()')   
    refresh()
    state.lastMovementCommands = [:]  // Only tracks up/down movements
    state.currentMovementStatus = [:] // Tracks current movement state    
    unschedule()
    runIn(55, "verifyAllChannelsOff") // Restart the 30-second verification after update
}

def initialize() {
    unschedule()
    logTrace('Run Initialize()')
    
    if (!device_IP_address) {
        logError 'IP do Device not configured'
        return
    }

    // Query the board for its current relay status
    queryBoardStatus()
    
    // Create child devices if not already created
    if (boardstatus == "online") {
        logTrace("Go to-> Creating Childs")
        createchilds()
    }

    runIn(10, "refresh")
    runIn(30, "verifyAllChannelsOff") // Start the 30-second verification
}

// Modified verifyAllChannelsOff method
def verifyAllChannelsOff() {
    // Skip verification if any movement is in progress
    if (isAnyMovementInProgress()) {
        logDebug("Skipping channel verification - movement in progress")
    } else {
        logDebug("Verifying all channels are off")
        if (state.inputcount) {
            for (int i = 1; i <= state.inputcount; i++) {
                sendHTTPCommand(i, "off")
                pauseExecution(2000) // 2-second delay between commands
            }
        }
    }
    runIn(30, "verifyAllChannelsOff") // Reschedule for 30 seconds later
}

def createchilds() {
    String thisId = device.id
    def cd = getChildDevice("${thisId}-Curtain")
    state.netids = "${thisId}-Curtain-"
    log.debug "Dentro do CreateChilds. Childscreated = " + state.childscreated
    if (state.childscreated == 0) {
        if (!cd) {
            log.info "Inputcount = " + state.inputcount 
            for(int i = 1; i<=state.inputcount/2; i++) {        
                if (i < 10) {
                    numerocurtain = "0" + Integer.toString(i)
                } else {
                    numerocurtain = Integer.toString(i)
                }             
                cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-Curtain-" + numerocurtain, [name: "${device.displayName} Curtain-" + numerocurtain , isComponent: true])
                cd.sendEvent(name: "supportedCommands", value: ["on", "off", "stop"], isStateChange: true)                             
                log.info "added curtain # " + i + " from " + state.inputcount/2            
            }
        }
        state.childscreated = 1   
    } else {
        log.info "Childs previously created. Nothing done"
    }
}

def queryBoardStatus() {
    try {
        def params = [
            uri: "http://${device_IP_address}:${device_port}",
            path: "/relay_cgi_load.cgi",
            timeout: 10,
            contentType: "text/plain"  // Explicitly expect text response
        ]
        
        httpGet(params) { resp ->
            if (resp.status == 200) {
                setBoardStatus("online")
                // Get the response data as text
                def responseText = resp.data.text ?: resp.data.toString()
                parseRelayStatus(responseText)
            } else {
                logError("Failed to fetch relay status: HTTP ${resp.status}")
                setBoardStatus("offline")
            }
        }
    } catch (Exception e) {
        logError("Error in queryBoardStatus: ${e.message}")
        setBoardStatus("offline")
    }
}

def parseRelayStatus(responseText) {
    // The response is in format like: &0&4&1&0&1&0&
    def parts = responseText.split('&')
    if (parts.size() >= 7) {  // Minimum valid response has 7 parts (including empty first part)
        def channelCount = parts[2] as int
        state.inputcount = channelCount
        
        // Update child devices based on relay status
        for (int i = 1; i <= channelCount/2; i++) {
            def upRelayStatus = parts[3 + (i*2)-2] as int
            def downRelayStatus = parts[3 + (i*2)-1] as int
            updateChildStatus(i, upRelayStatus, downRelayStatus)
        }
    } else {
        logError("Invalid response format: ${responseText}")
    }
}

void updateChildStatus(int curtainNumber, int upRelayStatus, int downRelayStatus) {
    def curtainNumberStr = curtainNumber < 10 ? "0${curtainNumber}" : "${curtainNumber}"
    def chdid = "${state.netids}${curtainNumberStr}"
    def childDevice = getChildDevice(chdid)
    if (childDevice) {
        // Initialize state if not exists
        if (state.lastMovementCommands == null) state.lastMovementCommands = [:]
        if (state.currentMovementStatus == null) state.currentMovementStatus = [:]
        
        if (upRelayStatus == 1) {
            childDevice.sendEvent(name: "switch", value: "on", descriptionText: "${childDevice.displayName} is moving up")
            childDevice.sendEvent(name: "curtainStatus", value: "up", isStateChange: true)
            state.lastMovementCommands[childDevice.deviceNetworkId] = "up"
            state.currentMovementStatus[childDevice.deviceNetworkId] = "up"
        } else if (downRelayStatus == 1) {
            childDevice.sendEvent(name: "switch", value: "on", descriptionText: "${childDevice.displayName} is moving down")
            childDevice.sendEvent(name: "curtainStatus", value: "down", isStateChange: true)
            state.lastMovementCommands[childDevice.deviceNetworkId] = "down"
            state.currentMovementStatus[childDevice.deviceNetworkId] = "down"
        } else {
            childDevice.sendEvent(name: "switch", value: "off", descriptionText: "${childDevice.displayName} stopped")
            childDevice.sendEvent(name: "curtainStatus", value: "stop", isStateChange: true)
            state.currentMovementStatus[childDevice.deviceNetworkId] = "stop"
        }
    }
}

def refresh() {
    logInfo('Refresh()')
    queryBoardStatus()
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
    logInfo("MasterON() Executed")
    for (int i = 1; i <= state.inputcount/2; i++) {
        def curtainNumber = i < 10 ? "0${i}" : "${i}"
        def chdid = "${state.netids}${curtainNumber}"
        def cd = getChildDevice(chdid)
        if (cd) {
            on(cd)
            pauseExecution(1000) // 2-second delay between commands
        }
    }
}

def masteroff() {
    logInfo("MasterOFF() Executed")
    for (int i = 1; i <= state.inputcount/2; i++) {
        def curtainNumber = i < 10 ? "0${i}" : "${i}"
        def chdid = "${state.netids}${curtainNumber}"
        def cd = getChildDevice(chdid)
        if (cd) {
            off(cd)
            pauseExecution(1000) // 2-second delay between commands
        }
    }
}

private sendHTTPCommand(relay, action) {
    def cmd = action == "on" ? "1" : "2"
    def params = [
        uri: "http://${device_IP_address}:${device_port}",
        path: "/relay_cgi.cgi",
        query: [
            type: "0",
            relay: "${relay-1}",
            on: cmd == "1" ? "1" : "0",
            time: "0",
            pwd: "${devicePassword ?: 0}"
        ],
        timeout: 10
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                logDebug("Command ${action} for relay ${relay} successful")
                return true
            } else {
                logError("Command ${action} for relay ${relay} failed: HTTP ${resp.status}")
                return false
            }
        }
    } catch (Exception e) {
        logError("Error sending command to relay ${relay}: ${e.message}")
        return false
    }
}

void componentRefresh(cd){
    if (logEnable) log.info "received refresh request from ${cd.displayName}"
    refresh()
}

void componentOn(cd) {
    if (logEnable) log.info "received on request from ${cd.displayName}"
    def childDevice = getChildDevice(cd.deviceNetworkId)
    if (childDevice) {
        // Initialize state if not exists
        if (state.lastMovementCommands == null) state.lastMovementCommands = [:]
        if (state.currentMovementStatus == null) state.currentMovementStatus = [:]
        
        // Check current movement status
        if (state.currentMovementStatus[childDevice.deviceNetworkId] == "down") {
            logInfo("Cannot move UP while curtain ${childDevice.displayName} is going DOWN")
            return
        }
        
        // Check if already moving up
        if (state.currentMovementStatus[childDevice.deviceNetworkId] == "up") {
            logInfo("Curtain ${childDevice.displayName} is already moving UP")
            return
        }
        
        def relay = getRelayNumber(cd)
        def upRelay = (relay * 2) - 1
        
        // First turn off any existing movement
        sendHTTPCommand(upRelay, "off")
        pauseExecution(2000) // 2-second delay
        sendHTTPCommand(upRelay+1, "off") // Ensure down relay is also off
        pauseExecution(2000) // 2-second delay
        
        if (sendHTTPCommand(upRelay, "on")) {
            logInfo("Curtain ${childDevice.displayName} moving UP")
            childDevice.sendEvent(name: "switch", value: "on", descriptionText: "${childDevice.displayName} is moving up")
            childDevice.sendEvent(name: "curtainStatus", value: "up", isStateChange: true)
            state.lastMovementCommands[childDevice.deviceNetworkId] = "up"
            state.currentMovementStatus[childDevice.deviceNetworkId] = "up"
            runIn(settings.stopTime, "stop", [data: cd])
        }
    } else {
        logError("Could not find child device ${cd.deviceNetworkId}")
    }
}

void componentOff(cd) {
    if (logEnable) log.info "received off request from ${cd.displayName}"
    def childDevice = getChildDevice(cd.deviceNetworkId)
    if (childDevice) {
        // Initialize state if not exists
        if (state.lastMovementCommands == null) state.lastMovementCommands = [:]
        if (state.currentMovementStatus == null) state.currentMovementStatus = [:]
        
        // Check current movement status
        if (state.currentMovementStatus[childDevice.deviceNetworkId] == "up") {
            logInfo("Cannot move DOWN while curtain ${childDevice.displayName} is going UP")
            return
        }
        
        // Check if already moving down
        if (state.currentMovementStatus[childDevice.deviceNetworkId] == "down") {
            logInfo("Curtain ${childDevice.displayName} is already moving DOWN")
            return
        }
        
        def relay = getRelayNumber(cd)
        def downRelay = relay * 2
        
        // First turn off any existing movement
        sendHTTPCommand(downRelay, "off")
        pauseExecution(2000) // 2-second delay
        sendHTTPCommand(downRelay-1, "off") // Ensure up relay is also off
        pauseExecution(2000) // 2-second delay
        
        if (sendHTTPCommand(downRelay, "on")) {
            logInfo("Curtain ${childDevice.displayName} moving DOWN")
            childDevice.sendEvent(name: "switch", value: "on", descriptionText: "${childDevice.displayName} is moving down")
            childDevice.sendEvent(name: "curtainStatus", value: "down", isStateChange: true)
            state.lastMovementCommands[childDevice.deviceNetworkId] = "down"
            state.currentMovementStatus[childDevice.deviceNetworkId] = "down"
            runIn(settings.stopTime, "stop", [data: cd])
        }
    } else {
        logError("Could not find child device ${cd.deviceNetworkId}")
    }
}

void componentStop(cd) {
    if (logEnable) log.info "received stop request from ${cd.displayName}"
    def childDevice = getChildDevice(cd.deviceNetworkId)
    if (childDevice) {
        def relay = getRelayNumber(cd)
        def upRelay = (relay * 2) - 1
        def downRelay = relay * 2
        
        sendHTTPCommand(upRelay, "off")
        pauseExecution(2000) // 2-second delay
        sendHTTPCommand(downRelay, "off")
        
        logInfo("Curtain ${childDevice.displayName} stopped")  
        childDevice.sendEvent(name: "switch", value: "off", descriptionText: "${childDevice.displayName} stopped")
        childDevice.sendEvent(name: "curtainStatus", value: "stop", isStateChange: true)
        state.currentMovementStatus[childDevice.deviceNetworkId] = "stop"
    } else {
        logError("Could not find child device ${cd.deviceNetworkId}")
    }
}

void on(cd) {
    componentOn(cd)
}

void off(cd) {
    componentOff(cd)
}

void stop(cd) {
    componentStop(cd)
}

private getRelayNumber(cd) {
    def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1)
    def result01 = cd.deviceNetworkId.length() - substr1
    def numervalue1 = result01 > 2 ? cd.deviceNetworkId[substr1+1..substr1+2] : cd.deviceNetworkId[substr1+1]
    return numervalue1 as Integer
}

def setBoardStatus(String status) {
    if (state.boardstatus != status) {
        sendEvent(name: "boardstatus", value: status, isStateChange: true)
        boardstatus = status
        state.boardstatus = status
        
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

// Add this new method to check if any movement is in progress
private boolean isAnyMovementInProgress() {
    if (state.currentMovementStatus) {
        return state.currentMovementStatus.any { key, value -> value in ["up", "down"] }
    }
    return false
}




def connectionCheck() {
    queryBoardStatus()
    runIn(checkInterval, "connectionCheck")
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

void logError(String msg) {
    log.error "${drvThis}: ${msg}"
}
