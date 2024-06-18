/**
 *  Hubitat - TCP MolSmart Dimmer Drivers by TRATO - BETA OK
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
 *        Versão 1.0 25/4/2024  - V.BETA 1
 *       Versão 1.1 13/6/2024  - Added User Guide
 *       Versão 1.2 18/6/2024 - Fixed index and length calc of network id. 
 */
metadata {
  definition (name: "MolSmart DIMMER Driver TCP v3 - by TRATO", namespace: "TRATO", author: "TRATO", vid: "generic-contact") {
        capability "Switch"  
        capability "Configuration"
        capability "Initialize"
        capability "Refresh"
		capability "SwitchLevel"
		capability "ChangeLevel"      
     
      
  }
      
  }

import groovy.json.JsonSlurper
import groovy.transform.Field
command "buscainputcount"
command "createchilds"

    import groovy.transform.Field
    @Field static final String DRIVER = "by TRATO"
    @Field static final String USER_GUIDE = "https://github.com/hhorigian/hubitat_MolSmart_Dimmer"


    String fmtHelpInfo(String str) {
    String prefLink = "<a href='${USER_GUIDE}' target='_blank'>${str}<br><div style='font-size: 70%;'>${DRIVER}</div></a>"
    return "<div style='font-size: 160%; font-style: bold; padding: 2px 0px; text-align: center;'>${prefLink}</div>"
    }


  preferences {
        input "device_IP_address", "text", title: "IP Address of MolSmart Dimmer"
        input "device_port", "number", title: "IP Port of Device", required: true, defaultValue: 502
        input name: "outputs", type: "string", title: "How many Relays " , defaultValue: 6      
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "powerstatus", type: "string", title: "Power Status" 

    input 'logInfo', 'bool', title: 'Show Info Logs?',  required: false, defaultValue: true
    input 'logWarn', 'bool', title: 'Show Warning Logs?', required: false, defaultValue: true
    input 'logDebug', 'bool', title: 'Show Debug Logs?', description: 'Only leave on when required', required: false, defaultValue: true
    input 'logTrace', 'bool', title: 'Show Detailed Logs?', description: 'Only leave on when required', required: false, defaultValue: true

        //help guide
        input name: "UserGuide", type: "hidden", title: fmtHelpInfo("Manual do Driver") 	  

    attribute "powerstatus", "string"
    
      
  }   


@Field static String partialMessage = ''
@Field static Integer checkInterval = 600


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
    createchilds()
       
}


def createchilds() {

    String thisId = device.id
	log.info "info thisid " + thisId
	def cd = getChildDevice("${thisId}-Switch")
    state.netids = "${thisId}-Switch-"
	if (!cd) {
        log.info "inputcount = " + state.inputcount 
        for(int i = 1; i<=state.inputcount; i++) {        
        cd = addChildDevice("hubitat", "Generic Component Dimmer", "${thisId}-Switch-" + Integer.toString(i), [name: "${device.displayName} Switch-" + Integer.toString(i) , isComponent: true])
        log.info "added switch # " + i + " from " + state.inputcount            
        
    }
    }         
}

def buscainputcount(){

                               
            state.inputcount = 6  // deixar o numero de relays na memoria
            def ipmolsmart = settings.device_IP_address
           state.ipaddress = settings.device_IP_address
    
}



def refresh() {
    logTrace('refresh()')
    def msg = "test"    
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
       
    if (newmsg2.contains("6")) {
        state.primeira = newmsg2[0..17]
        state.channels = 6
        novaprimeira = newmsg2[0..17]
        log.debug "Placa Dimmer de 6"
    }      

     primeira =  newmsg2[0..17] //el valor de los primeros 17 
     dim1 = newmsg2[0..2]
     dim2 = newmsg2[3..5]
     dim3 = newmsg2[6..8]
     dim4 = newmsg2[9..11]
     dim5 = newmsg2[12..14]
     dim6 = newmsg2[15..17]    
    
    //compare last parse result with current, and if different, compare changes. 
    if ((novaprimeira)&&(oldprimeira)) {  //if not empty in first run

    if (novaprimeira.compareToIgnoreCase(oldprimeira) == 0){
        log.info "no changes from previous status"
    }
    else{
        
        if ( state.update == 0) {

            //compara dimmer1 
            def valold_dim01 = oldprimeira[0..2]
            def dif1 = valold_dim01.compareToIgnoreCase(dim1)  
                switch(difdim1) { 
                case 0:
                    log.info "no changes in Dimmer#" + (f+1) ;
                    break                     
                default:
                        def difprimerdigito = valold_dim01[0].compareToIgnoreCase(dim1[0])
                        if (difprimerdigito == -1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  0) {
                            log.info "primer dig 1CH = 0"
                            def difoutros = valold_dim01[1..2].compareToIgnoreCase(dim1[1..2])
                                if (difoutros !=  0) {
                                    //log.info "2 y 3 digitos changed too"
                                    log.info "new setvalue 1CH = " + dim1 + " --- antigo era " + valold_dim01                                    
                                    chdid = state.netids + "1"                                     
                                    def cd = getChildDevice(chdid)
                                    valdim = dim1[1..2]           
                                    valordim = valdim as Integer  
                                    
                                    if (valordim > 0) {
		                            getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ParseSetLevel > 0", isStateChange: true]])    
	                                } else {
	                            	getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ParseSetLevel < 0", isStateChange: true]])    
	                                } 
                                    getChildDevice(cd.deviceNetworkId).parse([[name:"level", value: valdim, descriptionText:"${cd.displayName} was dimmered via Parse"]])    
                                    //log.info "chdid = " + chdid
                                    //log.info "cd = " + cd
                                    //log.info "valdim = " + valdim
                                } 
                        }                    
                break
                }
        
        //compara dimmer2
            def valold_dim02 = oldprimeira[3..5]
            def dif2 = valold_dim02.compareToIgnoreCase(dim2)  
                switch(difdim2) { 
                case 0:
                    log.info "no changes in Dimmer#" + 2 ;
                    break                     
                default:
                        def difprimerdigito = valold_dim02[0].compareToIgnoreCase(dim2[0])
                        if (difprimerdigito == -1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  0) {
                            log.info "primer dig 2CH = 0"
                            def difoutros = valold_dim02[1..2].compareToIgnoreCase(dim2[1..2])
                                if (difoutros !=  0) {
                                    //log.info "2 y 3 digitos changed too"
                                    log.info "new setvalue 2CH = " + dim2 + " --- antigo era " + valold_dim02                                    
                                    chdid = state.netids + "2"                                     
                                    def cd = getChildDevice(chdid)
                                    valdim = dim2[1..2]  
                                    
                                    if (!valdim?.trim()) {
                                            logger.lifecycle("the string is null or empty.")
                                    }
                                    
                                    valordim = valdim as Integer  
                                    
                                    if (valordim > 0) {
		                            getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ParseSetLevel > 0", isStateChange: true]])    
	                                } else {
	                            	getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ParseSetLevel < 0", isStateChange: true]])    
	                                } 
                                    getChildDevice(cd.deviceNetworkId).parse([[name:"level", value: valdim, descriptionText:"${cd.displayName} was dimmered via Parse"]])    

                                } 
                        }                    
              break
              }
            
            
        //compara dimmer3
            def valold_dim03 = oldprimeira[6..8]
            def dif3 = valold_dim03.compareToIgnoreCase(dim3)  
                switch(difdim3) { 
                case 0:
                    log.info "no changes in Dimmer#" + 3 ;
                    break                     
                default:
                        def difprimerdigito = valold_dim03[0].compareToIgnoreCase(dim3[0])
                        if (difprimerdigito == -1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  0) {
                            log.info "primer dig 3CH = 0"
                            def difoutros = valold_dim03[1..2].compareToIgnoreCase(dim3[1..2])
                                if (difoutros !=  0) {
                                    log.info "new setvalue 3CH = " + dim3 + " --- antigo era " + valold_dim03                                    
                                    chdid = state.netids + "3"                                     
                                    def cd = getChildDevice(chdid)
                                    valdim = dim3[1..2]  

                                    valordim = valdim as Integer  
                                    
                                    if (valordim > 0) {
		                            getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ParseSetLevel > 0", isStateChange: true]])    
	                                } else {
	                            	getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ParseSetLevel < 0", isStateChange: true]])    
	                                } 
                                    getChildDevice(cd.deviceNetworkId).parse([[name:"level", value: valdim, descriptionText:"${cd.displayName} was dimmered via Parse"]])    

                                } 
                        }                    
              break
              }
                    
        //compara dimmer4
            def valold_dim04 = oldprimeira[9..11]
            def dif4 = valold_dim04.compareToIgnoreCase(dim4)  
                switch(difdim4) { 
                case 0:
                    log.info "no changes in Dimmer#" + 4 ;
                    break                     
                default:
                        def difprimerdigito = valold_dim04[0].compareToIgnoreCase(dim4[0])
                        if (difprimerdigito == -1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  0) {
                            log.info "primer dig 4CH = 0"
                            def difoutros = valold_dim03[1..2].compareToIgnoreCase(dim4[1..2])
                                if (difoutros !=  0) {
                                    log.info "new setvalue 4CH = " + dim4 + " --- antigo era " + valold_dim04                                    
                                    chdid = state.netids + "4"                                     
                                    def cd = getChildDevice(chdid)
                                    valdim = dim4[1..2]  

                                    valordim = valdim as Integer  
                                    
                                    if (valordim > 0) {
		                            getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ParseSetLevel > 0", isStateChange: true]])    
	                                } else {
	                            	getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ParseSetLevel < 0", isStateChange: true]])    
	                                } 
                                    getChildDevice(cd.deviceNetworkId).parse([[name:"level", value: valdim, descriptionText:"${cd.displayName} was dimmered via Parse"]])    

                                } 
                        }                    
              break
              }
                    

        //compara dimmer5
            def valold_dim05 = oldprimeira[12..14]
            //var.log "oldprimeira 13-15 = " + oldprimeira[13..15]
            def dif5 = valold_dim05.compareToIgnoreCase(dim5)  
                switch(difdim5) { 
                case 0:
                    log.info "no changes in Dimmer#" + 5 ;
                    break                     
                default:
                        def difprimerdigito = valold_dim05[0].compareToIgnoreCase(dim5[0])
                        if (difprimerdigito == -1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  0) {
                            log.info "primer dig 5CH = 0"
                            def difoutros = valold_dim05[1..2].compareToIgnoreCase(dim5[1..2])
                                if (difoutros !=  0) {
                                    log.info "new setvalue 5CH = " + dim5 + " --- antigo era " + valold_dim05                                    
                                    chdid = state.netids + "5"                                     
                                    def cd = getChildDevice(chdid)
                                    valdim = dim5[1..2]  

                                    valordim = valdim as Integer  
                                    
                                    if (valordim > 0) {
		                            getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ParseSetLevel > 0", isStateChange: true]])    
	                                } else {
	                            	getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ParseSetLevel < 0", isStateChange: true]])    
	                                } 
                                    getChildDevice(cd.deviceNetworkId).parse([[name:"level", value: valdim, descriptionText:"${cd.displayName} was dimmered via Parse"]])    

                                } 
                        }                    
              break
              }
                    
            
        //compara dimmer6
            def valold_dim06 = oldprimeira[15..17]
            def dif6 = valold_dim06.compareToIgnoreCase(dim6)  
                switch(difdim6) { 
                case 0:
                    log.info "no changes in Dimmer#" + 6 ;
                    break                     
                default:
                        def difprimerdigito = valold_dim06[0].compareToIgnoreCase(dim6[0])
                        if (difprimerdigito == -1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  1) {
                            log.info "primer dig dif -1"
                        }
                        if (difprimerdigito ==  0) {
                            log.info "primer dig 6CH = 0"
                            def difoutros = valold_dim06[1..2].compareToIgnoreCase(dim6[1..2])
                                if (difoutros !=  0) {
                                    log.info "new setvalue 6CH = " + dim6 + " --- antigo era " + valold_dim06                                    
                                    chdid = state.netids + "6"                                     
                                    def cd = getChildDevice(chdid)
                                    valdim = dim6[1..2]  

                                    valordim = valdim as Integer  
                                    
                                    if (valordim > 0) {
		                            getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ParseSetLevel > 0", isStateChange: true]])    
	                                } else {
	                            	getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ParseSetLevel < 0", isStateChange: true]])    
	                                } 
                                    getChildDevice(cd.deviceNetworkId).parse([[name:"level", value: valdim, descriptionText:"${cd.displayName} was dimmered via Parse"]])    

                                } 
                        }                    
              break
              }
                    
            
            
                    
                    
            
            
        
            
            
            
            
            
        }          
                    
                
      
        state.update = 0  
    }}

        for(int f = 0; f <state.inputcount; f++) {  
        val = state.primeira[f]
        //log.info "posição f= " + f + " valor state = " + val
        }
        log.info "newmsg2  = " + newmsg2
        log.info "primeira  = " + primeira
        
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
        initialize();
    }
    else {
        logDebug("connectionCheck ok");
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
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ComponentOn "]])       
    on(cd)  

    
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
    getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ComponentOFF "]])    
	off(cd)


}

void componentSetLevel(cd,level) {
    if (logEnable) log.info "received set level dimmer from ${cd.displayName}"
    def valueaux = level as Integer
	def level2 = Math.max(Math.min(valueaux, 99), 0)
	if (level2 > 0) {
		getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on via ComponentSetLevel > 0", isStateChange: true]])    
	} else {
		getChildDevice(cd.deviceNetworkId).parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off via ComponentSetLevel < 0", isStateChange: true]])    
	}
    SetLevel(cd,level)   
}




////// Driver Commands /////////

void SetLevel(cd,level) {

    def valueaux = level as Integer
	def level2 = Math.max(Math.min(valueaux, 99), 0)	
    
	ipdomodulo  = state.ipaddress
    lengthvar =  (cd.deviceNetworkId.length())
    int relay = 0
    
// Start verify of length 
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
     def comando = "1" + stringrelay + "%" + level2
     interfaces.rawSocket.sendMessage(comando)
     log.info "Foi Alterado o Dimmer " + relay + " via TCP " + comando 
     state.update = 1  //variable to control update with board on parse  
    


}//of function



//SEND ON COMMAND IN CHILD BUTTON
void on(cd) {
if (logEnable) log.debug "Turn device ON "	
sendEvent(name: "switch", value: "on", isStateChange: true)
//cd.updateDataValue("Power","On")    
//cd.parse([[name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on"]])

ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0

// Start verify of length     
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

///
     def stringrelay = relay
     def comando = "1" + stringrelay
     interfaces.rawSocket.sendMessage(comando)
     log.info "Foi Ligado o Relay " + relay + " via TCP " + comando 
     sendEvent(name: "power", value: "on")
     state.update = 1  //variable to control update with board on parse
    
}


//SEND OFF COMMAND IN CHILD BUTTON 
void off(cd) {
if (logEnable) log.debug "Turn device OFF"	
sendEvent(name: "switch", value: "off", isStateChange: true)
cd.updateDataValue("Power","Off")
//cd.parse([[name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off"]])
    
ipdomodulo  = state.ipaddress
lengthvar =  (cd.deviceNetworkId.length())
int relay = 0

//Start verify length
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

///
     def stringrelay = relay
     def comando = "2" + stringrelay
     interfaces.rawSocket.sendMessage(comando)
     log.info "Foi Desligado o Relay " + relay + " via TCP " + comando 
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

