extends CanvasLayer
var webxr_interface: WebXRInterface
var vr_supported := false
var _starting := false
var _browser
var selected_sources: Dictionary = {}

func _select_start(source_id: int) -> void:
    selected_sources[source_id] = true

func _select_end(source_id: int) -> void:
    selected_sources.erase(source_id)

func source_selected(tracker_name: StringName) -> bool:
    var source_id := 0 if tracker_name == &"left_hand" else 1 if tracker_name == &"right_hand" else 2
    return selected_sources.get(source_id, false) == true

func clear_source(tracker_name: StringName) -> void:
    var source_id := 0 if tracker_name == &"left_hand" else 1 if tracker_name == &"right_hand" else 2
    selected_sources.erase(source_id)

func _ready() -> void:
    $Button.text = "INICIAR VR"
    $Button.pressed.connect(_on_button_pressed)
    if OS.has_feature("web"):
        _browser = JavaScriptBridge.get_interface("opensaber")
    webxr_interface = XRServer.find_interface("WebXR") as WebXRInterface
    if webxr_interface:
        webxr_interface.session_supported.connect(_webxr_session_supported)
        webxr_interface.session_started.connect(_webxr_session_started)
        webxr_interface.session_ended.connect(_webxr_session_ended)
        webxr_interface.session_failed.connect(_webxr_session_failed)
        webxr_interface.selectstart.connect(_select_start)
        webxr_interface.selectend.connect(_select_end)
        webxr_interface.is_session_supported("immersive-vr")

func _webxr_session_supported(mode: String, supported: bool) -> void:
    if mode == "immersive-vr": vr_supported = supported

func _on_button_pressed() -> void:
    if _starting: return
    if not vr_supported:
        OS.alert("Este navegador nao oferece WebXR immersive-vr. Use um navegador VR compativel no headset. Uma tela comum nao e VR.")
        return
    _starting = true
    webxr_interface.session_mode = "immersive-vr"
    webxr_interface.requested_reference_space_types = "local-floor,local"
    webxr_interface.required_features = ""
    webxr_interface.optional_features = "local-floor"
    if not webxr_interface.initialize():
        _webxr_session_failed("O navegador recusou a sessao.")

func _webxr_session_started() -> void:
    _starting = false
    $Button.visible = false
    get_viewport().use_xr = true
    vr.inVR = true
    if _browser != null: _browser.immersive = true
    # A local-only reference space is eye-level, unlike local-floor.
    var game = GlobalReferences.main_game_scene
    game.xr_origin.position.y = 1.65 if webxr_interface.reference_space_type == "local" else 0.0
    await get_tree().process_frame
    game.recenter()
    print("[G2] WebXR session: " + webxr_interface.reference_space_type)

func _webxr_session_ended() -> void:
    selected_sources.clear()
    _starting = false
    $Button.visible = true
    get_viewport().use_xr = false
    vr.inVR = false
    if _browser != null: _browser.immersive = false
    var game = GlobalReferences.main_game_scene
    if game.gamestate == game.gamestate_playing:
        game._transition_game_state(game.gamestate_paused)
    game.xr_origin.position.y = 0.0

func _webxr_session_failed(message: String) -> void:
    _starting = false
    $Button.visible = true
    if _browser != null: _browser.immersive = false
    OS.alert("Nao foi possivel iniciar VR: " + message)
