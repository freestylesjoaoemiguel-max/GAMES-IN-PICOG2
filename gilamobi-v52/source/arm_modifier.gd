extends SkeletonModifier3D

var target: Node3D
var player: Node3D
var suffix := "L"
var side := -1.0
var g2_palm_local:=Basis.IDENTITY
var g2_palm_cached:=false

func _process_modification() -> void:
 var sk := get_skeleton()
 if sk == null or target == null: return
 var upper := sk.find_bone("upperarm_" + suffix)
 var lower := sk.find_bone("lowerarm_" + suffix)
 var hand := sk.find_bone("hand_" + suffix)
 if upper < 0 or lower < 0 or hand < 0: return
 var upper_pose := sk.get_bone_global_pose(upper)
 var lower_pose := sk.get_bone_global_pose(lower)
 var hand_pose := sk.get_bone_global_pose(hand)
 var start := upper_pose.origin
 var end := sk.global_transform.affine_inverse() * target.global_position
 var rest_a := lower_pose.origin - start
 var rest_b := hand_pose.origin - lower_pose.origin
 var l1 := rest_a.length()
 var l2 := rest_b.length()
 if l1 < 0.000001 or l2 < 0.000001: return
 var delta := end - start
 var distance := maxf(delta.length(), 0.000001)
 var direction := delta / distance
 # Arms only stretch when the target is beyond the original reach.
 var stretch := clampf(distance / (l1 + l2) * 1.01, 1.0, 2.5)
 l1 *= stretch
 l2 *= stretch
 var reached := minf(distance, (l1 + l2) * 0.999)
 var pole_world: Vector3 = player.global_basis * Vector3(side * 0.6, -0.1, 0.8)
 var pole := sk.global_basis.inverse() * pole_world
 pole -= direction * pole.dot(direction)
 if pole.length_squared() < 0.0000001:
  pole = direction.cross(Vector3.UP)
 pole = pole.normalized()
 var along := (l1*l1 - l2*l2 + reached*reached) / (2.0 * reached)
 var bend := sqrt(maxf(0.0, l1*l1 - along*along))
 var elbow := start + direction * along + pole * bend
 var end_reached := start + direction * reached
 var upper_rotation := Quaternion(rest_a.normalized(), (elbow - start).normalized())
 var lower_rotation := Quaternion(rest_b.normalized(), (end_reached - elbow).normalized())
 var stretch_basis := Basis.from_scale(Vector3(1.0, stretch, 1.0))
 var upper_basis := Basis(upper_rotation) * upper_pose.basis.orthonormalized() * stretch_basis
 var lower_basis := Basis(lower_rotation) * lower_pose.basis.orthonormalized() * stretch_basis
 sk.set_bone_global_pose(upper, Transform3D(upper_basis, start))
 sk.set_bone_global_pose(lower, Transform3D(lower_basis, elbow))
 sk.set_bone_global_pose(hand, Transform3D(Basis(lower_rotation) * hand_pose.basis.orthonormalized(), end_reached))

 # Align the hand's finger axis with the controller, including measured roll.
 if has_meta("g2_orientation"):
  if not g2_palm_cached:
   var index:=sk.find_bone("index1_"+suffix)
   var pinkie:=sk.find_bone("pinkie1_"+suffix)
   var rest_hand:=sk.get_bone_global_rest(hand)
   if index>=0 and pinkie>=0:
    var a:=sk.get_bone_global_rest(index).origin-rest_hand.origin
    var b:=sk.get_bone_global_rest(pinkie).origin-rest_hand.origin
    var forward:=(a+b).normalized()
    var right:=(a-b).normalized()*(1.0 if suffix=="R" else -1.0)
    var up:=right.cross(forward).normalized()
    right=forward.cross(up).normalized()
    if up.length_squared()>0.9:
     g2_palm_local=rest_hand.basis.orthonormalized().inverse()*Basis(right,up,-forward)
   g2_palm_cached=true
  var hand_basis:=sk.global_basis.orthonormalized().inverse()*target.global_basis.orthonormalized()*g2_palm_local.inverse()
  sk.set_bone_global_pose(hand,Transform3D(hand_basis,end_reached))
