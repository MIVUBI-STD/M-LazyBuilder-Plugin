package com.halokaryamedia.lazybuilder.client;

import com.halokaryamedia.lazybuilder.client.MapAreaSelectionState.DragMode;

/** Pure screen-space geometry for area-selection bounds, handles, and hit testing. */
final class MapAreaSelectionGeometry {
    private MapAreaSelectionGeometry() {}

    static SelectionRect rect(int x1, int y1, int x2, int y2) {
        return new SelectionRect(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.max(x1, x2),
                Math.max(y1, y2));
    }

    static Handle[] handles(SelectionRect rect) {
        int midX = rect.left() + (rect.right() - rect.left()) / 2;
        int midY = rect.top() + (rect.bottom() - rect.top()) / 2;
        return new Handle[] {
                new Handle(rect.left(), rect.top(), DragMode.NW),
                new Handle(midX, rect.top(), DragMode.N),
                new Handle(rect.right(), rect.top(), DragMode.NE),
                new Handle(rect.right(), midY, DragMode.E),
                new Handle(rect.right(), rect.bottom(), DragMode.SE),
                new Handle(midX, rect.bottom(), DragMode.S),
                new Handle(rect.left(), rect.bottom(), DragMode.SW),
                new Handle(rect.left(), midY, DragMode.W)
        };
    }

    static DragMode hit(
            SelectionRect rect,
            double mouseX,
            double mouseY,
            int handleRadius
    ) {
        for (Handle handle : handles(rect)) {
            if (Math.abs(mouseX - handle.x()) <= handleRadius + 3
                    && Math.abs(mouseY - handle.y()) <= handleRadius + 3) {
                return handle.mode();
            }
        }
        if (mouseX > rect.left() + handleRadius
                && mouseX < rect.right() - handleRadius
                && mouseY > rect.top() + handleRadius
                && mouseY < rect.bottom() - handleRadius) {
            return DragMode.MOVE;
        }
        return DragMode.NONE;
    }

    record Handle(int x, int y, DragMode mode) {}

    record SelectionRect(int left, int top, int right, int bottom) {}
}
