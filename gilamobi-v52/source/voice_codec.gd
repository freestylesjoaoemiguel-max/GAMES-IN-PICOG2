extends RefCounted
# G.711 mu-law: independent samples, so a dropped packet cannot corrupt the next.
static func encode_sample(input:int)->int:
 var sign:=0
 var value:=clampi(input,-32768,32767)
 if value<0:sign=0x80;value=-value
 value=mini(value,32635)+132
 var exponent:=7
 var mask:=0x4000
 while exponent>0 and (value & mask)==0:
  exponent-=1;mask>>=1
 var mantissa:=(value>>(exponent+3)) & 15
 return (~(sign | (exponent<<4) | mantissa)) & 255

static func decode_sample(code:int)->float:
 var u:=(~code)&255
 var value:=(((u & 15)<<3)+132)<<((u>>4)&7)
 value-=132
 if (u & 128)!=0:value=-value
 return float(value)/32768.0

static func encode_pcm(data:PackedByteArray)->PackedByteArray:
 var result:=PackedByteArray()
 result.resize(data.size()/2)
 for i in result.size():
  var value:=int(data[2*i])|(int(data[2*i+1])<<8)
  if value>=32768:value-=65536
  result[i]=encode_sample(value)
 return result
