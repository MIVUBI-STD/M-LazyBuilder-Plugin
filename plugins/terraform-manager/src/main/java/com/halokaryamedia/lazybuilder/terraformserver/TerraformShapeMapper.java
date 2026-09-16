package com.halokaryamedia.lazybuilder.terraformserver;

import com.halokaryamedia.lazybuilder.terraform.*;
import com.halokaryamedia.lazybuilder.terraform.wire.TerraformWireProtocol;
import java.util.List;

final class TerraformShapeMapper {
    private TerraformShapeMapper() {}
    static BoundedShapeField shape(TerraformWireProtocol.Apply request) {
        TerrainTool tool = TerrainTool.valueOf(request.tool().name());
        TerrainVariation variation = TerrainVariation.valueOf(request.variation().name());
        List<Vec3d> points = request.points().stream().map(p -> new Vec3d(p.x(), p.y(), p.z())).toList();
        return TerrainShapeFactory.create(tool, points, new Vec3d(request.frontX(), 0.0, request.frontZ()),
                request.size(), request.height(), variation, request.seed());
    }
}
