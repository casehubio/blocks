package io.casehub.blocks.annotations.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class BlocksAnnotationsProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("casehub-blocks-annotations");
    }
}
