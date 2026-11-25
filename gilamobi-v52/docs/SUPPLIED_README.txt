GILAMOBI 0.52 — CÓDIGO-FONTE

Este pacote contém o projeto da versão v52 para celular e VR Box. Não contém o modo VR nativo do PICO nem os experimentos separados do PICO.

PASTAS
- projeto-godot/: projeto completo Godot; abra o arquivo project.godot usando Godot 4.4.1 estável. Contém cenas, scripts, imagens, modelos, sons, licenças, créditos e as bibliotecas já compiladas da CameraServerExtension para Android 32/64 bits e Linux x86-64.
- android-java/: fontes Java do plugin Android para câmera, MediaPipe, sensores, voz e gravação/publicação do vídeo na galeria.
- camera-extension/: código-fonte C++/Android e licença da CameraServerExtension. O submódulo godot-cpp deve ser obtido separadamente, caso se queira recompilar a extensão.
- ferramentas-referencia/: scripts usados para gerar o dex e adaptar o template, fornecidos como referência técnica. Eles usam caminhos absolutos do ambiente original e requerem adaptação; NÃO são uma compilação automática pronta para outro computador.
- modelos/: arquivo hand_landmarker.task usado pela detecção experimental de mãos.

ABRIR NO GODOT
1. Instale o Godot 4.4.1 e abra projeto-godot/project.godot.
2. Aguarde a importação dos recursos. O código do jogo pode ser editado normalmente.
3. Consulte CREDITOS-MODELO.txt, LICENSE.txt, THIRD_PARTY_HAND_TRACKING.txt e as licenças dentro de addons antes de redistribuir.

GERAR APK
O preset de exportação foi conservado como referência, mas o template Android personalizado usado no APK v52 NÃO está incluído neste ZIP. O caminho absoluto em projeto-godot/export_presets.cfg é do ambiente original; precisa ser substituído por um template preparado com os plugins. O APK oficial de template do Godot sozinho não inclui o plugin Java de câmera/mãos/voz/gravação nem as dependências MediaPipe. Para reconstruí-lo, também são necessários o Android SDK e ferramentas de build, JDK 17, as dependências do MediaPipe Tasks Vision 0.10.21 (inclusive JNI para ambas arquiteturas), os templates Android Godot 4.4.1 e configuração dos plugins, além de adaptar as ferramentas de referência. O godot-cpp e demais dependências não estão todos incluídos.

Assine o APK com sua própria chave. Um APK assinado com chave diferente não atualiza diretamente o GilaMobi instalado com outra assinatura; trocar a instalação pode afetar dados locais. Não compartilhamos nenhuma chave de assinatura neste pacote.

REDE
A versão usa o protocolo/tópicos v19 e o broker público test.mosquitto.org, como na v52. O broker é público e sua disponibilidade, privacidade e capacidade não são garantidas.

AVISOS
Este é o código do jogo v52, não uma garantia de compatibilidade com todos os aparelhos. A gravação Android e outras funções podem precisar de testes em aparelhos reais.
