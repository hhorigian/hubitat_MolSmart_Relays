/**
 *  MolRelay Relays
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
metadata {
  definition (name: "MolSmart Relays Driver", namespace: "TRATO", author: "MolSmart", vid: "generic-contact") {
    capability "Contact Sensor"
    capability "Sensor"
    capability "Switch"  
  }
      
  }

  preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "debugOutput", type: "bool", title: "Habilitar  Log", defaultValue: false
        input name: "ipdomodulo", type: "string", title: "Mol IP", defaultValue: ""     
      
  }   
  

def initialized()
{
    state.currentip = ""
    //state.newState = "" 
    log.debug "initialized()"
}



def on() {
     sendEvent(name: "switch", value: "on", isStateChange: true)
     log.info "Device OFF - sending command http" 
     sendCommandtoMolON()    
}

def off() {
     sendEvent(name: "switch", value: "off", isStateChange: true)
     log.info "Device OFF - sending command http" 
     sendCommandtoMolOFF()
}


def AtualizaIP(ipADD) {
    state.currentip = ipADD
    ipdomodulo  = state.currentip
    device.updateSetting("ipdomodulo", [value:"${ipADD}", type:"string"])
    log.info "Device com IP atualizada " + state.currentip
    
}


def sendCommandtoMolON() {  
ipdomodulo  = state.currentip
def numervalue1 = (device.deviceNetworkId as String)[11]
def numervalue2 = (device.deviceNetworkId as String)[12]  
def valor = ""
int relay = 0
    if (numervalue2 == "_") {
    valor =   numervalue1 as Integer
    relay = valor - 1
    } else {
    valor = (numervalue1 + numervalue2) as Integer       
    relay = valor - 1
    }


    def onURI = "http://"+ ipdomodulo + "/relay_cgi.cgi?type=0&relay=" + relay +"&on=1&time=0&pwd=0&"
    

try {
        httpGet(onURI) { resp ->
            if (resp.success) {
                //
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
                log.debug "Foi Ligado o Relay " + relay + " via http " + onURI
                //log.debug "numervalue1 = " + numervalue1 + " numervalue2 = " + numervalue2 + " + relay = " + relay
        }
    } catch (Exception e) {
        log.warn "Erro para LIGAR: ${e.message}"
        log.warn "IP do módulo " + ipdomodulo    
    }
}




def sendCommandtoMolOFF() {
ipdomodulo  = state.currentip
def numervalue1 = (device.deviceNetworkId as String)[11]
def numervalue2 = (device.deviceNetworkId as String)[12]
def valor = ""
int relay = 0
    if (numervalue2 == "_") {
    valor =   numervalue1 as Integer
    relay = valor - 1
    } else {
    valor = (numervalue1 + numervalue2) as Integer       
    relay = valor - 1
    }


def offURI = "http://"+ ipdomodulo + "/relay_cgi.cgi?type=0&relay=" + relay +"&on=0&time=0&pwd=0&"

try {
        httpGet(offURI) { resp ->
            if (resp.success) {
                //
            }
            if (logEnable)
                if (resp.data) log.debug "${resp.data}"
                log.debug "Foi DesLigado o Relay " + relay + " via http " + offURI
                //log.debug "numervalue1 = " + numervalue1 + " numervalue2 = " + numervalue2 + " + relay = " + relay
        }
    } catch (Exception e) {
        log.warn "Erro para DESLIGAR: ${e.message}"
        log.warn "IP do módulo " + ipdomodulo
    }
}




