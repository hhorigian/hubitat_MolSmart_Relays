<h1>**Módulos Relay MolSmart de 2/4/8/16/32 CH**</h1>

1- Instalar o Driver via o HPM se possível para manter ele atualizado sempre e aproveitar as melhorias. 
2- Após instalar o driver, adicionar um novo Device do tipo VIRTUAL, do tipo: " MolSmart - Relay 4/8/16/32CH (TCP)"

Agora, entrando no device: 
Incluir 2 informações:

1. Endereço IP do módulo MolSmart. 
2. Porta UDP do módulo MolSmart (padrão é 502).
3. Salvar as preferencias. IMPORTANTE. Só salvando o próximo passo vai funcionar. 
4. Apertar o botão de "Initialize" na pagina do Device. Esse comando vai criar os Canais e finalizar a configuração.
   

OBS: O driver reconhece automáticamente a quantidade de relays que o módulo tem.  

Tudo deveria estar pronto, o driver criar a quantidade de circuitos disponíveis do módulo. 

Para o dasbhoard reconhecer os canais como "switch", precisa uma única vez, ligar/desligar cada canal. Desse jeito eles serão detectados no dashboard com switches automáticamente. 
