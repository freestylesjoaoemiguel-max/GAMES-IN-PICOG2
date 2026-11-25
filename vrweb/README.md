# PICO VR: Navegador (Discord web) para Pico G2

Um **navegador VR** para o **Pico G2 / G2 4K** que abre o **Discord web oficial** (`discord.com/app`) numa tela
flutuante, para você **conversar por texto e falar por voz** com seus amigos, com **teclado virtual completo**.

> **Não é um cliente alternativo do Discord.** É o próprio site oficial do Discord rodando num navegador
> (WebView do Android) dentro do VR; você entra com a sua conta normalmente. Não usa o token da sua conta,
> não altera nem automatiza nada do Discord, então não fere os termos de uso. Não usa a marca/logo do Discord.
> Também serve para abrir qualquer outro site.

**[⬇ Baixar PICO-VR-Navegador.apk](https://github.com/freestylesjoaoemiguel-max/GAMES-IN-PICOG2/raw/ccr-3de8f23e-tx6y3b/release/PICO-VR-Navegador.apk)**
· pacote `com.joaolimz.vrweb` · precisa de internet (permissões: INTERNET, microfone, vibração)

```
adb install -r PICO-VR-Navegador.apk
adb shell pm grant com.joaolimz.vrweb android.permission.RECORD_AUDIO     # microfone (se o aviso não aparecer)
adb shell am start -n com.joaolimz.vrweb/.MainActivity
```

## Como usar
| Ação | Controle do Pico G2 |
|---|---|
| Apontar | o **laser** segue o controle (3DoF) |
| Clicar / arrastar / selecionar | **GATILHO** (segurar e mover = arrastar) |
| Rolar a página | **encoste o polegar no touchpad e deslize** para cima/baixo (sem clicar) |
| Mostrar/esconder o teclado | **APP** (toque curto) |
| Menu | **APP segurado ~1 s** (ou botão *Menu* na barra) |
| Recentralizar | **HOME segurado ~1 s** |

Na primeira vez que o controle conecta abre a **calibração de 6 passos** (frente, cima, direita; touchpad
centro/cima/direita): ela descobre os eixos do *seu* controle. *Menu → Controle Pico → Diagnóstico* mostra os
dados ao vivo.

**Barra acima da tela:** Voltar · Frente · Recarregar · Início (Discord) · A- / A+ (zoom) · Teclado · Menu.
**Teclado:** QWERTY em português, Shift (toque duplo = Caps Lock), `123#` para números e símbolos,
`ã é ç` para acentos, Apagar (segurar repete), Espaço, Enter. Digita no campo selecionado da página; em
*Menu → Abrir endereço / buscar* digita na barra de endereço (sem ponto/`http` vira busca no Google).

## Entrando no Discord
1. Abra o app: ele carrega `discord.com/app`. Aponte para o campo de e-mail, clique e digite pelo teclado.
2. **Dica importante:** se a **verificação (captcha / "verifique que você é humano")** não passar, **entre pelo QR
   code**: na tela de login clique em *Entrar com QR code* e escaneie com o app do Discord no **celular**
   (Configurações → Escanear QR code). Não tem captcha nem senha para digitar.
3. **Voz:** entre num canal de voz. Na primeira vez permita o microfone (ou use o comando `adb ... pm grant` acima,
   ou *Menu → Microfone: PERMITIR*).

### Se a verificação falha
- O app usa o **navegador real do aparelho** (mesma versão do Chromium), só mudando para "modo computador". Ele
  **não finge ser um Chrome mais novo**: isso reprovaria o captcha. *Menu* mostra a versão do motor.
- O WebView do Pico G2 (Android 8) pode ser **antigo demais** para o Discord ou para o captcha. Sem a Play Store
  no G2 não dá para atualizá-lo por aqui. Nesse caso o **QR code** é o caminho. Se nem a página carregar, me diga
  a versão que o menu mostra.
- Ative/desative *Modo do site: COMPUTADOR/PADRÃO* no menu (recarrega a página).

## Limites honestos
- **Não testado em um Pico G2 real nem contra o servidor do Discord** (este ambiente não acessa o Discord).
  Foi validado: compilação, assinatura, shaders e 11 testes na JVM (mira → pixel certo, clique/arrastar,
  teclado com Shift/acentos/Apagar/Enter, barra de endereço, rolagem pelo touchpad, menu).
- A página é desenhada ~15 vezes por segundo (WebView → textura): rolagem e vídeo ficam menos fluidos que no
  navegador normal. *Menu → Qualidade da tela* troca a resolução (LOW 1024×640 é o padrão do G2).
- Voz depende do WebView suportar WebRTC e de haver microfone no headset.
- Chamadas de **vídeo/câmera e compartilhar tela** não são suportadas.

## Técnica
Motor VR próprio compartilhado com os outros jogos do repositório (estéreo, lente, sensores, controle Pico).
`platform/WebPanel.java`: WebView real atrás da superfície GL, desenhado em bitmaps com triple buffering e
enviado como textura (`render/VrRenderer`); eventos de toque, rolagem por JavaScript, texto via `InputConnection`.
Lógica de VR (ponteiro, teclado, barra, menus) em `game/` e testada na JVM com um navegador falso.

Compilar: `./gradlew :vrweb:assembleRelease` ou `MODULE=vrweb tools/build-apk-manual.sh /caminho/android.jar`.
