package com.planet.joboutbox.messaging;

import com.planet.joboutbox.domain.FileDomain;

import java.util.UUID;

public record ImportEvent(
        UUID importId,
        String state,
        String fileReference,
        Long companyId,
        FileDomain domain
) {}
