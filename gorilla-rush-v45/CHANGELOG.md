# Alterações — v45 / PICO G2

## 1.0.8 — port do Gorilla Movement enviado pelo usuário
- ZIP de Lights Out: Bear Facility analisado e preservado com hashes; não identificado como código oficial de Gorilla Tag.
- Núcleo GMotion: erro follower/target, média de mãos em contato, suporte, histórico de 8 velocidades efetivas, soltura 1,1× com cap 4 m/s e gravidade 5 m/s². Remove ganho/interpolação/atrito do motor anterior.
- Port usa colisor contínuo/corpo existentes, raio da mão real e adaptação 3DoF. Mantém segurança/recentralização/cap de contato, menu/tirolesa/água e touchpad opt-in. Sem trocar renderer ou adicionar Rapier ao runtime.
- 43 testes Node, comparação numérica com o update original em quatro taxas; 5 checks do ZIP executados separadamente. Entrada/estéreo/locomoção/pele, suporte e queda após soltar, escala e frame rates testados. Sem teste físico no G2.
- APK tela+navegador 1.0.8/code 10, cache `v45-gmotion-10`, HUD **FISICA 1.0.8 / GMOTION**.

## 1.0.7 — mãos livres, sem mola no gatilho / touchpad opt-in
- Removida a reação por erro acumulado da âncora ao segurar gatilho. Contato sempre reage à velocidade relativa da mão; levantar solta com ou sem botão. Mantém momento, gravidade, limites e colisões.
- Assistência de touchpad desligada por padrão. Toque explicitamente falso impede eixos residuais de mover o corpo; zona morta radial de 18% na assistência opcional. Menu/pulo/giro/tirolesa preservados.
- Três regressões reproduzidas antes da correção; 36 testes Node, 7 de entrada, 12 de navegador e suíte estendida de locomoção. Sem teste físico G2.
- APK tela+navegador 1.0.7/code 8, cache `v45-free-8`, HUD **FISICA 1.0.7**.

## 1.0.6 — velocidade das mãos e gravidade independente do contato
- Diagnóstico antes da alteração: posição ignorada, mão única, retorno antecipado em contato e velocidade sobrescrita pela correção da âncora.
- Estende o input existente: fontes esquerda/direita únicas, gripSpace/targetRaySpace WebXR e posição WebVR quando válida; quaternion/botões preservados. Posição emulada ou ausente usa braço 3DoF identificado, não tracking fictício.
- Reação calculada a partir de delta da mão relativo ao corpo / dt. Integração única de velocidade, gravidade e colisões; média de duas reações ativas. Mantém impulso após soltura e limita velocidade sem motor de caminhada fixa.
- Contato aberto não desliga gravidade nem cancela pulo. Repouso no chão tem atrito; perda/troca de fonte não injeta impulso.
- Geometria local esquerda/direita reversível; fontes ausentes não fazem força. Tirolesa/tag local respeitam mãos disponíveis. Câmera, resolução, framebuffer, mapas, menu e protocolo de rede preservados.
- 33 testes Node, regressões de entrada/estéreo/colisão, nova suíte dos 13 cenários pedidos e casos de reconexão/limite/momento/3DoF. Sem validação física no G2.
- Cache `v45-motion-7`, painel **FISICA 1.0.6**, links do README/launcher atualizados, APK tela+navegador 1.0.6/code 7.

- Integra a main 1.0.5 sem perder neutro automático, trajetória 3DoF amplificada, histerese/retorno aéreo e touchpad de reserva. Testes anteriores mantidos; novos testes de repouso/calibração com poses repetidas e touchpad.

## 1.0.5 — calibração automática e touchpad como reserva
- Relato no G2: ainda "parece que estou no ar". Provável causa: o ângulo que o navegador/controle informa para a mão relaxada difere do esperado; com leitura para baixo a mão fica sempre no chão, com leitura para cima nunca alcança o chão.
- **Neutro automático**: o ângulo do controle parado é aprendido (rápido nos primeiros 3 s, depois devagar) e tratado como "reto"; o chão fica sempre ~15° abaixo da sua posição relaxada. Não aprende com o gatilho segurando.
- **Touchpad anda por padrão** (deslize o dedo), como reserva; pode desligar no menu (AJUDA NO TOUCHPAD).
- Painel mostra ângulo do controle, ângulo "reto" aprendido e distância mão-chão.
- Simulado com mão relaxada lida a 0°, +20° e −25°, tremor de 2° e poses repetidas: neutro aprendido em ±1°, ~0,8 corpo por empurrão, repouso sem piscar nem subir, touchpad ~1,7 corpo/s. 26 testes Node e 12 de navegador. Sem headset físico.

## 1.0.4 — sem piscar NO AR/NO CHÃO e sem voar
- Relato no G2: parado, o painel alternava NO AR/NO CHÃO rapidamente e o corpo "voava". Reproduzido com tremor real de mão (2–3° a 9 Hz) e poses repetidas: 45 trocas em 3 s, deriva de 2 corpos e subida de 2 alturas.
- Contato decidido por **histerese de ângulo**, não por velocidade: solta só ao inclinar ~5° de volta a partir do ponto mais fundo; só agarra de novo após inclinar para baixo outra vez (ou depois de sair da superfície).
- Com a mão aberta no chão, o corpo segue o ponto **mais fundo** do empurrão: voltar o controle ou tremer não puxa para trás nem empurra para cima. Removida a regra "só empurra" que acumulava cada tremida como subida.
- Filtro mais forte da orientação (12/s); impulso de soltura só após contato ≥ 0,1 s e subida limitada.
- Simulado com tremor de 3°: 0 trocas em repouso, altura máxima 0,07 do corpo, ~1 corpo por empurrão. 25 testes Node (novos: tremor em repouso, retorno sem arrasto) e 12 de navegador. Sem headset físico.

## 1.0.3 — locomoção estilo Gorilla Tag
- Braço 3DoF longo e reto pivotando no ombro; inclinação do pulso amplificada 2×, caminho achatado perto do chão. Controle reto = mão logo acima do chão; ~13° para baixo = mão no chão; ~45° = mão atrás do corpo.
- Soltura imediata ao inclinar o controle de volta para cima (detector sem filtro + filtrado); o retorno passa pelo ar e não puxa o corpo para trás. Varrer o braço para trás continua empurrando.
- Impulso de soltura 1,25× (limite 6,5 × escala/s). Desvio lateral girando o controle com a mão no chão.
- Painel **GORILA 1.0.3** com instruções novas; cache `v45-gorila-4`; link WebXR `v=1.0.5-gorila` no README e no launcher do APK (1.0.3/code 4).
- Testes: 23 Node (novos: passadas curtas com ruído, mão perto do chão com controle reto) e os 12 cenários de navegador com ângulos atualizados. Poses simuladas; sem headset físico.

## 1.0.2 — âncora e colisão da mão
- Substituídos os sete raios do pulso por colisão contínua esfera/triângulo, cobrindo faces, arestas e vértices com consulta à grade real dos mapas.
- Raio conservador calculado para envolver mão e dedos; o raio antigo de 0,075 × escala não cobria a malha (~0,335 × escala até os dedos).
- Âncora fixa em superfícies, soltura ao levantar sem puxar o corpo de volta, gatilho mantém apoio real; remoção de superfície e perda de pose invalidam a âncora.
- Movimento causado por gravidade também passa pelo solver; removido o deslocamento visual sem colisão após a física. Pulso visível coincide com o alvo após IK.
- 21 testes Node e 12 cenários de navegador, mais 7 de entrada VR. Novas verificações da malha visível, âncora, bordas, rampas, cantos, queda e frames de 50 ms. Poses/runtime simulados; sem headset físico.
- Cache `v45-anchor-3`, painel **ANCORA 1.0.2**, link WebXR do README e launcher atualizados; APK tela/navegador 1.0.2/code 3. MonkeGo e outros jogos preservados.

## 1.0.1 — iniciar diretamente em VR
- Botão principal **INICIAR VR** no lugar do atalho principal de entrada rápida; o clique solicita a sessão imersiva no próprio gesto do usuário, sem abrir câmera, microfone ou rede antes.
- Removidos o painel flutuante superior e o seletor de VR Box. Ferramentas avançadas, modo tela e entrada rápida online continuam em Configurações → Controles.
- WebXR tem preferência; WebVR permanece disponível para navegadores legados G2. Sem API imersiva, o botão é desabilitado com aviso, não há VR simulado.
- Proteção contra entrada duplicada; permissão negada permite nova tentativa; sair restaura o botão de início.
- URLs de scripts e CSS versionadas; APK 1.0.1/code 2. Novos testes de interface, clique, negação, reentrada, ausência de API e viewport móvel, com fronteira XR simulada.

## Entrada nova, sem substituir a anterior
- Importado `Gorilla_Rush5_v45 (1).txt` completo; não reutilizado o mapa antigo no lugar do novo.
- 68 payloads distintos extraídos; ambos os mapas, avatar, cosméticos, sons, música, loja, teclado, portais e código de rede preservados.
- `/monke-go/` e pacote `com.picog2.monkego` não alterados. Novo pacote/endereço permite voltar imediatamente.

## Integração executável
- WebXR local: viewer pose, fonte targetRaySpace, eventos select, ciclo de apresentação do Three.
- WebVR 1.1: VRDisplay, getFrameData, projeções e views independentes, dimensionamento por olho, submitFrame e saída.
- Sem Cardboard automático no caminho PICO. Mensagem explícita quando não existe API imersiva.
- Controle único dirige braço direito; IK de dois segmentos, mão visível orientada pelo quaternion completo, dedos fechados pelo gatilho.
- Suporte ao toque/empurrão do piso no caminho Solo e de rede, antes da integração de física antiga por landmarks. Distâncias/velocidades adaptadas ao PS real do v45.
- Apoio tangencial, soltura ao levantar, ar sem propulsão, casts sem captura acidental da face inferior do chão. Medição do deslocamento após colisões; limites de alcance/velocidade.
- Reentrada, mudança de fonte, perda de pose, menu, teleporte e troca de escala zeram contato/velocidade. Torcer o pulso não cancela apoio.
- Menu 3D essencial: continuar, calibrar braço, ajuda opcional no touchpad, respawn, sair. Laser parte da mão; também seleção pelo touchpad.
- Painel com API, pose ausente, contato no ar/apoiado, distância ao piso e deslocamento acumulado. Exportador JSON para diagnóstico no aparelho real.
- Água recebe gesto estimado; tirolesa acessível pelo contato/gatilho; atrito menor na neve. A equivalência completa de mecânicas em todas as áreas não foi validada fisicamente.

## Portabilidade e custo
- Sem DecompressionStream: gzip resolvido na extração. Bibliotecas essenciais locais; câmera mobile opcional sob demanda.
- Sem conexão de presença social automática no Solo; sem câmera/worker MediaPipe em VR.
- Baixo por padrão; espelho menor/um passe por frame; áudio preload metadata.
- Erro de mapa não substitui silenciosamente a área por um plano vazio.
- Build reproduzível Java/DEX/APK de tela + navegador externo, verificação de assinatura/manifesto/assets; ZIP de fonte e exportação web.

## Limites explícitos
- Não é APK VR nativo nem física idêntica de Gorilla Tag; posição de mão estimada a partir de um controle 3DoF.
- Não é ragdoll, não são duas mãos posicionais medidas, não há promessa sem bugs.
- Perfil/amigos/microfone/configurações completas do original ainda são telas 2D, acessíveis fora da sessão.
- Testes físicos, instalação, desempenho, voz/câmera e multiplayer na internet não realizados. Consulte relatórios por categoria no README.
