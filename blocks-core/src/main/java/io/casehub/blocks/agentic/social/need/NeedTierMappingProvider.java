package io.casehub.blocks.agentic.social.need;

import java.util.Map;
import java.util.Set;

public interface NeedTierMappingProvider {
    Map<String, Set<NeedTier>> tierMapping();
}
