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
command "ManualKeepAlive"
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
        input 'logWarn', 'bool', title: 'Show Warning Logs?', required: false, defaultValue: false
        input 'logDebug', 'bool', title: 'Show Debug Logs?', description: 'Only leave on when required', required: false, defaultValue: false
        input 'logTrace', 'bool', title: 'Show Detailed Logs?', description: 'Only leave on when required', required: false, defaultValue: false
    
    //help guide
    input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver") 

    attribute "powerstatus", "string"
    attribute "boardstatus", "string"
    //attribute "boardlaststatus", "string"  
           
  }   


@Field static String partialMessage = ''
@Field static Integer checkInterval = 150

def installed() {
    logTrace('installed()')
    state.childscreated = 0
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
    interfaces.rawSocket.close();
    unschedule()
    //state.clear()
    
    //Llama la busca de count de inputs+outputs
    buscainputcount()
    
    try {
        logTrace("ManualKeepAlive: Tentando conexão com o device no ${device_IP_address}...na porta ${device_port}");
        interfaces.rawSocket.connect(device_IP_address, (int) device_port);
        state.lastMessageReceivedAt = now();        
        runIn(checkInterval, "connectionCheck");
        if (boardstatus != "online") { 
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)    
            boardstatus = "online"
        }
        refresh();  // se estava offline, preciso fazer um refresh
        
    }
    catch (e) {
        logError( "ManualKeepAlive: ${device_IP_address}  error: ${e.message}" )
        if (boardstatus != "offline") { 
            boardstatus = "offline"
            sendEvent(name: "boardstatus", value: "offline", isStateChange: true)
        }
        runIn(60, "initialize");
    }    
}


def initialize() {
    unschedule()
    logTrace('Run Initialize()')
    interfaces.rawSocket.close();
    interfaces.rawSocket.close();
    if (!device_IP_address) {
        logError 'IP do Device not configured'
        return
    }

    if (!device_port) {
        logError 'Porta do Device não configurada.'
        return
    }
    
    //Llama la busca de count de inputs+outputs via HTTP
    buscainputcount()
    
    try {
        logTrace("Initialize: Tentando conexão com o device no ${device_IP_address}...na porta configurada: ${device_port}");
        interfaces.rawSocket.connect(device_IP_address, (int) device_port);
        state.lastMessageReceivedAt = now();        
        if (boardstatus != "online") { 
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)    
            boardstatus = "online"
        }
        boardstatus = "online"
        runIn(checkInterval, "connectionCheck");
        
    }
    catch (e) {
        logError( "Initialize: com ${device_IP_address} com um error: ${e.message}" )
        boardstatus = "offline"
        runIn(60, "initialize");
    }
    
    try{
         
          logTrace("Criando childs")
          createchilds()       
        
    }
    catch (e) {
        logError( "Error de Initialize: ${e.message}" )
    }
    runIn(10, "refresh");
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
        log.info "Childs já foram criados"
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
            }
  
        }
    } catch (Exception e) {
        log.warn "BuscaInputs-Initialize(): Erro no BuscaInputs: ${e.message}"
       
    }
         def ipmolsmart = settings.device_IP_address
         state.ipaddress = settings.device_IP_address
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


def parse(msg) {
   
    state.lastMessageReceived = new Date(now()).toString();
    state.lastMessageReceivedAt = now();
    
    def newmsg = hubitat.helper.HexUtils.hexStringToByteArray(msg) //na Mol, o resultado vem em HEX, então preciso converter para Array
    def newmsg2 = new String(newmsg) // Array para String    
    state.lastmessage = newmsg2
    
    log.info "****** New Block LOG Parse ********"
    log.info "Last Msg: " + newmsg2
    log.debug "Qde chars = " + newmsg2.length()   
   
//START PLACA 4CH 
    if (state.inputcount == 4) {
        state.channels = 4

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
            
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)        
            log.debug "Placa MolSmart Online"
       
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
    
    
    ///  INPUTS //////
      /*     //codigo para verificar alteração no input
             if (inputs_changed.contains("1")) {
                   log.info ("Yes - change in Input")
                   inputchanged = inputs_changed.indexOf('1'); 
                   log.info "inputs_changed input # = " + inputchanged
             }
       */   
    

 //START PLACA 8CH 
    if (state.inputcount == 8) {
        state.channels = 8    
    
            
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
            
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)        
            log.debug "Placa MolSmart Online"
       
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
     
     } //END PLACA 8CH

    
    
    
 //START PLACA 16CH 
    if (state.inputcount == 16) {
        state.channels = 16    
    
            
        if ((newmsg2.length() > 140 )) {
             //log.info "Entrou no > 140.."
             
             outputs_changed_1 = newmsg2[39..54]    //changes in relays reported in 1st line of return. Sometimes it returns in first line. 
             //log.debug " outputs_changed = " +  outputs_changed_1  + " qtde chars = " + outputs_changed_1.length()
             
             outputs_changed_2 = newmsg2[114..129]  //changes in relays reported in 2nd line of return  
             //log.debug " outputs_changed_2 = " +  outputs_changed_2 + " qtde chars = " + outputs_changed_2.length()
             
             outputs_status = newmsg2[75..90]     //status of relays reported in 2nd line of return 
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
        
                  
     if ((newmsg2.length() == 75 )) {    
            
            outputs_changed = newmsg2[39..54]       
            inputs_changed = newmsg2[56..74]
            novaprimeira_output = newmsg2[0..15]
            novaprimeira_input  = newmsg2[17..34]
            
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)        
            log.debug "Placa MolSmart Online"
       
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
     
     } //PLACA 16CH
    

 
 //START PLACA 32CH 
    if (state.inputcount == 32) {
        state.channels = 32   
    
            
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
            
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)        
            log.debug "Placa MolSmart Online"
       
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
     
     } //PLACA 32CH
    

        
    
}


////////////////
////Commands 
////////////////

def on()
{
    logDebug("Master Power ON()")
    masteron()
    //def msg = "1X"
    //sendCommand(msg)
}

def off()
{
    logDebug("Master Power OFF()")
    masteroff()
    //def msg = "2X"
    //sendCommand(msg)
}


def masteron()
{
        log.info "MasterON() Executed"  
        for(int i = 1; i<=state.inputcount; i++) {        
                if (i < 10) {     //Verify to add double digits to switch name. 
                numerorelay = "0" + Integer.toString(i)
                }    
                else {
                numerorelay = Integer.toString(i)
                }     
    
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])    
                 log.info "Switch " + cd + " turned ON"
                 on(cd)  
                 pauseExecution(250)     
       
        }
}

def masteroff()
{
        log.info "MasterOFF() Executed"
        for(int i = 1; i<=state.inputcount; i++) {        
                if (i < 10) {     //Verify to add double digits to switch name. 
                numerorelay = "0" + Integer.toString(i)
                }    
                else {
                numerorelay = Integer.toString(i)
                }     
    
                 chdid = state.netids + numerorelay               
                 def cd = getChildDevice(chdid)
                 getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])    
                 log.info "Switch " + cd + " turned OFF"
                 off(cd)  
                 pauseExecution(250)     
       
        }
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
        logError("ConnectionCheck:Sem mensagens desde ${(now - state.lastMessageReceivedAt)/60000} minutos, vamos tentar reconectar ...");
        if (boardstatus != "offline") { 
            sendEvent(name: "boardstatus", value: "offline", isStateChange: true)    
            boardstatus = "offline"
        }
        runIn(30, "connectionCheck");
        initialize();
    }
    else {
        logInfo("Connection Check: Status OK - Board Online");
        if (boardstatus != "online") { 
            sendEvent(name: "boardstatus", value: "online", isStateChange: true)    
            boardstatus = "online"
        }
        runIn(checkInterval, "connectionCheck");
    }
}



//Socket Status - NOT USED. 

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
