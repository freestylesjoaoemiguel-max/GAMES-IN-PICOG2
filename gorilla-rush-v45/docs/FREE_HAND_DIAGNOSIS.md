# Diagnóstico — corpo preso / touchpad (após 1.0.6)

## Inspeção antes da correção

- `arm-physics.js`: ao tocar segurando gatilho, `held=true` troca velocidade relativa da mão por `(target - desired) / dt`. Como `target` é uma âncora fixa no mundo, o deslocamento acumulado e a gravidade passam a virar força de retorno mesmo com o controle parado. O gatilho também impede a liberação ao levantar e desativa a trajetória de retorno/anti-tremor da mão estimada. Isso é um mecanismo de agarrar, não uma passada livre.
- `pico.js`: a velocidade do corpo é interpolada para essa reação em todos os eixos. Assim a correção de âncora pode frear o impulso e sustentar o corpo. Não é necessário trocar renderer, câmera ou mapas para corrigir esse caminho.
- Touchpad: assistência vem ligada, e `tracking.js` considera qualquer eixo >0,01 como toque mesmo quando o runtime informa `touched:false`. Eixos residuais passam a acelerar o corpo enquanto se tenta andar pelas mãos. Não há zona morta para essa aceleração.

## Correção planejada

1. Mãos empurram pelo delta relativo ao corpo em contato, com ou sem gatilho. Gatilho mantém funções de menu, pose dos dedos e tirolesa, mas não cria mola/cola no chão ou paredes. Levantar libera a mão também com gatilho segurado.
2. Preservar contato contínuo, âncora visual durante a passada, histerese anti-tremor, calibração, gravidade, limites e colisões. Não substituir movimento por uma velocidade fixa.
3. Assistência do touchpad desligada por padrão; ativação opcional no menu. Respeitar toque explicitamente informado e aplicar zona morta radial no caminho opcional de locomoção.
4. Regressões específicas para gatilho mantido, repouso sem força acumulada, levantamento/retorno sem arrasto, eixos residuais e assistência desligada; repetir testes de mãos/gravidade/colisão.

O diagnóstico identifica caminhos concretos no código, não comprova qual estado exato ocorreu no headset do usuário. Sem G2 físico; 3DoF continua uma adaptação por rotação, não o tracking posicional do Gorilla Tag original.

## Reprodução e resultado

Antes da alteração, os 33 testes existentes passaram e os 3 testes novos falharam: força com mão imóvel/gatilho, soltura com gatilho e toque explicitamente falso. Depois, 36/36 passaram; o teste antigo que exigia agarrar com gatilho foi atualizado para exigir a nova semântica solicitada (sem cola), não simplesmente excluído.

A suíte de frames reais (poses simuladas) também passou: 7 cenários de entrada, 12 de navegador, 13 cenários originais de locomoção e novos casos de mãos livres. Uma passada posicional deslocou 0,4634 m virtuais e continuou 0,2957 m após soltar, com resultados iguais com/sem gatilho. Três passadas 3DoF deslocaram 1,316 / 1,356 / 1,397 m, soltando a mão em cada retorno, também idênticas com/sem gatilho. Corpo voltou ao chão; eixos residuais e ruído dentro da zona morta produziram deriva zero. Assistência opcional explicitamente ligada continua testada.

Renderer/framebuffer/câmera, modelos, mapas, rede, sons, tirolesa e entrada de sessão não foram substituídos. O caminho de agarrar superfícies com gatilho foi intencionalmente retirado: gatilho continua operando dedos, menus e tirolesa. A âncora restante é apenas um ponto visual/de contato durante uma passada, não uma mola que prende o corpo.

Sem instalação/ensaio físico no G2, FPS ou conforto medidos. Resultados exatos reproduzíveis em `NODE_TESTS.txt`, `BROWSER_TESTS.json` e `LOCOMOTION_TESTS.json`.
