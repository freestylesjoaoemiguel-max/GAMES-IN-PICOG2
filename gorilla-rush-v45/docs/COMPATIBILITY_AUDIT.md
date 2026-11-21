# Auditoria — entrada v45 e PICO G2

## Material efetivamente inspecionado

Entrada: Drive `175t347wHIk0-n6sGLnQJAf_Q95xSR6rC`, `Gorilla_Rush5_v45 (1).txt`, 34.090.637 bytes, MD5 `66e690a8491beedaaa194a72675f8d9c`. Não é ZIP: é um documento HTML com CSS, oito scripts inline e assets base64. Nenhum diretório Android/Unity/Unreal, manifesto nativo, Gradle, AAR ou SDK PICO na entrada.

O importador percorre todo o arquivo e extrai 68 payloads distintos, inclusive duplicados referenciados várias vezes, registrando tamanho e SHA256. Dois mapas gzip contêm cabeçalho JSON e buffers quantizados; foram expandidos para remover dependência de uma API web recente. A estrutura de cada GLB/mapa está em `SOURCE_STRUCTURE.json`. Código textual original integral com payloads abreviados: `SOURCE_READABLE.txt`.

## Plataforma original e problemas concretos

| Área | Entrada v45 | Modificação / limite |
|---|---|---|
| Engine/renderização | Three r128, WebGL; mapa quantizado e GLB skinned | Mesma engine e mesmos assets, bibliotecas locais |
| Sessão VR | `renderPlay` duplica câmera em dois viewports; sem requestSession/requestPresent | Integração WebXR local e WebVR 1.1, loop do runtime, projeção/view por olho e submitFrame |
| Cabeça | DeviceOrientation/arrastar; não pose do compositor | ViewerPose/VRFrameData antes da física; yaw comum ao controle |
| Controle | Gamepad eixos/botões; nenhuma orientação PICO usada como mão | Pose da fonte targetRaySpace atual ou Gamepad.pose.orientation legado, normalização/rejeição de inválidos |
| Mãos | MediaPipe câmera, landmarks em `slot[]` | Câmera/worker não usados em sessão VR; uma mão direita com posição estimada, quaternion real e IK |
| Movimento | Só landmarks ativam `on`/contato; Solo usa outro caminho (`soloFly`) | Caminho de física do controle entra antes do Solo e da física de câmera, sem dupla integração |
| Escala | `PS=.25`, `CSC<=.14`, `EYEUP=1.1*(PS-CSC)`; aumenta na praia | Distâncias, gravidade, velocidade e tamanho do rig parametrizados por PS; resets em teleporte/troca de tamanho |
| Mapas | `DecompressionStream('gzip')` obrigatório | Expansão no importador, sem mudar conteúdo dos mapas; falha explícita, sem substituir mapa por plano vazio |
| Dependências | Three/PeerJS/MediaPipe por CDN | Engine/loaders/Reflector/PeerJS locais; câmera original opcional sob demanda, ainda externa |
| Interface | HTML na janela, não dentro do compositor | Menu/painel/laser 3D para ações essenciais; perfil/amigos/configurações completas ainda 2D |
| Braço | Camera landmarks, repouso pendurado | Braço direito articulado, alcance limitado, pulso 360°, esquerda removida da malha local sem modificar GLB |
| Atrito | Não recebe gesto do controle no código original | Apoio tangencial no piso; empurrão oposto ao gesto, soltura ao levantar, velocidade medida após colisão |
| Segurança da pose | Sem gerenciamento de source/pose perdida | Reseta apoio, filtro e velocidade ao perder/trocar pose, abrir menu, teleportar ou recentrar abruptamente |
| APK | Inexistente na entrada | Build Java/DEX assinado, tela offline e link para browser; NÃO runtime VR nativo |

Não há API Quest exclusiva no código recebido; portanto não foi inventada uma substituição Oculus/OpenXR desnecessária. O problema é a ausência de qualquer sessão VR imersiva e de integração do controlador no caminho de física, além do hardware 3DoF não informar translação.

## Hardware / runtime: o que se sabe e o que não se sabe

As variantes G2/G2 4K usam Snapdragon 835/Adreno 540 e tracking rotacional 3DoF com um controle; resolução, memória e SO diferem por variante. A referência comunitária lista G2 1440×1600 por olho/90 Hz, G2 4K 1920×2160/75 Hz e variantes Enterprise com memória/câmera diferentes. Não aplicamos valores de uma variante como fato sobre o aparelho do usuário. [1](https://pico.crx.moe/docs/about-pico/legacy/) [2](https://vrarwiki.com/wiki/Pico_G2)

O usuário respondeu **Firefox Reality / Wolvic**, sem especificar versão, e confirmou rastreamento funcionando na edição anterior. Isso não identifica o firmware/ABI exatos. O programa detecta a API oferecida de fato. No WebVR, o framebuffer usa os tamanhos retornados pelo runtime e orçamento de ~1 milhão de pixels por olho; no WebXR, escala conservadora 0,55. WebGL/OpenGL ES compatível é necessário, mas não implica acesso ao compositor.

As tabelas históricas de suporte diferenciam SDK Unity legado para G2/G2 4K e Integration SDK de geração mais nova. [3](https://github.com/m07studio/Pico) A página legada Unreal declara suporte ao G2 4K e rastreamento 3DoF, não prova que qualquer SDK OpenXR atual suporte esse aparelho. [4](https://developer.pico-interactive.com/sdk/index?id=5)

Foi encontrada uma alternativa comunitária Wolvic específica para G2, baseada no backend PicoVR legado, não picoxr/OpenXR. O projeto requer um AAR PicoVR licenciado para recompilar. Não baixamos, auditamos ou instalamos seu APK; isso NÃO é uma recomendação de segurança nem teste independente de compatibilidade. Como o usuário já tem tracking no navegador, não é necessário trocar de navegador para esta adaptação. [5](https://github.com/C-Y-T-258/wolvic-pico-g2-port/blob/main/README.en.md)

## Física de Gorilla Tag: limite técnico, não apenas licenciamento

O repositório público `Another-Axiom/GorillaLocomotion` disponibiliza um sistema de locomoção sob MIT. Inspecionamos README e Player.cs: utiliza posições de `leftHandTransform/rightHandTransform`, colisores/casts e Rigidbody Unity. Não foi importado como se funcionasse em JavaScript, nem copiado para alegar fidelidade exata. [6](https://github.com/Another-Axiom/GorillaLocomotion)

Um controle G2 3DoF não mede essas duas posições. A adaptação implementada é própria, com varredura contínua de uma esfera conservadora envolvendo a mão contra triângulos, âncora na superfície, correção do corpo e soltura. O volume não reproduz cada dedo separadamente e pode deixar folga visual; o pulso é alinhado ao alvo após IK. Pode reproduzir o princípio de empurrar superfícies com um braço virtual, mas não a mesma entrada/física de duas mãos 6DoF. Torção/rotação é medida; translação é estimada. Ragdoll e dedos medidos não foram implementados nem simulados como tracking real.

## Otimizações aplicadas

- Base64 gigante retirado do parser HTML/JS; assets deduplicados, cacheáveis e com hashes.
- Mapas expandidos fora do navegador, sem APIs de gzip modernas ou buffers base64 intermediários para GLB/mapas.
- Baixo por padrão, sem antialias, sem sombras dinâmicas por padrão, pós-processamento desativado como no original.
- Espelho preservado em 384×192, atualizado no máximo uma vez por frame no estéreo.
- Reutilizados batching/culling/grade de colisão do v45; ambas as áreas preservadas.
- MediaPipe/câmera não inicializados em VR; presença PeerJS só ao abrir amigos, não conexão contínua sem necessidade no Solo.
- Música carrega metadados em vez de preload total; todos os arquivos de música/efeitos recebidos preservados.
- Um loop de apresentação; filtro somente da direção do braço, sem suavizar/limitar quaternion visual do pulso.

Sem medição de FPS, uso de RAM, bateria, temperatura ou latência física. Otimização de código não é garantia de desempenho no headset.

## Evidência / pendências

1. **Compilação real:** relatório Android com assinatura e empacotamento; Web export com sintaxe verificada.
2. **Análise estática + testes simulados:** hashes de todos os assets; testes de movimento em várias escalas; runtime de jogo e malha reais em Chromium com poses simuladas; não confundir com headset.
3. **Não testado no PICO G2 físico:** instalação, sessão imersiva real, firmware, limites de GPU, ergonomia e rede.

Faltam para cumprir a exigência de um APK VR nativo: runtime legado licenciado compatível com o firmware, integração de renderização/compositor/entrada nativa e validação física. Não há AAR/SDK PICO no projeto entregue, não há `libpvr` ou stubs alegando integração. Entregamos a alternativa de navegador efetivamente implementada e um APK honestamente de tela/launcher.
