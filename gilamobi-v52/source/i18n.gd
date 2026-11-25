extends Node
const LANGUAGES:=["pt","en","ru"]
var language:="pt"
func _ready()->void:
 var data=JSON.parse_string(FileAccess.get_file_as_string("res://i18n/translations.json"))
 if data is Dictionary:
  var source:=Translation.new();source.locale="pt"
  for key in data.get("en",{}):source.add_message(key,key)
  TranslationServer.add_translation(source)
  for locale in ["en","ru"]:
   var translation:=Translation.new();translation.locale=locale
   for key in data.get(locale,{}):translation.add_message(key,str(data[locale][key]))
   TranslationServer.add_translation(translation)
 var cfg:=ConfigFile.new()
 if cfg.load("user://idioma.cfg")==OK:
  var saved:=str(cfg.get_value("idioma","codigo","pt"))
  if saved in LANGUAGES:language=saved
 TranslationServer.set_locale(language)
func set_language(value:String)->void:
 if value not in LANGUAGES:return
 language=value;TranslationServer.set_locale(value)
 var cfg:=ConfigFile.new();cfg.set_value("idioma","codigo",value);cfg.save("user://idioma.cfg")
func native_status(value:String)->String:
 # Translate public status labels while leaving exception names untouched.
 if value.begins_with("erro:"):return tr("Erro no dispositivo")
 return tr(value)
