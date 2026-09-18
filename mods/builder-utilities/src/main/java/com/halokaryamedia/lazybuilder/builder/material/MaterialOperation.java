package com.halokaryamedia.lazybuilder.builder.material;

import com.halokaryamedia.lazybuilder.builder.operation.BuilderOperation;

/** Operation contract for tools whose block output is resolved by the shared material engine. */
public interface MaterialOperation extends BuilderOperation {
    BuilderMaterial material();

    default MaterialMask materialMask() {
        return MaterialMask.all();
    }
}
