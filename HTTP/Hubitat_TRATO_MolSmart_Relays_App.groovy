/**
 *  MolSmart Relays App for Hubitat
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
 */

definition(
    name: "MolSmart Relays App",
    namespace: "TRATO",
    author: "VH",
    description: "MolSmart Relay Board para Hubitat ",
    category: "Lights",
    iconUrl: "",
    iconX2Url: "")

preferences {
    page name: "mainPage", title: "", install: true, uninstall: true
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    section("MolSmart Setup Configuration") {
        input "thisName", "text", title: "Nome personalizado para o Módulo", submitOnChange: true
		if(thisName) app.updateLabel("$thisName")
        input name: "molIPAddress", type: "text", title: "MolSmart IP Address", submitOnChange: true, required: true, defaultValue: "192.168.1.100" 
        input name: "pollFrequency", type: "number", title: "Frequência para obter o feedback do status (em segundos)", defaultValue: 5
        input name: "relaycount", type: "text", title: "MolSmart Relay Count", required: false, defaultValue: "0"         
        input name: "debugOutput", type: "bool", title: "Habilitar Log", defaultValue: false
    }
    }
}

def installed() {
    log.debug "installed(): Installing MolSmart App Parent App"
    
    initialize()
    
    //updated()
}

def updated() {
    log.debug "updated(): Updating MolSmart App"
    //unschedule(pollStatus)
    initialize()

}

def uninstalled() {
    unschedule()
    state.remove('working')
    log.debug "uninstalled(): Uninstalling MolSmart App"
}


def initialize() {


    // 8 board. Relay cgi = Length = 21   = &0&8&0&1&1&0&0&0&0&0& 
    // 16 board. Relay cgi = Length = 38 =  &0&16&0&0&0&0&0&0&0&0&0&0&0&0&0&0&0&0&

    unschedule()
    state.working = 0
    
    int inputCount = 0

    try {
        httpGet("http://" + settings.molIPAddress + "/relay_cgi_load.cgi") { resp ->
            if (resp.success) {
                logDebug "initialize(): Response = " + resp.data
                logDebug "initialize(): Response[3] = " + (resp.data as String)[3]
                
                
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
            
                    app.updateSetting("relaycount", [value:"${inputCount}", type:"string"])  
                    // atualiza a config para deixar o numero de relays. 
    
                
            }
            else {
                if (resp.data) logDebug "initialize(): Falha para obter o # de relays ${resp.data}"
            }
        }
    } catch (Exception e) {
        log.warn "initialize(): Call failed: ${e.message}"
    }

    for(int i = 1; i<=inputCount; i++) {
        def contactName = "MolSmRelay-" + Integer.toString(i) + "_${app.id}"
	    logDebug "initialize(): adding driver = " + contactName
        
        def contactDev = getChildDevice(contactName)
	    if(!contactDev) contactDev = addChildDevice("TRATO", "MolSmart Relays Driver", contactName, null, [name: "MolRelay " + Integer.toString(i), inputNumber: thisName])

    
    }
    
     def ipmolsmart = settings.molIPAddress
     def devices = getAllChildDevices()
     for (aDevice in devices)
    {
        
        
        aDevice.AtualizaIP(ipmolsmart)  //coloco o ip no relay.
        logDebug "Coloco el ip en cada device = $aDevice, " + ipmolsmart
        
    }

   
    state.working = 0     
    
        schedule("*/${pollFrequency} * * ? * * *", poll, [overwrite: false])  // usualmente cada 1 seg.   
        logDebug "Configuro a frequência de atualização para cada  ${pollFrequency} second(s)"
    
        
}



def poll() {


    if (state.working > 0) {
        state.working = state.working - 1
    } else if (state.working < 0) {
        state.working = 0
    } else {
        state.working = 5        

        def requestParams = [ uri: "http://" + settings.molIPAddress + "/relay_cgi_load.cgi" ]
    
        logDebug "poll(): $requestParams"
	    
        asynchttpGet("pollHandler", requestParams)
    }


}


def pollHandler(resp, data) {
	if ((resp.getStatus() == 200 || resp.getStatus() == 207) && resp.data[0] == '&') {
		doPoll(resp.data)
     
    } else {
		log.error "MOL não devolveu dados: $resp"
       
        
	}
    
    state.working = 0
}

def doPoll(response) {
    
    def ipmolsmart = settings.molIPAddress

    logDebug "doPoll(): Resposta = $response"
    
   
    def devices = getAllChildDevices()

    for (aDevice in devices)
    {
        int inputNum = (aDevice.deviceNetworkId as String)[11] as int
       
        
        def inputState = response[3+(2*inputNum)] //aca viene la cantidad de inputs de la placa.    
        def switchstatus = aDevice.currentValue("switch") 
        
        
        if ((inputState == "1") && (switchstatus == "off")) 
        {
           
            aDevice.on()
            logDebug "Device = $aDevice FOI LIGADA, " + inputState 
        }    
        
        else if ((inputState == "0") && (switchstatus == "on"))
          { 
            aDevice.off()
            logDebug "Device = $aDevice FOI DESLIGADA, " + inputState
          } 
        else 
         
            logDebug "Device = $aDevice NAO FAZER NADA" 
 
        
        
    }
}

private logDebug(msg) {
  if (settings?.debugOutput || settings?.debugOutput == null) {
    log.debug "$msg"
  }
}




