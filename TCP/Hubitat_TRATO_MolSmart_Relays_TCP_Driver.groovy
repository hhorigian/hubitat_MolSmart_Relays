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
 *        1.4 05/22/2024 - Fix Scenes by adding "pauseExecution(250)" for On and Off in Childs 
 *        1.5 06/05/2024 - Added Help Guide Link
 *        1.6 13/06/2024 - Added Help Guide Link  v2
 *        1.7 26/06/2024 - Added "0" digit to switch name, to sort nicely the switch names. 
 *        1.8 27/06/2024 - Fixed double digit error on updates after v.1.7.  
 *        1.9 29/06/2024 - Added Board Status  Fix for Onlin/Offline to be used in Rule Machine Notifications + Improved Initialize/Update/Install Functions + Improved Logging + Added ManualKeepAlive Check Command.
 *        2.0 16/07/2024 - Fixed erro on line code 587 - MANDATORY UPDATE.
 *        2.1 30/07/2024 - Changed ouput status reading response method for TCP + Improved feedback response and status + Fixed false ghost feedback + Changed Master on/Master Off sequence with 250ms after each ch.  
 *        2.2 30/07/2024 - Fixed 16CH Count Relays. Fixed 32Ch.  Update for Long NetworkIds, used a new function to find index of in lines 959 and 1006. Added 32CH Master on/off.
 *        2.3 05/08/2024 - Added Line 278, with String thisId = device.id
 *        2.4 13/08/2024 - Added singleThreaded: true to metadata definition to fix Alexa Group issue
 *        2.5 17/02/2025 - Added 2CH Board. 
 *						 - Improved Reconnect function for disconnected board.  
 *						 - Updated and splitted code for Parsing, Added Status for Online/Offline. Changed Masteron/masteroff speeds and processing.  
 *						 - Changed Logging defaults. Added BoardStatus State variable and Notification OPtion. 
 *						 - UPDATE: Added Variable for Check Interval in seconds, 
 *						 - UPDATE: Added option for enable notifications. 
 *
 *
 */
metadata {
  definition (name: "MolSmart - Relay 2/4/8/16/32CH (TCP)", namespace: "TRATO", author: "VH", vid: "generic-contact", singleThreaded: true) {
        capability "Switch"  
        capability "Configuration"
        capability "Initialize"
        capability "Refresh"       
  }
  }

import groovy.json.JsonSlurper
import groovy.transform.Field
//command "buscainputcount"
//command "createchilds"
//command "connectionCheck"
//command "ManualKeepAlive"
command "queryBoardStatus"

command "masteron"
command "masteroff"
//command "clearAllvalues"

    import groovy.transform.Field
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
      
    
    //help guide
    input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver") 

    attribute "powerstatus", "string"
    attribute "boardstatus", "string"
    //attribute "boardlaststatus", "string"  
           
  }   


@Field static String partialMessage = ''
//@Field static Integer checkInterval = 150

def installed() {
    logTrace('installed()')
    state.childscreated = 0
    state.boardstatus = "offline"    
    boardstatus = "offline"
    runIn(1800, logsOff)
} //OK

def uninstalled() {
    logTrace('uninstalled()')
    unschedule()
    interfaces.rawSocket.close()
} //OK

def updated() {
    logTrace('updated()')   
    refresh()
}


def ManualKeepAlive (){
    logTrace('ManualKeepAlive()')
    interfaces.rawSocket.close();
    unschedule()
    
    //Llama la busca de count de inputs+outputs
    buscainputcount()
    
 try {
        logTrace("Initialize: Tentando conexão com a MolSmart no IP:  ${device_IP_address}...na porta configurada: ${device_port}")
        interfaces.rawSocket.connect(device_IP_address, (int) device_port)
        state.lastMessageReceivedAt = now()
        setBoardStatus("online")
        runIn(checkInterval, "connectionCheck")
    }

    catch (e) {
        logError("Initialize: com ${device_IP_address} com um error: ${e.message}")
        logError("Problemas na Rede ou Conexão com o Board MolSmart - Verificar IP/PORTA/Rede/Energia")
        setBoardStatus("offline")
        def retryDelay = Math.min(300, (state.retryCount ?: 0) * 60) // Exponential backoff, max 5 minutes
        state.retryCount = (state.retryCount ?: 0) + 1
        runIn(retryDelay, "initialize") // Retry after calculated delay
    }
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
	    def cd = getChildDevice("${thisId}-Switch")
        state.netids = "${thisId}-Switch-"
	
	if (state.childscreated == 0) {
    

	if (!cd) {
        log.info "Inputcount = " + state.inputcount 
        for(int i = 1; i<=state.inputcount; i++) {        
                if (i < 10) {     //Verify to add double digits to switch name. 
                numerorelay = "0" + Integer.toString(i)
                }    
                else {
                numerorelay = Integer.toString(i)
                }             
        cd = addChildDevice("hubitat", "Generic Component Switch", "${thisId}-Switch-" + numerorelay, [name: "${device.displayName} Switch-" + numerorelay , isComponent: true])
        log.info "added switch # " + i + " from " + state.inputcount            
        
    }
    }
      state.childscreated = 1   
    }
    else {
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
                    for (int i = 1; i <= relayCount; i++) {
                        def relayNumber = i < 10 ? "0${i}" : "${i}"
                        def chdid = "${state.netids}${relayNumber}"
                        def cd = getChildDevice(chdid)
                        if (cd) {
                            def status = relayStatus[i + 2] as int
                            if (status == 1) {
                                cd.parse([[name: "switch", value: "on", descriptionText: "${cd.displayName} was turned on"]])
                            } else {
                                cd.parse([[name: "switch", value: "off", descriptionText: "${cd.displayName} was turned off"]])
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


/////////////////////////
//Feedback e o tratamento 
/////////////////////////

 def fetchChild(String type, String name){
    String thisId = device.id
    def cd = getChildDevice("${thisId}-${type}_${name}")
    if (!cd) {
        cd = addChildDevice("hubitat", "Generic Component ${type}", "${thisId}-${type}_${name}", [name: "${name}", isComponent: true])
        cd.parse([[name:"switch", value:"off", descriptionText:"set initial switch value"]]) //TEST!!
    }
    return cd 
}


def parse2CH(String newmsg2) {
    // Handle 2-channel relay parsing
    if ((newmsg2.length() > 28 )) {
             outputs_changed_1 = newmsg2[12..16]  //changes in relays reported in 1st line of return. Sometimes it returns in first line. 
             outputs_changed_2 = newmsg2[34..37]  //changes in relays reported in 2nd line of return
             outputs_status = newmsg2[22..25]     //status of relays reported in 2nd line of return 

                   if ((outputs_changed_2.contains("1")) || (outputs_changed_1.contains("1"))) {
                       if (outputs_changed_2.contains("1")) {
                                relaychanged = outputs_changed_2.indexOf('1'); 
                           
                       } else
                           {
                                relaychanged = outputs_changed_1.indexOf('1');
                           }
                       
                   log.debug ("Yes - change in Relay (with input)")
                   log.debug "outputs_changed_2 relay # = " + relaychanged    
                        
                 z = relaychanged +1 
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                     
                 }    
                
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 } 
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                 statusrelay = outputs_status.getAt(relaychanged)
                       //log.debug "relaychanged  = " +  relaychanged
                       //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                 }            
        } //LENGTH = 44
        
        
        if ((newmsg2.length() == 14 )) {    
            
            outputs_changed = newmsg2[8..9]       
            inputs_changed = newmsg2[11..12]
            novaprimeira_output = newmsg2[0..1]
            novaprimeira_input  = newmsg2[3..5]
            
       
            //Verifico cambios en los RELAYS/OUTPUTS = Vinieron pelo APP;
               if (outputs_changed.contains("1")) {
                   relaychanged = outputs_changed.indexOf('1'); 
                   log.debug ("Yes - change in relay (only) ")
                   log.debug "outputs_changed relay # = " + relaychanged  
                                  
                 z = relaychanged +1 
                 log.debug "z = " + z
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                 //log.debug "fue menor que 10 = " + numerorelay
                 }    
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 //log.debug "fue mayor que 10 = " + numerorelay    
                 }  
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                    
                  //buscar y cambiar el status a ON del switch

                  statusrelay = novaprimeira_output.getAt(relaychanged)
                  log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                   
               } else {                  
                       log.info "Placa 2Ch - com feedback de status cada 30 segs - IP " + state.ipaddress
                       log.info ("No changes reported")
              }           
        }
                 
}  //END PARSE  PLACA 2CH 

def parse4CH(String newmsg2) {
    // Handle 4-channel relay parsing
        if ((newmsg2.length() > 43 )) {
             outputs_changed_1 = newmsg2[12..16]  //changes in relays reported in 1st line of return. Sometimes it returns in first line. 
             outputs_changed_2 = newmsg2[34..37]  //changes in relays reported in 2nd line of return
             outputs_status = newmsg2[22..25]     //status of relays reported in 2nd line of return 

                   if ((outputs_changed_2.contains("1")) || (outputs_changed_1.contains("1"))) {
                       if (outputs_changed_2.contains("1")) {
                                relaychanged = outputs_changed_2.indexOf('1'); 
                           
                       } else
                           {
                                relaychanged = outputs_changed_1.indexOf('1');
                           }
                       
                   log.debug ("Yes - change in Relay (with input)")
                   log.debug "outputs_changed_2 relay # = " + relaychanged    
                        
                 z = relaychanged +1 
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                     
                 }    
                
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 } 
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                 statusrelay = outputs_status.getAt(relaychanged)
                       //log.debug "relaychanged  = " +  relaychanged
                       //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                 }            
        } //LENGTH = 44

        if ((newmsg2.length() == 22 )) {    
            
            outputs_changed = newmsg2[12..16]       
            inputs_changed = newmsg2[17..20]
            novaprimeira_output = newmsg2[0..3]
            novaprimeira_input  = newmsg2[5..8]
            
       
            //Verifico cambios en los RELAYS/OUTPUTS = Vinieron pelo APP;
               if (outputs_changed.contains("1")) {
                   relaychanged = outputs_changed.indexOf('1'); 
                   log.debug ("Yes - change in relay (only) ")
                   log.debug "outputs_changed relay # = " + relaychanged  
                                  
                 z = relaychanged +1 
                 log.debug "z = " + z
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                 //log.debug "fue menor que 10 = " + numerorelay
                 }    
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 //log.debug "fue mayor que 10 = " + numerorelay    
                 }  
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                    
                  //buscar y cambiar el status a ON del switch

                  statusrelay = novaprimeira_output.getAt(relaychanged)
                  log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                   
               } else {                  
                       log.info "Placa 4Ch - com feedback de status cada 30 segs - IP " + state.ipaddress
                       log.info ("No changes reported")
              }   
            
        }
     
     }  //END PLACA 4CH     


def parse8CH(String newmsg2) {
    // Handle 8-channel relay parsing
       if ((newmsg2.length() > 75 )) {
             //log.info "Entrou no > 70.."
             
             outputs_changed_1 = newmsg2[20..27]    //changes in relays reported in 1st line of return. Sometimes it returns in first line. 
             //log.debug " outputs_changed = " +  outputs_changed_1  + " qtde chars = " + outputs_changed_1.length()
             
             outputs_changed_2 = newmsg2[58..65]  //changes in relays reported in 2nd line of return  
             //log.debug " outputs_changed_2 = " +  outputs_changed_2 + " qtde chars = " + outputs_changed_2.length()
             
             outputs_status = newmsg2[38..45]     //status of relays reported in 2nd line of return 
             //log.debug " outputs_status = " +  outputs_status + " qtde chars = " + outputs_status.length()

                   if ((outputs_changed_2.contains("1")) || (outputs_changed_1.contains("1"))) {
                       if (outputs_changed_2.contains("1")) {
                                relaychanged = outputs_changed_2.indexOf('1'); 
                           
                       } else
                           {
                                relaychanged = outputs_changed_1.indexOf('1');
                           }
                       
                   log.debug ("Yes - change in Relay (with input)")
                   log.debug "outputs_changed_2 relay # = " + relaychanged    
                        
                 z = relaychanged +1 
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                     
                 }    
                
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 } 
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                 statusrelay = outputs_status.getAt(relaychanged)
                       //log.debug "relaychanged  = " +  relaychanged
                       //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                 }            
        } //LENGTH = 76
        
                
           if ((newmsg2.length() == 38 )) {    
            
            outputs_changed = newmsg2[20..27]       
            inputs_changed = newmsg2[29..37]
            novaprimeira_output = newmsg2[0..7]
            novaprimeira_input  = newmsg2[10..17]
            
       
            //Verifico cambios en los RELAYS/OUTPUTS = Vinieron pelo APP;
               if (outputs_changed.contains("1")) {
                   relaychanged = outputs_changed.indexOf('1'); 
                   log.debug ("Yes - change in relay (only) ")
                   log.debug "outputs_changed relay # = " + relaychanged  
                                  
                 z = relaychanged +1 
                 //log.debug "z = " + z
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                 //log.debug "fue menor que 10 = " + numerorelay
                 }    
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 //log.debug "fue mayor que 10 = " + numerorelay    
                 }  
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                    
                  //buscar y cambiar el status a ON del switch

                  statusrelay = novaprimeira_output.getAt(relaychanged)
                  //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                   
               } else {                  
                       log.info "Placa 8Ch - com feedback de status cada 30 segs - IP " + state.ipaddress
                       log.info ("No changes reported")
              }   
            
        }
     

}  //END PLACA 8CH  


def parse16CH(String newmsg2) {
    // Handle 16-channel relay parsing
      if ((newmsg2.length() > 140 )) {
             //log.info "Entrou no > 140.."
             
             outputs_changed_1 = newmsg2[37..52]    //changes in relays reported in 1st line of return. Sometimes it returns in first line. 
             //log.debug " outputs_changed = " +  outputs_changed_1  + " qtde chars = " + outputs_changed_1.length()
             
             outputs_changed_2 = newmsg2[108..123]  //changes in relays reported in 2nd line of return  
             //log.debug " outputs_changed_2 = " +  outputs_changed_2 + " qtde chars = " + outputs_changed_2.length()
             
             outputs_status = newmsg2[71..86]     //status of relays reported in 2nd line of return 
             //log.debug " outputs_status = " +  outputs_status + " qtde chars = " + outputs_status.length()

                   if ((outputs_changed_2.contains("1")) || (outputs_changed_1.contains("1"))) {
                       if (outputs_changed_2.contains("1")) {
                                relaychanged = outputs_changed_2.indexOf('1'); 
                           
                       } else
                           {
                                relaychanged = outputs_changed_1.indexOf('1');
                           }
                       
                   log.debug ("Yes - change in Relay (with input)")
                   log.debug "outputs_changed_2 relay # = " + relaychanged    
                        
                 z = relaychanged +1 
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                     
                 }    
                
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 } 
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                 statusrelay = outputs_status.getAt(relaychanged)
                       //log.debug "relaychanged  = " +  relaychanged
                       //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                 }            
        } //LENGTH = 150
        
                  
     if ((newmsg2.length() == 71 )) {    
            
            outputs_changed = newmsg2[37..52]
            //log.info "outputs_changed = " + outputs_changed
            inputs_changed = newmsg2[55..70] 
            //log.info "inputs_changed = " + inputs_changed
            novaprimeira_output = newmsg2[0..15]
            //log.info "novaprimeira_output = " + novaprimeira_output
            novaprimeira_input  = newmsg2[17..32]
            //log.info "novaprimeira_input = " + novaprimeira_input
       
       
            //Verifico cambios en los RELAYS/OUTPUTS = Vinieron pelo APP;
               if (outputs_changed.contains("1")) {
                   relaychanged = outputs_changed.indexOf('1'); 
                   log.debug ("Yes - change in relay (only) ")
                   log.debug "outputs_changed relay # = " + relaychanged  
                                  
                 z = relaychanged +1 
                 //log.debug "z = " + z
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                 //log.debug "fue menor que 10 = " + numerorelay
                 }    
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 //log.debug "fue mayor que 10 = " + numerorelay    
                 }  
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                    
                  //buscar y cambiar el status a ON del switch

                  statusrelay = novaprimeira_output.getAt(relaychanged)
                  //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                   
               } else {                  
                       log.info "Placa 16Ch - com feedback de status cada 30 segs - IP " + state.ipaddress
                       log.info ("No changes reported")
              }   
            
        }

} //END PLACA 16CH  

def parse32CH(String newmsg2) {
    // Handle 32-channel relay parsing

if ((newmsg2.length() > 140 )) {
             //log.info "Entrou no > 140.."
            
             outputs_changed_1 = newmsg2[69..100]    //changes in relays reported in 1st line of return. Sometimes it returns in first line. 
             //log.debug " outputs_changed = " +  outputs_changed_1  + " qtde chars = " + outputs_changed_1.length()
             
             outputs_changed_2 = newmsg2[204..235]  //changes in relays reported in 2nd line of return  
             //log.debug " outputs_changed_2 = " +  outputs_changed_2 + " qtde chars = " + outputs_changed_2.length()
             
             outputs_status = newmsg2[135..166]     //status of relays reported in 2nd line of return 
             //log.debug " outputs_status = " +  outputs_status + " qtde chars = " + outputs_status.length()

                   if ((outputs_changed_2.contains("1")) || (outputs_changed_1.contains("1"))) {
                       if (outputs_changed_2.contains("1")) {
                                relaychanged = outputs_changed_2.indexOf('1'); 
                           
                       } else
                           {
                                relaychanged = outputs_changed_1.indexOf('1');
                           }
                       
                   log.debug ("Yes - change in Relay (with input)")
                   log.debug "outputs_changed_2 relay # = " + relaychanged    
                        
                 z = relaychanged +1 
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                     
                 }    
                
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 } 
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                 statusrelay = outputs_status.getAt(relaychanged)
                       //log.debug "relaychanged  = " +  relaychanged
                       //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                 }            
        } //LENGTH = 150
        
                  
     if ((newmsg2.length() == 135 )) {    
            
            outputs_changed = newmsg2[69..100]       
            inputs_changed = newmsg2[102..134]
            novaprimeira_output = newmsg2[0..31]
            novaprimeira_input  = newmsg2[33..65]
            
            //Verifico cambios en los RELAYS/OUTPUTS = Vinieron pelo APP;
               if (outputs_changed.contains("1")) {
                   relaychanged = outputs_changed.indexOf('1'); 
                   log.debug ("Yes - change in relay (only) ")
                   log.debug "outputs_changed relay # = " + relaychanged  
                                  
                 z = relaychanged +1 
                 //log.debug "z = " + z
                 if (z < 10) {     //Verify to add double digits to switch name. 
                 numerorelay = "0" + Integer.toString(relaychanged+1)
                 //log.debug "fue menor que 10 = " + numerorelay
                 }    
                 else {
                 numerorelay = Integer.toString(relaychanged+1)
                 //log.debug "fue mayor que 10 = " + numerorelay    
                 }  
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 
                    
                  //buscar y cambiar el status a ON del switch

                  statusrelay = novaprimeira_output.getAt(relaychanged)
                  //log.debug "statusrelay = " +  statusrelay
                       switch(statusrelay) { 
                       case "0": 
                       log.info "OFF"
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])                               
                       break      
                           
                       case "1": 
                       log.info "ON" 
                       getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                       break
                           
                       default:
                       log.info "NADA" 
                       break
                           
                       }
                   
               } else {                  
                       log.info "Placa 32Ch - com feedback de status cada 30 segs - IP " + state.ipaddress
                       log.info ("No changes reported")
              }   
            
        }
     
     }  //END PLACA 32CH





def parse(msg) {
    
    String thisId = device.id
    //def cd = getChildDevice("${thisId}-Switch")
    state.netids = "${thisId}-Switch-"
	
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
        parse8CH(newmsg2)
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



////////////////
////Commands 
////////////////

def on()
{
    logDebug("Master Power ON()")
    masteron()

}

def off()
{
    logDebug("Master Power OFF()")
    masteroff()

}



def masteron() {
    logInfo("MasterON() Executed (new)")
    for (int i = 1; i <= state.inputcount; i++) {
        def relayNumber = i < 10 ? "0${i}" : "${i}"
        def chdid = "${state.netids}${relayNumber}"
        def cd = getChildDevice(chdid)
        if (cd) {
            cd.parse([[name: "switch", value: "on", descriptionText: "${cd.displayName} was turned on"]])
            logInfo("Switch ${cd.displayName} turned ON")
            on(cd)
            pauseExecution(200) // Reduced delay
        }
    }
}



def masteroff() {
    logInfo("MasterOFF() Executed (new)")
    for (int i = 1; i <= state.inputcount; i++) {
        def relayNumber = i < 10 ? "0${i}" : "${i}"
        def chdid = "${state.netids}${relayNumber}"
        def cd = getChildDevice(chdid)
        if (cd) {
            cd.parse([[name: "switch", value: "off", descriptionText: "${cd.displayName} was turned off"]])
            logInfo("Switch ${cd.displayName} turned OFF")
            off(cd)
            pauseExecution(200) // Reduced delay
        }
    }
}


private sendCommand(s) {
    logDebug("sendCommand ${s}")
    interfaces.rawSocket.sendMessage(s)    
}


////////////////////////////
//// Connections Checks ////
////////////////////////////



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

        	boardstatus = status
            state.boardstatus = status
            if (status == "online" || status == "offline") {
            sendEvent(name: "boardstatus", value: status, isStateChange: true)
            
        }
        
        // Check if a notification has already been sent for this status
        if (state.lastNotificationStatus != status) {
            //log.info "Status Antigo = " + state.lastNotificationStatus + "Novo : " + status
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



/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Component Child Actions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

void componentRefresh(cd){
	if (logEnable) log.info "received refresh request from ${cd.displayName}"
	refresh()
        
}

def componentOn(cd){
	if (logEnable) log.info "received on request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])  
    //log.debug "cd on = " + cd 
    on(cd)  
    pauseExecution(250)
    
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])   
    //log.debug "cd off = " + cd 
	off(cd)
    pauseExecution(250)

}

//////////////////////////////
////// Driver Commands /////////
//////////////////////////////


//SEND ON COMMAND IN CHILD BUTTON
void on(cd) {
sendEvent(name: "switch", value: "on", isStateChange: true)
//cd.updateDataValue("Power","On")    

ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0
/// Inicio verificación del length    
//    def substr1 = (cd.deviceNetworkId.indexOf("-",5))
      def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1);

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
     //sendEvent(name: "power", value: "on")
     log.info "Send command ON = " + comando
     //state.update = 1  //variable to control update with board on parse
    
}


//SEND OFF COMMAND IN CHILD BUTTON 
void off(cd) {
sendEvent(name: "switch", value: "off", isStateChange: true)
//cd.updateDataValue("Power","Off")
    
ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0
/// Inicio verificación del length    
//    def substr1 = (cd.deviceNetworkId.indexOf("-",5))
      def substr1 = cd.deviceNetworkId.indexOf("-", cd.deviceNetworkId.indexOf("-") + 1);
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
     log.info "Send command OFF = " + comando
    
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
    device.updateSetting('logEnable', [value:'false', type:'bool'])
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
