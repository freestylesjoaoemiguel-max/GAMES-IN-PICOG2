# Discord VR G2 — passo a passo fácil

## 1. Abrir o WebXR

Abra este endereço em uma aba do navegador (não precisa baixar ZIP):

**https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/discord-vr-g2/**

O site original PicoVerso continua na raiz; o Discord VR G2 fica na pasta acima.

## 2. Experimentar

1. Você começa no **modo local**, sem precisar de conta.
2. Digite no campo de mensagem e toque na seta de envio.
3. Clique em **Prévia espacial** (no computador) ou **Entrar em VR**.
4. Na tela espacial, clique em **Teclado** para mostrar as teclas 3D.
5. Use **Recentrar** para colocar o painel à sua frente.

## 3. Entrar em VR de verdade

1. Abra o mesmo link no navegador do headset.
2. Toque em **Entrar em VR** / **Entrar WebXR**.
3. Se o navegador pedir permissão, aceite.
4. Aponte o controle e use o gatilho, se o runtime WebXR fornecer esse controle.

Se aparecer **WebXR não disponível**, o navegador não oferece VR imersivo compatível. A prévia 3D não significa que o VR imersivo funciona. O navegador antigo do PICO G2 pode não suportar WebXR; o site não consegue adicionar esse suporte ao firmware.

## 4. Baixar o APK sem login GitHub

**https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/discord-vr-g2/downloads/Discord-VR-G2.apk**

É um arquivo APK, não um ZIP. É uma build de desenvolvimento Java/OpenGL, sem validação física no G2, sem runtime PICO e sem pose proprietária do controle.

Para levar o site a outra hospedagem HTTPS, baixe:

**https://freestylesjoaoemiguel-max.github.io/GAMES-IN-PICOG2/discord-vr-g2/downloads/Discord-VR-G2-WebXR.zip**

Extraia o ZIP e hospede os arquivos. Não abra `index.html` por `file://`.

## 5. Entender o modo local

GitHub Pages hospeda arquivos estáticos, não o backend OAuth2. Portanto, neste endereço, **não há login Discord, voz, DMs ou recebimento real**. As mensagens são demonstrações locais, claramente identificadas na interface. Para a integração oficial limitada, é necessário hospedar o backend Node por HTTPS e configurar sua própria aplicação OAuth2; veja o README. Nunca coloque client secret, senha ou tokens no Pages.

## 6. Se o site não atualizar

1. Abra a aba **Actions** do repositório.
2. Abra a execução mais recente de **Deploy GitHub Pages**.
3. Aguarde os dois jobs, **build** e **deploy**, ficarem verdes.
4. Recarregue o site. Se necessário, tente uma aba anônima.

O Pages publica automaticamente após alterações na `main`. O workflow **Discord VR G2 - APK and WebXR** compila o APK separadamente e fornece artefatos de desenvolvimento. O Pages também recompila o APK no GitHub. Sem keystore estável configurada, cada execução usa uma nova chave de desenvolvimento: uma atualização pode exigir reinstalação e apagar dados locais. Não é uma release com assinatura estável.
