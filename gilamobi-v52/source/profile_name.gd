extends RefCounted
# The broker is public: don't trust arbitrary incoming labels. Limit the
# length and remove control/directional formatting characters.
static func clean(value:Variant)->String:
 if not value is String:return "Jogador"
 var result:=""
 for i in value.length():
  var code:int=value.unicode_at(i)
  if code<32 or (code>=127 and code<=159) or (code>=0x200b and code<=0x200f) or (code>=0x202a and code<=0x202e) or (code>=0x2066 and code<=0x2069) or code==0xfeff:continue
  result+=value.substr(i,1)
  if result.length()>=20:break
 result=result.strip_edges()
 return "Jogador" if result.is_empty() else result
