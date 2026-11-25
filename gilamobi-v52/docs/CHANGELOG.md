# v52-g2web-1.0.2 — corpo acompanha o olhar e identificação local

- Corrigido corpo imóvel ao olhar para os lados: torso/cápsula e yaw enviado pela rede acompanham a direção horizontal real da cabeça. Referencial WebXR independente do corpo, sem realimentação/giro acumulado da câmera. Olhar não produz caminhada automática, nem inclina o corpo ao olhar para cima/baixo; mãos mantêm suas poses mundiais.
- A 1.0.1 criava etiquetas somente nos avatares remotos. Agora o próprio personagem também tem `[VR PICO G2]` vermelho acima do nome verde durante VR, com nome salvo, troca de personagem e saída de VR sincronizados.
- Identificação local na camada do avatar completo: visível no espelho e na câmera de gravação, não colada dentro dos olhos. Espelho deixa de ser desligado em VR; mantém limites de distância/visibilidade/resolução. Câmera Sandbox agora inclui avatar local completo e nome. Há custo adicional de renderização do espelho; FPS físico ainda não medido.
- Novas regressões: yaw esquerda/direita por vários frames sem feedback, poses das mãos preservadas, nenhum deslocamento artificial, polos verticais, reconstrução Grinner, saída e cores da identidade local; leitura de pixels GL verifica vermelho/verde realmente renderizados.
- Cache PCK/controles em 1.0.2. Rollback 1.0.1 preservado no commit `36c4828` e compilação `45655143379`. Jogos independentes e mudanças externas no repositório preservados.
- Ainda sem teste em Pico G2 físico ou dois headsets pela internet. Outros jogadores devem reabrir a versão atualizada.

---

# v52-g2web-1.0.1 — movimento clássico e presença VR

- Após relato de impossibilidade de andar no G2, o caminho VR voltou aos **impulsos por velocidade do v52 recebido**: velocidade de mão limitada a 6 m/s × escala, reação 1,15, limiar 0,22 m/s, impulso individual máximo 5 m/s, intervalo 0,22 s, limites corporais originais 7 m/s horizontal e 4,8 m/s vertical, gravidade 9,8. A média GMotion/apoio plantado/impulso de soltura da 1.0.0 não controla mais o corpo. Mantidas as colisões por esfera para não atravessar o mapa.
- Posição da cabeça no modo **estimado 3DoF** ancorada na altura do personagem (1,36 m × escala); offsets de altura diferentes em `local`/`local-floor` não afastam a mão do chão. Rotação continua vindo do controle/cabeça reais; não é tracking posicional 6DoF.
- A mão acompanha todos os eixos do controle. Novo pacote transmite quaternion mundial e máscara das mãos rastreadas; receptor interpola a orientação e aplica ao IK. Antes a rede transmitia apenas posição, perdendo o giro do pulso.
- Ao entrar em sessão imersiva, os outros clientes **atualizados** mostram `[VR PICO G2]` vermelho, acima do nome escolhido em verde. Ao sair, a etiqueta é removida. Isso indica o modo do software, não autenticação do modelo físico do aparelho.
- Campo de nome no lobby Web, salvo antes de iniciar VR; opção **Nome** no painel VR abre o editor/teclado original. Nome limitado e higienizado pelo validador recebido.
- Pacotes antigos continuam aceitos sem etiqueta/rotação inventadas. Outros jogadores precisam atualizar/reabrir o jogo para exibir os campos novos.
- Cache do PCK e dos controles versionado em 1.0.1. A 1.0.0 continua recuperável no commit `6257ffb` e no artefato da compilação `45531181134`; os arquivos de referência de movimento anteriores não foram apagados.
- Testes ampliados: movimento clássico em diferentes frequências, alturas 0/1,6/2,8 m do runtime, nome no lobby, mensagens reais de estado de ida/volta para o avatar remoto, giro 360° em três eixos, cores/ordem das etiquetas, saída de VR e rejeição de quaternions inválidos.
- **Ainda sem teste físico no G2 e sem ensaio de dois headsets pela internet.** O teste de rede usa o serializador e o receptor reais em fixture local; não comprova disponibilidade do broker público.

---

# v52-g2web-1.0.0

## Projeto/proveniência
- Importado o código e assets efetivamente recebidos, com inventário SHA-256, README/preset Android originais preservados.
- Diretório independente; sem sobrescrever OpenSaber, Gorilla Rush ou MonkeGo.
- Godot 4.4.1 Web single-thread; componentes exclusivos Android isolados em `reference/`.
- Licenças e atribuição originais preservadas; autorização específica declarada pelo usuário, não verificada documentalmente.

## VR/input
- WebXRInterface, XROrigin3D e XRCamera3D reais; sessão immersive-vr solicitada diretamente por gesto do botão.
- local-floor preferido, fallback local com altura de olho virtual; cabeça orientada pelo runtime.
- Mapeamento esquerda/direita e único controle sem handedness; perda de pose rejeita dados antigos, oculta braço e remove apoio.
- Braço 3DoF explicitamente estimado, opção de pose medida para hardware 6DoF.
- Rigs/modelos recebidos mantidos, braços particionados por pesos de osso; orientação da mão e IK preservados.
- Gatilho/select para UI; longo de 1,1 s abre painel; correção para não tratar a soltura de um gesto longo como clique curto.
- Menu original renderizado em SubViewport no mundo, raio de controle e teclado virtual; painel rápido para mapas, personagens, ferramentas, câmera e voz.

## Locomoção
- Núcleo baseado no GMotion fornecido: média dos contatos, histórico de oito velocidades corporais reais, limiar 0,4, ganho de soltura 1,1, limite 4 m/s, contato 8 m/s e gravidade 5 m/s²; escalados pelo personagem.
- Sweep de esfera, recuperação de sobreposição e deslizamento usando o mundo físico do mapa.
- Apoio da mão pode sustentar o corpo; levantar solta; perda/descontinuidade de tracking reinicia amostras para não lançar o jogador.
- Sem caminhada automática pelo touchpad, sem duplicar um controle em duas mãos, sem modo de voo permanente.

## Web/renderização
- Ponte HTML em português com verificação de WebXR, WebGL2 e multiview, e avisos honestos.
- Layers polyfill Apache-2.0 fixado em 1.1.0 para navegadores com XRWebGLLayer sem Layers; pequena correção de preservação de framebuffer documentada em `web/vendor/NOTICE.md`.
- Corrigida tela preta estéreo causada pelo post-process escalado do Godot 4.4: somente em XR mantém escala 1,0. Qualidade baixa da prévia/espelho não foi convertida silenciosamente em alta.
- Corrigido render target multiview residual na saída de VR, recriando target mono; testes de reentrada/reconexão.
- Gravação Web termina o AVI original e solicita download via JavaScriptBridge; não finge usar encoder/galeria Android.

## Build/testes
- Importação, testes do motor com GL/Xvfb e export via CI com versão fixa do motor.
- Testes de variação de frequência/escala, impulsos, paredes/piso finos, sobreposição, mapas/rigs, perda de pose e braços ausentes.
- Browser fixture usa WebXRInterface do próprio jogo, não bypass de locomotion em JS. Emulador restrito a testes.
- Verificação de hashes fonte/export e proteção para não publicar o runtime IWER de testes.
- Relatórios explicitam que Pico físico e performance não foram testados.

## Ainda não comprovado

Compatibilidade de uma versão específica do navegador G2, framerate, consumo, conforto, mic e downloads no headset, multiplayer/voz entre aparelhos, cobertura completa de combate/itens e teclado original. Não se declara “perfeito”, “sem bugs” ou física idêntica ao Gtag. Veja `COMPATIBILITY.md`.
