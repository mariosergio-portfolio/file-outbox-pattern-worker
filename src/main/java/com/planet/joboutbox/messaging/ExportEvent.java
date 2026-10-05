package com.planet.joboutbox.messaging;

import com.planet.joboutbox.domain.ExportFormat;
import com.planet.joboutbox.domain.FileDomain;

import java.util.List;
import java.util.UUID;

public record ExportEvent(
        UUID exportId,
        String state,
        Long companyId,
        FileDomain domain,
        ExportFormat format,
        List<String> columns,
        String customerEmail
) {}
