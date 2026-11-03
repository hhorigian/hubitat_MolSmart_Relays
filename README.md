# hubitat_MolSmart_Relays

Driver para Módulos Relay da MolSmart - para Hubitat. 

A últimq Versão e a indicada é a TCP. 
Recomendamos sempre usar o HPM para instalar e receber atualizações. 

Necessário ter o Módulo da MolSmart instalado na rede via Ethernet ou WiFi. 
O Driver já reconhece a quantidade de relays que o módulo tem. 




## TecnoSimples — atualização / update

### Português

Este driver é mantido pela **TecnoSimples** a partir do código original de Vartan Horigian (VH / TRATO), sob a licença Apache 2.0. O nome, o namespace `TRATO` e os IDs do pacote são os mesmos de sempre: quem instalou por aqui recebe a versão nova pelo **HPM → Update**, sem trocar o Type de nenhum device.

**Como atualizar**
1. Atualize o próprio HPM antes (Hubitat Package Manager 1.9.16 ou mais novo).
2. HPM → **Update** → marque este pacote → aguarde a página dizer "complete" antes de sair dela.
3. Abra cada placa e clique **Save Preferences** uma vez: o IP migra para os campos novos e o `driverVersion` aparece. Até lá a tela mostra o IP em branco, mas a placa segue funcionando.

**Como saber de onde veio o driver:** o atributo `driverVersion` do device mostra `TS-3.0.0-VH` quando o código foi baixado deste repositório (`TS-3.0.0`, sem `-VH`, é o mesmo código pelo repositório da TecnoSimples).

**Atenção**
- **Placas criadas com o driver TCP até a versão 1.6** têm filhos `-Switch-1` (sem zero à esquerda). Ao atualizar direto, os relés 1 a 9 perdem o vínculo: o driver cria filhos novos e os antigos param de atualizar. Confira o DNI do relé 1; se terminar em um dígito só, atualize antes para a 2.x deste repositório ou recrie a placa.
- Regras que liam `boardstatus` passam a ler `online`; `masteron`/`masteroff` viram o On/Off do device pai.
- Aparecem 16 filhos de entrada (contato e botão). A opção "Sensor em repouso aparece como ABERTO" vem ligada; confira antes de usar as entradas.
- Os nomes por canal (`chNN_name`) saem das Preferences: renomeie na página de cada filho.

### English

This driver is maintained by **TecnoSimples**, based on the original code by Vartan Horigian (VH / TRATO), under the Apache 2.0 license. The name, the `TRATO` namespace and the package IDs are unchanged: if you installed from here, you get the new version through **HPM → Update**, without changing any device's Type.

**How to update**
1. Update HPM itself first (Hubitat Package Manager 1.9.16 or newer).
2. HPM → **Update** → tick this package → wait for the page to say "complete" before leaving it.
3. Open each board and click **Save Preferences** once: the IP moves to the new fields and `driverVersion` shows up. Until then the IP field looks blank, but the board keeps working.

**Where did the driver come from:** the device's `driverVersion` attribute shows `TS-3.0.0-VH` when the code was downloaded from this repository (`TS-3.0.0`, without `-VH`, is the same code from the TecnoSimples repository).

**Heads-up**
- **Boards created with the TCP driver up to version 1.6** have `-Switch-1` children (no leading zero). Updating straight away, relays 1 to 9 lose their binding: the driver creates new children and the old ones stop updating. Check the DNI of relay 1; if it ends in a single digit, first update to 2.x from this repository or recreate the board.
- Rules that read `boardstatus` now read `online`; `masteron`/`masteroff` become the parent device's On/Off.
- 16 input children appear (contact and button). The option "Sensor em repouso aparece como ABERTO" (sensor at rest shows as OPEN) ships enabled; check it before using the inputs.
- Per-channel names (`chNN_name`) leave the Preferences: rename each child on its own device page.
