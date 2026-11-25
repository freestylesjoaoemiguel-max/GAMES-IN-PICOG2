try{THREE.ShaderChunk.dithering_fragment+="\n{float _g=dot(gl_FragColor.rgb,vec3(.299,.587,.114));gl_FragColor.rgb=mix(vec3(_g),gl_FragColor.rgb,.82);}\n"}catch(e){}
