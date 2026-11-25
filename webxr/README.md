# Pico G2 · WebXR — jogos e ambientes direto no navegador

Nada para instalar: abra uma página **HTTPS** no **Navegador do Pico**, toque em **ENTRAR EM VR**.

| Modo | O que é | Precisa de arquivos? |
|---|---|---|
| 🦍 **Pega-Pega** | Estilo *Gorilla Tag*: balance o(s) braço(s) para empurrar chão, paredes e árvores; solte para lançar o corpo. 5 bots (um é o pegador). | Não |
| 🔫 **Operation Zero** | Atirador em ondas de robôs: mire com o controle (raio), gatilho atira, touchpad anda. | Não |
| 🌍 **Ambientes (Metulas)** | Visita os **7 ambientes 3D** do `Metulas.apk` (bolhas, cidade cyber, deserto, domo, ryokan, estação espacial, cabana de inverno). | Sim: **o seu** `Metulas.apk`, escolhido na página (lido só no aparelho) |

> O Metulas **não é um jogo**: é um *lançador/shell* de ambientes. Por isso aqui ele vira um **visitante de ambientes** (dá para andar, voar, trocar de ambiente), não um jogo.

## 1. Colocar o site no ar (uma vez, 1 minuto)

O repositório é público e o workflow `.github/workflows/pages-webxr.yml` publica o site na branch `gh-pages` a cada push que mexe em `webxr/`.

1. GitHub → **Settings → Pages** → *Build and deployment* → **Source: Deploy from a branch** → **Branch: `gh-pages` / `(root)`** → Save.
2. Aguarde ~1 min. O endereço fica: **`https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/`**
3. No Pico: Navegador → digite o endereço (ou gere um QR code do endereço e abra pelo celular → "enviar para o headset").

Alternativas sem GitHub Pages: arraste a pasta de `release/pico-g2-webxr-site.zip` (descompactada) para <https://app.netlify.com/drop>, ou use qualquer hospedagem estática com HTTPS. WebXR **só funciona em HTTPS** (ou `localhost`; com ADB: `adb reverse tcp:8080 tcp:8080` e abra `http://localhost:8080` no headset).

## 2. Como usar no Pico G2

1. Abra a página. Os selos mostram o que o navegador suporta: **HTTPS ✔**, **WebXR immersive-vr ✔**, **ASTC ✔**.
2. Escolha o modo. Em *Ambientes*, selecione o `Metulas.apk` (coloque-o antes em *Downloads* do headset).
3. **ENTRAR EM VR**.
4. Dentro da VR: **olhe para baixo** → aparece o botão **☰ MENU** (trocar de modo, configurar, mapear botões, diagnóstico, sair). Aponte o controle (raio azul) e use o **gatilho**.

### Controles (Pico G2 = **1 controle, 3DoF**: só rotação, sem posição)

| Ação | Controle |
|---|---|
| Selecionar/atirar | **Gatilho** |
| Andar | **Touchpad/analógico** (para onde o controle aponta; opção "cabeça" nas configurações) |
| Ação extra (recarregar / ligar voo nos ambientes) | Qualquer outro botão que o navegador reporte |
| Menu | Olhar para baixo → ☰ MENU (ou mapeie um botão em *Mapear botões*) |
| Pega-Pega | A posição da mão é **estimada** a partir da rotação do controle (modelo de braço: ombro + comprimento do braço). Com 1 controle, a outra mão **espelha** a primeira |

Os **números dos botões/eixos do G2 no navegador não são documentados** e variam por firmware. Por isso nada é fixo: o app lê `XRInputSource.gamepad` em tempo real, mostra os índices no **Diagnóstico** (página inicial e menu VR) e o menu **Mapear botões** deixa você apertar o botão desejado para cada ação. Se algo ficar invertido: *Configurações → Inverter frente / lados*.

### Ambientes (Metulas)
Menu do painel: ◀ ▶ trocam o ambiente; **Voo** liga/desliga voar na direção do controle; **Altura ±10 cm**; **Escala ±**; **Velocidade ±**; **Recentrar**. A qualidade das texturas (512–4096 px) controla o uso de memória: o padrão é 2048 e os níveis maiores do mipmap são descartados.

## 3. Se não funcionar (diagnóstico honesto)

* **"Sem WebXR ✖"** ou **"immersive-vr não suportado"**: o navegador/firmware do seu G2 não oferece WebXR. Atualize o sistema e o Navegador do Pico. Se ainda assim não houver, não existe como forçar WebXR por página web — nesse caso só resta o modo 2D de teste (janela comum) ou os APKs nativos já existentes no repositório.
* **"Sem HTTPS ✖"**: abra o endereço `https://…`.
* **"ASTC ✖"**: os ambientes aparecem só com cores (as texturas do APK são ASTC). Pega-Pega e Operation Zero não dependem disso.
* **Controle não aparece**: acorde-o apertando um botão; veja o Diagnóstico → *Controles*.
* Toque em **Copiar diagnóstico** na página inicial e me envie o texto: com ele eu ajusto o mapeamento ao seu G2 real.

## 4. O que foi (e o que NÃO foi) testado

**Testado automaticamente (neste repositório):**
* `npm test` — 20 testes: leitor de ZIP/APK por fatias, parser do formato de cena, leitor KTX/ASTC, detecção de eixos/botões e remapeamento, modelo de braço 3DoF, locomoção por braços (empurrar/lançar/colisão), lógica dos jogos. Com `METULAS_ENV_DIR=…/assets/env` também valida as **7 cenas reais**.
* `npm run e2e` (Chromium headless + [IWER](https://github.com/meta-quest/immersive-web-emulation-runtime), um emulador WebXR configurado como **1 controle estilo G2: gatilho + touchpad + 2 botões**): entra numa sessão `immersive-vr` real, detecta a entrada, clica botões de menu com o raio, anda com o touchpad, atira, recarrega e empurra o chão com o braço virtual; abre o `Metulas.apk` real (260 MB) e carrega/renderiza os 7 ambientes.

**NÃO testado — não tenho acesso ao seu Pico G2:**
* Se o seu firmware/navegador expõe WebXR e **quais índices** o controle usa de fato.
* Desempenho (FPS) no hardware real: cenas têm 56 mil a 312 mil triângulos e até ~360 objetos; a resolução de renderização (padrão 75 %) e as texturas podem precisar de ajuste.
* Conforto, latência e a "sensação" do braço virtual 3DoF.
Portanto: **não** afirmo que está 100 % funcional no G2 — o que está provado é o código, o formato dos arquivos e o comportamento num emulador.

## 5. Sobre os ambientes do Metulas (direitos e como foram lidos)

* Este repositório/site **não contém nenhum arquivo do Metulas**. O APK é escolhido por você e lido localmente (`Blob.slice`), nunca enviado a lugar algum.
* O formato (`assets/env/*/scene.bin` "V40S" + texturas ASTC `.ktx`) **não é documentado**; foi deduzido dos próprios dados:
  * cabeçalho `V40S`, versão 2, raio da cena, nº de nós; nós de 52 bytes (pai, posição, quaternion, escala, malha, campo auxiliar);
  * malhas (vértices 20 B = posição+UV, ou 52 B para malhas "animadas"; índices u32), materiais (textura, flags, cor), nomes de textura.
* Detalhes assumidos/limitações: **animações são ignoradas** (palmeiras, peixes, etc. ficam parados); a unidade é metros ou centímetros (heurística pelo raio da cena); a origem da cena é o chão do ponto de vista original; materiais com `flags=1` são tratados como translúcidos (névoa/vidro/brilho em aditivo). Ainda não há colisão (você flutua/anda livremente).
* Se você não tem o direito de usar esses ambientes, não use. Eles pertencem aos autores originais.

## 6. Estrutura

```
index.html            página 2D (selos de suporte, escolha de modo/APK, diagnóstico)
css/style.css
src/main.js           liga página + painéis de VR (menu, configurações, mapear botões, diagnóstico)
src/app.js            renderer, sessão XR (local-floor com fallback local), entrada, laser/UI, modo teste 2D
src/inputmap.js       detecção de botões/eixos, remapeamento (puro, testável)
src/armmodel.js       posição da mão a partir da rotação (3DoF)          [puro]
src/loco.js           locomoção por braços estilo Gorilla                [puro]
src/ui.js, audio.js   painéis 3D com raio; áudio sintetizado
src/apk.js, scene.js, ktx.js   leitor de APK por fatias, cena V40S, texturas ASTC
src/modes/            viewer.js (ambientes), pegapega.js, fps.js
tools/build.mjs       esbuild -> dist/app.js (three.js embutido, alvo chrome80)
tests/                unitários (node --test) e e2e/ (Playwright + IWER)
```

Desenvolvimento: `cd webxr && npm ci && npm run build && npm test`; servir com `python3 -m http.server 8080`.

## 7. Discord / conversar com amigos
O próprio **Navegador do Pico** abre `discord.com/app` normalmente (teclado virtual do sistema e microfone do headset) — não precisa de app nem de WebXR para isso.

## Licença
Código próprio: MIT (`LICENSE`). three.js (MIT) embutido. Ver avisos em `dist/THIRD-PARTY-LICENSES.txt`.
