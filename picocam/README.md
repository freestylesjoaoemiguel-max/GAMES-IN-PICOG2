# PicoCam — câmera do celular no Pico G2

Projeto separado do jogo. Dois apps:

* `phone/` → **PicoCam (Celular)**: liga a câmera traseira em **zoom 0,5x** (ou o menor que o aparelho tiver), mostra o **IP** na tela e transmite vídeo + giroscópio pela rede Wi-Fi.
* `headset/` → **PicoCam (Pico)**: pede o IP, você toca em **Confirmar** e a câmera do celular abre em VR (tela dividida), com giroscópio.

## Usar
1. Celular e Pico na **mesma rede Wi-Fi** (ou o Pico no ponto de acesso do celular).
2. Abra **PicoCam (Celular)** e permita a câmera. Ele mostra o IP (ex.: `192.168.0.12`).
3. Abra **PicoCam (Pico)**, digite o IP no teclado, escolha zoom/modo e toque em **Confirmar**.
4. Modos: **No óculos** (celular preso ao óculos; vídeo preso ao rosto) ou **Na mão** (a imagem aparece na direção para onde o celular aponta, usando o giroscópio do celular).
5. Voltar (botão do controle) para a tela do IP · toque duplo recentraliza.

APKs: baixe na Release `picocam-latest` (workflow *PicoCam APKs*).
