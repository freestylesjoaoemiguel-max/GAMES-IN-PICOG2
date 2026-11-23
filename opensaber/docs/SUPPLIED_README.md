# OpenSaber — Pico G2 (WebXR)

Build do **OpenSaber** (Godot 4.3) recompilado para WebXR, com modificações
para rodar no **Pico G2** (controle 3DoF) e perfil de desempenho para o
Snapdragon 835.

## Jogar (GitHub Pages)

1. Vá em **Settings → Pages**
2. Em *Source*, escolha **Deploy from a branch**
3. Branch: `main` · Pasta: **`/ (root)`** → **Save**
4. Aguarde ~1 min. O jogo fica em `https://SEU-USUARIO.github.io/SEU-REPO/`

O GitHub Pages já serve em **HTTPS**, que é obrigatório para WebXR.

## No Pico G2

1. Instale o navegador **Wolvic (port G2)**:
   https://github.com/C-Y-T-258/wolvic-pico-g2-port/releases/latest
2. Abra a URL do Pages no Wolvic
3. Toque em **Enter VR Mode**

## Estrutura

| Arquivo | O que é |
|---|---|
| `404.html` | **o jogo** (renomeado para o Pages servir em qualquer subcaminho) |
| `index.html` | redireciona para `404.html` |
| `index.wasm` | engine Godot (34 MB) |
| `index.pck` | o jogo (11 MB) |
| `.nojekyll` | impede o Jekyll de mexer nos arquivos |
| `.gitattributes` | marca `.wasm`/`.pck` como binários |

### Por que `404.html`?

O Godot web carrega `index.pck` e `index.wasm` com caminhos relativos a
`index.html`. Em um repositório de projeto o jogo fica em `/SEU-REPO/`, e
apontar para `/index.html` daria 404. O GitHub Pages usa `404.html` para
qualquer rota inexistente — então renomeando o jogo para `404.html` ele
funciona direto na raiz do repositório, sem subpasta.

## Limite de tamanho do GitHub (importante)

O `index.wasm` tem **34 MB** — acima do limite de **25 MB** para arquivos
enviados pela interface web do GitHub.

Para subir, use **Git LFS**:

```bash
git lfs install
git lfs track "*.wasm"
git add .gitattributes
git add .
git commit -m "OpenSaber WebXR para Pico G2"
git push
```

Se não quiser LFS, hospede a pasta em **Netlify**, **Cloudflare Pages** ou
**Vercel** (arraste a pasta, sem limite de 25 MB) — todos servem HTTPS.

## Modificações aplicadas ao código original

- `game/scripts/BeepSaberController.gd` — pose 3DoF: posição vem do olhar da
  cabeça, rotação vem do giro real do controle. Em 6DoF não altera nada.
- `game/scripts/g2_perf.gd` — escala de render ajustável (60–100%), padrão 80%.

Código-fonte completo e diffs: no workspace, pasta `OpenSaber-G2/`.
