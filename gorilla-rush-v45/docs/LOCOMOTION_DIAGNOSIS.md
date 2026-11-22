# Diagnóstico antes da alteração — base 729a068

Inspeção do caminho em execução (não é Unity/Rigidbody): `source-06.js` chama `PICO.beforeFrame`, `handFrame`, `physics`, `afterPose` e então renderiza. Posição real do jogador: `P`; velocidade: `vel`; colisores existentes: `stepMove`, `wallFix`, `sweepFloor`; escala: `PS`, altura física dos pés: `1.1 * CSC`, olhos: `EYEUP`.

## Causas encontradas

1. `beforeFrame` escolhe uma única fonte, preferindo direita. Lê somente quaternion e botões. Ignora `gripSpace`, `transform.position` e `Gamepad.pose.position`, mesmo quando o runtime fornece translação real. Uma translação sem rotação não muda a mão estimada e não pode produzir impulso.
2. `physics` retorna imediatamente em qualquer `arm.contact`: não integra gravidade e cancela `jumpReq`. Uma âncora aberta em parede/piso pode suspender o corpo indefinidamente. O contato visual está sendo usado como suspensão global da física.
3. `handFrame` move o corpo diretamente e sobrescreve `vel` com uma média da correção de erro da âncora. Não há amostra explícita de velocidade da mão relativa ao corpo; a velocidade cai para zero quando a correção acaba. Esse caminho também mistura a reação de contato com reposicionamento, em vez de ter um integrador único de velocidade/gravidade.
4. A geometria da mão esquerda é removida da cópia local ao entrar no VR. Não há dois estados de contato independentes. A solicitação atual exige suporte a fontes esquerda/direita reais, diferente da restrição antiga de mão direita apenas.

## O que preservar

WebXR/WebVR, parâmetros de renderização/escala por olho, câmera, menus e mapeamento configurável de botões; ambos os mapas e assets; colisão contínua da mão já introduzida; colisores do corpo; áudio; fluxo Solo/rede e configurações. Não criar um jogo novo, trocar engine ou substituir o input de botões.

## Limite de hardware

O controlador original do PICO G2 é 3DoF. Não mede translação independente da mão, nem fornece por si só dois controles posicionais. Ler fontes reais quando existirem; identificar explicitamente posição estimada quando houver apenas orientação. Uma única fonte não deve ser copiada para duas mãos. Sem teste físico, as verificações de esquerda/direita/duas fontes serão fixtures automatizadas, não alegação sobre capacidades do aparelho do usuário.

## Correção e critérios de verificação

- `tracking.js`: valida translação finita e capacidade; fontes únicas por lado. Fonte anônima única é alocada à direita; fontes explicitamente esquerda/direita são respeitadas. Posição emulada ou zero sem indicação explícita não é tratada como translação medida.
- `arm-physics.js`: delta relativo ao corpo / dt, contato/superfície e reação por mão; gravidade não reaparece como velocidade sintética de mão.
- `pico.js`: estende leitura existente, combina reações e integra `vel` uma vez; mantém `stepMove`, `wallFix`, `sweepFloor`. Não há Rigidbody Unity nesse projeto.
- `right-arm.js`: preserva geometria original, usa máscaras locais reversíveis conforme lados conectados.
- `source-06.js`: somente seleção do lado na pegada da tirolesa e no teste local de tag; demais sistemas preservados.

Resultados reproduzíveis: `NODE_TESTS.txt`, `ENTRY_TESTS.json`, `BROWSER_TESTS.json`, `LOCOMOTION_TESTS.json`. Esta última suíte cobre esquerda/direita/duas mãos, parede, rápido/lento, repouso, pulo, queda, colisão corporal, frente/trás/lados; também velocidade máxima, momento após soltar, malha acima do piso, reordenação/desconexão, ausência de propulsão no ar e alternativa 3DoF de ambos os lados. Compositor/poses são fixtures: não mede FPS nem latência no G2 e não certifica o hardware.
