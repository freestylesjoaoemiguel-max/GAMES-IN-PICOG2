extends Control
# 21 MediaPipe landmarks in camera image coordinates.
var points: Array = []
var image_size:=Vector2(640,480)
const LINKS := [[0,1],[1,2],[2,3],[3,4],[0,5],[5,6],[6,7],[7,8],[5,9],[9,10],[10,11],[11,12],[9,13],[13,14],[14,15],[15,16],[13,17],[17,18],[18,19],[19,20],[17,0]]
func set_hands(value:Array,dimensions:Vector2)->void:
 points=value
 if dimensions.x>0 and dimensions.y>0:image_size=dimensions
 queue_redraw()
func _draw()->void:
 if image_size.x<=0 or image_size.y<=0:return
 var scale_factor:=minf(size.x/image_size.x,size.y/image_size.y)
 var shown:=image_size*scale_factor
 var offset:=(size-shown)*.5
 for hand in points:
  if not hand is Array or hand.size()!=21:continue
  var coords:Array[Vector2]=[]
  for p in hand:
   if p is Array and p.size()>=2:coords.append(offset+Vector2(float(p[0]),float(p[1]))*shown)
  if coords.size()!=21:continue
  for edge in LINKS:draw_line(coords[edge[0]],coords[edge[1]],Color("40f6c8"),3.0,true)
  for p in coords:draw_circle(p,4.0,Color("fff3a0"))
