package ru.x5.devpulse.adapter.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Ответ {@code /merge_requests/:iid/approvals}: кто заапрувил. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitlabApprovalsDto(
        @JsonProperty("approved_by") List<ApprovedBy> approvedBy
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApprovedBy(GitlabUserDto user) {}
}
