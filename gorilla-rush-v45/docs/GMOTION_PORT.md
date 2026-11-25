# Port da locomoção enviada pelo usuário

## Entrada examinada antes da edição

Drive `1EXzcK1RDot5FTe49oqlknTC_yc4SP3xe`: `Gorilla Movement copy (1).zip`, 16.087 bytes, MD5 `887b7e9eb6f9f675aabbcd8c00d9c5cd`. Os seis arquivos estão preservados byte a byte em `reference/gorilla-movement`; hashes em `GMOTION_IMPORT.json`.

O README identifica a origem como Lights Out: Bear Facility, baseado em gmotion_alpha_2.html. É um motor JavaScript/Three/Rapier estilo Gorilla Tag, não prova de procedência oficial da Another Axiom. Não veio arquivo de licença; não atribuímos MIT nem certificação ao ZIP.

## Diferença para a 1.0.7

A 1.0.7 aplicava velocidade da mão relativa ao corpo, ganho 1,15 e interpolação, com gravidade/atrito em cada frame. O ZIP usa followers bloqueados, média de `(follower - target) / dt` das mãos em contato, suporte sem gravidade durante contato e salto ao soltar pela média de oito deslocamentos efetivos do corpo. Threshold de soltura 0,4; ganho 1,1; cap 4 m/s; gravidade padrão 5 m/s². Os parâmetros são convertidos pela escala PS do jogo.

## Integração executada

- Portar o núcleo de reação/histórico/soltura, não apenas copiar um arquivo sem executá-lo.
- Reutilizar a colisão contínua esfera/triângulos existente e o único integrador corporal, evitando dois motores moverem P no mesmo frame. Não trocar Three r128/renderizador por r160/Rapier nem encolher a mão visível para a esfera de 3 cm da demonstração.
- Manter raio que envolve o modelo, adaptação de entrada 3DoF, fontes independentes e histerese de retorno. Posições medidas ignoram a estimativa.
- Gatilho não agarra superfícies; touchpad permanece opt-in. Menu/teleporte/perda de fonte limpam o histórico, impedindo impulso antigo.
- A mão apoiada pode sustentar o corpo, como no ZIP; quando solta, gravidade volta. Isso difere de flutuar sem contato. Testar explicitamente suporte e queda após soltar.
- Manter proteção contra saltos de pose e limite de velocidade de contato por segurança. Conservar mapas, câmera/VR, sons, tirolesa, água, modelos e rede.

É um port com adaptações explícitas de colisão/escala/entrada, não execução binariamente idêntica ao Rapier. Sem headset físico; 3DoF não mede translação nem cria um segundo controle.

## Validação e diferenças observáveis

- Os 5 checks do exemplo enviado passaram com suas dependências originais, isoladas do runtime (`GMOTION_REFERENCE_TESTS.txt`). O script original só imprime resultados; o port tem asserções bloqueantes próprias.
- 43 testes Node, incluindo hashes dos seis arquivos recebidos, constantes, média de duas mãos, histórico de deslocamento efetivo, cap e reset. Um teste executa o `update()` de referência substituindo somente imports e injetando um backend determinístico para isolar as seções 9–11; comparação numérica em 30/60/72/90 Hz. Isso não afirma equivalência de Rapier com nosso colisor.
- Frame real: uma passada de 0,45 m produz 0,45 m de corpo; mesma distância de 0,4 m em 8/40 frames gera picos de 3,6/0,72 m/s e lançamentos de 3,96/0,792 m/s. A média vem do corpo após colisão, não da velocidade pretendida.
- Testes específicos: contato de ambas as mãos, fonte única, 3DoF, gatilho mantido, apoio na parede seguido de retração/queda, pulo por clique a partir de palma apoiada, velocidade cap 8, impulso de soltura cap 4, escalas 0,14/0,25/0,5 e 30/60/72/90 Hz.
- Os testes antigos que exigiam gravidade permanente durante contato e ausência de correção do follower foram substituídos por verificações de suporte estável + soltura real. Isso é uma mudança deliberada de semântica solicitada pelo ZIP, não remoção de cobertura de queda/colisão.
- Sem novo renderer, SDK ou WASM; dependências do jogo não mudaram. A esfera de 3 cm e o teleporte vertical do ZIP não são usados: preservamos a mão visível com raio conservador e deslocamento corporal varrido/limitado. O frame corporal antigo é o único integrador, com gravidade 5 fora de apoio.

Versão final: **1.0.8/code 10**, cache `v45-gmotion-10`, painel **FISICA 1.0.8 / GMOTION / SO MAOS**. Sem hardware G2, compositor real, FPS, instalação Android ou multiplayer validados.

### Reinício de pose não é soltura física

A revisão final também limpa o histórico corporal quando o estimador sinaliza salto de pose/recentralização internamente, não só nos resets externos. A perda de contato causada por um salto inválido de dois metros não pode disparar o impulso 1,1×. Teste no frame real verifica velocidade prévia preservada sem lançamento extra. As fixtures de soltura usam levantamento plausível (<0,65 m por frame); não confundem um teleporte de controle com um gesto. Cache final `v45-gmotion-10`, APK code 10.
