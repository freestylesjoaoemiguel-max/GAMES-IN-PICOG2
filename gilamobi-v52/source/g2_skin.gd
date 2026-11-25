extends RefCounted
# Split the supplied skinned mesh by bone weights, keeping its original rig,
# materials and body. This allows a disconnected arm to be hidden independently.
static func split(mesh: MeshInstance3D, rig: Skeleton3D) -> Dictionary:
    var source := mesh.mesh as ArrayMesh
    var skin := mesh.skin
    var parts: Array[ArrayMesh] = [ArrayMesh.new(),ArrayMesh.new(),ArrayMesh.new()]
    var selected := [{},{}]
    var radii := [0.08,0.08]
    for side in 2:
        var root := rig.find_bone("upperarm_"+("L" if side==0 else "R"))
        for bind in skin.get_bind_count():
            var bone := rig.find_bone(str(skin.get_bind_name(bind)))
            if bone<0: bone=skin.get_bind_bone(bind)
            while bone>=0:
                if bone==root:
                    selected[side][bind]=true
                    break
                bone=rig.get_bone_parent(bone)
    for surface in source.get_surface_count():
        var arrays := source.surface_get_arrays(surface)
        var vertices: PackedVector3Array = arrays[Mesh.ARRAY_VERTEX]
        var bones: PackedInt32Array = arrays[Mesh.ARRAY_BONES]
        var weights: PackedFloat32Array = arrays[Mesh.ARRAY_WEIGHTS]
        var indices: PackedInt32Array = arrays[Mesh.ARRAY_INDEX]
        if vertices.is_empty() or bones.is_empty(): continue
        if indices.is_empty():
            for index in vertices.size(): indices.append(index)
        var count := int(bones.size()/vertices.size())
        var labels := PackedInt32Array()
        labels.resize(vertices.size())
        for v in vertices.size():
            labels[v]=2
            for side in 2:
                var weight := 0.0
                for j in count:
                    var at:=v*count+j
                    if selected[side].has(bones[at]): weight+=weights[at]
                if weight>=0.5: labels[v]=side
        var kept: Array[PackedInt32Array]=[PackedInt32Array(),PackedInt32Array(),PackedInt32Array()]
        for i in range(0,indices.size(),3):
            var a:=labels[indices[i]]
            var b:=labels[indices[i+1]]
            var c:=labels[indices[i+2]]
            var part:=a if a==b or a==c else b if b==c else 2
            for j in 3: kept[part].append(indices[i+j])
        for part in 3:
            if kept[part].is_empty(): continue
            var copy:=arrays.duplicate()
            copy[Mesh.ARRAY_INDEX]=kept[part]
            parts[part].add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES,copy,[],{},Mesh.ARRAY_FLAG_USE_8_BONE_WEIGHTS if count==8 else 0)
            parts[part].surface_set_material(parts[part].get_surface_count()-1,source.surface_get_material(surface))
    var arms: Array[MeshInstance3D]=[]
    for side in 2:
        var clone:=MeshInstance3D.new()
        clone.name="G2ArmLeft" if side==0 else "G2ArmRight"
        clone.mesh=parts[side]
        clone.skin=skin
        clone.skeleton=mesh.skeleton
        clone.transform=mesh.transform
        clone.layers=mesh.layers
        mesh.get_parent().add_child(clone)
        arms.append(clone)
    mesh.mesh=parts[2]
    return {"arms":arms,"original":source,"radius":0.18}
