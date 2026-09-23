package io.casehub.blocks.social.jpa;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.casehub.blocks.agentic.social.narrative.DerivedTheme;
import io.casehub.blocks.agentic.social.narrative.GroupEpisode;
import io.casehub.blocks.agentic.social.narrative.IndividualEpisode;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = IndividualEpisode.class, name = "individual"),
        @JsonSubTypes.Type(value = GroupEpisode.class, name = "group"),
        @JsonSubTypes.Type(value = DerivedTheme.class, name = "theme")
})
abstract class NarrativeFragmentMixin {}
