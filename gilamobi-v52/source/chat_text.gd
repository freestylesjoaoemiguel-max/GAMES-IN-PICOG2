extends RefCounted
const MAX_CHARS:=96
static func clean(value:Variant)->String:
 if not value is String:return ""
 var s:String=value
 # Single line only, collapse whitespace and strip control and invisible bidi controls.
 var out:="";var prev_space:=false
 for i in mini(s.length(),MAX_CHARS*2):
  var u:=s.unicode_at(i)
  if u<32 or u==127 or (u>=0x200b and u<=0x200f) or (u>=0x202a and u<=0x202e) or (u>=0x2066 and u<=0x2069):continue
  if u==32:
   if prev_space:continue
   prev_space=true
  else:prev_space=false
  out+=String.chr(u)
  if out.length()>=MAX_CHARS:break
 return out.strip_edges()
