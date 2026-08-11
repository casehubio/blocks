package io.casehub.blocks.trust;

import java.util.List;
import java.util.UUID;

public sealed interface VouchResult {
    record Accepted(UUID attestationEntryId) implements VouchResult {}
    record Rejected(List<String> reasons) implements VouchResult {}
}
