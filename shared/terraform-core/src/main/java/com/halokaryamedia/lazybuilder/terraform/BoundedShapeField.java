package com.halokaryamedia.lazybuilder.terraform;

/** Shape field with an explicit finite evaluation region. */
public interface BoundedShapeField extends ShapeField {
    ShapeBounds bounds();
}
