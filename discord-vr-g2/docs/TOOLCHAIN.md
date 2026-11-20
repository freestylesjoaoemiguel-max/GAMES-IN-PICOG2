# Proveniência da toolchain Android

## O que foi executado

O ambiente não possuía Java/Android SDK/Unity. Downloads diretos de releases externos e do Google não estavam acessíveis. Foi possível instalar um runtime Java e ferramentas públicas por distribuições disponíveis no PyPI/npm. Não foram contornadas restrições de rede e não foi usado o APK enviado anteriormente.

- `jdk4py==21.0.8.2`, PyPI: runtime Java 21 e keytool. Licenças do OpenJDK/distribuição (GPL com Classpath Exception e avisos correspondentes). Não é incluído no APK.
- `@drxiaozhi/minapk@0.5.1`, npm, usado **exclusivamente como mirror de quatro build tools**: ECJ, android.jar, D8 e apksigner. O pacote contém notices e licenças dessas ferramentas. Não executei seus scripts de build, não usei seu MainActivity, WebView, Bun, template ou keystore.
- `aaptjs3@2.0.2`, npm: somente o binário AAPT2 para Linux x86_64. O wrapper JavaScript não é usado no aplicativo.

Os mirrors não são uma distribuição PICO, não fornecem rastreamento de controle e não certificam compatibilidade de hardware. O script fixa os hashes dos arquivos efetivamente usados. As alegações do mirror sobre igualdade com arquivos oficiais **não foram independentemente comparadas contra downloads Google nesta sessão**, pois esses hosts estavam inacessíveis.

## Hashes SHA-256 verificados pelo build

| Arquivo | SHA-256 |
|---|---|
| android.jar (API 34, segundo notice do mirror) | `6cea1df3efb77103ac3e2beb9bf4718964b0e0869ab16d39d29d5cbae1c147ad` |
| d8.jar | `d43c8a94c9b1f1da1a7cc49c32b81e8cee1708b37ee8b530a81a0688222b42c0` |
| apksigner.jar | `eefdd6aed9db9fb849e4c98a50d8741e19d1b674ba6547220bcb9c3ed152123a` |
| ecj-3.45.0.jar | `c8f5e66787ea6e4e0394ba3051c86a4edc50683ce9850f71739646529a99a2d7` |
| AAPT2 Linux | `e4dff6060827a3e401bce376e47916f2f93a89602af54973ffd3fdfc2528be3f` |

Versões observadas: ECJ 3.45.0; D8 8.2.2-dev; AAPT2 2.20-15009934. A biblioteca de compilação é API 34, mas o manifest define **min 23/target 28**; compilar contra uma API mais nova não valida automaticamente métodos no Android antigo. O código procura usar APIs existentes em 23. A aceitação no dispositivo permanece necessária.

ECJ gera Java 8; D8 faz o desugaring de lambdas. A compilação usa tipos Java do runtime de build mais `android.jar`. Nenhuma classe do runtime Java desktop é empacotada: só classes próprias e a saída do desugaring. Regras de compatibilidade foram revisadas, mas não substituem o teste Android.

## Licenças dos tools

- ECJ: Eclipse Public License 2.0.
- Android framework/AAPT2/apksigner: termos AOSP/Apache 2.0 e respectivas dependências/avisos; artefatos empacotados do SDK também sujeitos aos termos do Android SDK.
- R8/D8: BSD 3-Clause e licenças de dependências listadas pelo upstream.
- Binários/mirrors conservam seus notices sob `.cache/android/.../NOTICE`, `LICENSES` e `THIRD_PARTY_NOTICES` após bootstrap. Não são versionados nem distribuídos no APK.

Antes de usar a toolchain em produção, audite os mirrors ou substitua-os por seus próprios downloads oficiais verificados. O script rejeita mudanças de hash; atualize-o deliberadamente quando trocar versões. Não reutilize automaticamente ferramentas obtidas de fontes desconhecidas.

## Assinatura

Keystore original de desenvolvimento gerada por keytool, RSA 2048, mantida fora do Git. Sem senha embutida e sem uso da keystore de exemplo do mirror. Assinaturas v1/v2/v3 verificadas por apksigner. A assinatura não afirma que o app foi testado, autorizado ou distribuído pela PICO/Discord.

`docs/ANDROID_BUILD.txt` registra metadados, certificado público, verificação e SHA-256 reais. Senhas, tokens e chave privada não estão no relatório.
