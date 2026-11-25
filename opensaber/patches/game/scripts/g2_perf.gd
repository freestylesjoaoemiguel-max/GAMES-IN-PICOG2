extends CanvasLayer
# Web integration for the supplied OpenSaber export. Not a replacement game.
const SONG_PATH := "user://OpenSaber/Songs/PicoPulse/"
var _callback
var _browser
var _last_state := ""
var _status_timer := 0.0
var _held_trigger := 0.0
var _hold_consumed := false

func _ready() -> void:
    _install_training_song()
    if OS.has_feature("web"):
        _browser = JavaScriptBridge.get_interface("opensaber")
        _callback = JavaScriptBridge.create_callback(_web_command)
        if _browser != null:
            _browser.command = _callback
    call_deferred("_announce_ready")

func _install_training_song() -> void:
    DirAccess.make_dir_recursive_absolute(SONG_PATH)
    for filename in ["Info.dat", "Right.dat", "Left.dat", "Both.dat", "pulse.ogg", "cover.png"]:
        var data := FileAccess.get_file_as_bytes("res://g2/training/" + filename)
        var file := FileAccess.open(SONG_PATH + filename, FileAccess.WRITE)
        if file != null:
            file.store_buffer(data)
    for filename in ["user://highscores.json", "user://play_count.json"]:
        if not FileAccess.file_exists(filename):
            var file := FileAccess.open(filename, FileAccess.WRITE)
            if file != null:
                file.store_string("{}")

func _announce_ready() -> void:
    await get_tree().process_frame
    var game = GlobalReferences.main_game_scene
    while not is_instance_valid(game) or not game.is_node_ready() or not game.cube_pool.prepared:
        await get_tree().process_frame
        game = GlobalReferences.main_game_scene
    if _browser != null:
        _browser.ready = true
    print("[G2] OpenSaber web patch 1.0.0 ready; original game + Pico Pulse training")

func _web_command(args: Array) -> void:
    if args.is_empty(): return
    var game = GlobalReferences.main_game_scene
    if not is_instance_valid(game): return
    var command := str(args[0])
    match command:
        "vr":
            if is_instance_valid(vr.webxr_initializer):
                vr.webxr_initializer._on_button_pressed()
        "right", "left", "both":
            var info := Map.load_map_info(SONG_PATH)
            if info == null or info.difficulty_beatmaps.size() < 3: return
            game.song_player.stop()
            game.start_map(info, info.difficulty_beatmaps[{"right": 0, "left": 1, "both": 2}[command]])
        "pause":
            if game.gamestate == game.gamestate_playing:
                game._transition_game_state(game.gamestate_paused)
        "resume":
            if game.gamestate == game.gamestate_paused:
                # No duplicate countdowns if a button is pressed repeatedly.
                game.song_player.play(game.pause_position)
                game._transition_game_state(game.gamestate_playing)
        "menu":
            game.song_player.stop()
            game._main_menu_button()
        "recenter":
            game.recenter()

func _process(delta: float) -> void:
    var game = GlobalReferences.main_game_scene
    if not is_instance_valid(game) or not game.is_node_ready(): return
    # G2 often has no B/Y button. Holding trigger pauses, without reserving
    # short presses needed by the game's original raycast menu.
    if vr.inVR and game.gamestate == game.gamestate_playing:
        var held: bool = (game.left_controller.visible and game.left_controller.trigger) or (game.right_controller.visible and game.right_controller.trigger)
        _held_trigger = _held_trigger + delta if held else 0.0
        if not held: _hold_consumed = false
        if _held_trigger > 1.2 and not _hold_consumed:
            _hold_consumed = true
            game._transition_game_state(game.gamestate_paused)
    else:
        _held_trigger = 0.0
    _status_timer += delta
    if _status_timer < 0.25: return
    _status_timer = 0.0
    var state := "menu"
    if game.gamestate == game.gamestate_playing: state = "playing"
    elif game.gamestate == game.gamestate_paused: state = "paused"
    elif game.gamestate == game.gamestate_mapcomplete or game.gamestate == game.gamestate_newhighscore: state = "complete"
    if state != _last_state:
        print("[G2] state=" + state)
        _last_state = state
    if _browser != null:
        _browser.state = state
        _browser.audioPlaying = game.song_player.playing
        _browser.audioTime = game.song_player.get_playback_position()
        _browser.notesRemaining = Map.note_stack.size()
        _browser.points = Scoreboard.points
        _browser.fps = Engine.get_frames_per_second()
