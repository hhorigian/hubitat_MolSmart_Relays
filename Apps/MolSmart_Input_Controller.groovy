/**
 *  MolSmart Input Controller App
 *
 *  Copyright 2026 VH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Maps inputs (push/hold) from a MolSmart relay module to switch device actions.
 *
 *  Version history:
 *        1.0 27/4/2026 - Initial release. Push/Hold per input → turn on/off switch devices.
 *        1.1 27/4/2026 - Added Toggle action for Push and Hold.
 *        1.2 27/4/2026 - Added conflict validation: same device cannot have more than one action per event type.
 *        1.3 27/4/2026 - Added music player actions (play/pause/stop/next/previous/volumeUp/volumeDown/mute/unmute).
 *        1.4 27/4/2026 - Added dimmer actions (on/off/setLevel with level value).
 */

definition(
    name:        "MolSmart Input Controller",
    namespace:   "TRATO",
    author:      "VH",
    description: "Mapeia entradas dos módulos MolSmart (push/hold) para ações em dispositivos Hubitat",
    iconUrl:     "",
    iconX2Url:   "")

preferences {
    page(name: "mainPage")
    page(name: "inputPage")
}

/* ================= Pages ================= */

def mainPage(){
    dynamicPage(name: "mainPage", title: "MolSmart Input Controller", install: true, uninstall: true){
        section("Módulo MolSmart"){
            input "molDevice", "capability.pushableButton",
                title: "Selecione o módulo MolSmart", required: true, submitOnChange: true
        }

        if (molDevice){
            int numInputs = (molDevice.currentValue("numberOfButtons") ?: 0) as int
            if (numInputs > 0){
                section("Entradas — clique para configurar cada uma"){
                    for (int i = 1; i <= numInputs; i++){
                        String num = i.toString().padLeft(2,'0')
                        href "inputPage",
                            title:       "Input ${num}",
                            description: inputSummary(num),
                            params:      [inputNum: num]
                    }
                }
            } else {
                section{
                    paragraph "Nenhuma entrada detectada. Verifique se o módulo está inicializado e o numberOfButtons está disponível."
                }
            }
        }

        section("Identificação"){
            label title: "Nome desta configuração (opcional)", required: false
        }
    }
}

def inputPage(params){
    String num = params?.inputNum ?: "01"
    dynamicPage(name: "inputPage", title: "Configurar Input ${num}"){
        section("Push — Pressionar rapidamente"){
            input "push${num}_on",     "capability.switch", title: "Ligar ao pressionar",    multiple: true, required: false, submitOnChange: true
            input "push${num}_off",    "capability.switch", title: "Desligar ao pressionar",  multiple: true, required: false, submitOnChange: true
            input "push${num}_toggle", "capability.switch", title: "Alternar ao pressionar",  multiple: true, required: false, submitOnChange: true
            List pushConflicts = conflictNames(num, "push")
            if (pushConflicts)
                paragraph "<b style='color:red'>⚠ Conflito Push:</b> ${pushConflicts.join(', ')} está em mais de uma ação. Cada dispositivo deve ter apenas uma ação por evento."
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
                paragraph "<b style='color:red'>⚠ Conflito Hold:</b> ${heldConflicts.join(', ')} está em mais de uma ação. Cada dispositivo deve ter apenas uma ação por evento."
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

/* ================= Summary helper ================= */

private String inputSummary(String num){
    List parts = []
    int onPush  = (settings["push${num}_on"]     as List)?.size() ?: 0
    int offPush = (settings["push${num}_off"]    as List)?.size() ?: 0
    int togPush = (settings["push${num}_toggle"] as List)?.size() ?: 0
    int onHeld  = (settings["held${num}_on"]     as List)?.size() ?: 0
    int offHeld = (settings["held${num}_off"]    as List)?.size() ?: 0
    int togHeld = (settings["held${num}_toggle"] as List)?.size() ?: 0
    int musPush = (settings["push${num}_music_devs"]   as List)?.size() ?: 0
    int musHeld = (settings["held${num}_music_devs"]   as List)?.size() ?: 0
    int dimPush = (settings["push${num}_dimmer_devs"]  as List)?.size() ?: 0
    int dimHeld = (settings["held${num}_dimmer_devs"]  as List)?.size() ?: 0
    if (onPush)  parts << "Push→ON(${onPush})"
    if (offPush) parts << "Push→OFF(${offPush})"
    if (togPush) parts << "Push→TOG(${togPush})"
    if (dimPush){
        String da = settings["push${num}_dimmer_action"] ?: '?'
        String dl = (da == "setLevel") ? "@${settings["push${num}_dimmer_level"] ?: '?'}%" : ""
        parts << "Push→DIM:${da}${dl}(${dimPush})"
    }
    if (musPush) parts << "Push→♪${settings["push${num}_music_action"] ?: '?'}(${musPush})"
    if (onHeld)  parts << "Hold→ON(${onHeld})"
    if (offHeld) parts << "Hold→OFF(${offHeld})"
    if (togHeld) parts << "Hold→TOG(${togHeld})"
    if (dimHeld){
        String da = settings["held${num}_dimmer_action"] ?: '?'
        String dl = (da == "setLevel") ? "@${settings["held${num}_dimmer_level"] ?: '?'}%" : ""
        parts << "Hold→DIM:${da}${dl}(${dimHeld})"
    }
    if (musHeld) parts << "Hold→♪${settings["held${num}_music_action"] ?: '?'}(${musHeld})"
    return parts ? parts.join("  |  ") : "Toque para configurar"
}

/* ================= Lifecycle ================= */

def installed(){ initialize() }
def updated()  { unsubscribe(); initialize() }
def uninstalled(){ unsubscribe() }

def initialize(){
    if (!molDevice){ log.warn "MolSmart Input Controller: nenhum dispositivo selecionado."; return }
    subscribe(molDevice, "pushed", handlePushed)
    subscribe(molDevice, "held",   handleHeld)
    log.info "MolSmart Input Controller: subscrito a '${molDevice.displayName}'"
    // Warn about any conflicts saved in settings
    int numInputs = (molDevice.currentValue("numberOfButtons") ?: 0) as int
    for (int i = 1; i <= numInputs; i++){
        String num = i.toString().padLeft(2,'0')
        List pc = conflictNames(num, "push")
        List hc = conflictNames(num, "held")
        if (pc) log.warn "Input ${num} PUSH: conflito de ação nos dispositivos: ${pc.join(', ')}"
        if (hc) log.warn "Input ${num} HOLD: conflito de ação nos dispositivos: ${hc.join(', ')}"
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
    log.info "Input ${num} PUSH → ON:${onDevs*.displayName}  OFF:${offDevs*.displayName}  TOG:${togDevs*.displayName}  DIM:${dimmerAct ?: '-'}${dimmerAct=='setLevel'?dimmerLvl:''}  MUSIC:${musicAct ?: '-'}"
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
    log.info "Input ${num} HOLD → ON:${onDevs*.displayName}  OFF:${offDevs*.displayName}  TOG:${togDevs*.displayName}  DIM:${dimmerAct ?: '-'}${dimmerAct=='setLevel'?dimmerLvl:''}  MUSIC:${musicAct ?: '-'}"
}

/* ================= Util ================= */

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

private String padNum(String v){
    try { return v.toInteger().toString().padLeft(2,'0') } catch(e) { return v?.padLeft(2,'0') ?: "01" }
}

private void toggleDevice(dev){
    if (dev.currentValue("switch") == "on") dev.off()
    else dev.on()
}

private void executeDimmerAction(dev, String action, Integer level){
    switch(action){
        case "on":       dev.on();                          break
        case "off":      dev.off();                         break
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
