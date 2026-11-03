/**
 * MolSmart - Relay 2/4/8/16/32CH (TCP)
 *
 * Fork TecnoSimples do driver "MolSmart - Relay 2/4/8/16/32CH (TCP)" de VH.
 * Copyright 2024 VH (original)
 * Copyright 2026 TecnoSimples Tecnologia LTDA (modificações)
 * contato@tecnosimples.com.br | (14) 99760-6885
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Versão do pacote: 3.0.0
 *
 * Versões TecnoSimples:
 *   TS-1.0.0  20/07/2026  Fork do VH 3.6
 *   TS-1.0.1  20/07/2026  Correções de reconexão e remoção de código morto
 *   TS-1.1.0  20/07/2026  Verificação assíncrona do On/Off geral
 *   TS-1.2.0  20/07/2026  Limite de canais e remoção de filhos fantasmas
 *   TS-1.2.1  08/09/2026  Correção da trava de reconexão
 *   TS-1.2.2  09/09/2026  Contadores de recepção
 *   TS-1.3.0  10/08/2026  Qualificação do sinal das entradas
 *   TS-1.3.1  10/09/2026  Tela para o integrador e robustez
 *   TS-1.3.2  21/09/2026  Parser e restauração de relés após reinício da placa
 *   TS-1.3.3  28/09/2026  Migração de filhos vindos do VH 2.7 / TS-2.8
 *   TS-1.3.4  28/09/2026  Preferência vazia volta ao padrão da tela
 *   TS-1.3.5  05/10/2026  Placa vinculada pelo número de série e IP redescoberto sozinho
 *   TS-3.0.0  29/10/2026  Versão única nos dois canais (pacote TecnoSimples e repositório do VH); sem mudança de comportamento
 */
 
import groovy.transform.Field
metadata {
definition (
name: "MolSmart - Relay 2/4/8/16/32CH (TCP)",
namespace: "TRATO",
author: "TecnoSimples Tecnologia LTDA (fork do driver de VH/TRATO)",
importUrl: "https://raw.githubusercontent.com/hhorigian/hubitat_MolSmart_Relays/main/TCP/Hubitat_TRATO_MolSmart_Relays_TCP_Driver.groovy",
singleThreaded: true
) {
capability "Initialize"
capability "Refresh"
capability "Actuator"
capability "PushableButton"
capability "HoldableButton"
capability "Switch"
command "detectAndSyncChannels"
command "setChannelName", [[name:"channel*", type:"NUMBER", description:"Número do relé"], [name:"name*", type:"STRING", description:"Novo nome do relé"]]
command "sendRebootLAN"
command "vincularPlaca"
attribute "driverVersion", "string"
attribute "lastHeld", "number"
attribute "lastPushed", "number"
attribute "lastmessage", "string"
attribute "lastRx", "number"
attribute "online", "string"
attribute "numberOfButtons", "number"
attribute "Entrada12V-1", "text"
attribute "Entrada12V-2", "text"
attribute "contactQualify", "string" 
attribute "contactQualifyStuck", "string" 
attribute "syncStatus", "string" 
attribute "vinculoPlaca", "string" 
attribute "ultimaTrocaIp", "string" 
}
preferences {
input(name: "secAjuda", type: "hidden", title: helpHtml())
input(name: "secConexao", type: "hidden", title: "<hr><b>Conexão</b>")
input(name: "ipAddress", type: "text", title: "IP da placa", required: true,
description: 'Endereço da placa MolSmart na rede. O dispositivo fica preso à placa pelo número de série: se o IP dela mudar, o driver a reencontra sozinho e atualiza este campo. A reserva de IP no roteador continua recomendada. Para trocar a placa por outra: mude o IP aqui e salve; se a placa nova ficou no mesmo IP, use o botão Vincular Placa. Ao salvar, o driver conecta e descobre quantos canais a placa tem; se os dispositivos não aparecerem, confira o IP e use Detect And Sync Channels.')
input(name: "ipPort", type: "number", title: "Porta TCP da placa", defaultValue: 502, range: "1..65535",
description: 'Padrão 502. Só mude se a placa foi configurada com outra porta.')
input(name: "hbInterval", type: "number", title: "Intervalo de verificação da placa (s)", defaultValue: 15, range: "5..120", required: true,
description: 'A cada X segundos o hub pede o estado da placa: mantém a conexão viva e atualiza relés e entradas. Padrão 15. Menor percebe queda mais rápido; maior gera menos tráfego, mas atrasa o tempo de confirmação do sensor.')
input(name: "idleTimeout", type: "number", title: "Considerar a placa desconectada após (s)", defaultValue: 60, range: "10..600", required: true,
description: 'Se a placa ficar esse tempo sem mandar nada — ou, depois de já ter mandado leituras válidas, sem mandar leitura válida — o driver reconecta. Nunca é menor que 3× o intervalo de verificação (com 15 s, no mínimo 45 s). Padrão 60.')
input(name: "reconnectMin", type: "number", title: "Reconexão: primeira espera (s)", defaultValue: 5, range: "1..120", required: true,
description: 'Depois de uma queda, o driver tenta de novo após este tempo e dobra a espera a cada falha, até o máximo abaixo. Padrão 5.')
input(name: "reconnectMax", type: "number", title: "Reconexão: espera máxima (s)", defaultValue: 60, range: "5..600", required: true,
description: 'Limite da espera entre tentativas. Padrão 60.')
input(name: "secEntradas", type: "hidden", title: "<hr><b>Entradas</b>")
input(name: "inputsNormalOpen", type: "bool", title: "Sensor em repouso aparece como ABERTO", defaultValue: true,
description: 'Ligado (padrão): o Hubitat mostra o que o fio faz — entrada fechada no GND = fechado; em repouso = aberto. Desligado: inverte. Placa que veio do driver antigo (HTTP "Inputs+Outputs"): desligue isto NO MESMO Save em que preencher o IP ou trocar o driver — senão os sensores aparecem invertidos e disparam alarme falso.')
input(name: "contactQualifyMs", type: "number", title: "Tempo de confirmação do sensor (ms)", defaultValue: 0, range: "0..10000",
description: '0 = desligado (padrão): o sensor muda na hora. Com um valor (ex.: 2000), aberto/fechado só muda se a mudança durar esse tempo — filtra piscadas elétricas de boia e falta de fase. A mudança aparece com atraso de até este tempo + o intervalo de verificação (~17 s com 2000 ms e o intervalo padrão de 15 s), e mudanças de poucos segundos podem nem aparecer: não use em porta, presença ou pulsador. Não afeta os botões.')
input(name: "secReles", type: "hidden", title: "<hr><b>Relés</b>")
input(name: "allowMasterOnOff", type: "bool", title: "Permitir ligar/desligar todos os relés pelo dispositivo principal", defaultValue: true,
description: 'Ligado (padrão): os botões On/Off deste dispositivo acionam todos os relés da placa, um de cada vez. Desligue onde isso for perigoso (bombas, exaustores): os botões On/Off continuam aparecendo, mas não fazem nada — só registram um aviso no log.')
input(name: "autoRebootBoard", type: "bool", title: "Reiniciar a placa sozinho após erros repetidos de envio", defaultValue: AUTO_REBOOT_DEFAULT,
description: AUTO_REBOOT_DESC)
input(name: "restoreChannels", type: "text", title: "Religar estes relés se a placa reiniciar", required: false,
description: 'Quando a placa reinicia (falta de energia, reinício pela rede), ela volta com todos os relés desligados. Liste os relés que o driver deve religar se estavam ligados antes, por exemplo 14,15,16 ou 1-8. Vazio (padrão): não religa nada. Não liste bombas, exaustores nem cargas que não podem ligar sozinhas.')
if (settings?.restoreChannels?.trim()) {
input(name: "restoreMaxMin", type: "number", title: "Religar só se a placa ficou fora por até (min)", defaultValue: 30, range: "1..1440",
description: 'Se a placa ficou sem comunicação por mais tempo que isso, o driver não religa, porque a programação do hub pode ter mudado nesse meio tempo, e só registra no log. Padrão 30.')
}
input(name: "secTensao", type: "hidden", title: "<hr><b>Tensão da fonte (opcional)</b>")
input(name: "enablePwrMonitor", type: "bool", title: "Ler a tensão da fonte da placa", defaultValue: false,
description: 'Desligado (padrão). Ligado: o driver lê a tensão de alimentação de 12 V informada pela placa e mostra em Entrada12V-1 e Entrada12V-2. Só em placas MolSmart com essa função — em placa sem ela, o log mostra um aviso a cada leitura.')
if (settings?.enablePwrMonitor) {
input(name: "pwrPollSec", type: "number", title: "Intervalo da leitura de tensão (s)", defaultValue: 60, range: "5..3600",
description: 'Padrão 60. Só vale com a leitura de tensão ligada.')
}
input(name: "secAvancado", type: "hidden", title: "<hr><b>Avançado — normalmente não precisa mexer</b>")
input(name: "inputsActiveLow", type: "bool", title: "Entrada acionada = fechada no GND (não altere em placas MolSmart)", defaultValue: true,
description: 'Define qual sinal elétrico conta como acionado. Desligar inverte também os botões. Para inverter só o sensor, use a opção "Sensor em repouso aparece como ABERTO".')
input(name: "buttonDebounceMs", type: "number", title: "Anti-repique dos botões (ms)", defaultValue: 120, range: "0..2000",
description: 'Ignora um segundo toque no mesmo botão que chegue antes deste tempo (padrão 120 ms), para um toque não contar duas vezes. 0 = desligado. Vale só para os eventos de botão (apertado/segurado); não afeta o sensor de contato — para ele, use o tempo de confirmação.')
input(name: "autoFindBoard", type: "bool", title: "Vincular à placa pelo número de série e reencontrá-la se o IP mudar", defaultValue: true,
description: 'Ligado (padrão): o driver só conecta depois de a placa confirmar o número de série, e, se ela mudar de IP, procura na rede e atualiza o IP sozinho. Desligado: volta a confiar só no IP — inclusive conectando em outra placa que assuma esse IP. Placas sem número de série funcionam como desligado.')
input(name: "findAfterFails", type: "number", title: "Procurar a placa na rede depois de quantas tentativas sem resposta", defaultValue: 3, range: "1..20",
description: 'Padrão 3. Vale quando a placa não responde no IP configurado.')
input(name: "findEveryMin", type: "number", title: "Intervalo mínimo entre buscas (min)", defaultValue: 30, range: "5..1440",
description: 'Padrão 30. Se a busca não achar a placa, a próxima só acontece depois deste tempo.')
input(name: "logEnable", type: "bool", title: "Registrar detalhes técnicos no log (diagnóstico)", defaultValue: false,
description: 'Desligado (padrão). Ligue só para investigar um problema: o log passa a mostrar cada mensagem trocada com a placa. Desliga sozinho 30 minutos depois de salvar. Avisos e erros aparecem sempre.')
if (state?.inputcount) {
input(name: "secPorEntrada", type: "hidden", title: SEC_POR_ENTRADA)
int maxCh = (state.inputcount as Integer)
for (int i = 1; i <= maxCh; i++) {
String num = i.toString().padLeft(2,'0')
input "ch${num}_qual", "number", title: "Entrada ${num} (ms)", required: false, range: "0..10000"
}
} else {
input(name: "secPorEntrada", type: "hidden", title: "<hr><i>Os campos por canal aparecem depois que o driver descobre quantos canais a placa tem: salve o IP, espere alguns segundos e recarregue a página.</i>")
}
}
}



@Field static java.util.Random _rng = new java.util.Random()
@Field static final String TCP_TERMINATOR = "NONE"
@Field static final String DRIVER_VERSION = "TS-3.0.0-VH"


@Field static final int MAX_CHANNELS = 32
@Field static final int GUARD_EXTRA_S = 45 

@Field static final int FIND_WINDOW = 6 
@Field static final long SCAN_LEASE_MS = 180000L 
@Field static final long SEEN_FRESH_MS = 300000L 
@Field static final String VINC_OK = "ok"
@Field static final String VINC_OFF = "desligado"
@Field static final String VINC_NOSN = "sem série"
@Field static final String VINC_NOCONF = "sem confirmação — a placa não respondeu o número de série"
@Field static final String VINC_WAIT = "aguardando a placa do IP novo responder"

@Field static final java.util.concurrent.ConcurrentHashMap SCAN_LEASE = new java.util.concurrent.ConcurrentHashMap() 
@Field static final java.util.concurrent.ConcurrentHashMap SEEN = new java.util.concurrent.ConcurrentHashMap() 
@Field static final java.util.concurrent.ConcurrentHashMap SCAN_CNT = new java.util.concurrent.ConcurrentHashMap() 
@Field static final int PRUNE_BATCH = 100 
@Field static final int RX_LEFTOVER_MAX = 1024 
@Field static final String SYNC_UNKNOWN = "canais desconhecidos — a placa não respondeu por HTTP; confira o IP"
@Field static final long CONFIRM_MIN_MS = 5000L 
@Field static final long CONFIRM_MAX_MS = 120000L 



@Field static final boolean AUTO_REBOOT_DEFAULT = false

@Field static final String AUTO_REBOOT_DESC = 'Se o envio de comandos à placa falhar 5 vezes em 2 minutos (erro "broken pipe"), o driver reinicia a placa — no máximo 1 vez a cada 10 min. Silêncio ou dados corrompidos não reiniciam a placa: nesses casos o driver só reconecta. Desligado (padrão): reiniciar o módulo desliga o relé que estava ligado, e mantém desligado após o boot. Ligue apenas se o módulo não controla carga que não pode desligar sozinha (bomba, exaustor).'
@Field static final String REBOOT_HELP = 'Reinicia a placa (não o hub). A conexão cai por alguns segundos e volta sozinha. Os relés ligados desligam no reinício e ficam desligados: em placa com carga crítica (bomba, exaustor), use em horário seguro.'



@Field static final String HELP_BODY_A = '''<p><b>Botões (aba Commands)</b> — os nomes ficam em inglês (o Hubitat não deixa traduzir)</p><ul>
<li><b>Initialize</b> — Reconecta à placa e confere os canais, criando os dispositivos que faltarem. Também roda sozinho ao salvar e ao reiniciar o hub. Trocou a placa por outra no mesmo IP? Use Vincular Placa.</li>
<li><b>Refresh</b> — Pede agora o estado de todos os relés e entradas.</li>
<li><b>On / Off</b> — Ligam ou desligam TODOS os relés da placa, um por vez (numa placa de 32, ~8 s), e conferem no fim. Com a trava das Preferences desligada, os botões continuam aparecendo, mas não fazem nada.</li>
<li><b>Push / Hold</b> — Disparam o evento de botão do dispositivo principal (botão N apertado ou segurado, marcado como digital). Servem para testar regras que usam esse botão. Não mudam o sensor da entrada (Mol Input NN), não disparam regras ligadas a ele e não mexem na placa.</li>
<li><b>Detect And Sync Channels</b> — ⚠️ Pode APAGAR dispositivos: se a placa tiver menos canais que antes, os dispositivos que sobraram são apagados, e isso quebra regras, painéis e integrações que os usam. Antes, o driver confere o número de canais pelas duas vias (HTTP e conexão TCP) e pede confirmação: o 1º clique só mostra em syncStatus o que seria apagado; espere alguns segundos, leia a lista e clique de novo em até 2 minutos para confirmar (um clique duplo rápido não confirma). Se algo não bater no 2º clique, nada é apagado e é preciso recomeçar. Durante a exclusão, novos cliques só mostram o andamento; Save ou Initialize interrompem uma exclusão em andamento, e o que já foi apagado não volta. Também cria os dispositivos que faltarem. O resultado de cada clique aparece em syncStatus. Não troca a placa vinculada, e fica recusado enquanto a placa não for confirmada (veja vinculoPlaca).</li>
<li><b>Vincular Placa</b> — Vincula este dispositivo à placa que está no IP configurado, pelo número de série dela. Use quando trocar uma placa queimada por outra no mesmo IP. Se a placa não responder o número de série, nada muda.</li>
<li><b>Send Reboot LAN</b> — '''
@Field static final String HELP_BODY_B = '''</li>
<li><b>Set Channel Name</b> — Renomeia o relé N; é o mesmo que mudar o Device label na página dele.</li>
</ul><p><b>Estados (Current States e State Variables)</b></p><ul>
<li><b>Online</b> — true = conectado à placa agora; false = sem conexão (o driver tenta reconectar sozinho).</li>
<li><b>Last Rx</b> — Hora da última leitura válida da placa, em milissegundos. Se parar de mudar: com Online = false, a placa está desconectada; com Online = true, ela está conectada, mas manda algo que o driver não reconhece (veja Rx Invalid e Rx Dropped).</li>
<li><b>Lastmessage</b> — A última leitura da placa: relés (1 = ligado) : entradas (0 = acionada, 1 = repouso, H = segurada) : nº de canais : …</li>
<li><b>Number Of Buttons</b> — Quantas entradas (botões) a placa tem.</li>
<li><b>Switch</b> — Estado do On/Off geral: on só com todos os relés ligados, off só com todos desligados; misturado, fica o último.</li>
<li><b>Pushed / Held / Last Pushed / Last Held</b> — Número da última entrada apertada ou segurada.</li>
<li><b>syncStatus</b> — Resultado da sincronização de canais: ok; aviso de dispositivos acima do número de canais da placa (congelados, sem atualização), com o que impede apagá-los; recusa com o motivo; pedido de confirmação antes de apagar; andamento e resultado da exclusão.</li>
<li><b>vinculoPlaca</b> — A que placa este dispositivo está preso. <i>ok</i> = a placa do IP configurado confirmou o número de série. <i>sem confirmação</i> = a placa não respondeu o número de série; o driver não conecta e tenta de novo sozinho (confira se a placa está ligada e na rede). <i>placa diferente neste IP</i> = quem está nesse IP é outra placa; o driver não conecta nem comanda, e procura a placa certa. Se a troca foi de propósito, use Vincular Placa. <i>procurando a placa</i> = busca na rede em andamento ou sem sucesso; repete sozinha. <i>aguardando a placa do IP novo responder</i> = você mudou o IP e a placa de lá ainda não respondeu. <i>sem série</i> = esta placa não informa número de série; o driver confia só no IP. <i>desligado</i> = recurso desligado nas Preferences (Avançado). Salvar as Preferences <b>sem mudar o IP</b> não troca a placa vinculada.</li>
<li><b>ultimaTrocaIp</b> — Última vez que o driver achou a placa em outro IP e atualizou o endereço sozinho (IP antigo → novo, com data). Se aparecer, falta a reserva de IP no roteador.</li>
<li><b>contactQualify</b> — Tempo de confirmação em uso. Ex.: 2000;7:0 = 2000 ms no geral, entrada 7 desligada.</li>
<li><b>contactQualifyStuck</b> — ok = tudo certo. Números (ex.: 1,4) = entradas cujo sinal oscila demais ou nunca se confirma; o sensor delas fica congelado no último estado confirmado. Verifique o sensor ou zere o tempo de confirmação dessas entradas.</li>
<li><b>Entrada12V-1 / -2</b> — Tensão da fonte da placa (só com a leitura de tensão ligada).</li>
<li><b>Driver Version</b> — Versão do driver instalada.</li>
<li><b>Rx Valid / Rx Invalid / Rx Dropped</b> (State Variables) — Leituras da placa que chegaram certas, corrompidas (Invalid) ou incompletas/descartadas (Dropped) desde Rx Stats Since (não zeram sozinhas). Invalid ou Dropped subindo = rede com problema, ou placa que o driver não reconhece.</li>
</ul>'''
private String helpHtml(){ "<details><summary><b>Ajuda: o que faz cada botão e estado da tela principal ▸</b></summary>${HELP_BODY_A}${REBOOT_HELP}${HELP_BODY_B}</details>" }
@Field static final String SEC_POR_ENTRADA = '<hr><b>Tempo de confirmação por entrada (opcional)</b> — preencha só as exceções. Vazio = usa o tempo geral. 0 = desliga nesta entrada (pulsador, sensor rápido). Um valor = tempo próprio desta entrada. Para renomear um relé ou uma entrada, abra o dispositivo dele e mude o Device label. Não use o botão Remove nesses dispositivos: apagar um relé ou uma entrada quebra as regras, painéis e integrações que usam aquele dispositivo.'


@Field static final int QUAL_FLIP_STUCK = 5


@Field static final String STUCK_CLEAR = "ok"
 
private void logDbg(msg){ if (settings?.logEnable) log.debug("${device.displayName ?: device.name}: ${msg}") }
private void logInf(msg){ log.info ("${device.displayName ?: device.name}: ${msg}") }
private void logWar(msg){ log.warn ("${device.displayName ?: device.name}: ${msg}") }
private void logErr(msg){ log.error("${device.displayName ?: device.name}: ${msg}") }
 
private void markRxNow() { state.lastRx = now(); sendEvent(name:"lastRx", value: state.lastRx) }
private int clampInt(v,l,h){ Math.max(l as int, Math.min(h as int, (v ?: 0) as int)) }


private int prefInt(String name, int dflt, int lo, int hi){
def v = settings?."${name}"
if (v == null || (v instanceof CharSequence && !v.toString().trim())) return dflt
try { return clampInt((v instanceof Number) ? v : new BigDecimal(v.toString().trim()), lo, hi) } 
catch (e) { logWar("Preferência ${name} inválida (${v}); usando ${dflt}"); return dflt }
}



private def childOrNull(String dni){
try { return getChildDevice(dni) }
catch (e) { logWar("filho ${dni} indisponível neste frame (${e})"); return null } 
}
private String netIdPrefix() { state.netids ?: (state.netids = device.deviceNetworkId ?: device.id.toString()) }
private String inPrefix() { state.inNetIds ?: (state.inNetIds = netIdPrefix()+"IN") }
private String resolveIP(){ String ip = settings?.ipAddress ?: settings?.device_IP_address ?: state?.ipAddress ?: state?.ipaddress; return ip?.trim() }
private Integer resolvePort(){ (settings?.ipPort as Integer) ?: (settings?.device_port as Integer) ?: 502 }
 
private boolean autoFindOn(){ settings?.autoFindBoard != false } 
private String expectedSn(){ String s = (state.NumeroSerie ?: "").toString().trim(); return s ?: null }
private boolean linkActive(){ autoFindOn() && expectedSn() != null }

private void ensureBoundIp(){ if (expectedSn() != null && !state.boundIp){ String ip = resolveIP(); if (ip) state.boundIp = ip } }
private void publishVinculo(String v){ if (state.vinc != v){ state.vinc = v; sendEvent(name: "vinculoPlaca", value: v) } }

private String boardCheck(boolean ligado, String esperada, boolean pendente, String lida){
if (!ligado) return "SKIP"
if (esperada == null) return (lida != null) ? "MATCH" : "SKIP" 
if (pendente) return (lida != null) ? "MATCH" : "UNKNOWN" 
if (lida == null) return "UNKNOWN"
return (lida == esperada) ? "MATCH" : "MISMATCH"
}

private boolean applyBoardCheck(String lida){
ensureBoundIp()
String ip = resolveIP()
String esp = expectedSn()
boolean pend = (esp != null) && state.boundIp && (state.boundIp != ip) 
switch (boardCheck(autoFindOn(), esp, pend, lida)){
case "SKIP":
publishVinculo(autoFindOn() ? VINC_NOSN : VINC_OFF)
return true
case "MATCH":
if (esp == null || pend){
if (esp != lida) logInf("Placa vinculada: série ${lida}" + (esp ? " (antes ${esp})" : ""))
state.NumeroSerie = lida
}
state.boundIp = ip
state.idMiss = 0
cancelScan()
publishVinculo(VINC_OK)
return true
case "MISMATCH":
String dif = "placa diferente neste IP (série ${lida}; esperada ${esp}) — use Vincular Placa para vincular a esta".toString()
if (state.vinc != dif) logWar("A placa em ${ip} tem a série ${lida}; este dispositivo está vinculado à série ${esp}. Não conecto. Procurando a placa certa; para vincular a esta, use Vincular Placa.") 
publishVinculo(dif)
maybeStartScan(true)
return false
default: 
state.idMiss = ((state.idMiss ?: 0) as int) + 1
if (pend){ publishVinculo(VINC_WAIT); return false } 
if (!(state.vinc ?: "").startsWith("placa diferente")) publishVinculo(vinculoBusca() ?: VINC_NOCONF)
maybeStartScan(false)
return false
}
}


private boolean linkBlocked(String oque, boolean avisar = true){
if (!linkActive()) return false
String v = state.vinc
if (v == null || v == VINC_OK) return false
if (avisar) logWar("${oque} recusado: a placa deste IP ainda não foi confirmada (vinculoPlaca = ${v})")
return true
}
 
private String vinculoBusca(){
String esp = expectedSn()
if (state.scan) return "procurando a placa (série ${esp})…".toString()
if (state.scanMiss) return "procurando a placa (série ${esp}) — última busca ${state.scanMiss}, não encontrada".toString()
return null
}
private void releaseLease(){
String me = device.id.toString()
Map l = SCAN_LEASE.get("l") as Map
if (l != null && l.dev == me) SCAN_LEASE.remove("l", l)
}
private void cancelScan(){
if (state.scan){
boolean mini = ((state.scan as Map)?.mini == true)
releaseLease(); SCAN_CNT.remove(device.id.toString()); state.remove("scan"); unschedule("scanTick")
if (!mini) state.remove("lastScanAt") 
}
state.remove("scanMiss")
}
private void scanEnd(boolean achou){
boolean mini = ((state.scan as Map)?.mini == true)
releaseLease(); SCAN_CNT.remove(device.id.toString()); state.remove("scan"); unschedule("scanTick")
if (!achou && mini){ String e = expectedSn(); if (e) SEEN.remove(e); return } 
if (!achou){
state.scanMiss = new Date().format("HH:mm", location.timeZone)
logWar("Busca terminada: a placa (série ${expectedSn()}) não foi encontrada na sub-rede. Nova busca em ${prefInt('findEveryMin', 30, 5, 1440)} min.")
if (!(state.vinc ?: "").startsWith("placa diferente")) publishVinculo(vinculoBusca())
}
}

private void maybeStartScan(boolean agora){
if (state.scan) return
if (!agora && ((state.idMiss ?: 0) as int) < prefInt("findAfterFails", 3, 1, 20)) return
String esp = expectedSn(); String ip = resolveIP()
if (!esp || !ip) return


Map seen = SEEN.get(esp) as Map
if (seen != null && (now() - (seen.at as Long)) < SEEN_FRESH_MS && (seen.ip as String) != ip){ probeCandidate(seen.ip as String); return }
long every = prefInt("findEveryMin", 30, 5, 1440) * 60000L
if (state.lastScanAt && (now() - (state.lastScanAt as Long)) < every) return
if (!(ip ==~ /\d{1,3}(\.\d{1,3}){3}/)){ logWar("Busca da placa: o IP configurado (${ip}) não é um IPv4 — sem busca."); return }
String me = device.id.toString()

Map lease = SCAN_LEASE.get("l") as Map
Map novo = [dev: me, at: now()]
boolean got
if (lease == null) got = (SCAN_LEASE.putIfAbsent("l", novo) == null)
else if ((now() - (lease.at as Long)) >= SCAN_LEASE_MS || lease.dev == me) got = SCAN_LEASE.replace("l", lease, novo)
else got = false
if (!got) return 
int id = ((state.scanSeq ?: 0) as int) + 1
state.scanSeq = id
state.lastScanAt = now()
state.remove("scanMiss")
state.scan = [id: id, base: ip.tokenize(".").take(3).join("."), next: 1]
java.util.concurrent.ConcurrentHashMap c = new java.util.concurrent.ConcurrentHashMap()
c.putAll([id: id, sent: 0, done: 0, last: now()])
SCAN_CNT.put(me, c)
logWar("Procurando a placa (série ${esp}) na sub-rede ${state.scan.base}.x…")
if (!(state.vinc ?: "").startsWith("placa diferente")) publishVinculo(vinculoBusca())
runIn(_rng.nextInt(20) + 1, "scanTick")
}



private void probeCandidate(String cand){
int id = ((state.scanSeq ?: 0) as int) + 1
state.scanSeq = id
state.scan = [id: id, base: cand.tokenize(".").take(3).join("."), next: 255, mini: true]
java.util.concurrent.ConcurrentHashMap c = new java.util.concurrent.ConcurrentHashMap()
c.putAll([id: id, sent: 1, done: 0, last: now()])
SCAN_CNT.put(device.id.toString(), c)
try { asynchttpGet("scanCallback", [uri: "http://${cand}/get/sn.cgi".toString(), timeout: 2], [id: id, ip: cand]) }
catch (e) { c.put("done", 1) }
runIn(1, "scanTick")
}
def scanTick(){
Map sc = state.scan as Map
if (!sc) return
Map c = SCAN_CNT.get(device.id.toString()) as Map
if (c == null || (c.id as Integer) != (sc.id as Integer)){ scanEnd(false); return } 
if ((now() - (c.last as Long)) > 30000L){ scanEnd(false); return } 
int n = sc.next as int
int infl = (c.sent as int) - (c.done as int)
while (infl < FIND_WINDOW && n <= 254){
String alvo = "${sc.base}.${n}".toString()
try {
asynchttpGet("scanCallback", [uri: "http://${alvo}/get/sn.cgi".toString(), timeout: 2], [id: sc.id, ip: alvo])
c.merge("sent", 1) { a, b -> a + b }; c.put("last", now()); infl++; n++
} catch (e) {
logDbg("busca: envio a ${alvo} falhou (${e}); repete no próximo segundo")
break
}
}
sc.next = n
state.scan = sc

if (n <= 254 || (infl > 0 && (now() - (c.last as Long)) < 10000L)) runIn(1, "scanTick")
else scanEnd(false)
}
def scanCallback(resp, Map data){
String lida = snFromResponse(resp)
if (lida) SEEN.put(lida, [ip: data?.ip, at: now()]) 
Map c = SCAN_CNT.get(device.id.toString()) as Map
if (c != null && (c.id as Integer) == (data?.id as Integer)) c.merge("done", 1) { a, b -> a + b }
Map sc = state.scan as Map
if (!sc || (sc.id as Integer) != (data?.id as Integer)) return 
if (!lida || lida != expectedSn()) return
if ((data.ip as String) == resolveIP()){ scanEnd(true); return } 
boardFound(data.ip as String)
}

private void boardFound(String ipNovo){
String ipAntigo = resolveIP()
int port = resolvePort() as int
scanEnd(true)
disconnectSocket() 
state.idMiss = 0
state.remove("verify"); unschedule("verifyTimeout")
device.updateSetting("ipAddress", [value: ipNovo, type: "text"])
state.boundIp = ipNovo
if (state.frameEp == "${ipAntigo}:${port}".toString()) state.frameEp = "${ipNovo}:${port}".toString() 
state.conIp = ipNovo
String quando = new Date().format("dd/MM/yyyy HH:mm", location.timeZone)
sendEvent(name: "ultimaTrocaIp", value: "${ipAntigo} → ${ipNovo} em ${quando}".toString())
logWar("Placa (série ${expectedSn()}) encontrada em ${ipNovo}; antes ${ipAntigo}. IP atualizado — confira a reserva de DHCP.")
unschedule("doReconnect")
state.reconnecting = true 
runIn(1, "doReconnect")
runIn(1 + GUARD_EXTRA_S, "reconnectGuard", [overwrite:true]) 
}

def vincularPlaca(){
String ip = resolveIP()
if (!ip){ logWar("Vincular Placa: IP não configurado."); return }
String lida = null
try {
httpGet([ uri: "http://${ip}/get/sn.cgi", headers: httpHeaders(), timeout: 5 ]) { resp ->
def d = resp.data
lida = (d instanceof Map) ? (d.sn != null ? d.sn.toString().trim() : null) : extractSnFlexible(d?.toString())
}
} catch (e) { logDbg("Vincular Placa: sn.cgi falhou: ${e}") }
if (!lida){
logWar("Vincular Placa: a placa em ${ip} não respondeu o número de série — nada mudou.")
return
}
String antes = expectedSn()
state.NumeroSerie = lida
state.boundIp = ip
logInf("Vincular Placa: dispositivo vinculado à série ${lida} em ${ip}" + ((antes && antes != lida) ? " (antes ${antes})" : ""))
initialize()
}
 


private Integer tcpChannelCount(){
def v = state.lastRBitsCh
if (v == null) return null
int n = v as int
return (n >= 1 && n <= MAX_CHANNELS) ? n : null
}
private void forgetFrameCount(){ state.remove("lastRBitsCh"); state.remove("lastRBits") }


private long watchdogBase(){
long c = (state.connectedAt ?: 0L) as Long
long rx = (state.lastRx ?: c) as Long
long any = (state.lastAnyRx ?: c) as Long
long b = (state.rxFormatSeen == true) ? rx : Math.max(rx, any)
return Math.max(c, b)
}

private void initRxStats(){
if (state.rxValid == null) state.rxValid = 0
if (state.rxInvalid == null) state.rxInvalid = 0
if (state.rxDropped == null) state.rxDropped = 0
if (state.rxStatsSince == null) state.rxStatsSince = new Date().format("yyyy-MM-dd HH:mm:ss")
state.remove("hbSent"); state.remove("rxBufMax") 
}
private void bumpRx(String k){
if (state.rxStatsSince == null) initRxStats()
state[k] = ((state[k] ?: 0) as long) + 1
}
 
private void publishSyncStatus(String v){
if ((device.currentValue("syncStatus") ?: "") != v) sendEvent(name: "syncStatus", value: v)
}


private List excessChildren(int n){
String rPrefix = netIdPrefix()
String iPrefix = inPrefix()
String legacyIPfx = "${device.id}-Input-"
String legacySPfx = "${device.id}-Switch-"
List out = []
for (def dev : (getChildDevices()?.collect{ it } ?: [])){
String dni = dev.deviceNetworkId ?: ""
Integer idx = null
boolean excess = false
if (dni.startsWith(iPrefix)){
idx = suffixIdx(dni, iPrefix)
excess = (idx == null || idx < 1 || idx > n)
} else if (dni.startsWith(rPrefix)){
idx = suffixIdx(dni, rPrefix)
excess = (idx == null || idx < 1 || idx > n)
} else if (dni.startsWith(legacyIPfx) || dni.startsWith(legacySPfx)){
idx = suffixIdx(dni, dni.startsWith(legacyIPfx) ? legacyIPfx : legacySPfx)
excess = (idx != null && idx > n)
}
if (excess) out << [dni: dni, label: (dev.displayName ?: dni).toString(), idx: idx]
}
return out.sort{ (it.idx ?: 0) as int }
}
private String frozenMsg(Integer h, Integer t, List ex){
List idxs = ex.findAll{ it.idx != null }.collect{ it.idx as int }
String ab = !idxs ? "?" : (idxs.min() == idxs.max() ? "${idxs.min()}" : "${idxs.min()}–${idxs.max()}")
if (t == null) return "atenção: dispositivos ${ab} estão congelados (acima dos ${h} canais informados por HTTP); ainda sem leitura válida da placa nesta conexão"
if (t == h) return "atenção: dispositivos ${ab} estão congelados (a placa tem ${t} canais) — use Detect And Sync Channels para apagá-los (pede confirmação)"
return "atenção: dispositivos ${ab} estão congelados; a placa informa ${h} canais por HTTP e ${t} pela conexão, e nada será apagado enquanto divergirem — confira a placa"
}



private void scanFrozen(String origem){

Map plan = state.prunePlan as Map
if (plan?.status == "aguardando" && (now() - ((plan.at ?: 0L) as Long)) > CONFIRM_MAX_MS){ state.remove("prunePlan"); plan = null }
if (plan || state.httpUnknown) return
Integer h = state.inputcount as Integer
if (!h) return
Integer t = tcpChannelCount()
int n = (t ?: h) as int
List ex = excessChildren(n)
logDbg("varredura de congelados (${origem}): N=${n}, ${ex.size()} acima")
publishSyncStatus(ex ? frozenMsg(h, t, ex) : "ok — ${n} canais")
}
 



private static List<Integer> parseChannelList(String txt, int max, List bad = null){
List<Integer> out = []
for (String s : (txt ?: "").trim().split(/[,;\s]+/)){
if (!s) continue
if (!(s ==~ /^\d{1,3}(-\d{1,3})?$/)){ bad?.add(s); continue }
String[] ab = s.split('-')
int a = ab[0] as int
int b = (ab.length > 1) ? (ab[1] as int) : a
if (a < 1 || b > max || a > b){ bad?.add(s); continue }
for (int n = a; n <= b; n++) out << n
}
return out.unique().sort()
}



private void noteRelayCmd(int n, String v){
if (state.lastCmd == null) state.lastCmd = [:]
state.lastCmd[n.toString()] = [v: v, t: now()]
}

private boolean wantOn(int n, Map cmds, Map kids, Long lastRx){
Map c = cmds[n.toString()] as Map
if (c != null && ((c.t ?: 0L) as long) > (lastRx ?: 0L)) return c.v == "on"
return kids[n] == "on"
}


private void checkBoardRestart(String rBits, int ch){
if (rBits.contains('1')) return 
if (state.sameEndpoint != true){ logDbg("reinício: outra placa (IP/porta mudou ou 1ª conexão) — nada a avaliar"); return }
Long lastRx = state.lastRx as Long
Map<Integer, String> kids = [:]
for (def cd : (getChildDevices() ?: [])){
Integer n = relayIndexFromDni(cd.deviceNetworkId)
if (n != null) kids[n] = cd.currentValue("switch") as String
}
List<Integer> wereOn = kids.findAll{ it.value == "on" }.collect{ it.key as Integer }.sort()
if (!wereOn) return 
if (kids.size() != ch){ logDbg("reinício: ${kids.size()} filhos relé para ${ch} canais — não é a mesma placa"); return }
if (lastRx == null){ logDbg("reinício: sem frame anterior — nada a avaliar"); return }
long gapMs = now() - lastRx
String base = "Placa voltou com todos os relés desligados após ${Math.round(gapMs / 1000d)} s sem comunicação (antes: ${wereOn.join(',')} ligados) — provável reinício da placa"
Map cmds = (state.lastCmd ?: [:]) as Map
List<Integer> alvo = parseChannelList(settings?.restoreChannels as String, MAX_CHANNELS).findAll{ it <= ch && wantOn(it, cmds, kids, lastRx) }
if (!alvo){ logWar(base); return }
int maxMin = prefInt("restoreMaxMin", 30, 1, 1440)
if (gapMs > maxMin * 60000L){

logWar("Placa voltou com todos os relés desligados após ${(long) Math.ceil(gapMs / 60000d)} min sem comunicação (limite ${maxMin}) — não religado: ${alvo.join(',')}")
return
}
logWar("${base} — religando: ${alvo.join(',')}")
runInMillis(100, "doRestore", [data: [chs: alvo, gen: state.connectedAt, at: now()], overwrite: true])
}


def doRestore(Map data){
if (!sameConnection(data)){ logDbg("restauração abortada: a conexão mudou"); return }
Map cmds = (state.lastCmd ?: [:]) as Map
List<Integer> sent = []
for (def x : (data.chs ?: [])){
if (!sameConnection(data)) break
int n = x as int
if (offAfter(cmds, n, data.at)){ logDbg("restauração: relé ${n} recebeu off depois da decisão — pulado"); continue }
noteRelayCmd(n, "on")
txRaw("1${n}", "restore")
sent << n
pauseExecution(250)
}
if (!sent) return
txRaw("00", "restore-poll")
if (!sameConnection(data)) return
runIn(3, "verifyRestore", [data: [chs: sent, gen: data.gen, at: data.at], overwrite: true])
}

def verifyRestore(Map data){
if (!sameConnection(data)){ logDbg("verificação da restauração abortada: a conexão mudou"); return }
String rBits = (state.lastRBits ?: '') as String
Map cmds = (state.lastCmd ?: [:]) as Map
List<Integer> falta = (data.chs ?: []).collect{ it as int }.findAll{ int n ->
!offAfter(cmds, n, data.at) && n <= rBits.length() && rBits.charAt(n - 1) == '0'
}
if (!falta) return
logWar("Restauração: relé(s) ${falta.join(',')} continuam desligados — reenviando uma vez")
for (int n : falta){ noteRelayCmd(n, "on"); txRaw("1${n}", "restore-retry"); pauseExecution(250) }
}
private boolean sameConnection(Map data){
return state.socketOnline == true && ((state.connectedAt ?: 0L) as long) == ((data?.gen ?: 0L) as long)
}
private boolean offAfter(Map cmds, int n, at){
Map c = cmds[n.toString()] as Map
return c?.v == "off" && ((c.t ?: 0L) as long) > ((at ?: 0L) as long)
}
 


private int qualForChannel(int idx){
def ov = settings["ch${idx.toString().padLeft(2,'0')}_qual"]
if (ov == null || (ov instanceof CharSequence && !ov.toString().trim())) {
ov = settings?.contactQualifyMs
}
try {
return clampInt(ov, 0, 10000)
} catch (e) {


logWar("ch${idx} qualify inválido (${ov}); herdando o global")
try {
return prefInt("contactQualifyMs", 0, 0, 10000)
} catch (e2) {


logWar("contactQualifyMs global também inválido; usando 0")
return 0
}
}
}



private String buildContactQualifyString(){
try {
int g = prefInt("contactQualifyMs", 0, 0, 10000)
StringBuilder sb = new StringBuilder(g.toString())
int maxCh = (state?.inputcount ?: 0) as int
for (int i = 1; i <= maxCh; i++){
def ov = settings["ch${i.toString().padLeft(2,'0')}_qual"]
if (ov == null || (ov instanceof CharSequence && !ov.toString().trim())) continue
sb.append(";${i}:${clampInt(ov, 0, 10000)}")
}
return sb.toString()
} catch (e) {
logWar("buildContactQualifyString falhou: ${e}")
try {
return prefInt("contactQualifyMs", 0, 0, 10000).toString()
} catch (e2) {
return "0"
}
}
}











private String stuckAttributeValue(){
List st = ((state.qualStuck ?: []) as List).collect{ it as Integer }.sort()
return st ? st.join(",") : STUCK_CLEAR
}


private void syncStuckAttribute(){
String v = stuckAttributeValue()
if ((device.currentValue("contactQualifyStuck") ?: "") != v){
sendEvent(name: "contactQualifyStuck", value: v)
}
}




private void publishStuckAttributeUnconditional(){
sendEvent(name: "contactQualifyStuck", value: stuckAttributeValue())
}



private void reapStarvedCandidates(int hbSec){
Map cands = (state.qualCand ?: [:]) as Map
if (state.qualStuck == null) state.qualStuck = []


state.qualStuck.removeAll{ o ->
String k = "${o}".toString()
!cands.containsKey(k) && ((((state.qualFlips ?: [:])[k]) ?: 0) as int) < QUAL_FLIP_STUCK
}

if (cands){
long nowMs = now()
cands.keySet().toList().each{ String k ->
int idx = k as int
int qual = qualForChannel(idx)
if (qual <= 0){ cands.remove(k); return } 
long budget = Math.max(3L * (qual as long), 2L * (hbSec as long) * 1000L)
long age = nowMs - ((cands[k]?.ts ?: 0L) as Long)
if (age >= budget){
logWar("Input ${idx}: candidato faminto descartado após ${age}ms (orçamento=${budget}ms) — nenhum frame válido para confirmar")
cands.remove(k)
if (!state.qualStuck.any{ (it as Integer) == idx }) state.qualStuck << idx
}
}
}
state.qualCand = cands
syncStuckAttribute()
}
 
def installed(){
sendEvent(name: "driverVersion", value: DRIVER_VERSION)
device.updateSetting("logEnable", [value:"false", type:"bool"])
initialize()
}
def updated(){
unschedule(); disconnectSocket();
if (settings?.logEnable) runIn(1800, "logsOff")
sendEvent(name: "driverVersion", value: DRIVER_VERSION)

List restoreBad = []
parseChannelList(settings?.restoreChannels as String, MAX_CHANNELS, restoreBad)
if (restoreBad) logWar("Religar estes relés se a placa reiniciar: itens ignorados (${restoreBad.join(', ')}) — use números de 1 a ${MAX_CHANNELS} separados por vírgula, ou faixas como 1-8")
state.rxFormatSeen = false 
sendEvent(name: "contactQualify", value: buildContactQualifyString())







publishStuckAttributeUnconditional()
initialize()
}
def uninstalled(){ unschedule(); state.remove("verify"); cancelScan(); disconnectSocket() }
def initialize(){
logInf("Initialize")
state.syncRetry = 0 
unschedule("retrySyncChildren") 
state.reconnecting = false 
initRxStats() 
state.remove("prunePlan") 
unschedule("continuePrune") 
state.remove("verify"); unschedule("verifyTimeout") 
cancelScan() 

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
state.remove("snRead")
try { ch = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }


boolean conecta = applyBoardCheck(state.snRead as String)
state.remove("snRead")
if (!conecta){
disconnectSocket() 
scheduleReconnect("placa não confirmada")
return
}
if (!ch || ch <= 0){
logWar("Quantidade de canais desconhecida. Use o comando 'Detect & Sync Channels'.")
state.httpUnknown = true
publishSyncStatus(SYNC_UNKNOWN) 
connectSocket()
runIn(2, "queryBoardStatus", [overwrite:true])
schedulePwrPolling()
return
}
state.inputcount = ch
state.remove("httpUnknown")


sendEvent(name: "contactQualify", value: buildContactQualifyString())



publishStuckAttributeUnconditional()
sendEvent(name:"numberOfButtons", value: ch)
state.lastButtons = ch

try { syncChildren(ch) } catch (e) { scheduleSyncRetry(e) } 
connectSocket()
try { scanFrozen("initialize") } catch (e) { logWar("varredura de congelados falhou: ${e}") }
runIn(2, "queryBoardStatus", [overwrite:true])
schedulePwrPolling()
if (settings?.logEnable) runIn(1800, "logsOff", [overwrite:true])
}
 
private void schedulePwrPolling(){
try{ unschedule('pwrPollTick') } catch(e){}
Integer sec = prefInt("pwrPollSec", 60, 5, 3600)
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
Integer sec = prefInt("pwrPollSec", 60, 5, 3600)
if (sec < 5) sec = 5
runIn(sec, 'pwrPollTick', [overwrite:true])
}
private void doAsyncPwrQuery(){
if (linkBlocked("Leitura de tensão", false)) return 
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
Integer cnt = (js?.cnt instanceof Number) ? (js.cnt as Integer)
: (js?.cnt?.toString()?.isInteger() ? js.cnt.toString().toInteger() : null)
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
 
private void connectSocket(){
String ip = resolveIP(); Integer port = resolvePort()
if (!ip){
logErr("IP não configurado nas Preferences.")
sendEvent(name:"online", value:"false"); state.socketOnline = false
unschedule("heartbeat"); unschedule("connectionCheck")
return
}
forgetFrameCount() 
try { interfaces.rawSocket.close() } catch(ignored){} 
try{
interfaces.rawSocket.connect(ip, port as int)
state.socketOnline = true



Map pendingCand = (state.qualCand ?: [:]) as Map
if (pendingCand){
List discardedIdx = pendingCand.keySet().collect{ it as Integer }.sort()
logWar("Conexão/reconexão descartou candidato(s) de qualificação sem nunca terem sido confirmados: canal(is) ${discardedIdx.join(',')}")
if (state.qualStuck == null) state.qualStuck = []
discardedIdx.each{ int idx -> if (!state.qualStuck.any{ (it as Integer) == idx }) state.qualStuck << idx }
syncStuckAttribute()
}
state.qualCand = [:] 




state.rxBuf = ''
sendEvent(name:"online", value:"true")


if (state.conIp != null && (state.conIp != ip || ((state.conPort ?: 0) as int) != (port as int))){
logInf("Placa nova em ${ip}:${port} (antes ${state.conIp}:${state.conPort}) — formato a reconhecer")
state.rxFormatSeen = false
state.remove("prunePlan") 
}



state.sameEndpoint = (state.frameEp == "${ip}:${port as int}".toString())
if (state.sameEndpoint != true) state.remove("lastCmd")
state.conIp = ip; state.conPort = port
state.connectedAt = now() 
state.rxCapWarned = false 
state.frozenScanDone = false 
state.restoreChecked = false 
logInf("Socket conectado a ${ip}:${port}")
state.reconnectAttempt = 0


unschedule("doReconnect"); unschedule("reconnectGuard"); state.reconnecting = false
scheduleHeartbeat(); scheduleWatchdog(); scheduleReconcile(); runIn(1, "heartbeat", [overwrite:true])
} catch(e){
logErr("Falha ao conectar em ${ip}:${port}: ${e}")
scheduleReconnect("connect error")
}
}




private void markSocketOffline(){ state.socketOnline=false; forgetFrameCount(); unschedule("heartbeat"); unschedule("doRestore"); unschedule("verifyRestore"); sendEvent(name:"online", value:"false") } 
private void disconnectSocket(){ markSocketOffline(); try { interfaces.rawSocket.close() } catch(ignored){} }
private void scheduleHeartbeat(){ int s = prefInt("hbInterval", 15, 5, 120); runIn(s, "heartbeat", [overwrite:true]) }
def heartbeat(){ txRaw("00", "HB"); scheduleHeartbeat() }
private void scheduleWatchdog(){ runIn(5, "connectionCheck", [overwrite:true]) }
private void scheduleReconcile(){ runIn(60, "reconcileRelayStates", [overwrite:true]) }
def reconcileRelayStates(){

if (state.socketOnline != true){ scheduleReconcile(); return }
String rBits = (state.lastRBits ?: '') as String
int ch = (state.lastRBitsCh ?: 0) as int
if (!rBits || ch <= 0 || rBits.length() != ch){ scheduleReconcile(); return }
int fixed = 0
for (int i = 0; i < ch; i++){
def cd = getChildDevice(childRelayDni(i+1))
if (!cd) continue
String expected = (rBits.charAt(i) == '1') ? "on" : "off"
if ((cd.currentValue("switch") ?: "unknown") != expected){
logWar("Relay ${i+1}: estado divergente detectado (atual=${cd.currentValue('switch')}, esperado=${expected}) — corrigindo")
cd.parse([[name:"switch", value: expected]])
fixed++
}
}
if (fixed) logInf("reconcileRelayStates: ${fixed} relay(s) corrigido(s)")


String parentExpected = !rBits.contains('0') ? "on" : (!rBits.contains('1') ? "off" : null)
if (parentExpected && (device.currentValue("switch") ?: "unknown") != parentExpected){
sendEvent(name:"switch", value: parentExpected)
}
scheduleReconcile()
}
def connectionCheck(){
int hb = prefInt("hbInterval", 15, 5, 120)
int userIdle = prefInt("idleTimeout", 60, 10, 600)
int effIdle = Math.max(userIdle, hb*3)




try { reapStarvedCandidates(hb) } catch(e){ logWar("reap de candidatos falhou: ${e}") }


if (state.connectedAt == null){ state.connectedAt = now(); scheduleWatchdog(); return }
long idle = now() - watchdogBase()
if (idle >= effIdle*1000L){
String oque = (state.rxFormatSeen == true) ? "leitura válida" : "dado nenhum"
logErr("Sem ${oque} da placa há ${String.format('%.1f', idle/1000.0)}s (limite=${effIdle}s). Reconnect...")
scheduleReconnect("idle timeout"); return
}
scheduleWatchdog()
}
private void scheduleReconnect(String reason, boolean closeSocket = true){
if (state.reconnecting == true) return
state.reconnecting = true
int attempt = ((state.reconnectAttempt ?: 0) as int) + 1
state.reconnectAttempt = attempt
int minS = prefInt("reconnectMin", 5, 1, 120)
int maxS = prefInt("reconnectMax", 60, 5, 600)
int delay = Math.min(maxS, (int)Math.pow(2D, Math.min(6,attempt-1)) * minS) + _rng.nextInt(Math.max(1, minS))
logWar("Reconectar (#${attempt}) em ~${delay}s (${reason})")
runIn(delay, "doReconnect", [overwrite:true])
runIn(delay + GUARD_EXTRA_S, "reconnectGuard", [overwrite:true]) 
if (closeSocket) disconnectSocket() else markSocketOffline() 
}





def reconnectGuard(){
if (state.socketOnline == true || !resolveIP()) return
Map v = state.verify as Map
if (v != null && (now() - ((v.at ?: 0L) as Long)) < 30000L){ 
runIn(GUARD_EXTRA_S, "reconnectGuard", [overwrite:true]); return 
}
logWar("Reconexão parada: a tentativa agendada não aconteceu. Retomando sozinho.")
state.reconnecting = false
scheduleReconnect("reconexão parada")
}
def doReconnect(){ state.reconnecting = false; verifyThenConnect() }

private void verifyThenConnect(){
ensureBoundIp() 
String ip = resolveIP()
if (!ip || !linkActive()){ connectSocket(); return } 
Map v = state.verify as Map
if (v != null && (now() - ((v.at ?: 0L) as Long)) < 30000L) return 
int gen = ((state.verifyGen ?: 0) as int) + 1
state.verifyGen = gen
state.verify = [gen: gen, ip: ip, at: now()]
runIn(15, "verifyTimeout", [overwrite: true, data: [gen: gen]]) 
try {
asynchttpGet("verifyCallback", [uri: "http://${ip}/get/sn.cgi".toString(), timeout: 5], [gen: gen, ip: ip])
} catch (e) {
state.remove("verify"); unschedule("verifyTimeout")
logWar("Conferência da série não pôde ser enviada: ${e}")
scheduleReconnect("conferência não pôde ser enviada")
}
}
def verifyCallback(resp, Map data){
Map v = state.verify as Map

if (v == null || (v.gen as Integer) != (data?.gen as Integer) || (data?.ip as String) != resolveIP()) return
state.remove("verify"); unschedule("verifyTimeout")
if (applyBoardCheck(snFromResponse(resp))) connectSocket()
else scheduleReconnect("placa não confirmada")
}
def verifyTimeout(Map data){ 
Map v = state.verify as Map
if (v == null || (v.gen as Integer) != (data?.gen as Integer)) return
state.remove("verify")
scheduleReconnect("conferência sem resposta")
}

private String snFromResponse(resp){
try {
if ((resp?.status as Integer) != 200) return null
def d = resp.getData()
if (d instanceof Map) return (d.sn != null) ? d.sn.toString().trim() : null
return extractSnFlexible(d?.toString())
} catch (e) { return null }
}
def socketStatus(String message){
logWar("socketStatus: ${message}")
boolean rebooting = false
if ((message ?: "").toLowerCase().contains("broken pipe")){
rebooting = noteBrokenPipeAndMaybeReboot("socketStatus")
}

if (!rebooting && !(message?.toLowerCase()?.contains("normal") ?: false)) scheduleReconnect("socketStatus", false)
}
 
def parse(String payload){
if (!payload) return
state.lastAnyRx = now() 

if (payload ==~ /(?i)^[0-9A-F]+$/ && (payload.length() % 2 == 0)){
try { payload = new String(hubitat.helper.HexUtils.hexStringToByteArray(payload)) } catch(e){}
}
payload = payload.replaceAll(/[\r\n]+/, ' ')

String rxBuf = ((state.rxBuf ?: '') as String) + payload
logDbg("RX chunk: ${payload.trim()} (buffer=${rxBuf.length()})")
while(true){
rxBuf = rxBuf.replaceFirst(/^\s+/, '')
if (!rxBuf) break
int sp = rxBuf.indexOf(' ')
String token = (sp >= 0) ? rxBuf.substring(0, sp) : rxBuf

int colons = token.findAll(':').size()
if (colons < 3 && sp < 0) break
if (colons < 3){ logDbg("Descartando resto sem frame: ${token}"); bumpRx("rxDropped"); rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''; continue }


String[] parts = token.split(':', -1)
if (parts.length != 4 && parts.length != 5){
if (sp < 0) break
logDbg("Descartando token inválido (partes=${parts.length}): ${token}")
bumpRx("rxInvalid")
rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''
continue
}
String rBits = parts[0]
String iBits = parts[1]
Integer ch = null
try { ch = parts[2].toInteger() } catch(ignored){}
String rMask = (parts.length >= 4) ? parts[3] : null
String iMask = (parts.length == 5) ? parts[4] : null

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
bumpRx("rxInvalid")
rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''
continue
}
rxBuf = (sp >= 0) ? rxBuf.substring(sp+1) : ''


if (state.restoreChecked != true){
state.restoreChecked = true
try { checkBoardRestart(rBits, ch as int) } catch (e) { logWar("checagem de reinício da placa falhou: ${e}") }
state.frameEp = "${state.conIp}:${(state.conPort ?: 0) as int}".toString() 
}

bumpRx("rxValid")
markRxNow()
if (state.rxFormatSeen != true) state.rxFormatSeen = true

for (int i=0; i<ch; i++){
String swDni = childRelayDni(i+1)
def cd = childOrNull(swDni)
if (cd) cd.parse([[name:"switch", value: (rBits.charAt(i)=='1') ? "on" : "off"]])
}

state.lastRBits = rBits
state.lastRBitsCh = ch

handleInputEventsAndContacts(iBits, iMask, ch)
sendEvent(name:"lastmessage", value: token)
if ((state?.lastButtons as Integer) != ch){ sendEvent(name:"numberOfButtons", value: ch); state.lastButtons = ch }

if (state.frozenScanDone != true){
try { scanFrozen("1º frame") } catch (e) { logWar("varredura de congelados falhou: ${e}") }
state.frozenScanDone = true
}
}


if (rxBuf.length() > RX_LEFTOVER_MAX){
bumpRx("rxDropped")
if (state.rxCapWarned != true){
logWar("Sobra do buffer de recepção passou de ${RX_LEFTOVER_MAX} caracteres sem formar frame — descartada (a placa manda dados que o driver não reconhece; ver Rx Dropped)")
state.rxCapWarned = true
}
rxBuf = ''
}
state.rxBuf = rxBuf
}
 
private void handleInputEventsAndContacts(String iBits, String iMask, int chan){
if (!iBits || chan <= 0) return
String prev = state.prevInputBits ?: ("1" * chan)
if (prev.length() != iBits.length()) prev = ("1" * iBits.length())
int debounce = prefInt("buttonDebounceMs", 120, 0, 2000)
boolean activeLow = (settings?.inputsActiveLow != false)
boolean normalOpen = (settings?.inputsNormalOpen != false)
long nowMs = now()
String ts = new Date().format("yyyy-MM-dd HH:mm:ss")
if (state.btnLastMs == null) state.btnLastMs = [:]
if (state.btnLastHeldMs == null) state.btnLastHeldMs = [:]
if (state.btnPendingPush == null) state.btnPendingPush = [:]
if (state.btnHeldCycle == null) state.btnHeldCycle = [:]


String qualBitsCur = state.qualBits
boolean boot = (qualBitsCur == null || qualBitsCur.length() != chan)
if (boot){
qualBitsCur = "0" * chan 
state.qualCand = [:] 
state.qualStuck = []
state.qualFlips = [:]
state.qualFlipTs = [:]
logInf("Qualificação: bootstrap para ${chan} canais")
}
if (state.qualCand == null) state.qualCand = [:]
if (state.qualStuck == null) state.qualStuck = []
if (state.qualFlips == null) state.qualFlips = [:]
if (state.qualFlipTs == null) state.qualFlipTs = [:]
StringBuilder qualBits = new StringBuilder(qualBitsCur)
for (int i=0; i<chan; i++){
char before = (i < prev.length()) ? prev.charAt(i) : '1'
char after = iBits.charAt(i)
char m = (iMask && i < iMask.length()) ? iMask.charAt(i) : '0'
int idx = i+1
boolean wasPressed = activeLow ? (before=='0' || before=='H') : (before=='1' || before=='H')
boolean nowPressed = activeLow ? (after =='0' || after =='H') : (after =='1' || after =='H')
boolean pressEdge = (!wasPressed && nowPressed)
boolean releaseEdge = ( wasPressed && !nowPressed)
boolean holdSeenFrm = (after=='H') || (m=='H')

String ck = "${idx}".toString()
int qual = qualForChannel(idx)
boolean qualPressed
boolean publicou = false
if (boot){
qualPressed = nowPressed 
qualBits.setCharAt(i, nowPressed ? ((char)'1') : ((char)'0'))
state.qualCand.remove(ck)
publicou = true
} else {
boolean efetivo = (qualBits.charAt(i) == ((char)'1')) 
if (qual == 0){
qualPressed = nowPressed 
qualBits.setCharAt(i, nowPressed ? ((char)'1') : ((char)'0'))
state.qualCand.remove(ck)
publicou = true
} else if (nowPressed == efetivo){
state.qualCand.remove(ck) 
qualPressed = efetivo
} else {
def cand = state.qualCand[ck]
if (cand == null || ((cand.v as boolean) != nowPressed)){
state.qualCand[ck] = [v: nowPressed, ts: nowMs] 


long lastFlipTs = ((state.qualFlipTs[ck]) ?: 0L) as Long
if (lastFlipTs <= 0L || (nowMs - lastFlipTs) > (10L * qual)){
state.qualFlips[ck] = 1 
} else {
state.qualFlips[ck] = (((state.qualFlips[ck]) ?: 0) as int) + 1 
}
state.qualFlipTs[ck] = nowMs
qualPressed = efetivo 
} else {
long candTs = (cand.ts ?: 0L) as Long
if ((nowMs - candTs) >= qual){
qualBits.setCharAt(i, nowPressed ? ((char)'1') : ((char)'0')) 
state.qualCand.remove(ck)
qualPressed = nowPressed
publicou = true
logDbg("Input ${idx}: mudança qualificada após ${nowMs - candTs}ms (qual=${qual}ms)")
} else {
qualPressed = efetivo 
}
}
}
}

String contactState = qualPressed ? (normalOpen ? "closed" : "open")
: (normalOpen ? "open" : "closed")
String inDni = childInputDni(idx)
def inChild = childOrNull(inDni)
if (inChild){
if (inChild.currentValue("contact") != contactState){
inChild.sendEvent(name:"contact", value: contactState)
}
}
if (pressEdge){
state.btnPendingPush["${idx}"] = true
state.btnHeldCycle ["${idx}"] = false
}

boolean wasHeldCycle = (state.btnHeldCycle["${idx}"] ?: false) as boolean
if (holdSeenFrm){
long lastHeld = (state.btnLastHeldMs["${idx}"] ?: 0L) as Long
if (!wasHeldCycle && (debounce <= 0 || (nowMs - lastHeld) >= debounce)){
sendEvent(name:"held", value: idx, isStateChange:true, type:"physical", descriptionText: "Input ${idx} held")
sendEvent(name:"lastHeld", value: idx)
state.btnLastHeldMs["${idx}"] = nowMs
}
state.btnHeldCycle["${idx}"] = true
state.btnPendingPush["${idx}"] = false
}
if (releaseEdge){
boolean pending = (state.btnPendingPush["${idx}"] ?: false) as boolean
boolean heldcyc = (state.btnHeldCycle ["${idx}"] ?: false) as boolean
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
state.btnHeldCycle ["${idx}"] = false
}
if (inChild){



if (state.inPub == null) state.inPub = [:]
String kid = "${inChild.id}".toString()
Map pub = (state.inPub[kid] ?: [:]) as Map
String holdStatus = ((state.btnHeldCycle["${idx}"] ?: false) || holdSeenFrm) ? "active" : "inactive"
if (pub.hold != holdStatus){
inChild.sendEvent(name:"holdStatus", value: holdStatus)
pub.hold = holdStatus
}
String combined = (contactState == "closed") ? (holdStatus=="active"?"held-closed":"closed")
: (holdStatus=="active"?"held-open":"open")
if (pub.comb != combined){
inChild.sendEvent(name:"combinedStatus", value: combined)
pub.comb = combined
}
state.inPub[kid] = pub
String tag
if (holdSeenFrm && !wasHeldCycle) tag = "(Hold)"
else if (pressEdge) tag = "(Press)"
else if (releaseEdge) tag = "(Release)"
else tag = ""
if (tag) inChild.sendEvent(name:"lastChange", value: "${ts} ${tag}")
}

if (publicou){
state.qualFlips[ck] = 0
state.qualStuck.removeAll{ (it as Integer) == idx } 
} else if ((((state.qualFlips[ck]) ?: 0) as int) >= QUAL_FLIP_STUCK){
if (!state.qualStuck.any{ (it as Integer) == idx }) state.qualStuck << idx
}
}
state.qualBits = qualBits.toString()
syncStuckAttribute()
state.prevInputBits = iBits
}
 
def refresh(){ logInf("Refresh() -> 00"); txRaw("00", "manual refresh") }
 
def push(button){ simulateButton("pushed", "lastPushed", "Push", button) }
def hold(button){ simulateButton("held", "lastHeld", "Hold", button) }


private void simulateButton(String evt, String lastAttr, String cmd, button){
int max = (state.inputcount ?: 0) as int
if (max <= 0){ logWar("${cmd}: número de entradas ainda desconhecido — nada foi feito"); return }
Integer n = null
try {
BigDecimal b = new BigDecimal(button.toString().trim())
if (b.stripTrailingZeros().scale() <= 0) n = b.intValueExact()
} catch (e) { }
if (n == null || n < 1 || n > max){ logWar("${cmd}: botão inválido (${button}) — use um número de 1 a ${max}"); return }
sendEvent(name: evt, value: n, isStateChange: true, type: "digital",
descriptionText: "Botão ${n} ${evt == 'pushed' ? 'apertado' : 'segurado'} (simulado pelo comando ${cmd}; a placa não foi acionada)")
sendEvent(name: lastAttr, value: n)
}



private int masterCount(){
Integer t = tcpChannelCount()
if (t) return t
int h = (state.inputcount ?: 0) as int
if (h >= 1 && h <= MAX_CHANNELS) return h
int kids = (getChildDevices()?.count{ it.typeName?.contains('Switch') } ?: 0) as int
return Math.min(kids, MAX_CHANNELS)
}
def masteron(){
int ch = masterCount()
logInf("Master ON: ${ch} relays (250ms/relay)")
for (int i=1; i<=ch; i++){ noteRelayCmd(i, "on"); txRaw("1${i}", "masterOn"); pauseExecution(250) }
runIn(2, "verifyMasterOn", [overwrite:true])
}
def verifyMasterOn(){
int ch = masterCount()
if (ch <= 0){ runIn(1, "refresh", [overwrite:true]); return }
txRaw("00", "verifyMasterOn-poll")


runIn(2, "checkMasterOnResults", [overwrite:true])
}
def checkMasterOnResults(){ checkMasterResults("on", "1") }
def masteroff(){
int ch = masterCount()
logInf("Master OFF: ${ch} relays (250ms/relay)")
for (int i=1; i<=ch; i++){ noteRelayCmd(i, "off"); txRaw("2${i}", "masterOff"); pauseExecution(250) }
runIn(2, "verifyMasterOff", [overwrite:true])
}
def verifyMasterOff(){
int ch = masterCount()
if (ch <= 0){ runIn(1, "refresh", [overwrite:true]); return }
txRaw("00", "verifyMasterOff-poll")
runIn(2, "checkMasterOffResults", [overwrite:true])
}
def checkMasterOffResults(){ checkMasterResults("off", "2") }
private void checkMasterResults(String expected, String cmdPrefix){
int ch = masterCount()
if (ch <= 0){ runIn(1, "refresh", [overwrite:true]); return }
List<Integer> failed = []
for (int i = 1; i <= ch; i++){
def cd = getChildDevice(childRelayDni(i))
if (cd && cd.currentValue("switch") != expected) failed << i
}
if (failed){
logWar("master ${expected.toUpperCase()} verify: ${failed.size()} relay(s) não responderam, reenviando: ${failed}")
for (int i : failed){ noteRelayCmd(i, expected); txRaw("${cmdPrefix}${i}", "master-${expected}-retry"); pauseExecution(250) }
runIn(2, "refresh", [overwrite:true])
} else {
logInf("master ${expected.toUpperCase()} verify: todos os ${ch} relays confirmados ${expected.toUpperCase()}.")
runIn(1, "refresh", [overwrite:true])
}
}
def componentRefresh(cd){ logInf("componentRefresh from ${cd?.displayName}"); txRaw("00", "componentRefresh") }
def componentOn(cd){ Integer ch = relayIndexFromDni(cd?.deviceNetworkId); if (ch!=null){ logInf("on from ${cd?.displayName}"); noteRelayCmd(ch, "on"); doControlTX("1${ch}") } }
def componentOff(cd){ Integer ch = relayIndexFromDni(cd?.deviceNetworkId); if (ch!=null){ logInf("off from ${cd?.displayName}"); noteRelayCmd(ch, "off"); doControlTX("2${ch}") } }
private void doControlTX(String cmd){
txRaw(cmd, "control")
runIn(1, "refresh", [overwrite:true])
}
private boolean txRaw(String msg, String reason){
String tx = withTerminator(msg)
try{
interfaces.rawSocket.sendMessage(tx)
logDbg("TX${reason?"(${reason})":''}: '"+escapePrint(msg)+"' + term='${TCP_TERMINATOR}' (len=${tx.length()})")
return true
} catch(e){
logWar("Falha ao enviar '${msg}': ${e}")
boolean rebooting = false
if (("${e}".toLowerCase().contains("broken pipe"))){
rebooting = noteBrokenPipeAndMaybeReboot("txRaw:${reason}")
}
if (!rebooting) scheduleReconnect("send fail") 
return false
}
}
 
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

def legacyChild = (legacyDni != dni) ? getChildDevice(legacyDni) : null
if (legacyChild){



def conflictChild = getChildDevice(dni)
if (conflictChild){


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

try{
addChildDevice("hubitat", "Generic Component Switch", dni,
[name: "Mol Relay ${num}", label: "Mol Relay ${num}", isComponent: true])
logInf("Child SWITCH criado: ${dni}")
} catch(e){ logErr("Falha ao criar switch ${dni}: ${e}") }
}
}
}
private void createInputChildren(){
int ch = (state?.inputcount ?: 0) as int
if (ch <= 0){ logWar("Canais ainda não detectados; não criarei inputs."); return }
(1..ch).each { n ->
String num = n.toString().padLeft(2,'0')
String dni = childInputDni(n)
String legacyDni = "${device.id}-Input-${num}"

def legacyChild = (legacyDni != dni) ? getChildDevice(legacyDni) : null
if (legacyChild){



def conflictChild = getChildDevice(dni)
if (conflictChild){
logInf("Removendo input novo sem histórico (${dni}) para liberar DNI")
try { deleteChildDevice(dni) } catch(e){ logWar("Falha ao remover filho conflitante ${dni}: ${e}") }
}
try {
legacyChild.setDeviceNetworkId(dni)
logInf("DNI de input migrado in-place: ${legacyDni} → ${dni} (device ID preservado, referências mantidas)")
} catch(e){
logWar("setDeviceNetworkId falhou (${legacyDni}→${dni}): ${e}")
}
} else if (!getChildDevice(dni)){
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
if (count != null && (count < 1 || count > MAX_CHANNELS)){
logErr("discoverChannelCount: placa reportou ${count} canais — implausível (máx ${MAX_CHANNELS}). Resposta ignorada para proteger os filhos.")
return null
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
state.snRead = snVal 
logDbg("Número de série lido: ${snVal}")
} else {
logDbg("SN não encontrado no conteúdo de sn.cgi: ${raw2}")
}
}
} catch (err2) {
logDbg("Falha ao obter número de série (sn.cgi): ${err2}") 
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
if (ch < 1 || ch > MAX_CHANNELS){
logErr("queryBoardStatus: placa reportou ${ch} canais — implausível (máx ${MAX_CHANNELS}). Ignorado.")
} else {
state.remove("httpUnknown")
if ((state?.inputcount ?: 0) != ch){
logInf("Board reportou ${ch} canais (antes: ${state?.inputcount ?: 0}). Sincronizando filhos...")
state.inputcount = ch
sendEvent(name:"numberOfButtons", value: ch)
state.lastButtons = ch
syncChildren(ch) 
} else {
sendEvent(name:"numberOfButtons", value: ch)
state.lastButtons = ch
}
scanFrozen("queryBoardStatus")
}
}
}
} catch(e){ logWar("queryBoardStatus falhou: ${e}") }
}
 
private static String extractSnFlexible(String s){
if (!s) return null
String t = s.trim()

try {
def js = new groovy.json.JsonSlurper().parseText(t)
def v = js?.sn
if (v != null) return v.toString().trim()
} catch(ex) {}

def m = (t =~ /(?i)\b"sn"\s*:\s*"?([\w\-\.\:]+)"?/)
if (m.find()) return m.group(1)
m = (t =~ /(?i)\bsn\s*[=:]\s*"?([\w\-\.\:]+)"?/)
if (m.find()) return m.group(1)


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
 
private String withTerminator(String s){
switch((TCP_TERMINATOR ?: "NONE").toString().toUpperCase()){
case "CR": return s + "\r"
case "CRLF": return s + "\r\n"
default: return s
}
}
private static String escapePrint(String s){
return (s ?: "").replace("\r","\\r").replace("\n","\\n")
}


private void scheduleSyncRetry(e){
int n = ((state.syncRetry ?: 0) as int) + 1
state.syncRetry = n
if (n > 3){ logWar("syncChildren falhou (${e}) — 3 tentativas esgotadas; use Initialize"); return }
int delay = 10 + _rng.nextInt(21)
logWar("syncChildren falhou (${e}) — nova tentativa ${n}/3 em ${delay}s")
runIn(delay, "retrySyncChildren", [overwrite:true])
}
def retrySyncChildren(){
int ch = (state?.inputcount ?: 0) as int
if (ch <= 0) return
try {
syncChildren(ch)
logInf("syncChildren concluído na tentativa ${state.syncRetry}")
state.syncRetry = 0
} catch (e) { scheduleSyncRetry(e) }
}
 
private void syncChildren(int ch){
if (ch < 1 || ch > MAX_CHANNELS){ logErr("syncChildren: valor de canais inválido (${ch}, máx ${MAX_CHANNELS}). Abortado."); return }
state.inputcount = ch
createRelayChildren()
createInputChildren()
}
private Integer suffixIdx(String dni, String prefix){
String s = dni.substring(prefix.length())
return s.isInteger() ? s.toInteger() : null
}
 

def detectAndSyncChannels(){
if (linkBlocked("Detect And Sync")){ publishSyncStatus("recusado: a placa deste IP não foi confirmada — veja vinculoPlaca"); return }
Map plan = state.prunePlan as Map
if (plan?.status == "em execução"){ 
publishSyncStatus("apagando: lote ${((plan.lotes ?: 0) as int) + 1} de ${lotTotal(plan)} — aguarde")
return
}
if (plan?.status == "aguardando"){
long age = now() - ((plan.at ?: 0L) as Long)
if (age < CONFIRM_MIN_MS){ 
publishSyncStatus("aguarde alguns segundos, leia a lista e clique de novo para confirmar: ${planSummary(plan)}")
return
}
if (age <= CONFIRM_MAX_MS){ confirmPrune(plan); return } 
state.remove("prunePlan") 
}
firstClick() 
}

private void firstClick(){
Integer h = null
try { h = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
if (!h){ state.httpUnknown = true; publishSyncStatus(SYNC_UNKNOWN); return }
state.remove("httpUnknown")
state.inputcount = h
sendEvent(name:"numberOfButtons", value: h)
state.lastButtons = h
syncChildren(h)
Integer t = tcpChannelCount()
if (t != null && t != h){
List exT = excessChildren(t)
publishSyncStatus("recusado: HTTP=${h}, TCP=${t} — nada foi apagado" + (exT ? "; ${frozenMsg(h, t, exT)}" : ""))
return
}
List ex = excessChildren(h)
if (!ex){ publishSyncStatus("ok — ${h} canais"); logInf("Sincronização concluída com ${h} canais."); return }
if (t == null){ publishSyncStatus("recusado: sem leitura TCP válida nesta conexão — nada foi apagado"); return }
state.prunePlan = [ch: h, dnis: ex.collect{ it.dni as String }, labels: ex.take(5).collect{ it.label as String },
at: now(), status: "aguardando", apagados: 0, lotes: 0]
publishSyncStatus("aguardando confirmação: ${planSummary(state.prunePlan as Map)} — espere alguns segundos e clique de novo em até 2 min")
runIn(((CONFIRM_MAX_MS / 1000L) as int) + 1, "expirePrunePlan", [overwrite:true]) 
}


def expirePrunePlan(){
Map plan = state.prunePlan as Map
if (plan?.status != "aguardando" || (now() - ((plan.at ?: 0L) as Long)) < CONFIRM_MAX_MS) return
state.remove("prunePlan")
logInf("Detect And Sync: confirmação não veio em 2 min — plano descartado, nada apagado")
scanFrozen("plano vencido")
}


private void confirmPrune(Map plan){
int pch = (plan.ch ?: 0) as int
Integer h = null
try { h = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
Integer t = tcpChannelCount()
String motivo = null
if (h == null) motivo = "a placa não respondeu por HTTP"
else if (t == null) motivo = "sem leitura TCP válida nesta conexão"
else if (h != pch || t != pch) motivo = "contagens divergentes (HTTP=${h}, TCP=${t}, plano=${pch})"
else if ((excessChildren(pch).collect{ it.dni as String } as Set) != (((plan.dnis ?: []) as List).collect{ it as String } as Set))
motivo = "a lista de dispositivos mudou desde o 1º clique"
if (motivo){
state.remove("prunePlan")
publishSyncStatus("falha no 2º clique: ${motivo} — nada foi apagado; clique de novo para recomeçar")
return
}
plan.status = "em execução"
state.prunePlan = plan
logInf("Detect And Sync confirmado: ${planSummary(plan)}")
pruneLot()
}



private void pruneLot(){
Map plan = state.prunePlan as Map
if (plan?.status != "em execução") return
int pch = (plan.ch ?: 0) as int
Set planejado = (((plan.dnis ?: []) as List).collect{ it as String }) as Set
Integer h = null
try { h = discoverChannelCount() as Integer } catch (e) { logWar("discoverChannelCount falhou: ${e}") }
Integer t = tcpChannelCount()
List ex = []
String motivo = null
if (h == null) motivo = "a placa não respondeu por HTTP"
else if (t == null) motivo = "sem leitura TCP válida nesta conexão"
else if (h != pch || t != pch) motivo = "contagens divergentes (HTTP=${h}, TCP=${t}, plano=${pch})"
else {
ex = excessChildren(pch)
def fora = ex.find{ !planejado.contains(it.dni as String) }
if (fora) motivo = "apareceu dispositivo fora do plano (${fora.label})"
}
if (!motivo){
int n = 0
for (Map e : ex){
if (n >= PRUNE_BATCH) break
try {
deleteChildDevice(e.dni as String)
n++
logInf("Removendo filho excedente: ${e.dni} (${e.label})")
} catch (err){
motivo = "falha ao apagar ${e.label}: ${err}" 
break
}
}
plan.apagados = ((plan.apagados ?: 0) as int) + n
plan.lotes = ((plan.lotes ?: 0) as int) + 1
}
Set vivos = ((getChildDevices()?.collect{ it.deviceNetworkId as String }) ?: []) as Set
int restam = planejado.count{ vivos.contains(it) } as int
if (motivo){
state.remove("prunePlan")
publishSyncStatus("interrompido: apagados ${plan.apagados ?: 0}, restam ${restam} — ${motivo}; nada mais foi apagado")
return
}
if (restam == 0){
state.remove("prunePlan")
logInf("Detect And Sync: ${plan.apagados} dispositivos apagados; placa com ${pch} canais")
publishSyncStatus("apagados: ${plan.apagados} dispositivos; placa com ${pch} canais")
return
}
state.prunePlan = plan
publishSyncStatus("apagando: lote ${plan.lotes + 1} de ${lotTotal(plan)}")
runIn(15, "continuePrune", [overwrite:true])
}
def continuePrune(){ pruneLot() } 
private int lotTotal(Map plan){ (int) Math.ceil(((plan.dnis ?: []) as List).size() / (double) PRUNE_BATCH) }

private String planSummary(Map plan){
List l = (plan.labels ?: []) as List
int total = ((plan.dnis ?: []) as List).size()
String nomes = l.join(", ")
return "apagar ${total} dispositivos (${total > l.size() ? "${nomes}, … e mais ${total - l.size()}" : nomes})"
}
def setChannelName(Integer ch, String name){
if (ch == null || ch < 1 || !name?.trim()){ logWar("setChannelName: parâmetros inválidos (ch=${ch}, name=${name})"); return }
def cd = getChildDevice(childRelayDni(ch))
if (cd){ cd.setLabel(name.trim()); logInf("Canal ${ch} renomeado para: '${name.trim()}'") }
else logWar("setChannelName: child para canal ${ch} não encontrado")
}
 
def on(){
if (linkBlocked("On geral")) return 
if (settings?.allowMasterOnOff == false){ logWar("On geral bloqueado nas Preferences (Permitir ligar/desligar todos os relés pelo dispositivo principal) — nada foi enviado à placa"); return }
if (masterCount() <= 0){ logWar("ON cancelado: canais desconhecidos. Use 'Detect & Sync Channels'."); return }
masteron()
sendEvent(name:"switch", value:"on")
}
def off(){
if (linkBlocked("Off geral")) return 
if (settings?.allowMasterOnOff == false){ logWar("Off geral bloqueado nas Preferences (Permitir ligar/desligar todos os relés pelo dispositivo principal) — nada foi enviado à placa"); return }
if (masterCount() <= 0){ logWar("OFF cancelado: canais desconhecidos. Use 'Detect & Sync Channels'."); return }
masteroff()
sendEvent(name:"switch", value:"off")
}
 
def sendRebootLAN(){
if (linkBlocked("Reinício da placa")) return
state.idMiss = 0 
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
def logsOff(){
log.info "Desabilitando logs de debug automaticamente (30 min)." 
device.updateSetting("logEnable", [value:"false", type:"bool"])
}

private boolean autoRebootEnabled(){
def v = settings?.autoRebootBoard
return AUTO_REBOOT_DEFAULT ? (v != false) : (v == true)
}

private boolean noteBrokenPipeAndMaybeReboot(String origin){
long nowMs = now()
long windowMs = 2 * 60 * 1000L
long cooldownMs = 10 * 60 * 1000L
if (!state.bpFirstAt) state.bpFirstAt = nowMs
if ((nowMs - (state.bpFirstAt as Long)) > windowMs){
state.bpFirstAt = nowMs
state.bpCount = 0
}
state.bpCount = ((state.bpCount ?: 0) as Integer) + 1
logWar("Broken pipe detectado (${state.bpCount}/5) [${origin}]")
if ((state.bpCount as Integer) >= 5 && !autoRebootEnabled()){
logWar("5x Broken pipe — reinício automático desligado nas Preferences; seguindo só com reconexão")
state.bpCount = 0
state.bpFirstAt = nowMs
return false
}
long lastRb = (state.lastAutoRebootAt ?: 0L) as Long
if ((state.bpCount as Integer) >= 5 && (nowMs - lastRb) > cooldownMs){
state.lastAutoRebootAt = nowMs
state.bpCount = 0
state.bpFirstAt = nowMs
logWar("5x Broken pipe -> solicitando reboot automático do módulo (LAN)")



unschedule("doReconnect")
if (origin == "socketStatus") markSocketOffline() else disconnectSocket()
try { sendRebootLAN() } catch(e) { logWar("Falha ao enviar reboot LAN: ${e}") }
runIn(30, "initialize", [overwrite: true])
runIn(30 + GUARD_EXTRA_S, "reconnectGuard", [overwrite: true]) 
return true
}
return false
}
