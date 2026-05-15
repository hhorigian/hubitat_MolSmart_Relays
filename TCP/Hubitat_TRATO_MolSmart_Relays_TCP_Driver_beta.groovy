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
 *        1.1 30/4/2024  - correção do indexof para verificar os digitos de Network ID.
 *        1.2 05/10/2024 - Adição do Check cada 5 minutos para keepalive.
 *        1.3 05/12/2024 - Added BoardStatus Attribute (online/offline)
 *        1.4 05/22/2024 - Fix Scenes by adding "pauseExecution(250)" for On and Off in Childs
 *        1.5 06/05/2024 - Added Help Guide Link
 *        1.6 13/06/2024 - Added Help Guide Link  v2
 *        1.7 26/06/2024 - Added "0" digit to switch name, to sort nicely the switch names.
 *        1.8 27/06/2024 - Fixed double digit error on updates after v.1.7.
 *        1.9 29/06/2024 - Added Board Status Fix for Online/Offline + Improved Initialize/Update/Install + ManualKeepAlive.
 *        2.0 16/07/2024 - Fixed erro on line code 587 - MANDATORY UPDATE.
 *        2.1 30/07/2024 - Changed output status reading response method for TCP + Improved feedback.
 *        2.2 30/07/2024 - Fixed 16CH Count Relays. Fixed 32Ch. Added 32CH Master on/off.
 *        2.3 05/08/2024 - Added Line 278, with String thisId = device.id
 *        2.4 13/08/2024 - Added singleThreaded: true to metadata definition to fix Alexa Group issue
 *        2.5 17/02/2025 - Added 2CH Board. Improved Reconnect. Updated Parsing. Changed Logging defaults.
 *        2.6 17/02/2025 - Fixed Return Inputs for 2CH Board.
 *        2.7 17/02/2025 - Added Button for "Atualiza IP + Porta".
 *        2.8 11/8/2025  - FIX: removed duplicated txRaw() methods. Single txRaw(String msg, String reason).
 *                       - Features: PushableButton from inputs, terminator, HTTP control fallback, reconnect/heartbeat.
 *        2.9 15/8/2025  - Per-input contact updates (open/closed) with timestamp (lastChange).
 *                       - HTTP discovery used for channel count.
 *        3.0  8/10/2025 - Added Power Monitor for MolSmart boards with support.
 *                       - Added Module Restart (MQTT cloud). Added Press and HOLD status feedback.
 *        3.1 14/10/2025 - Added Module LAN Restart.
 *        3.2 21/10/2025 - Added support for New Modules firmware with 4 parts (rBits:iBits:CH:rMask).
 *        3.3 13/1/2026  - Added Autodisable logdebug after 30 minutes. Removed TCP Terminator from preferences.
 *        3.4 26/1/2026  - Added auto-reboot and initialize when socketStatus: send error: Broken pipe (Write failed).
 *        3.5 27/4/2026  - FIX: Changed rxBuf from @Field static to state variable (per-device) to fix
 *                         StringIndexOutOfBoundsException when multiple relay boards share the same driver class.
 *                       - FIX: Restored singleThreaded: true (removed in 2.8; needed for Alexa groups and
 *                         to prevent concurrent parse() execution on same device).
 *                       - FIX: extractSnFlexible {k=v} branch now uses for-loop so return exits the method.
 *        3.6 27/4/2026  - FIX: Child devices created by older driver versions used device.id as DNI prefix
 *                         (e.g. "2934-Switch-05"). New driver uses UUID prefix. createRelayChildren() now
 *                         migrates legacy children via setDeviceNetworkId() (in-place DNI remap), which
 *                         preserves the Hubitat device ID and all references (rules, Alexa, scenes, API).
 *                         If both legacy and new UUID child coexist, the newer "clean" child is deleted
 *                         and the legacy one is remapped — keeping history and references intact.
 *                       - Added reconcileRelayStates(): periodic (60s) comparison between last received
 *                         rBits and each child's currentValue("switch"), auto-correcting any divergence.
 *        3.7 28/4/2026  - FIX: Relay state updates now use cd.sendEvent() instead of cd.parse() to
 *                         reliably persist state even when child's switch attribute is null (uninitialized).
 *                         cd.parse() with isStateChange:false was silently dropping null→"off" transitions.
 *                         New relay children are also initialized with sendEvent(switch:"off") at creation.
 *        3.8 15/5/2026  - FIX: Removed verifyMasterOn/verifyMasterOff. With singleThreaded:true, the
 *                         pauseExecution(500) inside verify blocked parse() from processing the "00"
 *                         status response, so child states were always stale — causing spurious retries
 *                         of relays that were already ON/OFF. masteron/masteroff now end with a simple
 *                         runIn(3,"refresh") and rely on reconcileRelayStates (60s) for correction.
 *        3.9 15/5/2026  - FIX: masteron/masteroff rewritten to use runIn chain (doMasterNext) instead
 *                         of a pauseExecution loop. Each relay fires independently via runIn(1s), so
 *                         parse() is never blocked between commands — identical behavior to componentOn
 *                         called in sequence, which is confirmed to work reliably.
 *        3.10 15/5/2026 - Added masterDelayMs preference (default 500ms). Values < 1000ms batch
 *                         multiple relays per runIn call with pauseExecution between them, so parse()
 *                         still breathes every ~1 second between batches. Example: 250ms → 3 relays
 *                         per batch ≈ 11s for 32ch; 500ms → 1 per batch = 32s for 32ch (confirmed OK).
 */

import groovy.transform.Field

metadata {
  definition (
    name: "MolSmart - Relay 2/4/8/16/32CH (TCP) - beta 1",
    namespace: "TRATO",
    author: "VH",
    importUrl: "",
    singleThreaded: true
  ) {
    capability "Initialize"
    capability "Refresh"
    capability "Actuator"
    capability "PushableButton"
    capability "HoldableButton"
    capability "Switch"
    command "detectAndSyncChannels"
    command "setChannelName", [[name:"channel*", type:"NUMBER", description:"Canal (1-based)"], [name:"name*", type:"STRING", description:"Novo nome"]]
    //command "sendReboot"
    command "sendRebootLAN"

    attribute "driverVersion", "string"
    attribute "lastHeld", "number"
    attribute "lastPushed", "number"
    attribute "lastmessage", "string"
    attribute "lastRx", "number"
    attribute "online", "string"
    attribute "numberOfButtons", "number"
  }

  preferences {
        input name: "enablePwrMonitor", type: "bool", title: "Habilitar monitoramento de voltagem (pwr.cgi)?", defaultValue: false
        input name: "pwrPollSec", type: "number", title: "Intervalo de leitura (segundos)", defaultValue: 60, range: "5..3600"
        input name: "autoPruneChildren", type: "bool", title: "Remover filhos excedentes ao sincronizar?", defaultValue: true
        input name: "autoCreateChildren", type: "bool", title: "Criar filhos automaticamente após descobrir canais?", defaultValue: true
        input name: "molsmartSN", type: "text", title: "Numero de Serie da Mol", required: false

    // Network
    input name: "ipAddress",   type: "text",   title: "IP do módulo", required: true
    input name: "ipPort",      type: "number", title: "Porta TCP", defaultValue: 502, range: "1..65535"

    // Heartbeat / reconnection
    input name: "hbInterval",  type: "number", title: "Keep-alive (segundos)", defaultValue: 15, range: "5..120", required: true
    input name: "idleTimeout", type: "number", title: "Timeout de inatividade (s)", defaultValue: 60, range: "10..600", required: true
    input name: "reconnectMin",type: "number", title: "Backoff mínimo (s)", defaultValue: 5,  range: "1..120", required: true
    input name: "reconnectMax",type: "number", title: "Backoff máximo (s)", defaultValue: 60, range: "5..600", required: true

    // Master on/off relay interval
    input name: "masterDelayMs", type: "number", title: "Intervalo entre relays (master on/off) em ms", defaultValue: 500, range: "250..2000"

    // Inputs — contacts/buttons
    input name: "inputsActiveLow", type: "bool", title: "Entradas ativas em nível baixo? (0 = pressionado)", defaultValue: true
    input name: "inputsNormalOpen", type: "bool", title: "Contato lógico normal = OPEN?", defaultValue: false
    input name: "buttonDebounceMs", type: "number", title: "Debounce (ms)", defaultValue: 120, range: "0..2000"

    // Logging
    input name: "logEnable", type: "bool", title: "Logs de debug", defaultValue: true

    // Channel names — visible after first initialization
    if (state?.inputcount) {
        input name: "channelNamesTitle", type: "hidden", title: "<hr><b>Nomes dos Canais</b> — edite e clique Save para renomear"
        int maxCh = (state.inputcount as Integer)
        for (int i = 1; i <= maxCh; i++) {
            String num = i.toString().padLeft(2,'0')
            input "ch${num}_name", "text", title: "Canal ${num}", required: false
        }
    } else {
        input name: "channelNamesTitle", type: "hidden", title: "<hr><i>Salve o IP e aguarde a inicialização para configurar os nomes dos canais.</i>"
    }
  }

            attribute "Entrada12V-1", "text"
            attribute "Entrada12V-2", "text"
            attribute "pwrVoltages", "text"
            attribute "pwrLow", "STRING"
            attribute "pwrAlertText", "STRING"
            attribute "lastPwrAlertAt", "STRING"
}


// rxBuf intentionally NOT declared as @Field static — per-device state is used instead.
// A shared static rxBuf causes StringIndexOutOfBoundsException when multiple boards
// use this driver simultaneously (second instance corrupts first's buffer mid-parse).
@Field static java.util.Random _rng = new java.util.Random()
@Field static Long lastHbSentAt = 0L
@Field static final String BROKER_HOST = "bms.molsmart.com.br"
@Field static final Integer BROKER_PORT = 1883
@Field static final String USERNAME = "molsmartboardbms"
@Field static final String PASSWORD = ''
@Field static final String PAYLOAD  = '{"command":"reboot"}'
@Field static final Integer DISCONNECT_DELAY_SEC = 1
@Field static final Integer CONNECT_TIMEOUT_SEC  = 10
@Field static final String TCP_TERMINATOR = "NONE"
@Field static final String DRIVER_VERSION = "3.10"


/* ===== Logger helpers ===== */
private void logDbg(msg){ if (settings?.logEnable) log.debug("${device.displayName ?: device.name}: ${msg}") }
private void logInf(msg){ log.info ("${device.displayName ?: device.name}: ${msg}") }
private void logWar(msg){ log.warn ("${device.displayName ?: device.name}: ${msg}") }
private void logErr(msg){ log.error("${device.displayName ?: device.name}: ${msg}") }

/* ===== Small utils ===== */
private void   markRxNow()     { state.lastRx = now(); sendEvent(name:"lastRx", value: state.lastRx) }
private Long   msSinceRx()     { def t = (state.lastRx ?: 0L) as Long; return (t>0L) ? (now()-t) : Long.MAX_VALUE }
private int    clampInt(v,l,h){ Math.max(l as int, Math.min(h as int, (v ?: 0) as int)) }
private String netIdPrefix()   { state.netids ?: (state.netids = device.deviceNetworkId ?: device.id.toString()) }
private String inPrefix()      { state.inNetIds ?: (state.inNetIds = netIdPrefix()+"IN") }

private String resolveIP(){ String ip = settings?.ipAddress ?: settings?.device_IP_address ?: state?.ipAddress ?: state?.ipaddress; return ip?.trim() }
private Integer resolvePort(){ (settings?.ipPort as Integer) ?: (settings?.device_port as Integer) ?: 502 }

/* ================= Lifecycle ================= */

def installed(){
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    device.updateSetting("logEnable", [value:"false", type:"bool"])
    initialize()
}

def updated(){
    unschedule(); disconnectSocket();
    if (settings?.logEnable) runIn(1800, "logsOff")
    sendEvent(name: "driverVersion", value: DRIVER_VERSION)
    applyChannelNames()
    initialize()
}

def applyChannelNames() {
    if (!state?.inputcount) return
    int maxCh = (state.inputcount as Integer)
    for (int i = 1; i <= maxCh; i++) {
        String num = i.toString().padLeft(2,'0')
        String newName = settings["ch${num}_name"]
        if (newName?.trim()) {
            def cd = getChildDevice(childRelayDni(i))
            if (cd) {
                cd.setLabel(newName.trim())
                logInf("Canal ${num} renomeado para: ${newName.trim()}")
            }
        }
    }
}

def uninstalled(){ unschedule(); disconnectSocket() }

def initialize(){
    logInf("Initialize")
    // One-time migration from old driver setting names (device_IP_address / device_port)
    if (!settings?.ipAddress && settings?.device_IP_address){
        device.updateSetting("ipAddress", [value: settings.device_IP_address.trim(), type: "text"])
        logInf("Configuração migrada: device_IP_address → ipAddress (${settings.device_IP_address})")
    }
    if (!settings?.ipPort && settings?.device_port){
        device.updateSetting("ipPort", [value: (settings.device_port as Integer), type: "number"])
        logInf("Configuração migrada: device_port → ipPort (${settings.device_port})")
    }
    String ip = resolveIP()
    if (!ip){
        logWar("IP do módulo ainda não configurado. Sem criação de filhos até configurar o IP.")
        sendEvent(name:"online", value:"false")
        state.socketOnline = false
        return
    }
    Integer ch = null
    try { ch = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
    if (!ch || ch <= 0){
        logWar("Quantidade de canais desconhecida. Use o comando 'Detect & Sync Channels'.")
        connectSocket()
        runIn(2, "queryBoardStatus", [overwrite:true])
        schedulePwrPolling()
        return
    }
    state.inputcount = ch
    sendEvent(name:"numberOfButtons", value: ch)
    state.lastButtons = ch

    if (settings?.autoCreateChildren != false){
        try { syncChildren(ch) } catch (e) { logWar("syncChildren falhou: ${e}") }
    } else {
        logInf("Criação automática de filhos está desativada (autoCreateChildren=false).")
    }

    connectSocket()
    runIn(2, "queryBoardStatus", [overwrite:true])
    schedulePwrPolling()
    if (settings?.logEnable) runIn(1800, "logsOff", [overwrite:true])
}


/* ================= Power Voltage (pwr.cgi) Monitoring ================= */
private void schedulePwrPolling(){
    try{ unschedule('pwrPollTick') } catch(e){}
    Integer sec = (settings?.pwrPollSec ?: 60) as Integer
    if (!(settings?.enablePwrMonitor)){
        logDbg("Monitoramento de voltagem desabilitado.")
        return
    }
    if (sec < 5) sec = 5
    logInf("Agendando leitura de voltagem (pwr.cgi) a cada ${sec}s")
    runIn(sec, 'pwrPollTick', [overwrite:true])
}

def pwrPollTick(){
    if (!(settings?.enablePwrMonitor)) return
    doAsyncPwrQuery()
    Integer sec = (settings?.pwrPollSec ?: 60) as Integer
    if (sec < 5) sec = 5
    runIn(sec, 'pwrPollTick', [overwrite:true])
}

private void doAsyncPwrQuery(){
    String ip = resolveIP()
    if (!ip){ logWar("pwr.cgi: IP não configurado."); return }
    Map params = [ uri: "http://${ip}/api/v2/pwr.cgi", headers: httpHeaders(), timeout: 5 ]
    try{
        asynchttpGet('pwrHttpCallback', params)
    } catch(e){
        logWar("Falha ao iniciar asynchttpGet para pwr.cgi: ${e}")
    }
}

def pwrHttpCallback(resp, data){
  try{
    if (!resp){ logWar("pwr.cgi: sem resposta"); return }
    if (resp?.hasError()){ logWar("pwr.cgi: HTTP erro ${resp?.getStatus()}"); return }

    String raw = resp?.getData() as String
    if (!raw){ logWar("pwr.cgi: corpo vazio"); return }

    def js = null
    try{ js = parseJson(raw) } catch(ex){ logWar("pwr.cgi: JSON inválido: ${ex}"); return }

    Integer status = (js?.status instanceof Number) ? (js.status as Integer)
                   : (js?.status?.toString()?.isInteger() ? js.status.toString().toInteger() : null)
    Integer cnt    = (js?.cnt    instanceof Number) ? (js.cnt as Integer)
                   : (js?.cnt?.toString()?.isInteger()    ? js.cnt.toString().toInteger()    : null)

    List vlist = (js?.v instanceof List) ? (List)js.v : []

    if (vlist.size() >= 1){
      String s1 = (vlist[0]?.toString() ?: "").trim()
      if (s1) sendEvent(name:"Entrada12V-1", value: s1)
    }
    if (vlist.size() >= 2){
      String s2 = (vlist[1]?.toString() ?: "").trim()
      if (s2) sendEvent(name:"Entrada12V-2", value: s2)
    }

    String joined = vlist.collect{ it?.toString() ?: "" }.join(", ")
    logDbg("pwr.cgi -> status=${status}, cnt=${cnt}, v=${joined}")
  } catch(e){
    logWar("pwrHttpCallback erro: ${e}")
  }
}


/* ================= Socket & Connection ================= */

private void connectSocket(){
  String ip = resolveIP(); Integer port = resolvePort()
  if (!ip){
    logErr("IP não configurado nas Preferences.")
    sendEvent(name:"online", value:"false"); state.socketOnline = false
    unschedule("heartbeat"); unschedule("connectionCheck")
    return
  }
  try{
    interfaces.rawSocket.connect(ip, port as int)
    state.socketOnline = true
    sendEvent(name:"online", value:"true")
    state.conIp = ip; state.conPort = port
    logInf("Socket conectado a ${ip}:${port}")
    state.reconnectAttempt = 0
    markRxNow()
    scheduleHeartbeat(); scheduleWatchdog(); scheduleReconcile(); runIn(1, "heartbeat", [overwrite:true])
  } catch(e){
    logErr("Falha ao conectar em ${ip}:${port}: ${e}")
    scheduleReconnect("connect error")
  }
}

private void disconnectSocket(){ try { interfaces.rawSocket.close() } catch(ignored){}; state.socketOnline=false; sendEvent(name:"online", value:"false") }

private void scheduleHeartbeat(){ int s = clampInt(settings?.hbInterval,5,120); runIn(s, "heartbeat", [overwrite:true]) }

def heartbeat(){ txRaw("00", "HB"); scheduleHeartbeat() }

private void scheduleWatchdog(){ runIn(5, "connectionCheck", [overwrite:true]) }

private void scheduleReconcile(){ runIn(60, "reconcileRelayStates", [overwrite:true]) }

def reconcileRelayStates(){
    String rBits = (state.lastRBits  ?: '') as String
    int    ch    = (state.lastRBitsCh ?: 0) as int
    if (!rBits || ch <= 0 || rBits.length() != ch){ scheduleReconcile(); return }
    int fixed = 0
    for (int i = 0; i < ch; i++){
        def cd = getChildDevice(childRelayDni(i+1))
        if (!cd) continue
        String expected = (rBits.charAt(i) == '1') ? "on" : "off"
        if ((cd.currentValue("switch") ?: "unknown") != expected){
            logWar("Relay ${i+1}: estado divergente detectado (atual=${cd.currentValue('switch')}, esperado=${expected}) — corrigindo")
            cd.sendEvent(name:"switch", value:expected, isStateChange:true)
            fixed++
        }
    }
    if (fixed) logInf("reconcileRelayStates: ${fixed} relay(s) corrigido(s)")
    scheduleReconcile()
}

def connectionCheck(){
  int hb = clampInt(settings?.hbInterval,5,120)
  int userIdle = clampInt(settings?.idleTimeout,10,600)
  int effIdle = Math.max(userIdle, hb*3)
  long idle = msSinceRx()
  if (idle >= effIdle*1000L){
    logErr("Sem mensagens há ${String.format('%.1f', idle/1000.0)}s (limite=${effIdle}s). Reconnect...")
    scheduleReconnect("idle timeout"); return
  }
  scheduleWatchdog()
}

private void scheduleReconnect(String reason){
  if (state.reconnecting == true) return
  state.reconnecting = true
  disconnectSocket()
  int attempt = ((state.reconnectAttempt ?: 0) as int) + 1
  state.reconnectAttempt = attempt
  int minS = clampInt(settings?.reconnectMin,1,120)
  int maxS = clampInt(settings?.reconnectMax,5,600)
  int delay = Math.min(maxS, (int)Math.pow(2D, Math.min(6,attempt-1)) * minS) + _rng.nextInt(1)
  logWar("Reconectar (#${attempt}) em ~${delay}s (${reason})")
  runIn(delay, "doReconnect", [overwrite:true])
}

def doReconnect(){ state.reconnecting = false; connectSocket() }

def socketStatus(String message){
    logWar("socketStatus: ${message}")
    if ((message ?: "").toLowerCase().contains("broken pipe")){
        noteBrokenPipeAndMaybeReboot("socketStatus")
    }
    if (!(message?.toLowerCase()?.contains("normal") ?: false)) scheduleReconnect("socketStatus")
}


/* ================= Parser ================= */

def parse(String payload){
  if (!payload) return
  markRxNow()

  // HEX -> texto (se vier em hexa contínuo)
  if (payload ==~ /(?i)^[0-9A-F]+$/ && (payload.length() % 2 == 0)){
    try { payload = new String(hubitat.helper.HexUtils.hexStringToByteArray(payload)) } catch(e){}
  }

  payload = payload.replaceAll(/[\r\n]+/, ' ')

  // Per-device receive buffer stored in state (not @Field static, which is shared across all instances)
  String rxBuf = ((state.rxBuf ?: '') as String) + payload
  logDbg("RX chunk: ${payload.trim()} (buffer=${rxBuf.length()})")

  while(true){
    rxBuf = rxBuf.replaceFirst(/^\s+/, '')
    if (!rxBuf) break

    int sp = rxBuf.indexOf(' ')
    String token = (sp >= 0) ? rxBuf.substring(0, sp) : rxBuf

    // Accept 3 or 4 colons (4 or 5 parts)
    int colons = token.findAll(':').size()
    if (colons < 3 && sp < 0) break
    if (colons < 3){ rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''; continue }

    String[] parts = token.split(':')
    if (parts.length != 4 && parts.length != 5){
      if (sp < 0) break
      logDbg("Descartando token inválido (partes=${parts.length}): ${token}")
      rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''
      continue
    }

    String rBits = parts[0]
    String iBits = parts[1]
    Integer ch = null
    try { ch = parts[2].toInteger() } catch(ignored){}

    String rMask = (parts.length >= 4) ? parts[3] : null
    String iMask = (parts.length == 5) ? parts[4] : null

    // iMask absent in newer FW — synthesize all-zeros
    if (iMask == null && ch != null && ch > 0) {
      iMask = "0" * ch
    }

    boolean sizesOk = (ch != null && ch > 0
      && rBits?.size()==ch && iBits?.size()==ch && rMask?.size()==ch && iMask?.size()==ch)

    boolean patternsOk = (rBits ==~ /^[01]+$/
      && iBits ==~ /^[01H]+$/
      && rMask ==~ /^[01]+$/
      && iMask ==~ /^[01H]+$/)

    if (!(sizesOk && patternsOk)){
      if (sp < 0) break
      logDbg("Frame inválido descartado: ${token}")
      rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''
      continue
    }

    rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''

    // Update relay switch children
    for (int i=0; i<ch; i++){
      String swDni = childRelayDni(i+1)
      def cd = getChildDevice(swDni)
      if (cd) cd.sendEvent(name:"switch", value: (rBits.charAt(i)=='1') ? "on" : "off")
    }
    // Store for periodic reconciliation
    state.lastRBits   = rBits
    state.lastRBitsCh = ch

    // Inputs → contacts + push/held
    handleInputEventsAndContacts(iBits, iMask, ch)

    sendEvent(name:"lastmessage", value: token)
    if ((state?.lastButtons as Integer) != ch){ sendEvent(name:"numberOfButtons", value: ch); state.lastButtons = ch }
  }

  state.rxBuf = rxBuf
}


/* ================= Inputs: Buttons + Contacts ================= */
private void handleInputEventsAndContacts(String iBits, String iMask, int chan){
  if (!iBits || chan <= 0) return

  String prev = state.prevInputBits ?: ("1" * chan)
  if (prev.length() != iBits.length()) prev = ("1" * iBits.length())

  int debounce = clampInt(settings?.buttonDebounceMs, 0, 2000)
  boolean activeLow  = (settings?.inputsActiveLow  != false)
  boolean normalOpen = (settings?.inputsNormalOpen != false)
  long nowMs = now()
  String ts = new Date().format("yyyy-MM-dd HH:mm:ss")

  if (state.btnLastMs      == null) state.btnLastMs      = [:]
  if (state.btnLastHeldMs  == null) state.btnLastHeldMs  = [:]
  if (state.btnPendingPush == null) state.btnPendingPush = [:]
  if (state.btnHeldCycle   == null) state.btnHeldCycle   = [:]

  for (int i=0; i<chan; i++){
    char before = (i < prev.length()) ? prev.charAt(i) : '1'
    char after  = iBits.charAt(i)
    char m      = (iMask && i < iMask.length()) ? iMask.charAt(i) : '0'
    int idx = i+1

    boolean wasPressed = activeLow ? (before=='0' || before=='H') : (before=='1' || before=='H')
    boolean nowPressed = activeLow ? (after =='0' || after =='H') : (after =='1' || after =='H')

    boolean pressEdge   = (!wasPressed &&  nowPressed)
    boolean releaseEdge = ( wasPressed && !nowPressed)
    boolean holdSeenFrm = (after=='H') || (m=='H')

    // Update contact child
    String contactState = nowPressed ? (normalOpen ? "closed" : "open")
                                     : (normalOpen ? "open"   : "closed")
    String inDni = childInputDni(idx)
    def inChild = getChildDevice(inDni)
    if (inChild){
      if (inChild.currentValue("contact") != contactState){
        inChild.sendEvent(name:"contact", value: contactState)
      }
    }

    if (pressEdge){
      state.btnPendingPush["${idx}"] = true
      state.btnHeldCycle  ["${idx}"] = false
    }

    if (holdSeenFrm){
      long lastHeld = (state.btnLastHeldMs["${idx}"] ?: 0L) as Long
      if (debounce <= 0 || (nowMs - lastHeld) >= debounce){
        sendEvent(name:"held", value: idx, isStateChange:true, type:"physical", descriptionText: "Input ${idx} held")
        sendEvent(name:"lastHeld", value: idx)
        state.btnLastHeldMs["${idx}"] = nowMs
      }
      state.btnHeldCycle["${idx}"] = true
      state.btnPendingPush["${idx}"] = false
    }

    if (releaseEdge){
      boolean pending = (state.btnPendingPush["${idx}"] ?: false) as boolean
      boolean heldcyc = (state.btnHeldCycle  ["${idx}"] ?: false) as boolean
      if (pending && !heldcyc){
        long lastMs = (state.btnLastMs["${idx}"] ?: 0L) as Long
        if (debounce <= 0 || (nowMs - lastMs) >= debounce){
          sendEvent(name:"pushed", value: idx, isStateChange:true, type:"physical", descriptionText: "Input ${idx} pushed")
          sendEvent(name:"lastPushed", value: idx)
          state.btnLastMs["${idx}"] = nowMs
        } else {
          logDbg("Input ${idx} push ignorado pelo debounce (${nowMs-lastMs}ms < ${debounce}ms)")
        }
      }
      state.btnPendingPush["${idx}"] = false
      state.btnHeldCycle  ["${idx}"] = false
    }

    if (inChild){
      String holdStatus = ((state.btnHeldCycle["${idx}"] ?: false) || holdSeenFrm) ? "active" : "inactive"
      if ((inChild.currentValue("holdStatus") ?: "inactive") != holdStatus){
        inChild.sendEvent(name:"holdStatus", value: holdStatus)
      }
      String combined = (contactState == "closed") ? (holdStatus=="active"?"held-closed":"closed")
                                                   : (holdStatus=="active"?"held-open":"open")
      inChild.sendEvent(name:"combinedStatus", value: combined)
      String tag
      if (holdSeenFrm)      tag = "(Hold)"
      else if (pressEdge)   tag = "(Press)"
      else if (releaseEdge) tag = "(Release)"
      else                  tag = ""
      if (tag) inChild.sendEvent(name:"lastChange", value: "${ts} ${tag}")
    }
  }

  state.prevInputBits = iBits
}


/* ================= Commands ================= */

def refresh(){ logInf("Refresh() -> 00"); txRaw("00", "manual refresh") }

def masteron(){
  int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName.contains('Switch') }?.size() ?: 8) as int
  logInf("Master ON: ${ch} relays (1s/relay via runIn)")
  try { unschedule("doMasterNext") } catch(e){}
  state.masterIdx    = 1
  state.masterCount  = ch
  state.masterAction = "on"
  doMasterNext()
}

def masteroff(){
  int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName.contains('Switch') }?.size() ?: 8) as int
  logInf("Master OFF: ${ch} relays (1s/relay via runIn)")
  try { unschedule("doMasterNext") } catch(e){}
  state.masterIdx    = 1
  state.masterCount  = ch
  state.masterAction = "off"
  doMasterNext()
}

def doMasterNext(){
  int idx = (state.masterIdx   ?: 0) as int
  int ch  = (state.masterCount ?: 0) as int
  if (idx < 1 || ch <= 0 || idx > ch){ runIn(2, "refresh", [overwrite:true]); return }

  int delayMs   = clampInt(settings?.masterDelayMs, 250, 2000)
  // If delay < 1000ms, batch multiple relays per runIn call (parse() breathes between batches)
  int batchSize = (delayMs < 1000) ? Math.max(1, (int)(900 / delayMs)) : 1
  String prefix = (state.masterAction == "on") ? "1" : "2"

  for (int b = 0; b < batchSize && idx <= ch; b++){
    txRaw("${prefix}${idx}", "master${state.masterAction}")
    logDbg("doMasterNext: relay ${idx}/${ch} ${state.masterAction}")
    idx++
    if (b < batchSize - 1 && idx <= ch) pauseExecution(delayMs)
  }

  state.masterIdx = idx
  if (idx <= ch) runIn(1, "doMasterNext", [overwrite:true])
  else           runIn(2, "refresh",      [overwrite:true])
}

def componentRefresh(cd){ logInf("componentRefresh from ${cd?.displayName}"); txRaw("00", "componentRefresh") }

def componentOn(cd){ Integer ch = relayIndexFromDni(cd?.deviceNetworkId); if (ch!=null){ log.info("on from ${cd?.displayName}");  doControlTX("1${ch}") } }

def componentOff(cd){ Integer ch = relayIndexFromDni(cd?.deviceNetworkId); if (ch!=null){ log.info("off from ${cd?.displayName}"); doControlTX("2${ch}") } }

private void doControlTX(String cmd){
  txRaw(cmd, "control")
  runIn(1, "refresh", [overwrite:true])
}

private boolean txRaw(String msg, String reason){
  String tx = withTerminator(msg)
  try{
    interfaces.rawSocket.sendMessage(tx)
    logDbg("TX${reason?"(${reason})":''}: '"+escapePrint(msg)+"' + term='${TCP_TERMINATOR}' (len=${tx.length()})")
    if (msg == "00") lastHbSentAt = now()
    return true
  } catch(e){
    logWar("Falha ao enviar '${msg}': ${e}")
    if (("${e}".toLowerCase().contains("broken pipe"))){
        noteBrokenPipeAndMaybeReboot("txRaw:${reason}")
    }
    scheduleReconnect("send fail")
    return false
  }
}


/* ================= Children creation ================= */

private String childRelayDni(int idx){ return "${netIdPrefix()}${idx.toString().padLeft(2,'0')}" }
private String childInputDni(int idx){ return "${inPrefix()}${idx.toString().padLeft(2,'0')}" }

private Integer relayIndexFromDni(String dni){
  if (!dni) return null
  String prefix = netIdPrefix()
  if (!dni.startsWith(prefix)) return null
  try { return dni.substring(prefix.length()).toInteger() } catch(e){ return null }
}

private void createRelayChildren(){
    int ch = (state?.inputcount ?: 0) as int
    if (ch <= 0){ logWar("Canais ainda não detectados; não criarei relays."); return }
    (1..ch).each { n ->
        String num = n.toString().padLeft(2,'0')
        String dni = childRelayDni(n)
        String legacyDni = "${device.id}-Switch-${num}"
        def legacyChild = getChildDevice(legacyDni)
        if (legacyChild){
            // Migrate legacy child by remapping its DNI in-place with setDeviceNetworkId().
            // This keeps the same Hubitat device ID, so all rules, Alexa links, scenes, and
            // API references continue working — nothing referencing this device breaks.
            def conflictChild = getChildDevice(dni)
            if (conflictChild){
                // A "clean" UUID child also exists (no rule references) — delete it first
                // so its DNI is free for the legacy child to claim.
                logInf("Removendo filho novo sem histórico (${dni}) para liberar DNI")
                try { deleteChildDevice(dni) } catch(e){ logWar("Falha ao remover filho conflitante ${dni}: ${e}") }
            }
            try {
                legacyChild.setDeviceNetworkId(dni)
                logInf("DNI migrado in-place: ${legacyDni} → ${dni} (device ID preservado, referências mantidas)")
            } catch(e){
                logWar("setDeviceNetworkId falhou (${legacyDni}→${dni}): ${e}")
            }
        } else if (!getChildDevice(dni)){
            // No legacy child and no new child — fresh creation
            try{
                def nc = addChildDevice("hubitat", "Generic Component Switch", dni,
                    [name: "Mol Relay ${num}", label: "Mol Relay ${num}", isComponent: true])
                nc.sendEvent(name:"switch", value:"off")
                logInf("Child SWITCH criado: ${dni}")
            } catch(e){ logErr("Falha ao criar switch ${dni}: ${e}") }
        }
    }
}

private void createInputChildren(){
    int ch = (state?.inputcount ?: 0) as int
    if (ch <= 0){ logWar("Canais ainda não detectados; não criarei inputs."); return }
    (1..ch).each { n ->
        String dni = childInputDni(n)
        if (!getChildDevice(dni)){
            try{
                def c = addChildDevice("hubitat", "Generic Component Contact Sensor", dni,
                    [name: "Mol Input ${n.toString().padLeft(2,'0')}", label: "Mol Input ${n.toString().padLeft(2,'0')}", isComponent: true])
                logInf("Child CONTACT criado: ${dni}")
                c.sendEvent(name:"contact", value:"unknown")
                c.sendEvent(name:"holdStatus", value:"inactive")
                c.sendEvent(name:"combinedStatus", value:"unknown")
            } catch(e){ logErr("Falha ao criar contact ${dni}: ${e}") }
        }
    }
}


/* ================= HTTP helpers ================= */

private Map httpHeaders(){ return [:] }

private Integer discoverChannelCount(){
    String ip = resolveIP()
    if (!ip){ logWar("discoverChannelCount: IP não configurado."); return null }
    try{
        Integer count = null
        httpGet([ uri: "http://${ip}/relay_cgi_load.cgi", headers: httpHeaders(), timeout: 5 ]) { resp ->
            String s = resp.data?.toString() ?: ''
            def parts = s.split('&')
            if (parts.size() > 2 && parts[2].isInteger()) count = parts[2].toInteger()
        }
        if (count && count > 0){
            logInf("Canais detectados (via HTTP): ${count}")
            try {
                httpGet([ uri: "http://${ip}/get/sn.cgi", headers: httpHeaders(), timeout: 5 ]) { resp2 ->
                    String raw2 = resp2.data?.toString() ?: ''
                    String snVal = extractSnFlexible(raw2)
                    if (!snVal && resp2?.data instanceof Map) {
                        def js2 = (Map)resp2.data
                        if (js2?.sn != null) snVal = js2.sn.toString().trim()
                    }
                    if (snVal) {
                        device.updateSetting("molsmartSN", [value: snVal, type: "text"])
                        state.NumeroSerie = snVal
                        logInf("Número de série detectado: ${snVal}")
                    } else {
                        logDbg("SN não encontrado no conteúdo de sn.cgi: ${raw2}")
                    }
                }
            } catch (err2) {
                logWar("Falha ao obter número de série (sn.cgi): ${err2}")
            }
            return count
        } else {
            logWar("Não foi possível interpretar a quantidade de canais via HTTP.")
            return null
        }
    } catch(e){
        logWar("discoverChannelCount falhou: ${e}")
        return null
    }
}

def queryBoardStatus(){
    String ip = resolveIP()
    if (!ip) { logWar("queryBoardStatus: IP não configurado."); return }
    try{
        httpGet([ uri: "http://${ip}/relay_cgi_load.cgi", headers: httpHeaders(), timeout: 5 ]) { resp ->
            String s = resp.data?.toString() ?: ''
            logDbg("HTTP status raw: ${s}")
            def parts = s.split('&')
            if (parts.size() > 2 && parts[2].isInteger()){
                int ch = parts[2].toInteger()
                if (ch > 0){
                    if ((state?.inputcount ?: 0) != ch){
                        logInf("Board reportou ${ch} canais (antes: ${state?.inputcount ?: 0}). Sincronizando filhos...")
                        state.inputcount = ch
                        sendEvent(name:"numberOfButtons", value: ch)
                        state.lastButtons = ch
                        if (settings?.autoCreateChildren != false) syncChildren(ch)
                    } else {
                        sendEvent(name:"numberOfButtons", value: ch)
                        state.lastButtons = ch
                    }
                }
            }
        }
    } catch(e){ logWar("queryBoardStatus falhou: ${e}") }
}


/* ===== Helper: extract SN from JSON or {k=v} map string ===== */
private static String extractSnFlexible(String s){
    if (!s) return null
    String t = s.trim()

    // 1) Standard JSON
    try {
        def js = new groovy.json.JsonSlurper().parseText(t)
        def v = js?.sn
        if (v != null) return v.toString().trim()
    } catch(ex) {}

    // 2) Tolerant regex (supports : or =, with/without quotes)
    def m = (t =~ /(?i)\b"sn"\s*:\s*"?([\w\-\.\:]+)"?/)
    if (m.find()) return m.group(1)

    m = (t =~ /(?i)\bsn\s*[=:]\s*"?([\w\-\.\:]+)"?/)
    if (m.find()) return m.group(1)

    // 3) Map.toString() format: {sn=51566, status=0}
    // Uses a for-loop so 'return' exits the method (not just a closure iteration)
    if (t.startsWith("{") && t.endsWith("}") && t.contains("=")) {
        String body = t.substring(1, t.length()-1)
        for (String pair : body.split(/\s*,\s*/)) {
            String[] kv = pair.split(/\s*[=:]\s*/, 2)
            if (kv.size() == 2 && kv[0].trim().equalsIgnoreCase("sn")) {
                return kv[1].trim().replaceAll(/^"(.*)"$/, "\$1")
            }
        }
    }
    return null
}


/* ================= String helpers ================= */
private String withTerminator(String s){
  switch((TCP_TERMINATOR ?: "NONE").toString().toUpperCase()){
    case "CR":   return s + "\r"
    case "CRLF": return s + "\r\n"
    default:     return s
  }
}

private static String escapePrint(String s){
  return (s ?: "").replace("\r","\\r").replace("\n","\\n")
}


/* Sync children to exactly 'ch' relays + 'ch' inputs */
private void syncChildren(int ch){
    if (ch <= 0){ logWar("syncChildren: valor de canais inválido (${ch})."); return }
    state.inputcount = ch
    createRelayChildren()
    createInputChildren()
    if (settings?.autoPruneChildren != false){
        String rPrefix = netIdPrefix()
        String iPrefix = inPrefix()
        getChildDevices()?.each { dev ->
            String dni = dev.deviceNetworkId ?: ""
            if (dni.startsWith(rPrefix)){
                String idxs = dni.substring(rPrefix.length())
                Integer idx = (idxs.isInteger() ? idxs.toInteger() : null)
                if (idx == null || idx < 1 || idx > ch){
                    logInf("Removendo relay filho excedente: ${dni}")
                    deleteChildDevice(dni)
                }
            } else if (dni.startsWith(iPrefix)){
                String idxs = dni.substring(iPrefix.length())
                Integer idx = (idxs.isInteger() ? idxs.toInteger() : null)
                if (idx == null || idx < 1 || idx > ch){
                    logInf("Removendo input filho excedente: ${dni}")
                    deleteChildDevice(dni)
                }
            }
        }
    } else {
        logInf("autoPruneChildren=false: filhos excedentes foram mantidos.")
    }
}

def detectAndSyncChannels(){
    Integer ch = null
    try { ch = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
    if (!ch || ch <= 0){ logWar("Não foi possível detectar a quantidade de canais neste momento."); return }
    state.inputcount = ch
    sendEvent(name:"numberOfButtons", value: ch)
    state.lastButtons = ch
    syncChildren(ch)
    logInf("Sincronização concluída com ${ch} canais.")
}

def setChannelName(Integer ch, String name){
    if (ch == null || ch < 1 || !name?.trim()){ logWar("setChannelName: parâmetros inválidos (ch=${ch}, name=${name})"); return }
    def cd = getChildDevice(childRelayDni(ch))
    if (cd){ cd.setLabel(name.trim()); logInf("Canal ${ch} renomeado para: '${name.trim()}'") }
    else logWar("setChannelName: child para canal ${ch} não encontrado")
}


/* Parent Switch capability — controls ALL relays */
def on(){
    int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName?.contains('Switch') }?.size() ?: 0) as int
    if (ch<=0){ logWar("ON cancelado: canais desconhecidos. Use 'Detect & Sync Channels'."); return }
    masteron()
    sendEvent(name:"switch", value:"on")
}

def off(){
    int ch = (state?.inputcount ?: getChildDevices()?.findAll{ it.typeName?.contains('Switch') }?.size() ?: 0) as int
    if (ch<=0){ logWar("OFF cancelado: canais desconhecidos. Use 'Detect & Sync Channels'."); return }
    masteroff()
    sendEvent(name:"switch", value:"off")
}


/* ===== LAN Reboot via HTTP ===== */
def sendRebootLAN(){
  String ip = resolveIP()
  if (!ip){ logWar("sendRebootLAN: IP não configurado."); return }
  Map params = [ uri: "http://${ip}/reboot.cgi", headers: httpHeaders(), timeout: 5 ]
  try{
    httpGet(params) { resp ->
      int st = (resp?.status ?: 0) as int
      logInf("Reboot LAN solicitado em ${ip}: HTTP ${st}")
    }
  } catch (e){
    logWar("Falha ao solicitar reboot LAN em ${ip}: ${e}")
  }
}


/* ===== MQTT Remote Reboot ===== */
def sendReboot() {
    try { unschedule("publishPending") } catch (ignored) {}
    state.op = "publishOnce"
    state.pendingTopic   = "/molsmart/relay" + settings.molsmartSN + "/in/control"
    state.pendingPayload = PAYLOAD
    state.pendingQoS     = 0
    state.pendingRetain  = false
    state.connToken      = now()
    try { interfaces.mqtt.disconnect() } catch (ignored) {}
    connectNow(state.connToken as Long)
    runIn(CONNECT_TIMEOUT_SEC, "abortIfNoConn")
}

private void connectNow(Long token) {
    String cid = "Hubitat-${device.id}-${token}"
    String uri = "tcp://${BROKER_HOST}:${BROKER_PORT}"
    try {
        interfaces.mqtt.connect(uri, cid, USERNAME, PASSWORD)
    } catch (e) {
        log.error "Erro ao conectar MQTT: ${e}"
        cleanupAfterError("connectFail")
    }
}

def mqttClientStatus(String status) {
    String s = status?.toLowerCase() ?: ""
    if (s.contains("connection succeeded")) {
        try { unschedule("abortIfNoConn") } catch (ignored) {}
        runIn(1, "publishNowFromStatus")
    } else if (s.startsWith("error") || s.contains("disconnected")) {
        cleanupAfterError("status:${status}")
    }
}

def publishNowFromStatus() {
    if (state.op != "publishOnce") return
    String topic   = state.pendingTopic
    String payload = state.pendingPayload
    int qos        = (state.pendingQoS ?: 0) as int
    boolean retain = (state.pendingRetain ?: false) as boolean
    try {
        interfaces.mqtt.publish(topic, payload?.toString() ?: "", qos, retain)
    } catch (e) {
        log.error "Falha ao publicar em '${topic}': ${e}"
        cleanupAfterError("publishFail")
        return
    }
    state.clear()
    runIn(DISCONNECT_DELAY_SEC, "disconnectNow")
}

def disconnectNow() {
    try { interfaces.mqtt.disconnect() } catch (e) {}
}

private void abortIfNoConn() {
    if (state.op == "publishOnce") {
        log.warn "Timeout de conexão MQTT (> ${CONNECT_TIMEOUT_SEC}s). Abortando envio."
        cleanupAfterError("timeout")
    }
}

private void cleanupAfterError(String reason) {
    state.clear()
    try { interfaces.mqtt.disconnect() } catch (ignored) {}
}

def logsOff(){
    log.warn "Desabilitando logs de debug automaticamente (30 min)."
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}


private void noteBrokenPipeAndMaybeReboot(String origin){
    long nowMs = now()
    long windowMs   = 2 * 60 * 1000L
    long cooldownMs = 10 * 60 * 1000L

    if (!state.bpFirstAt) state.bpFirstAt = nowMs
    if ((nowMs - (state.bpFirstAt as Long)) > windowMs){
        state.bpFirstAt = nowMs
        state.bpCount = 0
    }

    state.bpCount = ((state.bpCount ?: 0) as Integer) + 1
    logWar("Broken pipe detectado (${state.bpCount}/5) [${origin}]")

    long lastRb = (state.lastAutoRebootAt ?: 0L) as Long
    if ((state.bpCount as Integer) >= 5 && (nowMs - lastRb) > cooldownMs){
        state.lastAutoRebootAt = nowMs
        state.bpCount = 0
        state.bpFirstAt = nowMs
        logWar("5x Broken pipe -> solicitando reboot automático do módulo (LAN)")
        try { disconnectSocket() } catch(e) { logWar("Erro ao desconectar socket antes do reboot: ${e}") }
        try { sendRebootLAN() } catch(e) { logWar("Falha ao enviar reboot LAN: ${e}") }
        runIn(30, "initialize", [overwrite: true])
    }
}
