extends RefCounted
static func apply(face:MeshInstance3D,amount:float)->void:
 if not is_instance_valid(face):return
 amount=clampf(amount,0.0,1.0)
 var found:=false
 for entry in [["MouthOpen",amount],["EyesBulge",clampf(amount*1.15,0,1)],["Scream",clampf((amount-.55)*.45,0,.22)]]:
  var index:=face.find_blend_shape_by_name(entry[0])
  if index>=0:face.set_blend_shape_value(index,entry[1]);found=true
 # The downloaded monster uses jaw/eye bones rather than blend shapes.
 if not found and face.get_parent() is Skeleton3D:
  var rig:=face.get_parent() as Skeleton3D
  var jaw:=rig.find_bone("jaw")
  if jaw>=0:rig.set_bone_pose_rotation(jaw,rig.get_bone_rest(jaw).basis.get_rotation_quaternion()*Quaternion(Vector3.RIGHT,amount*.32))
  for name in ["eye_L","eye_R"]:
   var eye:=rig.find_bone(name)
   if eye>=0:rig.set_bone_pose_scale(eye,rig.get_bone_rest(eye).basis.get_scale()*(1.0+amount*.12))
