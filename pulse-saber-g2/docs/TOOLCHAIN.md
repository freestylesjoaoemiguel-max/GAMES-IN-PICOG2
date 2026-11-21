# Toolchain Android do Pulse Saber G2

A mesma toolchain verificada do aplicativo independente Discord VR G2 é reutilizada somente para **compilação**, sem incorporar seu cliente, UI ou integração. Veja [proveniência e hashes completos](../../discord-vr-g2/docs/TOOLCHAIN.md).

- Java/keytool: jdk4py 21.0.8.2 via PyPI.
- ECJ 3.45.0, android.jar, D8 e apksigner: mirror npm @drxiaozhi/minapk 0.5.1, só os build tools públicos. Nenhum template, WebView, runtime ou keystore desse pacote é utilizado.
- AAPT2 Linux: aaptjs3 2.0.2. Build script verifica SHA-256 de todas as cinco ferramentas.
- Manifest e três classes Java são próprios. D8 faz desugaring Java 8. Min API 23, target 28.
- Os assets originais/CC0 são incluídos em assets/. São comprimidos no APK; o MediaPlayer recebe uma cópia privada de cada WAV no cache do app ao tocar aquela faixa.
- Código/DEX/resources/assets/manifest são empacotados, assinados com chave de desenvolvimento original e verificados por apksigner. Sem chave privada, senha ou tokens nos artefatos.

O bootstrap também reconstrói o APK Discord para validar a instalação compartilhada das ferramentas, mas a saída Pulse é separada e usa outra chave/package.

Não há um runtime PICO embutido. Aprovação de assinatura APK não significa compatibilidade óptica, de controle ou com o firmware do G2. A comparação dos mirrors com downloads Google não pôde ser feita neste ambiente; verificação de integridade usa hashes registrados dos tools e notices dos distribuidores. Para produção, audite ou substitua por ferramentas oficiais baixadas por você.

Build CI documentada na aba Actions; build estática local em ANDROID_BUILD.txt. Instalação/runtime Android não foram executados neste ambiente.
