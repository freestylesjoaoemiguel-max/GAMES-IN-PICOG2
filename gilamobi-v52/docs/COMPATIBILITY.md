# Auditoria técnica e limites de validação

## Entrada recebida

`GilaMobi-v52-codigo-fonte.zip`, Drive `18jvpQ-GywpUh_D2ToaoyKRhdvKUau470`, 36.136.433 bytes, MD5 `7f57c645cfba2d59979b729eec3f481b`. Inventário completo em `SUPPLIED_FILES.json`: 262 entradas no ZIP, 41.999.445 bytes descompactados. Não foi substituído por Metulas ou Gorilla Rush.

Projeto Godot **4.4.1**, renderizador Compatibility. O preset recebido era Android e fazia referência a caminhos do autor e a componentes nativos/custom template. A cena original implementava VRBox/giroscópio/toque, não integração com o runtime VR do G2. A extensão Android de câmera não pode ser carregada por WebAssembly. Ela foi retirada dos addons ativos, mas fontes/binários foram preservados como referência.

## Dispositivo e navegador

As referências públicas distinguem G2/G2 Pro (1440×1600 por olho, 90 Hz nominais) de G2 4K (1920×2160, 75 Hz nominais). A família usa Snapdragon 835/Adreno 540 e tracking 3DoF; normalmente um controle 3DoF. Esses números **não são resultados de medição deste projeto**.

Referências consultadas durante a auditoria:
- https://pico.crx.moe/docs/about-pico/legacy/ — documentação comunitária de dispositivos legados.
- https://vr-compare.com/headset/picog24k — usado apenas para distinguir o modelo 4K, não para identificar o G2 do usuário.
- https://github.com/C-Y-T-258/wolvic-pico-g2-port — porte comunitário Wolvic/PicoVR legado. Existência desse projeto não comprova funcionamento deste jogo, nem constitui auditoria de seu APK.

**Faltam:** versão exata do navegador, firmware, resultado de `isSessionSupported`, extensão WebGL no aparelho e teste físico. Um Android que aceita APK/OpenXR não implica suporte WebXR. WebVR/WebGL1 não bastam para este export Godot.

## Renderização e custo

- Export single-thread, WebGL2 Compatibility, sem dependência de extensão nativa.
- Escala baixa 0,60 preservada na prévia comum e espelho em qualidade baixa; em VR, render target na resolução determinada pelo runtime. Não há promessa de 90 FPS.
- A tentativa de usar a escala 0,60 em VR produziu tela preta e `number of views ... does not match` no teste estéreo. Diagnóstico: Godot 4.4.1 `RasterizerSceneGLES3::_post_process` usa FBOs com `glFramebufferTextureLayer`, mas o shader de `post_copy` recebe `USE_MULTIVIEW`. Evitado o caminho escalado no target XR, sem duplicar imagem mono.
- Ao sair de VR, o render target é recriado explicitamente em mono, preservando as dimensões finais. Sem isso, o teste reproduziu FBO multiview residual mesmo após `use_xr=false`.
- Layers polyfill 1.1.0 aplica composição extra em navegadores antigos. Tem custo GPU e advertência upstream sobre texture arrays. É necessário testar no G2 real.
- Avisos de startup/transição `texParameter: no texture bound to target` foram observados no motor exportado; os relatórios os preservam. Não se afirma execução sem warnings.
- Assets originais não receberam redução destrutiva indiscriminada. Pele dividida por pesos permite ocultar cada braço desconectado. A duplicação de arrays de vértices na partição tem custo de memória; requer profiling em aparelho antes de qualquer promessa de otimização final.
- Espelho, rede, NPCs e câmera continuam custosos. Não foram removidos para apresentar um falso benchmark. Não há perfil GPU Adreno, teste térmico nem medição motion-to-photon.

## Níveis de evidência

1. **Motor real:** importação/compilação/exportação Godot oficial; fixtures instanciam cena, rig, mapas e consultas de física reais. Relatório `ENGINE_TESTS.json`.
2. **Navegador real, hardware emulado:** Chromium software WebGL2 + IWER; app usa seu WebXRInterface, PCK e WASM. Exercita duas views, poses, conexão/perda, gatilho, raio no menu, mapas, troca de rig e sessões. IWER não é carregado na versão publicada.
3. **Inspeção visual:** capturas locais do teste mostraram a cena recebida, dois olhos e braço conectado. Capturas são material de teste, não fotos do Pico.
4. **Não validado:** compositor/PicoVR/driver/controle físico, FPS/latência, equilíbrio corporal real, permissões de microfone/download, rede com dois aparelhos e experiência completa de combate.

O braço estimado é cinemático, alimentado pela orientação do controle e âncora da cabeça, limitado por consultas de colisão reais. Não reconstrói posição física perdida pelo 3DoF. O núcleo de locomoção transfere regras do ZIP GMotion recebido (Lights Out: Bear Facility), **não código oficial ou fidelidade certificada de Gorilla Tag**.
