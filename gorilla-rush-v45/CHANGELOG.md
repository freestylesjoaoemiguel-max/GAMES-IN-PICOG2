# Alterações — v45 / PICO G2

## 1.0.3 — velocidade das mãos e gravidade independente do contato
- Diagnóstico antes da alteração: posição ignorada, mão única, retorno antecipado em contato e velocidade sobrescrita pela correção da âncora.
- Estende o input existente: fontes esquerda/direita únicas, gripSpace/targetRaySpace WebXR e posição WebVR quando válida; quaternion/botões preservados. Posição emulada ou ausente usa braço 3DoF identificado, não tracking fictício.
- Reação calculada a partir de delta da mão relativo ao corpo / dt. Integração única de velocidade, gravidade e colisões; média de duas reações ativas. Mantém impulso após soltura e limita velocidade sem motor de caminhada fixa.
- Contato aberto não desliga gravidade nem cancela pulo. Repouso no chão tem atrito; perda/troca de fonte não injeta impulso.
- Geometria local esquerda/direita reversível; fontes ausentes não fazem força. Tirolesa/tag local respeitam mãos disponíveis. Câmera, resolução, framebuffer, mapas, menu e protocolo de rede preservados.
- 28 testes Node, regressões de entrada/estéreo/colisão, nova suíte dos 13 cenários pedidos e casos de reconexão/limite/momento/3DoF. Sem validação física no G2.
- Cache `v45-motion-4`, painel **FISICA 1.0.3**, links do README/launcher atualizados, APK tela+navegador 1.0.3/code 4.

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
