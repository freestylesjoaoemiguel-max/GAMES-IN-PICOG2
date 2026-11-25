# WebXR Layers compatibility

`webxr-layers-polyfill.js`: upstream **webxr-layers-polyfill 1.1.0**, Apache-2.0, https://github.com/immersive-web/webxr-layers-polyfill . Obtained from the pinned npm package, not a CDN at runtime. License: `webxr-layers-LICENSE.txt`.

Local change: in the pre-frame clear pass, capture `FRAMEBUFFER_BINDING` **before** binding the temporary framebuffer, so the application framebuffer is restored correctly. The change is marked `GilaMobi patch` in the file. Other upstream code and its warnings are retained.

This implements projection layers on top of the browser's **real XRWebGLLayer**. It does not simulate a headset or add WebXR to unsupported browsers. Godot still requires WebGL2 and OVR_multiview2. The upstream warning concerning texture arrays remains relevant; success in the Chromium/IWER fixture does not establish compatibility with a particular Pico/Gecko version.

IWER is used only in development tests and is NOT included in this runtime directory.
