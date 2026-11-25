extends RefCounted
# Additive v19 state fields. Old clients still read positions/names, but must
# update to this version to display the new badge and controller orientations.
static func encode_rotation(basis:Basis) -> Array:
    var q:=basis.orthonormalized().get_rotation_quaternion().normalized()
    return [q.x,q.y,q.z,q.w]

static func decode_rotation(value:Variant) -> Variant:
    if not value is Array or value.size()!=4:return null
    for number in value:
        if typeof(number) not in [TYPE_FLOAT,TYPE_INT] or not is_finite(float(number)) or absf(float(number))>1.01:return null
    var q:=Quaternion(float(value[0]),float(value[1]),float(value[2]),float(value[3]))
    if q.length_squared()<.5 or q.length_squared()>1.5:return null
    return q.normalized()

static func state(game:Node) -> Dictionary:
    var active:bool=is_instance_valid(game.g2_xr) and game.g2_xr.immersive
    var mask:=0
    if active:
        for i in 2:
            if game.g2_xr.tracked[i]:mask|=1<<i
    var packet:={"vr_pico_g2":active,"xr_hands":mask}
    for i in 2:
        if active and (mask & (1<<i))!=0:
            packet["left_rot" if i==0 else "right_rot"]=encode_rotation(game.targets[i].global_basis)
    return packet
