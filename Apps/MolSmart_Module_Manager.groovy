/**
 *  MolSmart Module Manager App
 *
 *  Copyright 2026 VH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  One instance per MolSmart relay module. Manages:
 *    - Relay (output) channel renaming from a dedicated page
 *    - Input (button) push/hold actions: on/off/toggle for switches,
 *      setLevel for dimmers, and play/pause/etc. for music players
 *
 *  Version history:
 *        1.0 27/4/2026 - Initial release. Relay renaming + input push/hold actions.
 *        1.1 27/4/2026 - Relay renaming: replaced capability.switch picker with per-channel
 *                        text inputs driven by numberOfButtons; names applied via setChannelName().
 */

definition(
    name:        "MolSmart Module Manager",
    namespace:   "TRATO",
    author:      "VH",
    description: "Gerencia nomes dos relays e ações dos inputs de um módulo MolSmart",
    iconUrl:     "",
    iconX2Url:   "")

preferences {
    page(name: "mainPage")
    page(name: "relayNamesPage")
    page(name: "inputListPage")
    page(name: "inputPage")
}

/* ================= Pages ================= */

def mainPage(){
    dynamicPage(name: "mainPage", title: "MolSmart Module Manager", install: true, uninstall: true){
        section("Módulo MolSmart"){
            input "molDevice", "capability.pushableButton",
                title: "Selecione o módulo", required: true, submitOnChange: true
        }

        if (molDevice){
            int numRelays = (molDevice.currentValue("numberOfButtons") ?: 0) as int
            int numInputs = numRelays

            section("Gerenciamento"){
                href "relayNamesPage",
                    title:       "Relays (Outputs)",
                    description: numRelays > 0 ? "${numRelays} canal(is) detectados — clique para renomear" : "Clique para renomear relays"

                href "inputListPage",
                    title:       "Inputs (Botões)",
                    description: numInputs > 0 ? "${numInputs} entradas detectadas — clique para configurar ações" : "Nenhuma entrada detectada"
            }
        }

        section("Identificação"){
            label title: "Nome desta instância (ex: MolSmart Sala)", required: false
        }
    }
}

/* ─── Relay Names ─── */

def relayNamesPage(){
    dynamicPage(name: "relayNamesPage", title: "Nomes dos Relays"){
        int ch = (molDevice?.currentValue("numberOfButtons") ?: 0) as int
        if (ch > 0){
            section("Digite o novo nome para cada canal (deixe em branco para não alterar)"){
                for (int i = 1; i <= ch; i++){
                    String num = i.toString().padLeft(2,'0')
                    input "chname_${num}", "text", title: "Canal ${num}", required: false
                }
            }
            section{ paragraph "<i>Os nomes são aplicados ao clicar Done.</i>" }
        } else {
            section{ paragraph "Módulo não inicializado. Execute 'Detect And Sync Channels' no driver primeiro." }
        }
    }
}

/* ─── Input List ─── */

def inputListPage(){
    dynamicPage(name: "inputListPage", title: "Configurar Inputs"){
        int numInputs = (molDevice?.currentValue("numberOfButtons") ?: 0) as int
        if (numInputs > 0){
            section("Entradas — clique para configurar cada uma"){
                for (int i = 1; i <= numInputs; i++){
                    String num = i.toString().padLeft(2,'0')
                    href "inputPage",
                        title:       inputTitle(num),
                        description: inputSummary(num),
                        params:      [inputNum: num]
                }
            }
        } else {
            section{ paragraph "Nenhuma entrada detectada. Verifique se o módulo está inicializado." }
        }
    }
}

/* ─── Per-Input Config ─── */

def inputPage(params){
    String num = params?.inputNum ?: "01"
    dynamicPage(name: "inputPage", title: "Input ${num} — ${inputTitle(num)}"){
        section("Push — Pressionar rapidamente"){
            input "push${num}_on",     "capability.switch", title: "Ligar ao pressionar",    multiple: true, required: false, submitOnChange: true
            input "push${num}_off",    "capability.switch", title: "Desligar ao pressionar",  multiple: true, required: false, submitOnChange: true
            input "push${num}_toggle", "capability.switch", title: "Alternar ao pressionar",  multiple: true, required: false, submitOnChange: true
            List pushConflicts = conflictNames(num, "push")
            if (pushConflicts)
                paragraph "<b style='color:red'>⚠ Conflito Push:</b> ${pushConflicts.join(', ')} está em mais de uma ação."
        }
        section("Push — Dimmer"){
            input "push${num}_dimmer_devs",   "capability.switchLevel", title: "Dimmers", multiple: true, required: false, submitOnChange: true
            if (settings["push${num}_dimmer_devs"]){
                input "push${num}_dimmer_action", "enum", title: "Ação", required: true,
                    options: ["on":"Ligar", "off":"Desligar", "setLevel":"Definir nível (%)"], submitOnChange: true
                if (settings["push${num}_dimmer_action"] == "setLevel")
                    input "push${num}_dimmer_level", "number", title: "Nível (0–100)", range: "0..100", required: true
            }
        }
        section("Push — Música"){
            input "push${num}_music_devs",   "capability.musicPlayer", title: "Dispositivos de música", multiple: true, required: false, submitOnChange: true
            if (settings["push${num}_music_devs"])
                input "push${num}_music_action", "enum", title: "Ação de música", required: true, options: musicActionOptions(), submitOnChange: true
        }

        section{ paragraph "<hr><div style='text-align:center;font-size:1.1em'><b>━━━━━━  HOLD — Pressionar e Segurar  ━━━━━━</b></div>" }

        section("Hold — Pressionar e segurar"){
            input "held${num}_on",     "capability.switch", title: "Ligar ao segurar",    multiple: true, required: false, submitOnChange: true
            input "held${num}_off",    "capability.switch", title: "Desligar ao segurar",  multiple: true, required: false, submitOnChange: true
            input "held${num}_toggle", "capability.switch", title: "Alternar ao segurar",  multiple: true, required: false, submitOnChange: true
            List heldConflicts = conflictNames(num, "held")
            if (heldConflicts)
                paragraph "<b style='color:red'>⚠ Conflito Hold:</b> ${heldConflicts.join(', ')} está em mais de uma ação."
        }
        section("Hold — Dimmer"){
            input "held${num}_dimmer_devs",   "capability.switchLevel", title: "Dimmers", multiple: true, required: false, submitOnChange: true
            if (settings["held${num}_dimmer_devs"]){
                input "held${num}_dimmer_action", "enum", title: "Ação", required: true,
                    options: ["on":"Ligar", "off":"Desligar", "setLevel":"Definir nível (%)"], submitOnChange: true
                if (settings["held${num}_dimmer_action"] == "setLevel")
                    input "held${num}_dimmer_level", "number", title: "Nível (0–100)", range: "0..100", required: true
            }
        }
        section("Hold — Música"){
            input "held${num}_music_devs",   "capability.musicPlayer", title: "Dispositivos de música", multiple: true, required: false, submitOnChange: true
            if (settings["held${num}_music_devs"])
                input "held${num}_music_action", "enum", title: "Ação de música", required: true, options: musicActionOptions(), submitOnChange: true
        }
    }
}

/* ================= Summary helpers ================= */

private String inputTitle(String num){
    def children = molDevice?.childDevices?.findAll{ (it.deviceNetworkId ?: "").endsWith("IN${num}") }
    String label = children ? (children[0].label ?: children[0].name) : null
    return label ?: "Input ${num}"
}

private String inputSummary(String num){
    List parts = []
    int onPush  = (settings["push${num}_on"]     as List)?.size() ?: 0
    int offPush = (settings["push${num}_off"]    as List)?.size() ?: 0
    int togPush = (settings["push${num}_toggle"] as List)?.size() ?: 0
    int dimPush = (settings["push${num}_dimmer_devs"] as List)?.size() ?: 0
    int musPush = (settings["push${num}_music_devs"]  as List)?.size() ?: 0
    int onHeld  = (settings["held${num}_on"]     as List)?.size() ?: 0
    int offHeld = (settings["held${num}_off"]    as List)?.size() ?: 0
    int togHeld = (settings["held${num}_toggle"] as List)?.size() ?: 0
    int dimHeld = (settings["held${num}_dimmer_devs"] as List)?.size() ?: 0
    int musHeld = (settings["held${num}_music_devs"]  as List)?.size() ?: 0
    if (onPush)  parts << "Push→ON(${onPush})"
    if (offPush) parts << "Push→OFF(${offPush})"
    if (togPush) parts << "Push→TOG(${togPush})"
    if (dimPush){ String da = settings["push${num}_dimmer_action"] ?: '?'; parts << "Push→DIM:${da}${da=='setLevel'?"@${settings["push${num}_dimmer_level"]?:'?'}%":''}(${dimPush})" }
    if (musPush) parts << "Push→♪${settings["push${num}_music_action"] ?: '?'}(${musPush})"
    if (onHeld)  parts << "Hold→ON(${onHeld})"
    if (offHeld) parts << "Hold→OFF(${offHeld})"
    if (togHeld) parts << "Hold→TOG(${togHeld})"
    if (dimHeld){ String da = settings["held${num}_dimmer_action"] ?: '?'; parts << "Hold→DIM:${da}${da=='setLevel'?"@${settings["held${num}_dimmer_level"]?:'?'}%":''}(${dimHeld})" }
    if (musHeld) parts << "Hold→♪${settings["held${num}_music_action"] ?: '?'}(${musHeld})"
    return parts ? parts.join("  |  ") : "Toque para configurar"
}

/* ================= Lifecycle ================= */

def installed(){ initialize() }

def updated(){
    unsubscribe()
    applyRelayNames()
    initialize()
}

def uninstalled(){ unsubscribe() }

def initialize(){
    if (!molDevice){ log.warn "${app.label ?: 'MolSmart Module Manager'}: nenhum dispositivo selecionado."; return }
    subscribe(molDevice, "pushed", handlePushed)
    subscribe(molDevice, "held",   handleHeld)
    log.info "${app.label ?: 'MolSmart Module Manager'}: subscrito a '${molDevice.displayName}'"
}

/* ================= Relay renaming ================= */

private void applyRelayNames(){
    if (!molDevice) return
    int ch = (molDevice.currentValue("numberOfButtons") ?: 0) as int
    for (int i = 1; i <= ch; i++){
        String num = i.toString().padLeft(2,'0')
        String newName = settings["chname_${num}"]?.trim()
        if (newName){
            molDevice.setChannelName(i, newName)
            log.info "${app.label ?: 'MolSmart Module Manager'}: Canal ${i} → '${newName}'"
        }
    }
}

/* ================= Event handlers ================= */

def handlePushed(evt){
    String num       = padNum(evt.value)
    List onDevs      = (settings["push${num}_on"]           as List) ?: []
    List offDevs     = (settings["push${num}_off"]          as List) ?: []
    List togDevs     = (settings["push${num}_toggle"]       as List) ?: []
    List dimmerDevs  = (settings["push${num}_dimmer_devs"]  as List) ?: []
    String dimmerAct = settings["push${num}_dimmer_action"]
    Integer dimmerLvl= settings["push${num}_dimmer_level"]  as Integer
    List musicDevs   = (settings["push${num}_music_devs"]   as List) ?: []
    String musicAct  = settings["push${num}_music_action"]
    onDevs.each    { it.on()  }
    offDevs.each   { it.off() }
    togDevs.each   { toggleDevice(it) }
    if (dimmerDevs && dimmerAct) dimmerDevs.each { executeDimmerAction(it, dimmerAct, dimmerLvl) }
    if (musicDevs  && musicAct)  musicDevs.each  { executeMusicAction(it, musicAct) }
    log.info "${app.label ?: 'MolSmart'}: Input ${num} PUSH → ON:${onDevs*.displayName}  OFF:${offDevs*.displayName}  TOG:${togDevs*.displayName}  DIM:${dimmerAct ?: '-'}  MUSIC:${musicAct ?: '-'}"
}

def handleHeld(evt){
    String num       = padNum(evt.value)
    List onDevs      = (settings["held${num}_on"]           as List) ?: []
    List offDevs     = (settings["held${num}_off"]          as List) ?: []
    List togDevs     = (settings["held${num}_toggle"]       as List) ?: []
    List dimmerDevs  = (settings["held${num}_dimmer_devs"]  as List) ?: []
    String dimmerAct = settings["held${num}_dimmer_action"]
    Integer dimmerLvl= settings["held${num}_dimmer_level"]  as Integer
    List musicDevs   = (settings["held${num}_music_devs"]   as List) ?: []
    String musicAct  = settings["held${num}_music_action"]
    onDevs.each    { it.on()  }
    offDevs.each   { it.off() }
    togDevs.each   { toggleDevice(it) }
    if (dimmerDevs && dimmerAct) dimmerDevs.each { executeDimmerAction(it, dimmerAct, dimmerLvl) }
    if (musicDevs  && musicAct)  musicDevs.each  { executeMusicAction(it, musicAct) }
    log.info "${app.label ?: 'MolSmart'}: Input ${num} HOLD → ON:${onDevs*.displayName}  OFF:${offDevs*.displayName}  TOG:${togDevs*.displayName}  DIM:${dimmerAct ?: '-'}  MUSIC:${musicAct ?: '-'}"
}

/* ================= Util ================= */

private String padNum(String v){
    try { return v.toInteger().toString().padLeft(2,'0') } catch(e) { return v?.padLeft(2,'0') ?: "01" }
}

private void toggleDevice(dev){
    if (dev.currentValue("switch") == "on") dev.off()
    else dev.on()
}

private void executeDimmerAction(dev, String action, Integer level){
    switch(action){
        case "on":       dev.on();                                   break
        case "off":      dev.off();                                  break
        case "setLevel": dev.setLevel(level != null ? level : 100); break
        default: log.warn "executeDimmerAction: ação desconhecida '${action}'"
    }
}

private void executeMusicAction(dev, String action){
    switch(action){
        case "play":          dev.play();          break
        case "pause":         dev.pause();         break
        case "stop":          dev.stop();          break
        case "nextTrack":     dev.nextTrack();     break
        case "previousTrack": dev.previousTrack(); break
        case "volumeUp":      dev.volumeUp();      break
        case "volumeDown":    dev.volumeDown();    break
        case "mute":          dev.mute();          break
        case "unmute":        dev.unmute();        break
        default: log.warn "executeMusicAction: ação desconhecida '${action}'"
    }
}

private Map musicActionOptions(){
    return [
        "play"          : "▶  Play",
        "pause"         : "⏸  Pause",
        "stop"          : "⏹  Stop",
        "nextTrack"     : "⏭  Próxima faixa",
        "previousTrack" : "⏮  Faixa anterior",
        "volumeUp"      : "🔊  Volume +",
        "volumeDown"    : "🔉  Volume -",
        "mute"          : "🔇  Mudo",
        "unmute"        : "🔈  Ativar som"
    ]
}

private List conflictNames(String num, String eventType){
    List aDevs = (settings["${eventType}${num}_on"]     as List) ?: []
    List bDevs = (settings["${eventType}${num}_off"]    as List) ?: []
    List cDevs = (settings["${eventType}${num}_toggle"] as List) ?: []
    Set aIds = aDevs.collect{ it.id } as Set
    Set bIds = bDevs.collect{ it.id } as Set
    Set cIds = cDevs.collect{ it.id } as Set
    Set conflictIds = new HashSet()
    conflictIds.addAll(aIds.intersect(bIds))
    conflictIds.addAll(aIds.intersect(cIds))
    conflictIds.addAll(bIds.intersect(cIds))
    if (!conflictIds) return []
    return (aDevs + bDevs + cDevs).findAll{ conflictIds.contains(it.id) }.collect{ it.displayName }.unique()
}
